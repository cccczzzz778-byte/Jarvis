package uz.jarvis.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        private const val AUDIO_PERMISSION = 101
        private const val NOTIFICATION_PERMISSION = 103
    }

    private lateinit var serviceStatus: TextView
    private lateinit var agentStatus: TextView
    private lateinit var resultText: TextView
    private lateinit var inputText: EditText
    private var pendingAlwaysOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 11, 18)
        window.navigationBarColor = Color.rgb(5, 11, 18)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (pendingAlwaysOnStart && hasAudioPermission() && Settings.canDrawOverlays(this)) {
            pendingAlwaysOnStart = false
            startAlwaysOnService()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(32))
            setBackgroundColor(Color.rgb(5, 11, 18))
        }

        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 28f
            setTextColor(Color.rgb(53, 215, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
            setPadding(0, dp(8), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "MOBILE v4  •  PHONE AGENT"
            textSize = 11f
            setTextColor(Color.rgb(130, 162, 178))
            gravity = Gravity.CENTER
        })
        root.addView(OrbView(this), LinearLayout.LayoutParams(dp(175), dp(175)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        })

        serviceStatus = statusCard("")
        agentStatus = statusCard("")
        root.addView(serviceStatus, marginParams(dp(4)))
        root.addView(agentStatus, marginParams(dp(8)))

        root.addView(actionButton("🎙  DOIMIY JARVISNI YOQISH") { enableAlwaysOn() }, fullButton(dp(12), 60))
        root.addView(actionButton("🧭  TELEFONNI BOSHQARISH RUXSATI") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            resultText.text = "JARVIS: Maxsus imkoniyatlardan JARVIS Mobile xizmatini yoqing."
        }, fullButton(dp(8), 54))
        root.addView(actionButton("💬  XABARLARNI O‘QISH/JAVOB RUXSATI") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            resultText.text = "JARVIS: Bildirishnomalarga kirish bo‘limidan JARVIS xabar yordamchisini yoqing."
        }, fullButton(dp(8), 54))
        root.addView(actionButton("📱  BOSHQA ILOVALAR USTIDA ISHLASH") { requestOverlayPermission() }, fullButton(dp(8), 54))
        root.addView(actionButton("■  DOIMIY REJIMNI O‘CHIRISH", true) { stopAlwaysOnService() }, fullButton(dp(8), 52))

        resultText = cardText("JARVIS: Avval yuqoridagi 3 ta ruxsatni yoqing, keyin doimiy JARVISni ishga tushiring.")
        root.addView(resultText, marginParams(dp(14)))

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        inputText = EditText(this).apply {
            hint = "Buyruqni shu yerda sinang..."
            setHintTextColor(Color.rgb(100, 128, 142)); setTextColor(Color.WHITE); textSize = 14f
            setSingleLine(true); background = rounded(Color.rgb(12, 25, 36), Color.rgb(38, 82, 102))
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val sendButton = smallButton("Sinash") {
            val value = inputText.text.toString().trim()
            if (value.isNotEmpty()) {
                val handled = JarvisCommandExecutor.execute(this, stripWakeWord(value)) { resultText.text = "JARVIS: $it" }
                if (!handled) resultText.text = "JARVIS: Bu buyruqni hali tushunmadim."
                inputText.text.clear()
            }
        }
        inputRow.addView(inputText, LinearLayout.LayoutParams(0, dp(50), 1f))
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(94), dp(50)).apply { leftMargin = dp(8) })
        root.addView(inputRow, marginParams(dp(10)))

        root.addView(section("PHONE AGENT BUYRUQLARI"))
        root.addView(cardText(
            "• JARVIS, Telegramni och\n" +
            "• JARVIS, bosh ekranga qayt\n" +
            "• JARVIS, orqaga qayt\n" +
            "• JARVIS, pastga sur / tepaga sur\n" +
            "• JARVIS, Davom et tugmasini bos\n" +
            "• JARVIS, shu joyga yoz Salom\n" +
            "• JARVIS, ekranda nima bor\n" +
            "• JARVIS, oxirgi xabarni o‘qi\n" +
            "• JARVIS, oxirgi xabarga hozir boraman deb javob ber\n" +
            "• JARVIS, tasdiqlayman"
        ))

        root.addView(section("MUHIM"))
        root.addView(cardText(
            "JARVIS ekrandagi oddiy Android boshqaruv elementlarini bosishi va swipe qilishi mumkin. " +
            "O‘yin ekrani OpenGL/Canvas bo‘lsa elementlar matn ko‘rinishida chiqmasligi mumkin; bunday o‘yinlarni to‘liq avtomatik o‘ynash uchun alohida vision-AI qatlam kerak. " +
            "Xabar yuborish tasdiqdan keyin bajariladi."
        ))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        refreshStatus()
    }

    private fun enableAlwaysOn() {
        pendingAlwaysOnStart = true
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION); return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION)
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            resultText.text = "JARVIS: Boshqa ilovalar ustida ishlash ruxsatini yoqing va qayting."
            return
        }
        pendingAlwaysOnStart = false
        startAlwaysOnService()
    }

    private fun startAlwaysOnService() {
        if (!hasAudioPermission()) return
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).edit().putBoolean(JarvisVoiceService.KEY_ALWAYS_ON, true).apply()
        resultText.text = "JARVIS: Doimiy rejim yoqildi. Endi boshqa ilovaga o‘tib ovozli buyruq bering."
        refreshStatus()
    }

    private fun stopAlwaysOnService() {
        startService(Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_STOP))
        getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).edit().putBoolean(JarvisVoiceService.KEY_ALWAYS_ON, false).apply()
        resultText.text = "JARVIS: Doimiy rejim o‘chirildi."
        refreshStatus()
    }

    private fun requestOverlayPermission() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun refreshStatus() {
        if (!::serviceStatus.isInitialized) return
        val enabled = getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).getBoolean(JarvisVoiceService.KEY_ALWAYS_ON, false)
        serviceStatus.text = if (enabled) "● OVOZLI JARVIS: YOQILGAN" else "○ OVOZLI JARVIS: O‘CHIQ"
        serviceStatus.setTextColor(if (enabled) Color.rgb(72, 224, 154) else Color.rgb(180, 194, 202))
        val agent = JarvisAccessibilityService.available()
        agentStatus.text = if (agent) "✓ PHONE AGENT: TELEFON BOSHQARUVI YOQILGAN" else "! PHONE AGENT: MAXSUS RUXSAT KERAK"
        agentStatus.setTextColor(if (agent) Color.rgb(72, 224, 154) else Color.rgb(255, 191, 71))
    }

    private fun hasAudioPermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) enableAlwaysOn() else resultText.text = "JARVIS: Mikrofon ruxsatisiz ovozli rejim ishlamaydi."
        }
    }

    private fun stripWakeWord(value: String): String {
        var result = JarvisCommandExecutor.normalize(value)
        listOf("jarvis", "jervis", "jarviz", "jarves", "jarvesh").forEach { wake ->
            if (result.startsWith(wake)) result = result.removePrefix(wake).trim().trim(',', '.', ':', '-')
        }
        return result
    }

    private fun section(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 11f; setTextColor(Color.rgb(130, 162, 178)); setPadding(0, dp(20), 0, dp(8))
    }
    private fun statusCard(initial: String) = TextView(this).apply {
        text = initial; textSize = 13f; gravity = Gravity.CENTER
        background = rounded(Color.rgb(9, 20, 29), Color.rgb(31, 67, 83)); setPadding(dp(12), dp(11), dp(12), dp(11))
    }
    private fun cardText(initial: String) = TextView(this).apply {
        text = initial; textSize = 14f; setTextColor(Color.rgb(222, 242, 248))
        background = rounded(Color.rgb(10, 22, 32), Color.rgb(24, 58, 74)); setPadding(dp(14), dp(14), dp(14), dp(14))
    }
    private fun actionButton(label: String, danger: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
        background = rounded(if (danger) Color.rgb(89, 35, 43) else Color.rgb(8, 104, 137), if (danger) Color.rgb(186, 75, 86) else Color.rgb(53, 215, 255))
        setOnClickListener { click() }
    }
    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(Color.rgb(12, 46, 61), Color.rgb(36, 94, 117)); setOnClickListener { click() }
    }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(fill); setStroke(dp(1), stroke)
    }
    private fun fullButton(top: Int, height: Int) = LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = top }
    private fun marginParams(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class OrbView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) * 0.43f
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(7, 55, 69); canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = width * 0.015f; paint.color = Color.rgb(53, 215, 255); canvas.drawCircle(cx, cy, r * 0.63f, paint)
            paint.strokeWidth = width * 0.005f; paint.color = Color.rgb(126, 226, 248); canvas.drawCircle(cx, cy, r * 0.36f, paint)
            paint.color = Color.rgb(20, 112, 137); canvas.drawCircle(cx, cy, r * 0.90f, paint)
        }
    }
}
