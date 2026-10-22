package com.example.pickwords

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

/**
 * PickWords v3: detecta seleções NATIVAS de texto (alças do Android).
 * Não depende da abertura do teclado e não desenha caixas sobre palavras.
 *
 * Acessibilidade não fornece evento dedicado para "alças ficaram visíveis":
 * TYPE_VIEW_TEXT_SELECTION_CHANGED com intervalo não vazio é o sinal mais
 * próximo. Alguns navegadores/apps não emitem esse evento ou não expõem
 * o conteúdo selecionado.
 */
class PickWordsService : AccessibilityService() {
    private data class CapturedSelection(
        val packageName: String,
        val windowId: Int,
        val start: Int,
        val end: Int,
        val content: String
    ) {
        val selected: String get() = content.substring(start, end)
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var toolbar: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    private var countLabel: TextView? = null
    private var previewLabel: TextView? = null

    private var activeSelection: CapturedSelection? = null
    private var lastSelectionAt: Long = 0L
    private val collected = mutableListOf<CapturedSelection>()
    private var appPackage: String? = null

    private val dismissal = Runnable {
        if (collected.isEmpty() && SystemClock.uptimeMillis() - lastSelectionAt > 12000L) {
            activeSelection = null
            removeToolbar()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::wm.isInitialized) return

        // Um intervalo não vazio indica seleção; cursor sozinho tem start == end.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val candidate = readSelection(event)
            if (candidate != null) {
                // Nunca misturar trechos de aplicativos diferentes.
                if (appPackage != null && appPackage != candidate.packageName) resetAll()
                appPackage = candidate.packageName
                activeSelection = candidate
                lastSelectionAt = SystemClock.uptimeMillis()
                showToolbar()
                updateToolbar()
                handler.removeCallbacks(dismissal)
                handler.postDelayed(dismissal, 12500L)
            }
            // Não apagamos a última seleção ao receber o evento de cursor:
            // alguns apps colapsam a seleção ao tocar em controles externos.
            return
        }

        // Se o app em primeiro plano mudou, não mantemos o conteúdo anterior.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName && appPackage != null && pkg != appPackage) {
                // Ignora uma mudança de janela de teclado ou sistema dentro do mesmo app.
                val sameAppVisible = windows.any {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    it.isActive &&
                    it.root?.packageName?.toString() == appPackage
                }
                if (!sameAppVisible) resetAll()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(dismissal)
        resetAll()
        super.onDestroy()
    }

    private fun readSelection(event: AccessibilityEvent): CapturedSelection? {
        if (event.isPassword) return null
        val node = event.source
        if (node?.isPassword == true) return null
        val pkg = event.packageName?.toString() ?: node?.packageName?.toString() ?: return null
        if (pkg == packageName) return null

        val nodeText = node?.text?.toString()
        val eventText = event.text.firstOrNull()?.toString()
        val text = if (!nodeText.isNullOrEmpty()) nodeText else eventText ?: return null

        // A maioria dos TextView/EditText fornece índices no evento.
        // Em outros, os índices só estão em AccessibilityNodeInfo.
        val eStart = event.fromIndex
        val eEnd = event.toIndex
        val nStart = node?.textSelectionStart ?: -1
        val nEnd = node?.textSelectionEnd ?: -1
        val eventValid = eStart >= 0 && eEnd > eStart && eEnd <= text.length
        val nodeValid = nStart >= 0 && nEnd > nStart && nEnd <= text.length

        val start = when {
            eventValid -> eStart
            nodeValid -> nStart
            else -> return null
        }
        val end = if (eventValid) eEnd else nEnd
        val selection = text.substring(start, end)

        if (selection.isBlank() || selection.length > 5000) return null
        return CapturedSelection(pkg, node?.windowId ?: event.windowId, start, end, text)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun panelBg() = GradientDrawable().apply {
        setColor(Color.rgb(39, 41, 47))
        cornerRadius = dp(16).toFloat()
    }

    // Não há gatilho pela abertura do teclado. Ele só muda o posicionamento.
    private fun bottomOffset(): Int {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val bounds = Rect()
        ime?.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return dp(74)
        val height = resources.displayMetrics.heightPixels
        val size = height - bounds.top
        return if (size in dp(130)..(height * 0.7f).toInt()) size + dp(8) else dp(74)
    }

    private fun showToolbar() {
        if (toolbar != null) return
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(7), 0)
            background = panelBg()
            elevation = dp(7).toFloat()
        }
        val count = TextView(this).apply {
            text = "0"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
        }
        countLabel = count
        line.addView(count)

        fun pill(text: String, callback: () -> Unit) = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(7), 0, dp(7), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
            )
            setOnClickListener { callback() }
        }
        line.addView(pill("+ Seleção") { addSelection() })
        line.addView(pill("Copiar") { copySelections() })
        line.addView(pill("Limpar") { resetAll() })

        val outer = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(line)
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = bottomOffset()
        }
        try {
            wm.addView(outer, params)
            toolbar = outer
            toolbarParams = params
        } catch (_: Exception) {
            countLabel = null
        }
    }

    private fun updateToolbar() {
        countLabel?.text = collected.size.toString() + " ✓"
        toolbarParams?.let { params ->
            val updatedOffset = bottomOffset()
            if (params.y != updatedOffset) {
                params.y = updatedOffset
                toolbar?.let { runCatching { wm.updateViewLayout(it, params) } }
            }
        }
    }

    private fun removeToolbar() {
        toolbar?.let { runCatching { wm.removeView(it) } }
        toolbar = null
        toolbarParams = null
        countLabel = null
        previewLabel = null
    }

    private fun addSelection() {
        val current = activeSelection
        if (current == null) {
            toast("Selecione um trecho usando as alças do Android")
            return
        }
        if (collected.any {
                it.packageName == current.packageName &&
                it.windowId == current.windowId &&
                it.start == current.start &&
                it.end == current.end &&
                it.content == current.content
            }) {
            toast("Esse trecho já foi adicionado")
            return
        }
        collected.add(current)
        lastSelectionAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(dismissal)
        updateToolbar()
        toast("Adicionado: " + current.selected.take(40))
    }

    private fun copySelections() {
        if (collected.isEmpty()) {
            toast("Pressione + Seleção primeiro")
            return
        }
        val content = collected.joinToString(" ") { it.selected }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("PickWords", content))
        toast(collected.size.toString() + " trecho(s) copiado(s)")
        resetAll()
    }

    private fun resetAll() {
        activeSelection = null
        appPackage = null
        collected.clear()
        handler.removeCallbacks(dismissal)
        removeToolbar()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
