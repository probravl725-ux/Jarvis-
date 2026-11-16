package com.sergey.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var keyField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { action() }
            return b
        }

        val title = TextView(this)
        title.text = "Джарвис"
        title.textSize = 28f
        col.addView(title)

        val info = TextView(this)
        info.text = "Пройди шаги сверху вниз, потом нажми «Запустить». После этого приложение можно свернуть."
        info.textSize = 16f
        info.setPadding(0, pad / 2, 0, pad / 2)
        col.addView(info)

        keyField = EditText(this)
        keyField.hint = "Ключ Gemini API (для сложных вопросов)"
        keyField.inputType = InputType.TYPE_CLASS_TEXT
        keyField.setText(getSharedPreferences("jarvis", MODE_PRIVATE).getString("gemini_key", ""))
        col.addView(keyField)

        col.addView(btn("1. Разрешить микрофон и уведомления") {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        })

        col.addView(btn("2. Разрешить показ поверх других окон") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })

        col.addView(btn("3. Настройки приложения (батарея, автозапуск)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        col.addView(btn("Запустить Джарвис") { startJarvis() })

        col.addView(btn("Остановить") {
            stopService(Intent(this, VoiceService::class.java))
            Toast.makeText(this, "Джарвис остановлен", Toast.LENGTH_SHORT).show()
        })

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Сначала нажми кнопку 1", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("jarvis", MODE_PRIVATE)
            .edit()
            .putString("gemini_key", keyField.text.toString().trim())
            .apply()
        startForegroundService(Intent(this, VoiceService::class.java))
        Toast.makeText(this, "Джарвис запущен. Скажи: Джарвис, открой YouTube", Toast.LENGTH_LONG).show()
    }
}
