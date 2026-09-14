package uz.jarvis.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "uz.jarvis.mobile.action.START"
        const val ACTION_STOP = "uz.jarvis.mobile.action.STOP"
        const val PREFS = "jarvis_prefs"
        const val KEY_ALWAYS_ON = "always_on"
        private const val CHANNEL_ID = "jarvis_always_on"
        private const val NOTIFICATION_ID = 2202
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var stopping = false
    private var speaking = false
    private var listening = false
    private var armedUntil = 0L

    private val wakeWords = listOf("jarvis", "jervis", "jarviz", "jarves")

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopJarvis()
            return START_NOT_STICKY
        }

        startAsForeground()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ALWAYS_ON, true).apply()
        stopping = false
        ensureRecognizer()
        scheduleListen(250)
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification("“JARVIS” kalit so‘zini kutyapman")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, JarvisVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(uz.jarvis.mobile.R.drawable.ic_jarvis)
            .setContentTitle("JARVIS doimiy rejim yoqilgan")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "O‘chirish", stopIntent).build())
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS doimiy tinglash",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS kalit so‘zini fon rejimida kutadi"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null || !SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    updateNotification(if (System.currentTimeMillis() < armedUntil) "Buyruqni kutyapman…" else "“JARVIS” kalit so‘zini kutyapman")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false }

                override fun onError(error: Int) {
                    listening = false
                    if (!stopping && !speaking) scheduleListen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 450)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val choices = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val text = choices.firstOrNull().orEmpty().trim()
                    if (text.isNotBlank()) processHeard(text)
                    else scheduleListen(300)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val normalized = JarvisCommandExecutor.normalize(text)
                    if (wakeWords.any { normalized.contains(it) }) {
                        updateNotification("JARVIS eshitdi: $text")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun processHeard(raw: String) {
        val normalized = JarvisCommandExecutor.normalize(raw)
        val now = System.currentTimeMillis()
        val wake = wakeWords.firstOrNull { normalized.contains(it) }

        if (wake != null) {
            val index = normalized.indexOf(wake)
            val command = normalized.substring(index + wake.length).trim().trim(',', '.', ':', '-')
            if (command.isBlank()) {
                armedUntil = now + 15_000
                speak("Eshitaman.")
                return
            }
            armedUntil = now + 2_000
            executeCommand(command)
            return
        }

        if (now < armedUntil) {
            armedUntil = 0L
            executeCommand(normalized)
        } else {
            scheduleListen(250)
        }
    }

    private fun executeCommand(command: String) {
        updateNotification("Buyruq: $command")
        val handled = JarvisCommandExecutor.execute(this, command) { response -> speak(response) }
        if (!handled) {
            speak("Bu buyruqni tushunmadim. Qayta ayting.")
        } else if (!speaking) {
            scheduleListen(700)
        }
    }

    private fun speak(text: String) {
        if (stopping) return
        speaking = true
        listening = false
        recognizer?.cancel()
        updateNotification(text)
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-bg")
        } else {
            speaking = false
            scheduleListen(600)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.forLanguageTag("uz-UZ"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.96f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        scheduleListen(450)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        scheduleListen(450)
                    }
                }
            })
        }
    }

    private fun scheduleListen(delay: Long) {
        if (stopping || speaking) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startListening() }, delay)
    }

    private fun startListening() {
        if (stopping || speaking || listening) return
        ensureRecognizer()
        try {
            recognizer?.cancel()
            recognizer?.startListening(recognizerIntent)
        } catch (_: Exception) {
            scheduleListen(1200)
        }
    }

    private fun stopJarvis() {
        stopping = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ALWAYS_ON, false).apply()
        mainHandler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        if (::tts.isInitialized) tts.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping) {
            try { recognizer?.destroy() } catch (_: Exception) {}
            if (::tts.isInitialized) tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
