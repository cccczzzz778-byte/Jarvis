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
    private lateinit var recognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var answer: TextView
    private lateinit var input: EditText
    private lateinit var mic: Button
    private var ttsReady = false
    private var inputMode: String? = null
    private var pendingListen = false
    private var pendingCamera = false

    companion object {
        const val REQ_AUDIO = 101
        const val REQ_CAMERA = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 11, 18)
        window.navigationBarColor = Color.rgb(5, 11, 18)
        buildUi()
        tts = TextToSpeech(this, this)
        setupSpeech()
        reply("Assalomu alaykum. Men JARVIS Mobile. Mikrofonni bosing va o‘zbekcha gapiring.", false)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
            setBackgroundColor(Color.rgb(5, 11, 18))
        }
        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; textSize = 29f; letterSpacing = 0.20f
            setTextColor(Color.rgb(53, 215, 255)); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "MOBILE  •  O‘ZBEKCHA OVOZLI YORDAMCHI"; textSize = 11f
            setTextColor(Color.rgb(125, 158, 174)); gravity = Gravity.CENTER
        })
        root.addView(OrbView(this), LinearLayout.LayoutParams(dp(210), dp(210)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10)
        })
        status = TextView(this).apply {
            text = "TAYYOR"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(53, 215, 255)); setPadding(0, 0, 0, dp(10))
        }
        root.addView(status)
        mic = Button(this).apply {
            text = "🎙  GAPIRISH"; textSize = 18f; isAllCaps = false
            setTextColor(Color.WHITE); background = bg(Color.rgb(8, 104, 137), Color.rgb(53, 215, 255))
            setOnClickListener { requestListening() }
        }
        root.addView(mic, LinearLayout.LayoutParams(-1, dp(60)))
        heard = card("Siz: —")
        answer = card("JARVIS: —")
        root.addView(heard, lp(dp(14)))
        root.addView(answer, lp(dp(8)))

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Buyruqni yozing..."; textSize = 15f; setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(Color.rgb(100, 128, 142))
            background = bg(Color.rgb(12, 25, 36), Color.rgb(38, 82, 102))
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val send = button("Yuborish") {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                when (inputMode) {
                    "youtube" -> youtubeSearch(text)
                    "maps" -> mapsSearch(text)
                    "google" -> googleSearch(text)
                    else -> command(text)
                }
                input.text.clear(); input.hint = "Buyruqni yozing..."; inputMode = null
            }
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(96), dp(50)).apply { leftMargin = dp(8) })
        root.addView(inputRow, lp(dp(14)))

        root.addView(TextView(this).apply {
            text = "TEZKOR AMALLAR"; textSize = 11f; setTextColor(Color.rgb(125, 158, 174)); setPadding(0, dp(18), 0, dp(8))
        })
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("Google") { searchPrompt("google", "Google qidiruvi") }, weight())
        row1.addView(button("YouTube") { searchPrompt("youtube", "YouTube qidiruvi") }, weight(true))
        row1.addView(button("Xarita") { searchPrompt("maps", "Xaritadan qidirish") }, weight(true))
        root.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Kamera") { openCamera() }, weight())
        row2.addView(button("Fayl") { openFiles() }, weight(true))
        row2.addView(button("Telefon") { dial("") }, weight(true))
        root.addView(row2, lp(dp(8)))
        root.addView(TextView(this).apply {
            text = "Misollar: “Soat nechi?”, “YouTube'dan musiqa qidir”, “Google'dan Buxoro yangiliklarini qidir”, “Xaritadan Arkni ko‘rsat”, “Kamerani och”."
            textSize = 12f; setTextColor(Color.rgb(121, 151, 166)); setPadding(dp(2), dp(18), dp(2), 0)
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "OVOZNI TANISH XIZMATI TOPILMADI"; mic.isEnabled = false; return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text = "TINGLAYAPMAN..."; mic.text = "●  TINGLAYAPMAN" }
            override fun onBeginningOfSpeech() { status.text = "OVOZ QABUL QILINDI" }
            override fun onRmsChanged(v: Float) = Unit
            override fun onBufferReceived(b: ByteArray?) = Unit
            override fun onEndOfSpeech() { status.text = "TUSHUNYAPMAN..." }
            override fun onError(e: Int) {
                status.text = "QAYTA URINING"; mic.text = "🎙  GAPIRISH"
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    Toast.makeText(this@MainActivity, "Ovozni tanishda xatolik: $e", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(r: Bundle?) {
                status.text = "TAYYOR"; mic.text = "🎙  GAPIRISH"
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { command(it) }
            }
            override fun onPartialResults(r: Bundle?) {
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard.text = "Siz: $it" }
            }
            override fun onEvent(t: Int, p: Bundle?) = Unit
        })
    }

    private fun requestListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingListen = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        } else startListening()
    }

    private fun startListening() {
        if (::recognizer.isInitialized) { heard.text = "Siz: ..."; recognizer.cancel(); recognizer.startListening(speechIntent) }
    }

    private fun command(raw: String) {
        heard.text = "Siz: $raw"
        val q = norm(raw)
        when {
            any(q, "assalomu alaykum", "salom") -> reply("Va alaykum assalom. Xizmatingizga tayyorman.")
            any(q, "shu yerdamisan", "bormisan") -> reply("Ha, shu yerdaman. Buyruq berishingiz mumkin.")
            any(q, "kimsan", "isming nima") -> reply("Men JARVIS Mobile, o‘zbekcha ovozli yordamchiman.")
            any(q, "nima qila olasan", "imkoniyatlaring", "yordam") -> reply("Men o‘zbekcha ovozni tushunaman, Google, YouTube va xaritadan qidiraman, kamera, fayl va telefon oynalarini ochaman, vaqt va sanani aytaman.")
            any(q, "soat nechi", "vaqt nechi", "hozir soat") -> reply("Hozir soat ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}.")
            any(q, "bugungi sana", "bugun sana", "sana nechi") -> reply("Bugungi sana ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.")
            any(q, "kamerani och", "kamera och", "kamerani yoq") -> openCamera()
            any(q, "fayl tanla", "faylni tanla", "fayllarni och") -> openFiles()
            any(q, "telefonni och", "raqam ter", "qo'ng'iroq") -> {
                val n = Regex("\\+?\\d[\\d \\-]{4,}\\d").find(q)?.value?.replace(" ", "")?.replace("-", "").orEmpty(); dial(n)
            }
            q.contains("youtube") && any(q, "qidir", "izla", "top") -> {
                val x = clean(q, listOf("youtube'dan", "youtube dan", "youtube", "qidir", "izla", "top", "menga")); if (x.isBlank()) searchPrompt("youtube", "YouTube qidiruvi") else youtubeSearch(x)
            }
            any(q, "youtube'ni och", "youtube ni och", "youtube och") -> openUrl("https://www.youtube.com")
            q.contains("google") && any(q, "qidir", "izla", "top") -> {
                val x = clean(q, listOf("google'dan", "google dan", "google", "qidir", "izla", "top", "menga")); if (x.isBlank()) searchPrompt("google", "Google qidiruvi") else googleSearch(x)
            }
            any(q, "google'ni och", "google ni och", "google och") -> openUrl("https://www.google.com")
            any(q, "xaritadan", "xaritada", "maps") -> {
                val x = clean(q, listOf("xaritadan", "xaritada", "google maps", "maps", "ko'rsat", "qidir", "top", "och")); if (x.isBlank()) searchPrompt("maps", "Xaritadan qidirish") else mapsSearch(x)
            }
            any(q, "telegramni och", "telegram och") -> openUrl("https://t.me")
            else -> { reply("Bu gap uchun maxsus telefon buyrug‘i hali yo‘q. Google qidiruvini ochaman.", false); googleSearch(raw) }
        }
    }

    private fun searchPrompt(mode: String, title: String) { inputMode = mode; input.hint = "$title: so‘rovni yozing"; input.requestFocus(); reply("So‘rovni yozib Yuborish tugmasini bosing.", false) }
    private fun googleSearch(q: String) { reply("Google'dan “$q” bo‘yicha qidiryapman."); openUrl("https://www.google.com/search?q=${Uri.encode(q)}") }
    private fun youtubeSearch(q: String) { reply("YouTube'dan “$q” bo‘yicha qidiryapman."); openUrl("https://www.youtube.com/results?search_query=${Uri.encode(q)}") }
    private fun mapsSearch(q: String) {
        reply("Xaritadan “$q” joyini ochyapman.")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(q)}"))
        if (i.resolveActivity(packageManager) != null) startActivity(i) else openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(q)}")
    }
    private fun dial(n: String) { reply(if (n.isBlank()) "Telefon terish oynasini ochyapman." else "$n raqamini terish oynasiga qo‘ydim."); startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
    private fun openFiles() { reply("Fayl tanlash oynasini ochyapman."); startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }) }
    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { pendingCamera = true; requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA); return }
        val i = Intent("android.media.action.IMAGE_CAPTURE")
        if (i.resolveActivity(packageManager) != null) { reply("Kamerani ochyapman."); startActivity(i) } else reply("Kamera ilovasi topilmadi.")
    }
    private fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { reply("Bu amalni ochadigan ilova topilmadi.", false) } }

    private fun reply(text: String, speak: Boolean = true) { answer.text = "JARVIS: $text"; if (speak && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis") }
    override fun onInit(s: Int) { if (s == TextToSpeech.SUCCESS) { val r = tts.setLanguage(Locale.forLanguageTag("uz-UZ")); ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED; tts.setSpeechRate(.95f) } }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(code, p, g); val ok = g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED
        if (code == REQ_AUDIO) { if (ok && pendingListen) startListening() else if (!ok) reply("Mikrofon ruxsati kerak.", false); pendingListen = false }
        if (code == REQ_CAMERA) { pendingCamera = false; if (ok) openCamera() else reply("Kamera ruxsati berilmadi.", false) }
    }

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace('’', '\'').replace('`', '\'').replace('ʻ', '\'').trim()
    private fun any(s: String, vararg k: String) = k.any { s.contains(it) }
    private fun clean(s: String, w: List<String>): String { var r = s; w.sortedByDescending { it.length }.forEach { r = r.replace(it, " ") }; return r.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.') }
    private fun card(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(Color.rgb(222,242,248)); background = bg(Color.rgb(10,22,32), Color.rgb(24,58,74)); setPadding(dp(14),dp(14),dp(14),dp(14)) }
    private fun button(s: String, f: () -> Unit) = Button(this).apply { text=s; textSize=12f; isAllCaps=false; setTextColor(Color.rgb(219,245,251)); background=bg(Color.rgb(11,38,51),Color.rgb(35,92,115)); setOnClickListener { f() } }
    private fun bg(fill: Int, stroke: Int) = GradientDrawable().apply { cornerRadius=dp(14).toFloat(); setColor(fill); setStroke(dp(1),stroke) }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1,-2).apply { topMargin=top }
    private fun weight(left: Boolean=false) = LinearLayout.LayoutParams(0,dp(48),1f).apply { if(left) leftMargin=dp(7) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { if (::recognizer.isInitialized) recognizer.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}

class OrbView(context: android.content.Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var phase = 0f
    override fun onDraw(c: Canvas) {
        val x=width/2f; val y=height/2f; val b=minOf(width,height)*.23f; val p=((kotlin.math.sin(phase.toDouble())+1)/2).toFloat()
        fill.color=Color.argb(42+(p*30).toInt(),53,215,255); c.drawCircle(x,y,b*(1.62f+p*.12f),fill)
        ring.color=Color.rgb(21,99,126); ring.strokeWidth=2f; c.drawCircle(x,y,b*1.43f,ring)
        ring.color=Color.rgb(53,215,255); ring.strokeWidth=5f; c.drawCircle(x,y,b,ring)
        fill.color=Color.rgb(8,50,67); c.drawCircle(x,y,b*.72f,fill)
        ring.color=Color.rgb(185,245,255); ring.strokeWidth=2f; c.drawCircle(x,y,b*.55f,ring)
        phase+=.08f; postInvalidateDelayed(40)
    }
}
