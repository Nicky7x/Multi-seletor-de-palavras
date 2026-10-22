package com.example.pickwords

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class PickWordsService : AccessibilityService() {

    private class Word(val text: String, val rect: Rect) {
        var selected = false
        var view: View? = null
    }

    private lateinit var wm: WindowManager
    private var fab: View? = null
    private var overlay: FrameLayout? = null
    private var copyBtn: Button? = null
    private var words = listOf<Word>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showFab()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        closeOverlay()
        fab?.let { runCatching { wm.removeView(it) } }
        fab = null
        super.onDestroy()
    }

    // Botão flutuante (arrastável na vertical)
    private fun showFab() {
        val lp = WindowManager.LayoutParams(
            dp(44), dp(44),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(2) }

        var downY = 0f
        var startY = 0
        var moved = false
        val btn = TextView(this).apply {
            text = "✎"; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC3F51B5"))
            }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = e.rawY; startY = lp.y; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dy) > dp(6)) moved = true
                        if (moved) { lp.y = startY + dy; wm.updateViewLayout(v, lp) }
                        true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) startPicking(); true }
                    else -> false
                }
            }
        }
        wm.addView(btn, lp)
        fab = btn
    }

    // Leitura da tela
    private fun startPicking() {
        if (overlay != null) return
        val root = rootInActiveWindow
        if (root == null) { toast("Não consegui ler esta tela"); return }
        val found = mutableListOf<Word>()
        collect(root, found)
        if (found.isEmpty()) { toast("Nenhum texto selecionável encontrado"); return }
        words = found
        fab?.visibility = View.GONE
        showOverlay()
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Word>) {
        if (n == null || !n.isVisibleToUser || n.isPassword) return
        val text = n.text?.toString()
            ?: if (n.childCount == 0) n.contentDescription?.toString() else null
        if (!text.isNullOrBlank()) addWords(n, text, out)
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun addWords(n: AccessibilityNodeInfo, text: String, out: MutableList<Word>) {
        val b = Rect(); n.getBoundsInScreen(b)
        if (b.isEmpty) return
        val chars = charRects(n, text)
        Regex("\\S+").findAll(text).forEach { m ->
            val s = m.range.first
            val e = m.range.last + 1
            var r: Rect? = null
            if (chars != null) {
                val u = RectF(); var any = false
                for (i in s until e) {
                    val c = chars.getOrNull(i) ?: continue
                    if (any) u.union(c) else { u.set(c); any = true }
                }
                if (any) r = Rect(u.left.toInt(), u.top.toInt(), u.right.toInt(), u.bottom.toInt())
            }
            if (r == null) {
                val w = b.width().toFloat()
                r = Rect(b.left + (w * s / text.length).toInt(), b.top,
                         b.left + (w * e / text.length).toInt(), b.bottom)
            }
            if (!r.isEmpty) out += Word(m.value, r)
        }
    }

    @Suppress("DEPRECATION")
    private fun charRects(n: AccessibilityNodeInfo, text: String): List<RectF?>? {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                minOf(text.length, AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH))
        }
        return try {
            if (n.refreshWithExtraData(key, args)) n.extras.getParcelableArray(key)?.map { it as? RectF }
            else null
        } catch (e: Exception) { null }
    }

    // Overlay de seleção
    private fun boxBg(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(4).toFloat()
        if (selected) { setColor(Color.parseColor("#88FFC107")); setStroke(dp(1), Color.parseColor("#FFFF9800")) }
        else setStroke(dp(1), Color.parseColor("#664F6BED"))
    }

    private fun showOverlay() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#33000000")) }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        root.post {
            val loc = IntArray(2); root.getLocationOnScreen(loc)
            words.forEach { w ->
                val v = View(this).apply { background = boxBg(false) }
                root.addView(v, 0, FrameLayout.LayoutParams(w.rect.width(), w.rect.height()).apply {
                    leftMargin = w.rect.left - loc[0]; topMargin = w.rect.top - loc[1]
                })
                w.view = v
            }
        }

        var paint = true
        root.setOnTouchListener { _, e ->
            val x = e.rawX.toInt(); val y = e.rawY.toInt()
            val hit = words.lastOrNull {
                Rect(it.rect).apply { inset(-dp(3), -dp(2)) }.contains(x, y)
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { if (hit != null) { paint = !hit.selected; setSel(hit, paint) } }
                MotionEvent.ACTION_MOVE -> { if (hit != null) setSel(hit, paint) }
            }
            true
        }

        fun barBtn(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }
        }
        val copy = barBtn("Copiar (0)") { copySelected() }
        copyBtn = copy
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isClickable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(Color.parseColor("#EE263238"))
            }
            addView(barBtn("Cancelar") { closeOverlay() })
            addView(barBtn("Limpar") { words.forEach { setSel(it, false) } })
            addView(copy)
        }
        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(72) })

        wm.addView(root, lp)
        overlay = root
    }

    private fun setSel(w: Word, sel: Boolean) {
        if (w.selected == sel) return
        w.selected = sel
        w.view?.background = boxBg(sel)
        copyBtn?.text = "Copiar (" + words.count { it.selected } + ")"
    }

    private fun closeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null; copyBtn = null; words = emptyList()
        fab?.visibility = View.VISIBLE
    }

    // Copiar
    private fun readingOrder(l: List<Word>): List<Word> {
        val out = mutableListOf<Word>()
        var line = mutableListOf<Word>()
        var anchor = 0
        for (w in l.sortedBy { it.rect.centerY() }) {
            if (line.isNotEmpty() && w.rect.centerY() - anchor > w.rect.height() / 2) {
                out += line.sortedBy { it.rect.left }; line = mutableListOf()
            }
            if (line.isEmpty()) anchor = w.rect.centerY()
            line += w
        }
        out += line.sortedBy { it.rect.left }
        return out
    }

    private fun copySelected() {
        val sel = readingOrder(words.filter { it.selected })
        if (sel.isEmpty()) { toast("Nenhuma palavra selecionada"); return }
        val punct = charArrayOf('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '“', '”')
        val text = sel.joinToString(" ") { w -> w.text.trim(*punct).ifEmpty { w.text } }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("PickWords", text))
        toast(sel.size.toString() + " palavra(s) copiada(s)")
        closeOverlay()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
