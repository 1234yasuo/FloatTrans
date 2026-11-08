package com.example.floattrans
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    
    private val langList = listOf("英语" to "en", "中文" to "zh", "日语" to "ja", "韩语" to "ko", "法语" to "fr", "德语" to "de", "西班牙语" to "es", "俄语" to "ru")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val spinner = findViewById<Spinner>(R.id.spinnerMode)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("英文 -> 中文", "中文 -> 英文", "自动识别 -> 中文"))
        spinner.setSelection(prefs.getInt("mode_index", 2))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { prefs.edit().putInt("mode_index", pos).apply() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Switch>(R.id.switchAutoTranslate).apply {
            isChecked = prefs.getBoolean("auto_translate", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_translate", checked).apply() }
        }

        val layoutLang = findViewById<LinearLayout>(R.id.layoutLangPacks)
        renderLangPacks(layoutLang)

        findViewById<Switch>(R.id.switchEnable).apply {
            isChecked = Settings.canDrawOverlays(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                val svc = Intent(this@MainActivity, FloatingTranslateService::class.java)
                if (checked) ContextCompat.startForegroundService(this@MainActivity, svc) else stopService(svc)
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
            val tvName = TextView(this).apply {
                text = name; textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnAction = Button(this).apply { text = "查询中..."; isEnabled = false }
            row.addView(tvName); row.addView(btnAction); container.addView(row)
            
            checkModelStatus(code, btnAction)
            
            btnAction.setOnClickListener {
                if (btnAction.tag == true) deleteModel(code, btnAction) else downloadModel(code, btnAction)
            }
        }
    }

    private fun checkModelStatus(langCode: String, btn: Button) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { isDownloaded ->
            btn.text = if (isDownloaded) "删除离线包" else "下载离线包"
            btn.tag = isDownloaded
            btn.isEnabled = true
        }.addOnFailureListener {
            btn.text = "状态查询失败"; btn.isEnabled = true
        }
    }

    private fun downloadModel(langCode: String, btn: Button) {
        btn.text = "下载中..."; btn.isEnabled = false
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { Toast.makeText(this, "下载成功", Toast.LENGTH_SHORT).show(); checkModelStatus(langCode, btn) }
            .addOnFailureListener { Toast.makeText(this, "下载失败，请检查网络或GMS服务", Toast.LENGTH_LONG).show(); btn.text = "下载离线包"; btn.isEnabled = true }
    }

    private fun deleteModel(langCode: String, btn: Button) {
        btn.text = "删除中..."; btn.isEnabled = false
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener { Toast.makeText(this, "已释放空间", Toast.LENGTH_SHORT).show(); checkModelStatus(langCode, btn) }
            .addOnFailureListener { Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show(); btn.isEnabled = true }
    }

    override fun onDestroy() {
        scope.cancel() // 修复内存泄漏
        super.onDestroy()
    }
}
