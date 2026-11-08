package com.example.floattrans.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
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
    private var langMenuView: View? = null
    private lateinit var resultOverlay: ResultOverlayManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val translator = MlKitTranslator()
    private val idClient = LanguageIdentification.getClient()
    
    private var currentSrcLang = "en"  // 当前页面识别出的语言
    private var currentTargetLang = "zh" // 用户选择的目标语言（默认中文）
    private var lastScannedText = ""
    private var isTranslating = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        resultOverlay = ResultOverlayManager(this)
        startForegroundCompat()
        addFloatingBall()
        
        // 实时静默检测页面语言
        scope.launch(Dispatchers.IO) {
            while (isActive) { 
                detectScreenLanguage()
                delay(2000) 
            }
        }
        
        // 实时滚动自动提取（如果开启）
        initAutoTranslate()
    }

    private fun initAutoTranslate() {
        scope.launch {
            while (TranslationAccessibilityService.instance == null) delay(1000)
            TranslationAccessibilityService.instance?.onContentChanged = {
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("auto_translate", false)) {
                    // 防抖 1 秒
                    scope.launch {
                        delay(1000) 
                        triggerTranslate()
                    }
                }
            }
        }
    }

    // 智能语言检测：中英混杂优先显示外语
    private suspend fun detectScreenLanguage() {
        val blocks = TranslationAccessibilityService.instance?.extractTextBlocks() ?: return
        if (blocks.isEmpty()) return
        val sampleText = blocks.take(10).joinToString(" ") { it.text }
        
        val langs = suspendCancellableCoroutine<List<String>> { cont ->
            idClient.identifyPossibleLanguages(sampleText)
                .addOnSuccessListener { cont.resumeWith(Result.success(it.map { l -> l.languageTag.substringBefore("-") })) }
                .addOnFailureListener { cont.resumeWith(Result.success(emptyList())) }
        }
        
        // 如果检测结果包含非中文，优先将外语作为源语言
        var detected = "zh" 
        if (langs.contains("en")) detected = "en"
        else if (langs.contains("ja")) detected = "ja"
        else if (langs.contains("ko")) detected = "ko"
        else if (langs.contains("fr")) detected = "fr"
        else if (langs.isNotEmpty() && langs[0] != "zh" && langs[0] != "und") detected = langs[0]

        if (detected != currentSrcLang) {
            currentSrcLang = detected
            updateBallText(detected)
        }
    }

    private fun updateBallText(langTag: String) {
        val display = when(langTag) { "en"->"英"; "zh"->"中"; "ja"->"日"; "ko"->"韩"; "fr"->"法"; "de"->"德"; "es"->"西"; "ru"->"俄"; else->"译" }
        scope.launch(Dispatchers.Main) { ballView?.text = display }
    }

    private fun addFloatingBall() {
        ballView = TextView(this).apply {
            text = "译"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#DD2196F3")); shape = GradientDrawable.OVAL }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val size = (56 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 400 }
        
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; 
        var isDragging = false
        var longPressJob: Job? = null

        ballView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { 
                    downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y; isDragging = false
                    // 长按 3 秒检测
                    longPressJob = scope.launch {
                        delay(3000)
                        if (!isDragging) {
                            withContext(Dispatchers.Main) { showLangMenu() }
                        }
                    }
                    true 
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > 18f || abs(dy) > 18f)) {
                        isDragging = true
                        longPressJob?.cancel() // 拖动取消长按
                    }
                    if (isDragging) { p.x = startX + dx.toInt(); p.y = startY + dy.toInt(); wm.updateViewLayout(ballView, p) }
                    true
                }
                MotionEvent.ACTION_UP -> { 
                    longPressJob?.cancel() // 抬手取消长按
                    if (!isDragging) { triggerTranslate() }; 
                    true 
                }
                else -> false
            }
        }
        wm.addView(ballView, p)
    }

    // 长按弹窗：选择目标语言
    private fun showLangMenu() {
        if (langMenuView != null) return
        
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EE333333"))
            setPadding(16, 16, 16, 16)
        }
        
        val btnZh = TextView(this).apply {
            text = "中"; setTextColor(Color.WHITE); textSize = 20f
            setPadding(32, 16, 32, 16)
            setOnClickListener { setTargetLang("zh") }
        }
        val btnEn = TextView(this).apply {
            text = "英"; setTextColor(Color.WHITE); textSize = 20f
            setPadding(32, 16, 32, 16)
            setOnClickListener { setTargetLang("en") }
        }
        menu.addView(btnZh)
        menu.addView(btnEn)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        
        wm.addView(menu, params)
        langMenuView = menu
    }
    
    private fun setTargetLang(lang: String) {
        currentTargetLang = lang
        if (langMenuView != null) wm.removeView(langMenuView)
        langMenuView = null
    }

    private fun triggerTranslate() {
        if (isTranslating) return
        isTranslating = true
        resultOverlay.showLoading()
        
        scope.launch {
            val blocks = withContext(Dispatchers.Default) { TranslationAccessibilityService.instance?.extractTextBlocks() ?: emptyList() }
            // 提取并保留换行排版
            val mergedText = blocks.joinToString("\n") { it.text }.filter { it.isNotBlank() }
            
            if (mergedText.isBlank()) { 
                resultOverlay.showResults("未识别到有效文本", "请尝试重新点击")
                isTranslating = false
                return@launch 
            }
            
            val currentText = mergedText
            if (currentText == lastScannedText && !isTranslating) return@launch // 防重复
            lastScannedText = currentText

            // 翻译逻辑
            val translatedLines = mutableListOf<String>()
            val linesToTranslate = currentText.split("\n")
            
            for (line in linesToTranslate) {
                if (line.length > 2) {
                    try { translatedLines.add(translator.translate(line, currentSrcLang, currentTargetLang)) } 
                    catch (e: Exception) { translatedLines.add("[翻译失败]") }
                } else {
                    translatedLines.add(line) // 标点符号直接透传
                }
            }
            
            withContext(Dispatchers.Main) { 
                resultOverlay.showResults(currentText, translatedLines.joinToString("\n")) 
                isTranslating = false
            }
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

    override fun onDestroy() { 
        super.onDestroy(); scope.cancel(); 
        ballView?.let { runCatching { wm.removeView(it) } }
        langMenuView?.let { runCatching { wm.removeView(it) } }
        resultOverlay.dismiss() 
    }
}
