package uz.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object JarvisAgentOrchestrator {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var pendingDecision: JarvisAiDecision? = null
    @Volatile private var running = false
    @Volatile private var stepsLeft = 0
    @Volatile private var originalRequest = ""

    fun hasPendingApproval(): Boolean = pendingDecision != null

    fun confirm(context: Context, reply: (String) -> Unit): Boolean {
        val decision = pendingDecision ?: return false
        pendingDecision = null
        val ok = perform(context, decision)
        reply(if (ok) decision.speech.ifBlank { "Tasdiqlangan amal bajarildi." } else "Tasdiqlangan amalni bajara olmadim.")
        return true
    }

    fun cancel(reply: (String) -> Unit): Boolean {
        if (pendingDecision == null) return false
        pendingDecision = null
        running = false
        reply("AI amali bekor qilindi.")
        return true
    }

    fun handle(context: Context, request: String, reply: (String) -> Unit): Boolean {
        if (!JarvisAiClient.configured(context)) {
            reply("Bu buyruq uchun AI kerak. JARVIS ichida AI server manzilini bir marta sozlang.")
            return true
        }
        if (!JarvisAccessibilityService.available()) {
            reply("AI agent ishlashi uchun JARVIS telefon boshqaruv ruxsatini yoqing.")
            return true
        }
        if (running) {
            reply("Oldingi AI vazifasi hali bajarilmoqda.")
            return true
        }
        running = true
        stepsLeft = 8
        originalRequest = request
        reply("Tushundim. Ekranni tekshiryapman.")
        requestNext(context, request, reply)
        return true
    }

    private fun requestNext(context: Context, request: String, reply: (String) -> Unit) {
        if (!running || stepsLeft-- <= 0) {
            running = false
            reply("Vazifani xavfsiz yakunlay olmadim. Ko‘pi bilan sakkizta AI qadamiga ruxsat berilgan.")
            return
        }
        val latest = JarvisNotificationService.latest()
        val screenText = JarvisAccessibilityService.visibleText()
        val packageName = JarvisAccessibilityService.activePackage()
        JarvisAccessibilityService.screenSnapshot { image ->
            JarvisAiClient.decide(
                context = context,
                request = request,
                screenText = screenText,
                packageName = packageName,
                screenshotBase64 = image,
                notificationTitle = latest?.title,
                notificationText = latest?.text
            ) { result ->
                mainHandler.post {
                    val decision = result.getOrElse {
                        running = false
                        reply("AI server bilan aloqa bo‘lmadi: ${it.message ?: "noma’lum xato"}.")
                        return@post
                    }
                    processDecision(context, decision, reply)
                }
            }
        }
    }

    private fun processDecision(context: Context, decision: JarvisAiDecision, reply: (String) -> Unit) {
        val sensitiveByMeaning = decision.action in setOf("send", "delete", "purchase", "payment", "call") ||
            decision.target.lowercase().contains(Regex("send|yubor|delete|o'chir|ochir|pay|to'la|sotib|buy|confirm|tasdiq"))
        if (decision.sensitive || sensitiveByMeaning) {
            pendingDecision = decision
            running = false
            reply("Tasdiq kerak. ${decision.speech.ifBlank { "Bu amal tashqi ta’sir qiladi." }} Tasdiqlayman deng yoki bekor qiling.")
            return
        }

        if (decision.action == "done" || decision.action == "say") {
            running = false
            reply(decision.speech.ifBlank { "Bajarildi." })
            return
        }
        if (decision.action == "ask") {
            running = false
            reply(decision.speech.ifBlank { "Qo‘shimcha ma’lumot kerak." })
            return
        }

        val ok = perform(context, decision)
        if (!ok) {
            running = false
            reply("AI tanlagan amalni telefonda bajara olmadim. ${decision.speech}")
            return
        }

        if (decision.speech.isNotBlank()) reply(decision.speech)
        mainHandler.postDelayed({
            if (running) requestNext(context, originalRequest, reply)
        }, 900)
    }

    private fun perform(context: Context, d: JarvisAiDecision): Boolean = when (d.action) {
        "tap" -> JarvisAccessibilityService.tapNormalized(d.x, d.y)
        "click_text" -> d.target.isNotBlank() && JarvisAccessibilityService.clickText(d.target)
        "type" -> d.text.isNotBlank() && JarvisAccessibilityService.typeText(d.text)
        "swipe" -> when (d.direction.lowercase()) {
            "up" -> JarvisAccessibilityService.swipeUp()
            "down" -> JarvisAccessibilityService.swipeDown()
            "left" -> JarvisAccessibilityService.swipeLeft()
            "right" -> JarvisAccessibilityService.swipeRight()
            else -> false
        }
        "back" -> JarvisAccessibilityService.back()
        "home" -> JarvisAccessibilityService.home()
        "recents" -> JarvisAccessibilityService.recents()
        "open_app" -> openApp(context, d.target)
        "send" -> d.target.isNotBlank() && JarvisAccessibilityService.clickText(d.target)
        else -> false
    }

    private fun openApp(context: Context, spoken: String): Boolean {
        val target = JarvisCommandExecutor.normalize(spoken)
        if (target.isBlank()) return false
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = context.packageManager.queryIntentActivities(launcher, 0)
            .map { it to JarvisCommandExecutor.normalize(it.loadLabel(context.packageManager).toString()) }
            .filter { (_, label) -> label.contains(target) || target.contains(label) }
            .minByOrNull { (_, label) -> kotlin.math.abs(label.length - target.length) }
            ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(match.first.activityInfo.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }
}
