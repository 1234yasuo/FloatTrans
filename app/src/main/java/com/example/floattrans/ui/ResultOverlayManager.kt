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
    var onDismiss: (() -> Unit)? = null
    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    fun showLoading() {
        dismiss()
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_panel, null)
        view.findViewById<LinearLayout>(R.id.layoutContent).addView(createTextView("正在全屏提取并翻译..."))
        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss(); onDismiss?.invoke() }
        addViewToWindow(view)
    }

    fun showResults(origLines: List<String>, transLines: List<String>) {
        dismiss()
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_panel, null)
        val container = view.findViewById<LinearLayout>(R.id.layoutContent)
        
        for (i in origLines.indices) {
            val orig = origLines[i].trim()
            if (orig.isEmpty()) continue
            val trans = if (i < transLines.size) transLines[i].trim() else "..."
            container.addView(createTextView("原文：$orig\n翻译：$trans\n"))
        }
        
        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss(); onDismiss?.invoke() }
        addViewToWindow(view)
    }

    private fun createTextView(text: String): TextView {
        return TextView(context).apply {
            this.text = text; setTextColor(android.graphics.Color.WHITE); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, 16, 0, 16); setLineSpacing(0f, 1.2f)
        }
    }

    private fun addViewToWindow(view: View) {
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        wm.addView(view, params); root = view
    }

    fun dismiss() { root?.let { runCatching { wm.removeView(it) } }; root = null }
}
