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
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.example.floattrans.translate.MlKitTranslator
import com.example.floattrans.ui.ResultOverlayManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs

class FloatingTranslateService : Service() {
    private lateinit var wm: WindowManager
    private var ballView: TextView? = null
    private var menuMaskView: View? = null
    private lateinit var resultOverlay: ResultOverlayManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val translator = MlKitTranslator()
    private val idClient = LanguageIdentification.getClient()
    
    @Volatile private var currentSrcLang = "en"
    private var translateJob: Job? = null
    private var lastClickTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        resultOverlay = ResultOverlayManager(this)

        startForegroundCompat()
        addFloatingBall()
        
        scope.launch(Dispatchers.IO) {
            var isDetecting = false
            while (isActive) { 
                if (!isDetecting) {
                    isDetecting = true
                    try {
                        val currentPkg = TranslationAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
                        if (currentPkg != null && currentPkg != packageName && !currentPkg.contains("launcher")) {
                            val blocks = TranslationAccessibilityService.instance?.extractTextBlocksQuick() ?: continue
                            val fullText = blocks.joinToString(" ") { it.text }
                            if (fullText.isNotBlank()) {
                                val langs = suspendCancellableCoroutine<List<String>> { cont ->
                                    idClient.identifyPossibleLanguages(fullText.take(1500))
                                        .addOnSuccessListener { if (cont.isActive) cont.resumeWith(Result.success(it.map { l -> l.languageTag.substringBefore("-") })) }
                                        .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(emptyList())) }
                                }
                                if (langs.isNotEmpty() && langs[0] != "und") {
                                    val topLang = langs[0]
                                    if (topLang != currentSrcLang) {
                                        currentSrcLang = topLang
                                        withContext(Dispatchers.Main) { updateBallText(topLang) }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                    } finally { isDetecting = false }
                }
                delay(1000) 
            }
        }
    }

    private fun updateBallText(langTag: String) {
        val display = when(langTag) { "en"->"英"; "zh"->"中"; "ja"->"日"; "ko"->"韩"; "fr"->"法"; "de"->"德"; "es"->"西"; "ru"->"俄"; else->"译" }
        ballView?.text = display
    }

    private fun addFloatingBall() {
        ballView = TextView(this).apply {
            text = "译"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#DD2196F3")); shape = GradientDrawable.OVAL }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val size = (56 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 400 }
        
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; 
        var isDragging = false
        var isLongPressTriggered = false
        var longPressJob: Job? = null

        ballView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { 
                    downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y
                    isDragging = false; isLongPressTriggered = false
                    longPressJob = scope.launch {
                        delay(2000) // 长按 2 秒
                        if (!isDragging) { isLongPressTriggered = true; withContext(Dispatchers.Main) { showLangMenu() } }
                    }
                    true 
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > 18f || abs(dy) > 18f)) { isDragging = true; longPressJob?.cancel() }
                    if (isDragging) { 
                        p.x = (startX + dx.toInt()).coerceIn(0, screenWidth - size)
                        p.y = (startY + dy.toInt()).coerceIn(0, screenHeight - size)
                        wm.updateViewLayout(ballView, p) 
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { 
                    longPressJob?.cancel()
                    if (!isDragging && !isLongPressTriggered) {
                        if (menuMaskView != null) {
                            closeLangMenu()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime > 1000) {
                                lastClickTime = now
                                triggerTranslate()
                            }
                        }
                    }
                    true 
                }
                else -> false
            }
        }
        wm.addView(ballView, p)
    }

    private fun showLangMenu() {
        if (menuMaskView != null) return
        val mask = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
            setOnClickListener { closeLangMenu() }
        }
        
        // 改为垂直布局，包裹所有常用语言
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE333333"))
            setPadding(16, 16, 16, 16)
        }
        
        val langs = listOf("中文" to "zh", "英语" to "en", "日语" to "ja", "韩语" to "ko", "法语" to "fr")
        langs.forEach { (name, code) ->
            val btn = TextView(this).apply { 
                text = name; setTextColor(Color.WHITE); textSize = 18f; setPadding(32, 16, 32, 16)
                setOnClickListener { setMotherTongue(code) } 
            }
            menu.addView(btn)
        }

        mask.addView(menu, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        )
        wm.addView(mask, params)
        menuMaskView = mask
    }
    
    private fun closeLangMenu() { if (menuMaskView != null) runCatching { wm.removeView(menuMaskView) }; menuMaskView = null }
    
    private fun setMotherTongue(lang: String) { 
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("mother_tongue", lang).apply()
        closeLangMenu() 
    }

    private fun triggerTranslate() {
        translateJob?.cancel()
        translateJob = scope.launch {
            resultOverlay.showLoading()
            try {
                val blocks = withContext(Dispatchers.Default) { TranslationAccessibilityService.instance?.extractTextBlocks() ?: emptyList() }
                val linesToTranslate = blocks.map { it.text }.filter { it.isNotBlank() }
                
                if (linesToTranslate.isEmpty()) { 
                    resultOverlay.showResults(listOf("未识别到有效文本"), listOf("请重新点击"))
                    return@launch 
                }

                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val motherTongue = prefs.getString("mother_tongue", "zh") ?: "zh"

                val semaphore = Semaphore(10)
                val translatedLines = coroutineScope {
                    linesToTranslate.map { line ->
                        async(Dispatchers.Default) {
                            semaphore.withPermit {
                                if (line.length > 2) {
                                    try { 
                                        translator.translate(line, null, motherTongue) 
                                    } 
                                    catch (e: Exception) { "[翻译失败]" }
                                } else line
                            }
                        }
                    }.awaitAll()
                }

                withContext(Dispatchers.Main) { resultOverlay.showResults(linesToTranslate, translatedLines) }
            } catch (e: CancellationException) {
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

    override fun onDestroy() { super.onDestroy(); scope.cancel(); ballView?.let { runCatching { wm.removeView(it) } }; closeLangMenu(); resultOverlay.dismiss() }
}
