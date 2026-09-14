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
    private lateinit var permissionStatus: TextView
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
            text = "MOBILE v2  •  TELEFON BO‘YLAB OVOZLI YORDAMCHI"
            textSize = 10.5f
            setTextColor(Color.rgb(130, 162, 178))
            gravity = Gravity.CENTER
        })

        root.addView(OrbView(this), LinearLayout.LayoutParams(dp(190), dp(190)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
        })

        serviceStatus = statusCard("")
        permissionStatus = statusCard("")
        root.addView(serviceStatus, marginParams(dp(4)))
        root.addView(permissionStatus, marginParams(dp(8)))

        val enableButton = actionButton("🎙  DOIMIY JARVISNI YOQISH") { enableAlwaysOn() }
        root.addView(enableButton, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(12) })

        val permissionButton = actionButton("📱  TELEFON BO‘YLAB ISHLASH RUXSATI") { requestOverlayPermission() }
        root.addView(permissionButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })

        val stopButton = actionButton("■  DOIMIY REJIMNI O‘CHIRISH", danger = true) { stopAlwaysOnService() }
        root.addView(stopButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        resultText = cardText("JARVIS: “JARVIS” deb chaqiring. Masalan: “JARVIS, Telegramga o‘t”.")
        root.addView(resultText, marginParams(dp(14)))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputText = EditText(this).apply {
            hint = "Buyruqni shu yerda ham sinang..."
            setHintTextColor(Color.rgb(100, 128, 142))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            background = rounded(Color.rgb(12, 25, 36), Color.rgb(38, 82, 102))
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val sendButton = smallButton("Sinash") {
            val value = inputText.text.toString().trim()
            if (value.isNotEmpty()) {
                val command = stripWakeWord(value)
                val handled = JarvisCommandExecutor.execute(this, command) { resultText.text = "JARVIS: $it" }
                if (!handled) resultText.text = "JARVIS: Bu buyruqni tushunmadim. Google qidiruviga yubormadim."
                inputText.text.clear()
            }
        }
        inputRow.addView(inputText, LinearLayout.LayoutParams(0, dp(50), 1f))
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(94), dp(50)).apply { leftMargin = dp(8) })
        root.addView(inputRow, marginParams(dp(10)))

        root.addView(TextView(this).apply {
            text = "QANDAY ISHLAYDI"
            textSize = 11f
            setTextColor(Color.rgb(130, 162, 178))
            setPadding(0, dp(20), 0, dp(8))
        })
        root.addView(cardText(
            "1. Telefon bo‘ylab ishlash ruxsatini bir marta yoqing.\n" +
            "2. DOIMIY JARVISNI YOQISH tugmasini bosing.\n" +
            "3. Ilovadan chiqing — JARVIS bildirishnomada ishlashda davom etadi.\n" +
            "4. “JARVIS” + buyruqni ayting. Yoki avval “JARVIS” deng, u “Eshitaman” degach 15 soniya ichida buyruq bering."
        ))

        root.addView(TextView(this).apply {
            text = "BUYRUQ MISOLLARI"
            textSize = 11f
            setTextColor(Color.rgb(130, 162, 178))
            setPadding(0, dp(20), 0, dp(8))
        })
        root.addView(cardText(
            "• JARVIS, aloqaga o‘t\n" +
            "• JARVIS, Telegramga o‘t\n" +
            "• JARVIS, Instagramni och\n" +
            "• JARVIS, YouTube'ni och\n" +
            "• JARVIS, kameraga o‘t\n" +
            "• JARVIS, sozlamalarga o‘t\n" +
            "• JARVIS, Google'dan Buxoro yangiliklarini qidir\n" +
            "• JARVIS, xaritadan Arkni ko‘rsat\n" +
            "• JARVIS, soat nechi"
        ))

        root.addView(TextView(this).apply {
            text = "Eslatma: doimiy ovoz tanish batareyani oddiy ilovaga qaraganda ko‘proq ishlatishi mumkin. Telefon JARVIS servisining mikrofon bildirishnomasini ko‘rsatib turadi."
            textSize = 11f
            setTextColor(Color.rgb(112, 139, 153))
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        refreshStatus()
    }

    private fun enableAlwaysOn() {
        pendingAlwaysOnStart = true
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION)
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            resultText.text = "JARVIS: ‘Boshqa ilovalar ustida ko‘rsatish’ ruxsatini yoqing va shu ekranga qayting."
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
        resultText.text = "JARVIS: Doimiy rejim yoqildi. Endi ilovadan chiqib, ‘JARVIS, aloqaga o‘t’ deb sinang."
        refreshStatus()
    }

    private fun stopAlwaysOnService() {
        startService(Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_STOP))
        getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE).edit().putBoolean(JarvisVoiceService.KEY_ALWAYS_ON, false).apply()
        resultText.text = "JARVIS: Doimiy rejim o‘chirildi."
        refreshStatus()
    }

    private fun requestOverlayPermission() {
        try {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun refreshStatus() {
        if (!::serviceStatus.isInitialized) return
        val enabled = getSharedPreferences(JarvisVoiceService.PREFS, MODE_PRIVATE)
            .getBoolean(JarvisVoiceService.KEY_ALWAYS_ON, false)
        serviceStatus.text = if (enabled) "● DOIMIY JARVIS: YOQILGAN" else "○ DOIMIY JARVIS: O‘CHIQ"
        serviceStatus.setTextColor(if (enabled) Color.rgb(72, 224, 154) else Color.rgb(180, 194, 202))

        val overlay = Settings.canDrawOverlays(this)
        permissionStatus.text = if (overlay) "✓ Telefon bo‘ylab ilovalarni ochish ruxsati berilgan" else "! Telefon bo‘ylab ishlash ruxsati hali berilmagan"
        permissionStatus.setTextColor(if (overlay) Color.rgb(72, 224, 154) else Color.rgb(255, 191, 71))
    }

    private fun hasAudioPermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) enableAlwaysOn() else resultText.text = "JARVIS: Mikrofon ruxsatisiz doimiy ovozli rejim ishlamaydi."
        }
    }

    private fun stripWakeWord(value: String): String {
        var result = JarvisCommandExecutor.normalize(value)
        listOf("jarvis", "jervis", "jarviz", "jarves").forEach { wake ->
            if (result.startsWith(wake)) result = result.removePrefix(wake).trim().trim(',', '.', ':', '-')
        }
        return result
    }

    private fun statusCard(initial: String) = TextView(this).apply {
        text = initial
        textSize = 13f
        gravity = Gravity.CENTER
        background = rounded(Color.rgb(9, 20, 29), Color.rgb(31, 67, 83))
        setPadding(dp(12), dp(11), dp(12), dp(11))
    }

    private fun cardText(initial: String) = TextView(this).apply {
        text = initial
        textSize = 14f
        setTextColor(Color.rgb(222, 242, 248))
        background = rounded(Color.rgb(10, 22, 32), Color.rgb(24, 58, 74))
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun actionButton(label: String, danger: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(
            if (danger) Color.rgb(89, 35, 43) else Color.rgb(8, 104, 137),
            if (danger) Color.rgb(186, 75, 86) else Color.rgb(53, 215, 255)
        )
        setOnClickListener { click() }
    }

    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(12, 46, 61), Color.rgb(36, 94, 117))
        setOnClickListener { click() }
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun marginParams(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class OrbView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * 0.43f
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(7, 55, 69)
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 0.015f
            paint.color = Color.rgb(53, 215, 255)
            canvas.drawCircle(cx, cy, r * 0.63f, paint)
            paint.strokeWidth = width * 0.005f
            paint.color = Color.rgb(126, 226, 248)
            canvas.drawCircle(cx, cy, r * 0.36f, paint)
            paint.color = Color.rgb(20, 112, 137)
            canvas.drawCircle(cx, cy, r * 0.90f, paint)
        }
    }
}
