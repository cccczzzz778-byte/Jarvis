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

    private lateinit var resultText: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var agentStatus: TextView
    private lateinit var aiUrl: EditText
    private lateinit var commandInput: EditText
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
            setPadding(dp(18), dp(16), dp(18), dp(32))
            setBackgroundColor(Color.rgb(5, 11, 18))
        }
        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 28f
            setTextColor(Color.rgb(53, 215, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        })
        root.addView(TextView(this).apply {
            text = "MOBILE v5  •  AI VISION PHONE AGENT"
            textSize = 10.5f
            setTextColor(Color.rgb(130, 162, 178))
            gravity = Gravity.CENTER
        })
        root.addView(OrbView(this), LinearLayout.LayoutParams(dp(175), dp(175)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        serviceStatus = statusCard("")
        agentStatus = statusCard("")
        root.addView(serviceStatus, margin(dp(4)))
        root.addView(agentStatus, margin(dp(7)))

        root.addView(actionButton("🎙  DOIMIY JARVISNI YOQISH") { enableAlwaysOn() }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
        root.addView(actionButton("♿  TELEFONNI BOSHQARISH RUXSATI") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
        root.addView(actionButton("🔔  XABARLARNI O‘QISH/JAVOB RUXSATI") { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
        root.addView(actionButton("▣  BOSHQA ILOVALAR USTIDA ISHLASH") { requestOverlayPermission() }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })

        root.addView(section("AI VISION SERVER"))
        aiUrl = EditText(this).apply {
            hint = "https://sizning-jarvis-serveringiz..."
            setHintTextColor(Color.rgb(90, 120, 136))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine(true)
            setText(JarvisAiClient.endpoint(this@MainActivity))
            background = rounded(Color.rgb(10, 22, 32), Color.rgb(35, 76, 96))
            setPadding(dp(13), dp(9), dp(13), dp(9))
        }
        root.addView(aiUrl, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(smallButton("AI SERVERNI SAQLASH") {
            val value = aiUrl.text.toString().trim()
            if (value.isNotBlank() && !value.startsWith("https://")) {
                resultText.text = "JARVIS: AI server manzili HTTPS bo‘lishi kerak."
            } else {
                JarvisAiClient.saveEndpoint(this, value)
                resultText.text = if (value.isBlank()) "JARVIS: AI server o‘chirildi." else "JARVIS: AI server saqlandi. Vision Agent tayyor."
                refreshStatus()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) })

        resultText = card("JARVIS: Oddiy buyruqlar telefonda bajariladi. Murakkab buyruqlar AI Vision Agentga beriladi.")
        root.addView(resultText, margin(dp(12)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        commandInput = EditText(this).apply {
            hint = "Masalan: Telegramda oxirgi chatni och..."
            setHintTextColor(Color.rgb(90, 120, 136))
            setTextColor(Color.WHITE)
            setSingleLine(true)
            background = rounded(Color.rgb(10, 22, 32), Color.rgb(35, 76, 96))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        row.addView(commandInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        row.addView(smallButton("Sinash") {
            val raw = commandInput.text.toString().trim()
            if (raw.isNotBlank()) {
                val q = stripWakeWord(raw)
                JarvisCommandExecutor.execute(this, q) { runOnUiThread { resultText.text = "JARVIS: $it" } }
                commandInput.text.clear()
            }
        }, LinearLayout.LayoutParams(dp(90), dp(50)).apply { leftMargin = dp(7) })
        root.addView(row, margin(dp(8)))

        root.addView(section("V5 NIMALAR QILADI"))
        root.addView(card(
            "• JARVIS deb uyg‘otish va o‘zbekcha ovozli javob\n" +
            "• Ilova ochish, Back/Home/Recents, tap va swipe\n" +
            "• Ekrandagi matn va UI elementlarini olish\n" +
            "• Android 11+ da ekran screenshotini AI serverga yuborish\n" +
            "• AI qaroriga ko‘ra tap / swipe / type / open-app qadamlarini ketma-ket bajarish\n" +
            "• Xabarlarni o‘qish va tasdiqdan keyin direct-reply\n" +
            "• Pul, xarid, o‘chirish, yuborish kabi xavfli actionlarda majburiy tasdiq"
        ))
        root.addView(section("MISOLLAR"))
        root.addView(card(
            "“JARVIS, Telegramni och”\n" +
            "“JARVIS, ekranda nima bor?”\n" +
            "“JARVIS, sozlamadan Bluetooth bo‘limiga kir”\n" +
            "“JARVIS, shu oynada Davom et tugmasini topib bos”\n" +
            "“JARVIS, oxirgi xabarni o‘qi”\n" +
            "“JARVIS, o‘yinda ekranga qarab keyingi xavfsiz qadamni bajar”"
        ))
        root.addView(actionButton("■  DOIMIY REJIMNI O‘CHIRISH", danger = true) { stopAlwaysOnService() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(14) })

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        refreshStatus()
    }

    private fun enableAlwaysOn() {
        pendingAlwaysOnStart = true
        if (!hasAudioPermission()) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION); return }
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
        val intent = Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).edit().putBoolean(JarvisVoiceService.KEY_ALWAYS_ON, true).apply()
        resultText.text = "JARVIS: Doimiy ovozli rejim yoqildi."
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
        val voice = getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).getBoolean(JarvisVoiceService.KEY_ALWAYS_ON, false)
        serviceStatus.text = if (voice) "● DOIMIY OVOZ: YOQILGAN" else "○ DOIMIY OVOZ: O‘CHIQ"
        serviceStatus.setTextColor(if (voice) Color.rgb(72, 224, 154) else Color.rgb(190, 200, 205))
        val ai = JarvisAiClient.configured(this)
        val access = JarvisAccessibilityService.available()
        agentStatus.text = "AI: ${if (ai) "ULANGAN" else "SOZLANMAGAN"}   •   PHONE AGENT: ${if (access) "FAOL" else "RUXSAT KERAK"}"
        agentStatus.setTextColor(if (ai && access) Color.rgb(72, 224, 154) else Color.rgb(255, 191, 71))
    }

    private fun hasAudioPermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) enableAlwaysOn()
    }

    private fun stripWakeWord(value: String): String {
        var result = JarvisCommandExecutor.normalize(value)
        listOf("jarvis", "jervis", "jarviz", "jarves").forEach { if (result.startsWith(it)) result = result.removePrefix(it).trim().trim(',', '.', ':', '-') }
        return result
    }

    private fun statusCard(s: String) = TextView(this).apply { text=s; textSize=12.5f; gravity=Gravity.CENTER; background=rounded(Color.rgb(9,20,29),Color.rgb(31,67,83)); setPadding(dp(10),dp(10),dp(10),dp(10)) }
    private fun card(s: String) = TextView(this).apply { text=s; textSize=13.5f; setTextColor(Color.rgb(222,242,248)); background=rounded(Color.rgb(10,22,32),Color.rgb(24,58,74)); setPadding(dp(13),dp(13),dp(13),dp(13)) }
    private fun section(s: String) = TextView(this).apply { text=s; textSize=11f; setTextColor(Color.rgb(130,162,178)); setPadding(0,dp(18),0,dp(7)) }
    private fun actionButton(label: String, danger: Boolean=false, click:()->Unit)=Button(this).apply { text=label; isAllCaps=false; textSize=14f; setTextColor(Color.WHITE); background=rounded(if(danger) Color.rgb(89,35,43) else Color.rgb(8,104,137),if(danger) Color.rgb(186,75,86) else Color.rgb(53,215,255)); setOnClickListener{click()} }
    private fun smallButton(label:String, click:()->Unit)=Button(this).apply { text=label; isAllCaps=false; setTextColor(Color.WHITE); background=rounded(Color.rgb(12,46,61),Color.rgb(36,94,117)); setOnClickListener{click()} }
    private fun rounded(fill:Int, stroke:Int)=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=dp(15).toFloat(); setColor(fill); setStroke(dp(1),stroke) }
    private fun margin(top:Int)=LinearLayout.LayoutParams(-1,-2).apply { topMargin=top }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    private class OrbView(context: android.content.Context): View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx=width/2f; val cy=height/2f; val r=minOf(width,height)*0.43f
            paint.style=Paint.Style.FILL; paint.color=Color.rgb(7,55,69); canvas.drawCircle(cx,cy,r,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=width*0.015f; paint.color=Color.rgb(53,215,255); canvas.drawCircle(cx,cy,r*0.63f,paint)
            paint.strokeWidth=width*0.005f; paint.color=Color.rgb(126,226,248); canvas.drawCircle(cx,cy,r*0.36f,paint)
            paint.color=Color.rgb(20,112,137); canvas.drawCircle(cx,cy,r*0.90f,paint)
        }
    }
}
