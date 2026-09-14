package uz.jarvis.mobile

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class JarvisAiDecision(
    val action: String,
    val speech: String,
    val text: String = "",
    val target: String = "",
    val x: Double = 0.5,
    val y: Double = 0.5,
    val direction: String = "",
    val sensitive: Boolean = false
)

object JarvisAiClient {
    const val PREFS = "jarvis_ai"
    const val KEY_URL = "agent_url"
    private const val DEFAULT_GATEWAY = "https://jarvis-ai-gateway-lkqid5.v2.appdeploy.ai"

    fun configured(context: Context): Boolean = endpoint(context).startsWith("https://")

    fun endpoint(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_URL, DEFAULT_GATEWAY)?.trim()?.trimEnd('/').orEmpty()

    fun saveEndpoint(context: Context, value: String) {
        val clean = value.trim().trimEnd('/')
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, clean.ifBlank { DEFAULT_GATEWAY }).apply()
    }

    fun resetGateway(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_URL).apply()
    }

    fun decide(
        context: Context,
        request: String,
        screenText: String,
        packageName: String,
        screenshotBase64: String?,
        notificationTitle: String?,
        notificationText: String?,
        callback: (Result<JarvisAiDecision>) -> Unit
    ) {
        val base = endpoint(context)
        if (!base.startsWith("https://")) {
            callback(Result.failure(IllegalStateException("AI gateway sozlanmagan")))
            return
        }
        thread(name = "jarvis-ai") {
            try {
                val body = JSONObject().apply {
                    put("request", request)
                    put("screen_text", screenText.take(3500))
                    put("package_name", packageName)
                    put("notification_title", notificationTitle ?: "")
                    put("notification_text", notificationText ?: "")
                    if (!screenshotBase64.isNullOrBlank()) put("screenshot_base64", screenshotBase64)
                }
                val connection = (URL("$base/api/agent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("AI gateway HTTP $code: ${raw.take(180)}")
                val json = JSONObject(raw)
                callback(Result.success(JarvisAiDecision(
                    action = json.optString("action", "say").lowercase(),
                    speech = json.optString("speech", "Bajarildi."),
                    text = json.optString("text", ""),
                    target = json.optString("target", ""),
                    x = json.optDouble("x", 0.5),
                    y = json.optDouble("y", 0.5),
                    direction = json.optString("direction", ""),
                    sensitive = json.optBoolean("sensitive", false)
                )))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}
