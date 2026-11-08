package com.example.floattrans.ui
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.floattrans.R
class ResultOverlayManager(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var lastText: String = ""
    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    fun show(resultText: String) {
        lastText = resultText
        if (root == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_result, null)
            view.findViewById<TextView>(R.id.tvResult).text = resultText
            view.findViewById<Button>(R.id.btnClose).setOnClickListener { dismiss() }
            view.findViewById<Button>(R.id.btnCopy).setOnClickListener {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("translation", lastText))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM; y = 120 }
            wm.addView(view, params); root = view
        } else { update(resultText) }
    }
    fun update(text: String) { lastText = text; root?.findViewById<TextView>(R.id.tvResult)?.text = text }
    fun dismiss() { root?.let { runCatching { wm.removeView(it) } }; root = null }
}
