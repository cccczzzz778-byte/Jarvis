package uz.jarvis.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
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
    private lateinit var audioManager: AudioManager
    private var ttsReady = false
    private var stopping = false
    private var speaking = false
    private var listening = false
    private var armedUntil = 0L
    private var audioMutedByJarvis = false
    private var savedMusicVolume = -1
    private var savedSystemVolume = -1

    private val wakeWords = listOf("jarvis", "jervis", "jarviz", "jarves", "jarvesh", "j.a.r.v.i.s")

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        tts = TextToSpeech(this, this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
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
        scheduleListen(300)
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification("JARVIS kalit so‘zini kutyapman")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, JarvisVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("JARVIS doimiy rejim yoqilgan")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSilent(true)
            .addAction(Notification.Action.Builder(null, "O‘chirish", stopIntent).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS doimiy tinglash", NotificationManager.IMPORTANCE_LOW).apply {
                description = "JARVIS kalit so‘zini fon rejimida kutadi"
                setSound(null, null)
                enableVibration(false)
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
                    mainHandler.postDelayed({ restoreRecognitionAudio() }, 180)
                    updateNotification(if (System.currentTimeMillis() < armedUntil) "Buyruqni kutyapman…" else "JARVIS kalit so‘zini kutyapman")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    listening = false
                    muteRecognitionTone()
                }

                override fun onError(error: Int) {
                    listening = false
                    mainHandler.postDelayed({ restoreRecognitionAudio() }, 260)
                    if (!stopping && !speaking) scheduleListen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1300 else 550)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val choices = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val text = choices.firstOrNull().orEmpty().trim()
                    if (text.isNotBlank()) processHeard(text) else scheduleListen(350)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val normalized = JarvisCommandExecutor.normalize(text)
                    if (wakeWords.any { normalized.contains(it) }) updateNotification("JARVIS eshitdi: $text")
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
            val command = normalized.substring(index + wake.length).trim().trim(',', '.', ':', '-', '!')
            if (command.isBlank()) {
                armedUntil = now + 18_000
                speak("Eshitaman. Buyruq bering.")
                return
            }
            armedUntil = 0L
            executeCommand(command)
            return
        }

        if (now < armedUntil) {
            armedUntil = 0L
            executeCommand(normalized)
        } else {
            scheduleListen(350)
        }
    }

    private fun executeCommand(command: String) {
        updateNotification("Buyruq: $command")
        val handled = JarvisCommandExecutor.execute(this, command) { response -> speak(response) }
        if (!handled) speak("Bu buyruqni tushunmadim. Boshqacha ayting.")
        else if (!speaking) scheduleListen(750)
    }

    private fun speak(text: String) {
        if (stopping) return
        speaking = true
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        muteRecognitionTone()
        updateNotification(text)

        mainHandler.postDelayed({
            restoreRecognitionAudio()
            if (ttsReady) {
                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-bg-${System.currentTimeMillis()}")
                if (result == TextToSpeech.ERROR) {
                    speaking = false
                    scheduleListen(650)
                }
            } else {
                speaking = false
                scheduleListen(650)
            }
        }, 260)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        val candidates = listOf(
            Locale.forLanguageTag("uz-UZ"),
            Locale("uz", "UZ"),
            Locale.forLanguageTag("tr-TR"),
            Locale.forLanguageTag("ru-RU"),
            Locale.US
        )
        ttsReady = false
        for (locale in candidates) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                break
            }
        }

        tts.setSpeechRate(0.93f)
        tts.setPitch(0.90f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    speaking = false
                    scheduleListen(520)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    speaking = false
                    scheduleListen(520)
                }
            }
        })
    }

    private fun muteRecognitionTone() {
        if (audioMutedByJarvis) return
        try {
            savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            savedSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            audioMutedByJarvis = true
        } catch (_: Exception) {}
    }

    private fun restoreRecognitionAudio() {
        if (!audioMutedByJarvis) return
        try {
            if (savedMusicVolume >= 0) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            if (savedSystemVolume >= 0) audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystemVolume, 0)
        } catch (_: Exception) {}
        audioMutedByJarvis = false
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
            muteRecognitionTone()
            recognizer?.cancel()
            recognizer?.startListening(recognizerIntent)
            mainHandler.postDelayed({ restoreRecognitionAudio() }, 500)
        } catch (_: Exception) {
            restoreRecognitionAudio()
            scheduleListen(1300)
        }
    }

    private fun stopJarvis() {
        stopping = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ALWAYS_ON, false).apply()
        mainHandler.removeCallbacksAndMessages(null)
        restoreRecognitionAudio()
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        if (::tts.isInitialized) tts.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        restoreRecognitionAudio()
        try { recognizer?.destroy() } catch (_: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
