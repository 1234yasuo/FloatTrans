package com.example.floattrans.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.example.floattrans.translate.MlKitTranslator
import com.example.floattrans.ui.ResultOverlayManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.*
import kotlin.math.abs
class FloatingTranslateService : Service() {
    private lateinit var wm: WindowManager
    private var ballView: TextView? = null
    private lateinit var resultOverlay: ResultOverlayManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val translator = MlKitTranslator()
    private val idClient = LanguageIdentification.getClient()
    private var currentSrcLang = "en"
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        resultOverlay = ResultOverlayManager(this)
        startForegroundCompat(); addFloatingBall()
        scope.launch(Dispatchers.IO) { while (isActive) { detectScreenLanguage(); delay(3000) } }
    }
    private suspend fun detectScreenLanguage() {
        val blocks = TranslationAccessibilityService.instance?.extractTextBlocks() ?: return
        if (blocks.isEmpty()) return
        val sampleText = blocks.take(5).joinToString(" ") { it.text }
        suspendCancellableCoroutine<String?> { cont ->
            idClient.identifyPossibleLanguages(sampleText).addOnSuccessListener { cont.resume(it.firstOrNull()?.languageTag?.substringBefore("-")) }.addOnFailureListener { cont.resume(null) }
        }?.let { lang -> if (lang != null && lang != "und" && lang != currentSrcLang) { currentSrcLang = lang; updateBallText(lang) } }
    }
    private fun updateBallText(langTag: String) {
        val display = when(langTag) { "en"->"英"; "zh"->"中"; "ja"->"日"; "ko"->"韩"; "fr"->"法"; "de"->"德"; "es"->"西"; "ru"->"俄"; else->"译" }
        scope.launch(Dispatchers.Main) { ballView?.text = display }
    }
    private fun addFloatingBall() {
        ballView = TextView(this).apply {
            text = "译"; setTextColor(android.graphics.Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(android.graphics.Color.parseColor("#DD2196F3")); shape = GradientDrawable.OVAL }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val size = (56 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 400 }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val threshold = 18f * resources.displayMetrics.density
        ballView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > threshold || abs(dy) > threshold)) dragging = true
                    if (dragging) { p.x = startX + dx.toInt(); p.y = startY + dy.toInt(); wm.updateViewLayout(ballView, p) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) onBallClicked(); true }
                else -> false
            }
        }
        wm.addView(ballView, p)
    }
    private fun onBallClicked() {
        resultOverlay.showLoading()
        scope.launch {
            val blocks = withContext(Dispatchers.Default) { TranslationAccessibilityService.instance?.extractTextBlocks() ?: emptyList() }
            val mergedLines = blocks.map { it.text }.filter { it.length > 3 && !it.matches(Regex("^[0-9\\p{Punct}\\s]+$")) }
            if (mergedLines.isEmpty()) { resultOverlay.showResults(listOf("未识别到有效文本"), listOf("请尝试重新点击")); return@launch }
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val mode = prefs.getInt("mode_index", 2)
            val src = if (mode == 0) "en" else if (mode == 1) "zh" else currentSrcLang
            val tgt = if (mode == 1) "en" else "zh"
            val translatedLines = mutableListOf<String>()
            for (line in mergedLines) {
                try { translatedLines.add(translator.translate(line, src, tgt)) } catch (e: Exception) { translatedLines.add("[翻译失败]") }
            }
            withContext(Dispatchers.Main) { resultOverlay.showResults(mergedLines, translatedLines) }
        }
    }
    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channelId = "floating_translate"
            if (nm.getNotificationChannel(channelId) == null) nm.createNotificationChannel(NotificationChannel(channelId, "全局翻译", NotificationManager.IMPORTANCE_LOW))
            val notif = Notification.Builder(this, channelId).setContentTitle("全局翻译运行中").setSmallIcon(android.R.drawable.ic_menu_search).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceCompat.startForeground(this, 1001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1001, notif)
        }
    }
    override fun onDestroy() { super.onDestroy(); scope.cancel(); ballView?.let { wm.removeView(it) }; resultOverlay.dismiss() }
}
