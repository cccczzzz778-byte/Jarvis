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
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var answerText: TextView
    private lateinit var inputText: EditText
    private lateinit var micButton: Button
    private var ttsReady = false
    private var pendingListen = false
    private var pendingCamera = false

    companion object {
        private const val AUDIO_PERMISSION = 101
        private const val CAMERA_PERMISSION = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 11, 18)
        window.navigationBarColor = Color.rgb(5, 11, 18)
        tts = TextToSpeech(this, this)
        buildUi()
        setupSpeechRecognizer()
        respond("Assalomu alaykum. Men JARVIS Mobile. Mikrofonni bosing va o‘zbekcha gapiring.", false)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(Color.rgb(5, 11, 18))
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 28f
            setTextColor(Color.rgb(53, 215, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
            setPadding(0, dp(10), 0, dp(2))
        }
        val subtitle = TextView(this).apply {
            text = "MOBILE  •  O‘ZBEKCHA OVOZLI YORDAMCHI"
            textSize = 11f
            setTextColor(Color.rgb(130, 162, 178))
            gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(subtitle)

        val orb = OrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(210), dp(210)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
            }
        }
        root.addView(orb)

        statusText = TextView(this).apply {
            text = "TAYYOR"
            textSize = 12f
            setTextColor(Color.rgb(53, 215, 255))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(statusText)

        micButton = Button(this).apply {
            text = "🎙  GAPIRISH"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(8, 104, 137), Color.rgb(53, 215, 255))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { requestListening() }
        }
        root.addView(micButton, LinearLayout.LayoutParams(-1, dp(60)))

        heardText = cardText("Siz: —")
        answerText = cardText("JARVIS: —")
        root.addView(heardText, marginParams(dp(14)))
        root.addView(answerText, marginParams(dp(8)))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputText = EditText(this).apply {
            hint = "Buyruqni yozing..."
            setHintTextColor(Color.rgb(100, 128, 142))
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            background = rounded(Color.rgb(12, 25, 36), Color.rgb(38, 82, 102))
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val sendButton = smallButton("Yuborish") {
            val value = inputText.text.toString().trim()
            if (value.isNotEmpty()) {
                handleCommand(value)
                inputText.text.clear()
            }
        }
        inputRow.addView(inputText, LinearLayout.LayoutParams(0, dp(50), 1f))
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(96), dp(50)).apply { leftMargin = dp(8) })
        root.addView(inputRow, marginParams(dp(14)))

        val quickTitle = TextView(this).apply {
            text = "TEZKOR AMALLAR"
            textSize = 11f
            setTextColor(Color.rgb(130, 162, 178))
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(quickTitle)

        val quickRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickRow1.addView(smallButton("Google") { askSearch("Google qidiruvi", "google") }, weightedButtonParams())
        quickRow1.addView(smallButton("YouTube") { askSearch("YouTube qidiruvi", "youtube") }, weightedButtonParams(true))
        quickRow1.addView(smallButton("Xarita") { askSearch("Xaritadan joy qidirish", "maps") }, weightedButtonParams(true))
        root.addView(quickRow1)

        val quickRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickRow2.addView(smallButton("Kamera") { openCamera() }, weightedButtonParams())
        quickRow2.addView(smallButton("Fayl") { openFilePicker() }, weightedButtonParams(true))
        quickRow2.addView(smallButton("Telefon") { openDialer("") }, weightedButtonParams(true))
        root.addView(quickRow2, marginParams(dp(8)))

        val help = TextView(this).apply {
            text = "Misollar: “Soat nechi?”, “YouTube'dan musiqa qidir”, “Google'dan Buxoro yangiliklarini qidir”, “Xaritadan Arkni ko‘rsat”, “Kamerani och”, “Telefonni och”."
            textSize = 12f
            setTextColor(Color.rgb(121, 151, 166))
            setPadding(dp(2), dp(18), dp(2), 0)
        }
        root.addView(help)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "OVOZNI TANISH XIZMATI TOPILMADI"
            micButton.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "TINGLAYAPMAN..."
                micButton.text = "●  TINGLAYAPMAN"
            }
            override fun onBeginningOfSpeech() { statusText.text = "OVOZ QABUL QILINDI" }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { statusText.text = "TUSHUNYAPMAN..." }
            override fun onError(error: Int) {
                statusText.text = "QAYTA URINING"
                micButton.text = "🎙  GAPIRISH"
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(this@MainActivity, "Ovozni tanishda xatolik: $error", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResults(results: Bundle?) {
                micButton.text = "🎙  GAPIRISH"
                statusText.text = "TAYYOR"
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) handleCommand(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val value = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!value.isNullOrBlank()) heardText.text = "Siz: $value"
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun requestListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingListen = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (::speechRecognizer.isInitialized) {
            heardText.text = "Siz: ..."
            speechRecognizer.cancel()
            speechRecognizer.startListening(speechIntent)
        }
    }

    private fun handleCommand(raw: String) {
        heardText.text = "Siz: $raw"
        val q = normalize(raw)
        when {
            containsAny(q, "salom", "assalomu alaykum") -> respond("Va alaykum assalom. Xizmatingizga tayyorman.")
            containsAny(q, "shu yerdamisan", "bormisan", "jarvis") && containsAny(q, "yerdamisan", "bormisan") -> respond("Ha, shu yerdaman. Buyruq berishingiz mumkin.")
            containsAny(q, "kimsan", "isming nima", "sen kimsan") -> respond("Men JARVIS Mobile, o‘zbekcha ovozli yordamchiman.")
            containsAny(q, "nima qila olasan", "imkoniyatlaring", "yordam") -> respond("Men o‘zbekcha ovozni tushunaman, Google va YouTube'dan qidiraman, xaritadan joy ochaman, kamera, fayl tanlash va telefon terish oynasini ishga tushiraman, vaqt va sanani aytaman.")
            containsAny(q, "soat nechi", "vaqt nechi", "hozir soat") -> {
                val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                respond("Hozir soat $time.")
            }
            containsAny(q, "bugun sana", "bugungi sana", "sana nechi") -> {
                val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                respond("Bugungi sana $date.")
            }
            containsAny(q, "kamerani och", "kamera och", "kamerani yoq") -> openCamera()
            containsAny(q, "fayl tanla", "faylni tanla", "fayllarni och") -> openFilePicker()
            containsAny(q, "telefonni och", "raqam ter", "qo'ng'iroq") -> {
                val number = Regex("\\+?\\d[\\d \\-]{4,}\\d").find(q)?.value?.replace(" ", "")?.replace("-", "").orEmpty()
                openDialer(number)
            }
            q.contains("youtube") && containsAny(q, "qidir", "izla", "top") -> {
                val term = cleanQuery(q, listOf("youtube'dan", "youtube dan", "youtube", "qidir", "izla", "top", "menga"))
                if (term.isBlank()) askSearch("YouTube qidiruvi", "youtube") else openYouTubeSearch(term)
            }
            containsAny(q, "youtube'ni och", "youtube ni och", "youtube och") -> openUrl("https://www.youtube.com")
            containsAny(q, "google'ni och", "google ni och", "google och") -> openUrl("https://www.google.com")
            q.contains("google") && containsAny(q, "qidir", "izla", "top") -> {
                val term = cleanQuery(q, listOf("google'dan", "google dan", "google", "qidir", "izla", "top", "menga"))
                if (term.isBlank()) askSearch("Google qidiruvi", "google") else openGoogleSearch(term)
            }
            containsAny(q, "xaritadan", "xaritada", "maps") -> {
                val term = cleanQuery(q, listOf("xaritadan", "xaritada", "google maps", "maps", "ko'rsat", "qidir", "top", "och"))
                if (term.isBlank()) askSearch("Xaritadan joy qidirish", "maps") else openMaps(term)
            }
            containsAny(q, "telegramni och", "telegram och") -> openUrl("https://t.me")
            containsAny(q, "internetdan", "google'dan", "qidir", "izla") -> {
                val term = cleanQuery(q, listOf("internetdan", "google'dan", "google dan", "qidir", "izla", "menga"))
                if (term.isBlank()) askSearch("Google qidiruvi", "google") else openGoogleSearch(term)
            }
            else -> {
                respond("Bu gap uchun maxsus telefon buyrug‘i hali yo‘q. Internetdan qidiruvni ochaman.", false)
                openGoogleSearch(raw)
            }
        }
    }

    private fun askSearch(title: String, mode: String) {
        inputText.hint = "$title: so‘rovni yozing"
        inputText.requestFocus()
        inputText.tag = mode
        respond("So‘rovni pastdagi maydonga yozing. Yuborish tugmasini bosing.", false)
    }

    private fun openGoogleSearch(term: String) {
        respond("Google'dan “$term” bo‘yicha qidiryapman.")
        openUrl("https://www.google.com/search?q=${Uri.encode(term)}")
    }

    private fun openYouTubeSearch(term: String) {
        respond("YouTube'dan “$term” bo‘yicha qidiryapman.")
        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(term)}")
    }

    private fun openMaps(term: String) {
        respond("Xaritadan “$term” joyini ochyapman.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(term)}"))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(term)}")
    }

    private fun openDialer(number: String) {
        respond(if (number.isBlank()) "Telefon terish oynasini ochyapman." else "$number raqamini terish oynasiga qo‘ydim.")
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCamera = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
            return
        }
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        if (intent.resolveActivity(packageManager) != null) {
            respond("Kamerani ochyapman.")
            startActivity(intent)
        } else respond("Bu telefonda kamera ilovasi topilmadi.")
    }

    private fun openFilePicker() {
        respond("Fayl tanlash oynasini ochyapman.")
        startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        })
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            respond("Bu amalni ochadigan ilova topilmadi.", false)
        }
    }

    private fun respond(text: String, speak: Boolean = true) {
        answerText.text = "JARVIS: $text"
        if (speak && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.forLanguageTag("uz-UZ"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.95f)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            AUDIO_PERMISSION -> {
                if (granted && pendingListen) startListening() else if (!granted) respond("Mikrofon ruxsatisiz ovozli buyruq ishlamaydi.", false)
                pendingListen = false
            }
            CAMERA_PERMISSION -> {
                pendingCamera = false
                if (granted) openCamera() else respond("Kamera uchun ruxsat berilmadi.", false)
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('’', '\'').replace('`', '\'').replace('ʻ', '\'').trim()

    private fun containsAny(value: String, vararg keys: String): Boolean = keys.any { value.contains(it) }

    private fun cleanQuery(value: String, words: List<String>): String {
        var result = value
        words.sortedByDescending { it.length }.forEach { result = result.replace(it, " ") }
        return result.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.')
    }

    private fun cardText(initial: String) = TextView(this).apply {
        text = initial
        textSize = 14f
        setTextColor(Color.rgb(222, 242, 248))
        background = rounded(Color.rgb(10, 22, 32), Color.rgb(24, 58, 74))
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.rgb(219, 245, 251))
        background = rounded(Color.rgb(11, 38, 51), Color.rgb(35, 92, 115))
        setOnClickListener { click() }
    }

    private fun rounded(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun weightedButtonParams(withLeftMargin: Boolean = false) = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        if (withLeftMargin) leftMargin = dp(7)
    }

    private fun marginParams(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

class OrbView(context: android.content.Context) : View(context) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private var phase = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.23f
        val pulse = (kotlin.math.sin(phase.toDouble()).toFloat() + 1f) * 0.5f
        glow.color = Color.argb(38 + (pulse * 30).toInt(), 53, 215, 255)
        canvas.drawCircle(cx, cy, base * (1.65f + pulse * 0.12f), glow)
        ring.color = Color.rgb(21, 99, 126)
        ring.strokeWidth = 2f
        canvas.drawCircle(cx, cy, base * 1.45f, ring)
        ring.color = Color.rgb(53, 215, 255)
        ring.strokeWidth = 5f
        canvas.drawCircle(cx, cy, base, ring)
        glow.color = Color.rgb(8, 50, 67)
        canvas.drawCircle(cx, cy, base * 0.72f, glow)
        ring.color = Color.rgb(185, 245, 255)
        ring.strokeWidth = 2f
        canvas.drawCircle(cx, cy, base * 0.56f, ring)
        phase += 0.08f
        postInvalidateDelayed(40)
    }
}
