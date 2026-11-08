package com.example.floattrans.ui
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.floattrans.R

class ResultOverlayManager(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    fun showLoading() {
        dismiss()
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_panel, null)
        view.findViewById<LinearLayout>(R.id.layoutContent).addView(createTextView("正在提取并翻译..."))
        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }
        addViewToWindow(view)
    }

    fun showResults(originals: List<String>, translations: List<String>) {
        dismiss()
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_panel, null)
        val container = view.findViewById<LinearLayout>(R.id.layoutContent)
        for (i in originals.indices) {
            val original = originals[i]
            val translated = if (i < translations.size) translations[i] else "翻译失败"
            container.addView(createTextView("原文：$original\n翻译：$translated\n"))
        }
        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }
        addViewToWindow(view)
    }

    private fun createTextView(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, 12, 0, 12)
            setLineSpacing(0f, 1.2f)
        }
    }

    private fun addViewToWindow(view: View) {
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        wm.addView(view, params)
        root = view
    }

    fun dismiss() { root?.let { runCatching { wm.removeView(it) } }; root = null }
}
