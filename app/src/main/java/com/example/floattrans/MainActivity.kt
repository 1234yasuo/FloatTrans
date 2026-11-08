package com.example.floattrans
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.floattrans.service.FloatingTranslateService
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val modelManager = RemoteModelManager.getInstance()
    
    private val langList = listOf(
        "中文" to "zh", "英语" to "en", "日语" to "ja", "韩语" to "ko",
        "法语" to "fr", "德语" to "de", "西班牙语" to "es", "俄语" to "ru"
    )
    private lateinit var spinner: Spinner
    private lateinit var switchEnable: android.widget.Switch
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        spinner = findViewById(R.id.spinnerMotherTongue)
        val langNames = langList.map { it.first }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langNames)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = langList[position].second
                prefs.edit().putString("mother_tongue", selectedCode).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val layoutLang = findViewById<LinearLayout>(R.id.layoutLangPacks)
        renderLangPacks(layoutLang)

        switchEnable = findViewById(R.id.switchEnable)
        switchEnable.isChecked = Settings.canDrawOverlays(this)
        switchEnable.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                    switchEnable.isChecked = false
                    return@setOnCheckedChangeListener
                }
                val svc = Intent(this, FloatingTranslateService::class.java)
                ContextCompat.startForegroundService(this, svc)
            } else {
                stopService(Intent(this, FloatingTranslateService::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized && ::spinner.isInitialized) {
            val savedMother = prefs.getString("mother_tongue", "zh") ?: "zh"
            val index = langList.indexOfFirst { it.second == savedMother }.coerceAtLeast(0)
            if (spinner.selectedItemPosition != index) {
                spinner.setSelection(index, false) 
            }
            // 同步开关状态
            if (::switchEnable.isInitialized) {
                switchEnable.isChecked = Settings.canDrawOverlays(this)
            }
        }
    }

    private fun renderLangPacks(container: LinearLayout) {
        container.removeAllViews()
        langList.forEach { (name, code) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val tvName = TextView(this).apply { text = name; textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val btnAction = Button(this).apply { text = "查询中..."; isEnabled = false }
            row.addView(tvName); row.addView(btnAction); container.addView(row)
            checkModelStatus(code, btnAction)
            btnAction.setOnClickListener { if (btnAction.tag == true) deleteModel(code, btnAction) else downloadModel(code, btnAction) }
        }
    }

    private fun checkModelStatus(langCode: String, btn: Button) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { isDownloaded ->
            btn.text = if (isDownloaded) "删除离线包" else "下载离线包"
            btn.tag = isDownloaded; btn.isEnabled = true
        }.addOnFailureListener { btn.text = "查询失败"; btn.isEnabled = true }
    }

    private fun downloadModel(langCode: String, btn: Button) {
        btn.text = "下载中..."; btn.isEnabled = false
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { Toast.makeText(this, "下载成功", Toast.LENGTH_SHORT).show(); checkModelStatus(langCode, btn) }
            .addOnFailureListener { Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show(); btn.text = "下载离线包"; btn.isEnabled = true }
    }

    private fun deleteModel(langCode: String, btn: Button) {
        btn.text = "删除中..."; btn.isEnabled = false
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener { Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); checkModelStatus(langCode, btn) }
            .addOnFailureListener { Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show(); btn.isEnabled = true }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
