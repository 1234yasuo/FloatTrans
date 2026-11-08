package com.example.floattrans.service
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import androidx.core.app.ServiceCompat
import com.example.floattrans.R
import com.example.floattrans.translate.MlKitTranslator
import com.example.floattrans.ui.ResultOverlayManager
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.*
import kotlin.math.abs
class FloatingTranslateService : Service() {
    companion object { private const val CHANNEL_ID = "floating_translate"; private const val NOTIF_ID = 1001; private const val TOUCH_THRESHOLD_DP = 18f }
    private lateinit var wm: WindowManager
    private var floatView: View? = null
    private lateinit var resultOverlay: ResultOverlayManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val translator = MlKitTranslator()
    private val targetLang = TranslateLanguage.CHINESE
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        resultOverlay = ResultOverlayManager(this)
        startForegroundCompat()
        addFloatingBall()
        scope.launch { runCatching { translator.translate("hello", "en", targetLang) } }
    }
    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "全局翻译", NotificationManager.IMPORTANCE_LOW))
            val notif = Notification.Builder(this, CHANNEL_ID).setContentTitle("全局翻译运行中").setContentText("点击悬浮球翻译屏幕文字").setSmallIcon(android.R.drawable.ic_menu_search).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIF_ID, notif)
        }
    }
    private fun addFloatingBall() {
        val view = ImageView(this).apply { setImageResource(R.drawable.ic_launcher); setBackgroundResource(R.drawable.bg_ball) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(120, 120, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 400 }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val threshold = TOUCH_THRESHOLD_DP * resources.displayMetrics.density
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > threshold || abs(dy) > threshold)) dragging = true
                    if (dragging) { p.x = startX + dx.toInt(); p.y = startY + dy.toInt(); wm.updateViewLayout(view, p) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) onBallClicked(); true }
                else -> false
            }
        }
        wm.addView(view, p); floatView = view
    }
    private fun onBallClicked() {
        scope.launch {
            val rawText = withContext(Dispatchers.Default) { TranslationAccessibilityService.instance?.extractVisibleText().orEmpty() }
            if (rawText.isBlank()) { resultOverlay.show("未识别到可翻译文字"); return@launch }
            resultOverlay.show("翻译中…")
            runCatching { translator.translate(rawText, null, targetLang) }.onSuccess { resultOverlay.update(it) }.onFailure { resultOverlay.update("翻译失败：${it.message}") }
        }
    }
    override fun onDestroy() { super.onDestroy(); scope.cancel(); floatView?.let { runCatching { wm.removeView(it) } }; resultOverlay.dismiss() }
}
