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
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val spinner = findViewById<Spinner>(R.id.spinnerMode)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("英文 -> 中文", "中文 -> 英文", "自动识别 -> 中文"))
        spinner.setSelection(prefs.getInt("mode_index", 2))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { prefs.edit().putInt("mode_index", pos).apply() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        val layoutLang = findViewById<LinearLayout>(R.id.layoutLangPacks)
        listOf("英语" to "en", "中文" to "zh", "日语" to "ja", "韩语" to "ko", "法语" to "fr", "德语" to "de", "西班牙语" to "es", "俄语" to "ru").forEach { (name, code) ->
            layoutLang.addView(Button(this).apply {
                text = "下载 $name 离线包"
                setOnClickListener { Toast.makeText(this@MainActivity, "翻译时会自动下载", Toast.LENGTH_SHORT).show() }
            })
        }
        findViewById<Switch>(R.id.switchEnable).apply {
            isChecked = Settings.canDrawOverlays(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                val svc = Intent(this@MainActivity, FloatingTranslateService::class.java)
                if (checked) ContextCompat.startForegroundService(this@MainActivity, svc) else stopService(svc)
            }
        }
    }
}
