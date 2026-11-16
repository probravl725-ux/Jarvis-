package com.sergey.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class VoiceService : Service(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var running = false
    private var speaking = false
    private var awaitingCommand = false

    private val wakeWords = listOf("джарвис", "жарвис", "джервис", "jarvis")
    private val openWords = listOf("открой", "запусти", "включи")

    // Как слово звучит по-русски -> как приложение называется на телефоне
    private val aliases = mapOf(
        "ютуб" to "youtube",
        "телеграм" to "telegram",
        "телега" to "telegram",
        "тикток" to "tiktok",
        "тик ток" to "tiktok",
        "ватсап" to "whatsapp",
        "вацап" to "whatsapp",
        "чат gpt" to "chatgpt",
        "чат джипити" to "chatgpt",
        "чатжпт" to "chatgpt",
        "гемини" to "gemini",
        "дипсик" to "deepseek",
        "клод" to "claude",
        "клауд" to "claude"
    )

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "Джарвис", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, "jarvis")
            .setContentTitle("Джарвис слушает")
            .setContentText("Скажи: Джарвис, открой YouTube")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        handler.post { listen() }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.post { speaking = false; listen() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post { speaking = false; listen() }
                }
            })
            ttsReady = true
        }
    }

    private fun listen() {
        if (!running || speaking) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        recognizer?.startListening(i)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            awaitingCommand = false
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopSelf()
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                recognizer?.destroy()
                recognizer = null
            }
            handler.postDelayed({ listen() }, 600)
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull()?.lowercase()?.trim() ?: ""
            handle(text)
        }
    }

    private fun handle(text: String) {
        if (text.isBlank()) { listen(); return }
        val hit = wakeWords.firstOrNull { text.contains(it) }
        if (hit == null && !awaitingCommand) { listen(); return }

        val cmd = if (hit != null) {
            text.substringAfter(hit).trim { it == ' ' || it == ',' || it == '.' || it == '!' }
        } else text
        awaitingCommand = false

        if (cmd.isEmpty()) {
            awaitingCommand = true
            speak("Слушаю")
            return
        }
        execute(cmd)
    }

    private fun execute(cmd: String) {
        val w = openWords.firstOrNull { cmd.startsWith(it) }
        if (w != null) {
            openApp(cmd.removePrefix(w).trim())
        } else {
            askGemini(cmd)
        }
    }

    private fun openApp(spoken: String) {
        if (spoken.isEmpty()) { speak("Что открыть?"); return }
        val query = aliases.entries.firstOrNull { spoken.contains(it.key) }?.value ?: spoken
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val found = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(query) }

        if (found != null) {
            val label = found.loadLabel(pm).toString()
            val intent = pm.getLaunchIntentForPackage(found.activityInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                speak("Открываю $label")
                startActivity(intent)
                return
            }
        }
        if (spoken.contains("телефон")) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            speak("Открываю телефон")
            startActivity(dial)
            return
        }
        speak("Не нашёл приложение $spoken")
    }

    private fun askGemini(prompt: String) {
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("gemini_key", "") ?: ""
        if (key.isBlank()) {
            speak("Вставь ключ Gemini в настройках приложения")
            return
        }
        Thread {
            val answer = try {
                callGemini(key, prompt)
            } catch (e: Exception) {
                "Не получилось связаться с Gemini"
            }
            handler.post { speak(answer) }
        }.start()
    }

    private fun callGemini(key: String, prompt: String): String {
        // Передаем ключ через параметр ?key= и используем актуальную модель gemini-2.5-flash
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val systemPrompt = "Ты голосовой ассистент Джарвис. Отвечай по-русски, коротко, одним-двумя предложениями, без markdown и списков."
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode != 200) {
            return "Gemini вернул ошибку, проверь ключ"
        }

        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        val text = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .getJSONObject(0).getString("text")
            
        return text.replace("*", "").trim().take(400)
    }

    private fun speak(text: String) {
        speaking = true
        recognizer?.cancel()
        if (!ttsReady) {
            speaking = false
            handler.postDelayed({ listen() }, 500)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j" + System.currentTimeMillis())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

