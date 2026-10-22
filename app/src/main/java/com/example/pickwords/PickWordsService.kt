package com.example.pickwords

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Acessório não invasivo do teclado.
 * Lê somente o campo editável em foco, ao pressionar "+ Palavra".
 * O Android não suporta múltiplas seleções nativas em outro aplicativo.
 */
class PickWordsService : AccessibilityService() {

    private data class Fragment(val start: Int, val end: Int, val text: String)

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bar: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var countView: TextView? = null
    private val picked = mutableListOf<Fragment>()
    private var selectedFieldText: String? = null
    private var selectedFieldWindowId: Int? = null
    private var selectedFieldPackage: String? = null
    private val tokenRegex = Regex("""[\p{L}\p{N}_]+(?:['’\-][\p{L}\p{N}_]+)*""")
    private val refresh = Runnable { syncWithKeyboard() }

    private fun dp(x: Int) = (x * resources.displayMetrics.density + .5f).toInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        scheduleRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val node = event.source
            if (node?.isEditable == true && !node.isPassword && selectedFieldText != null) {
                if (node.windowId == selectedFieldWindowId &&
                    node.packageName?.toString() == selectedFieldPackage &&
                    node.text?.toString() != selectedFieldText
                ) clearPicked()
            }
        }
        scheduleRefresh()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        removeBar()
        clearPicked()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 160)
    }

    @Suppress("DEPRECATION")
    private fun deviceHeight(): Int {
        val p = Point()
        wm.defaultDisplay.getRealSize(p)
        return p.y
    }

    private fun getEditor(): AccessibilityNodeInfo? {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true && focused.isFocused && !focused.isPassword) return focused
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val node = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node?.isEditable == true && node.isFocused && !node.isPassword) return node
        }
        return null
    }

    private fun syncWithKeyboard() {
        if (!::wm.isInitialized) return
        val screen = deviceHeight()
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val keyboardRect = Rect()
        ime?.getBoundsInScreen(keyboardRect)

        // Sem teclado ou campo de edição não mostramos nada.
        val visible = ime != null && !keyboardRect.isEmpty &&
            keyboardRect.top > screen / 3 &&
            keyboardRect.top < screen - dp(80) &&
            getEditor() != null

        if (!visible) {
            removeBar()
            return
        }
        val offset = (screen - keyboardRect.top + dp(3)).coerceAtLeast(dp(80))
        if (bar == null) createBar(offset)
        else {
            val params = barParams ?: return
            if (params.y != offset) {
                params.y = offset
                bar?.let { runCatching { wm.updateViewLayout(it, params) } }
            }
        }
    }

    private fun bg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(Color.rgb(38, 40, 44))
    }

    private fun createBar(offset: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
            background = bg()
            elevation = dp(5).toFloat()
        }
        val counter = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
            text = "✓ 0"
        }
        countView = counter
        row.addView(counter)

        fun button(label: String, click: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
            )
            setOnClickListener { click() }
        }

        row.addView(button("+ Palavra") { addAtCursor() })
        row.addView(button("Copiar") { copyPicked() })
        row.addView(button("Limpar") { clearPicked() })

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(row)
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = offset
        }
        try {
            wm.addView(scroll, params)
            bar = scroll
            barParams = params
            refreshCounter()
        } catch (_: Exception) {
            countView = null
        }
    }

    private fun removeBar() {
        bar?.let { runCatching { wm.removeView(it) } }
        bar = null
        countView = null
        barParams = null
    }

    private fun refreshCounter() {
        countView?.text = "✓ " + picked.size
    }

    private fun clearPicked() {
        picked.clear()
        selectedFieldText = null
        selectedFieldWindowId = null
        selectedFieldPackage = null
        refreshCounter()
    }

    private fun addAtCursor() {
        val editor = getEditor()
        if (editor == null) {
            toast("Toque primeiro no campo de escrita")
            return
        }
        val content = editor.text?.toString().orEmpty()
        if (content.isEmpty()) {
            toast("O campo está vazio")
            return
        }
        val start = editor.textSelectionStart
        val end = editor.textSelectionEnd
        if (start < 0 || end < 0 || start > content.length || end > content.length) {
            toast("Este aplicativo não informa a posição do cursor")
            return
        }

        val from: Int
        val to: Int
        if (start != end) {
            from = min(start, end)
            to = max(start, end)
        } else {
            // A alça não precisa ser arrastada: basta pôr o cursor na palavra.
            val caret = start
            val matches = tokenRegex.findAll(content).toList()
            val token = matches.firstOrNull {
                caret > it.range.first && caret <= it.range.last + 1
            } ?: matches.firstOrNull { caret == it.range.first }
            if (token == null) {
                toast("Posicione o cursor sobre uma palavra")
                return
            }
            from = token.range.first
            to = token.range.last + 1
        }

        val pkg = editor.packageName?.toString()
        if (selectedFieldText != content ||
            selectedFieldWindowId != editor.windowId ||
            selectedFieldPackage != pkg
        ) clearPicked()

        selectedFieldText = content
        selectedFieldWindowId = editor.windowId
        selectedFieldPackage = pkg

        if (picked.any { it.start == from && it.end == to }) {
            toast("Essa palavra já foi adicionada")
            return
        }
        picked += Fragment(from, to, content.substring(from, to))
        refreshCounter()
        toast("Adicionada: " + content.substring(from, to).take(36))
    }

    private fun copyPicked() {
        if (picked.isEmpty()) {
            toast("Adicione uma palavra antes")
            return
        }
        val text = picked.sortedBy { it.start }.joinToString(" ") { it.text }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("PickWords", text))
        toast(picked.size.toString() + " seleção(ões) copiadas")
        clearPicked()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
