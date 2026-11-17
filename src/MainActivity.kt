package com.sergey.jarvis

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min
import kotlin.math.sin

private val BG = 0xFF04070D.toInt()
private val PANEL = 0xFF0A1220.toInt()
private val EDGE = 0xFF12445E.toInt()
private val CYAN = 0xFF19E3FF.toInt()
private val ICE = 0xFFCFEFFF.toInt()
private val DIM = 0xFF5E8AA0.toInt()
private val RED = 0xFFFF4D5E.toInt()

private fun withAlpha(color: Int, a: Int): Int =
    (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

/** Анимированный реактор в стиле Джарвиса */
class ReactorView(context: Context) : View(context) {
    var active = false
    private var angle = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle += if (active) 2.4f else 0.7f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.96f
        val pulse = 0.75f + 0.25f * sin(angle * 0.06f)

        // свечение
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 0.62f,
            intArrayOf(withAlpha(CYAN, (170 * pulse).toInt()), withAlpha(CYAN, 0)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.62f, p)
        p.shader = null

        // внешнее кольцо
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = withAlpha(CYAN, 110)
        c.drawCircle(cx, cy, r, p)

        // риски по кругу
        c.save()
        c.rotate(angle * 0.25f, cx, cy)
        p.color = withAlpha(CYAN, 150)
        for (i in 0 until 72) {
            val len = if (i % 6 == 0) 0.07f else 0.035f
            c.drawLine(cx, cy - r * 0.92f, cx, cy - r * (0.92f - len), p)
            c.rotate(5f, cx, cy)
        }
        c.restore()

        // три дуги
        val ring = RectF(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
        p.strokeWidth = 7f
        p.strokeCap = Paint.Cap.ROUND
        p.color = CYAN
        for (i in 0 until 3) c.drawArc(ring, angle + i * 120f, 70f, false, p)
        p.strokeCap = Paint.Cap.BUTT

        // пунктир в обратную сторону
        p.strokeWidth = 3f
        p.color = withAlpha(CYAN, 170)
        p.pathEffect = DashPathEffect(floatArrayOf(10f, 14f), 0f)
        c.save()
        c.rotate(-angle * 0.8f, cx, cy)
        c.drawCircle(cx, cy, r * 0.6f, p)
        c.restore()
        p.pathEffect = null

        // катушки реактора
        p.style = Paint.Style.FILL
        p.color = withAlpha(CYAN, (140 + 90 * pulse).toInt())
        c.save()
        c.rotate(-angle * 0.3f, cx, cy)
        for (i in 0 until 10) {
            c.drawRoundRect(cx - r * 0.03f, cy - r * 0.5f, cx + r * 0.03f, cy - r * 0.36f, 6f, 6f, p)
            c.rotate(36f, cx, cy)
        }
        c.restore()

        // ядро
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = ICE
        c.drawCircle(cx, cy, r * 0.27f, p)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r * 0.24f,
            intArrayOf(Color.WHITE, withAlpha(CYAN, 220), withAlpha(CYAN, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.24f, p)
        p.shader = null
    }
}

class MainActivity : Activity() {

    private lateinit var keyField: EditText
    private lateinit var reactor: ReactorView
    private lateinit var statusView: TextView
    private lateinit var heardView: TextView
    private lateinit var replyView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 600)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(w, h)
        l.topMargin = dp(top)
        return l
    }

    private fun box(fill: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(14).toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        t.letterSpacing = spacing
        return t
    }

    private fun hudButton(text: String, accent: Int, filled: Boolean, action: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.typeface = Typeface.MONOSPACE
        b.textSize = 14f
        b.setTextColor(if (filled) BG else accent)
        val shape = box(if (filled) accent else 0x00000000, accent)
        b.background = RippleDrawable(ColorStateList.valueOf(0x4419E3FF), shape, null)
        b.stateListAnimator = null
        b.layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), 10)
        b.setOnClickListener { action() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(36), dp(20), dp(28))

        val title = label("J.A.R.V.I.S.", 26f, CYAN, true, 0.25f)
        title.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(title, lp(match, wrap))

        val sub = label("JUST A RATHER VERY INTELLIGENT SYSTEM", 9f, DIM, false, 0.12f)
        sub.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(sub, lp(match, wrap, 4))

        reactor = ReactorView(this)
        col.addView(reactor, lp(match, dp(280), 8))

        statusView = label("● ОФЛАЙН", 14f, DIM, true, 0.15f)
        statusView.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(statusView, lp(match, wrap))

        val log = LinearLayout(this)
        log.orientation = LinearLayout.VERTICAL
        log.background = box(PANEL, EDGE)
        log.setPadding(dp(16), dp(12), dp(16), dp(12))
        heardView = label("ТЫ  ›  —", 13f, ICE)
        replyView = label("ДЖАРВИС  ›  —", 13f, CYAN)
        log.addView(heardView, lp(match, wrap))
        log.addView(replyView, lp(match, wrap, 6))
        col.addView(log, lp(match, wrap, 14))

        col.addView(label("НАСТРОЙКА", 11f, DIM, true, 0.2f), lp(match, wrap, 22))

        keyField = EditText(this)
        keyField.hint = "Ключ Gemini API"
        keyField.setHintTextColor(DIM)
        keyField.setTextColor(ICE)
        keyField.typeface = Typeface.MONOSPACE
        keyField.textSize = 14f
        keyField.inputType = InputType.TYPE_CLASS_TEXT
        keyField.isSingleLine = true
        keyField.background = box(PANEL, EDGE)
        keyField.setPadding(dp(14), dp(12), dp(14), dp(12))
        keyField.setText(getSharedPreferences("jarvis", MODE_PRIVATE).getString("gemini_key", ""))
        col.addView(keyField, lp(match, wrap, 8))

        col.addView(hudButton("1  МИКРОФОН И УВЕДОМЛЕНИЯ", CYAN, false) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        })

        col.addView(hudButton("2  ПОВЕРХ ДРУГИХ ОКОН", CYAN, false) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })

        col.addView(hudButton("3  БАТАРЕЯ И АВТОЗАПУСК", CYAN, false) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        col.addView(label("УПРАВЛЕНИЕ", 11f, DIM, true, 0.2f), lp(match, wrap, 22))

        col.addView(hudButton("▶  ЗАПУСТИТЬ ДЖАРВИС", CYAN, true) { startJarvis() })

        col.addView(hudButton("■  ОСТАНОВИТЬ", RED, false) {
            stopService(Intent(this, VoiceService::class.java))
            Toast.makeText(this, "Джарвис остановлен", Toast.LENGTH_SHORT).show()
        })

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BG)
        scroll.isFillViewport = true
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        val on = VoiceService.active
        reactor.active = on
        statusView.text = if (on) "● ОНЛАЙН · СЛУШАЮ" else "● ОФЛАЙН"
        statusView.setTextColor(if (on) CYAN else DIM)
        heardView.text = "ТЫ  ›  " + VoiceService.heard.ifEmpty { "—" }
        replyView.text = "ДЖАРВИС  ›  " + VoiceService.reply.ifEmpty { "—" }
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
