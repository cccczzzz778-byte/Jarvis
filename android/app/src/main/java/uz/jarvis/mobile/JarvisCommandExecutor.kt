package uz.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object JarvisCommandExecutor {
    @Volatile private var pendingReplyText: String? = null

    fun execute(context: Context, raw: String, reply: (String) -> Unit): Boolean {
        val q = normalize(raw)
        if (q.isBlank()) return false

        if (containsAny(q, "tasdiqlayman", "tasdiqla", "ha bajar", "bajaraver") && JarvisAgentOrchestrator.hasPendingApproval()) {
            return JarvisAgentOrchestrator.confirm(context, reply)
        }
        if (containsAny(q, "bekor qil", "to'xtat", "bajarma") && JarvisAgentOrchestrator.hasPendingApproval()) {
            return JarvisAgentOrchestrator.cancel(reply)
        }
        if (containsAny(q, "tasdiqlayman", "ha yubor", "yuboraver") && pendingReplyText != null) {
            val text = pendingReplyText!!
            pendingReplyText = null
            return if (JarvisNotificationService.replyToLatest(text)) done(reply, "Javob yuborildi.")
            else done(reply, "Javob yuborilmadi. Oxirgi bildirishnomada tezkor javob imkoniyati yo‘q.")
        }
        if (containsAny(q, "bekor qil", "yuborma", "to'xtat") && pendingReplyText != null) {
            pendingReplyText = null
            return done(reply, "Javob yuborish bekor qilindi.")
        }

        return when {
            containsAny(q, "salom", "assalomu alaykum", "assalom") -> done(reply, "Va alaykum assalom. Xizmatingizga tayyorman.")
            containsAny(q, "shu yerdamisan", "bormisan", "eshityapsanmi", "aloqaga chiq") -> done(reply, "Ha, eshityapman. Buyruq bering.")
            containsAny(q, "kimsan", "isming nima") -> done(reply, "Men JARVIS Mobile AI Agentman.")
            containsAny(q, "nima qila olasan", "imkoniyatlaring", "nimalar qilasan", "yordam") -> done(reply, "Telefon ilovalarini ochaman, ekran bo‘ylab harakat qilaman, matn va tugmalarni taniyman, xabarlarni o‘qiyman, AI server ulanganida esa ekranni ko‘rib ko‘p bosqichli vazifalarni bajaraman.")
            containsAny(q, "soat nechi", "vaqt nechi", "soatni ayt") -> done(reply, "Hozir soat ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}.")
            containsAny(q, "bugun sana", "bugungi sana", "sana nechi") -> done(reply, "Bugungi sana ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.")

            containsAny(q, "bosh ekranga o't", "bosh ekranga qayt", "home ga o't") -> accessibility(reply, "Bosh ekranga o‘tdim.") { JarvisAccessibilityService.home() }
            containsAny(q, "orqaga qayt", "orqaga o't", "back") -> accessibility(reply, "Orqaga qaytdim.") { JarvisAccessibilityService.back() }
            containsAny(q, "oxirgi ilovalarni och", "recentni och", "ochiq ilovalarni ko'rsat") -> accessibility(reply, "Ochiq ilovalarni ko‘rsatyapman.") { JarvisAccessibilityService.recents() }
            containsAny(q, "bildirishnomalarni och", "notificationni och") -> accessibility(reply, "Bildirishnomalarni ochyapman.") { JarvisAccessibilityService.notifications() }
            containsAny(q, "tezkor sozlamalarni och", "quick settings", "yuqori panelni och") -> accessibility(reply, "Tezkor sozlamalarni ochyapman.") { JarvisAccessibilityService.quickSettings() }
            containsAny(q, "tepaga sur", "yuqoriga sur") -> accessibility(reply, "Tepaga surdim.") { JarvisAccessibilityService.swipeUp() }
            containsAny(q, "pastga sur", "quyiga sur") -> accessibility(reply, "Pastga surdim.") { JarvisAccessibilityService.swipeDown() }
            containsAny(q, "chapga sur", "chap tomonga sur") -> accessibility(reply, "Chapga surdim.") { JarvisAccessibilityService.swipeLeft() }
            containsAny(q, "o'ngga sur", "ongga sur") -> accessibility(reply, "O‘ngga surdim.") { JarvisAccessibilityService.swipeRight() }
            containsAny(q, "o'rtasini bos", "ekran o'rtasini bos") -> accessibility(reply, "Ekran markazini bosdim.") { JarvisAccessibilityService.tapCenter() }
            q.endsWith("ni bos") || q.endsWith(" tugmasini bos") -> {
                val target = q.removeSuffix(" tugmasini bos").removeSuffix("ni bos").trim().trim('"', '\'', ',', '.')
                if (target.isBlank()) false else accessibility(reply, "$target tugmasini bosdim.") { JarvisAccessibilityService.clickText(target) }
            }
            q.startsWith("shu joyga yoz ") || q.startsWith("matn yoz ") -> {
                val text = q.removePrefix("shu joyga yoz ").removePrefix("matn yoz ").trim().trim('"')
                if (text.isBlank()) false else accessibility(reply, "Matnni yozdim.") { JarvisAccessibilityService.typeText(text) }
            }
            containsAny(q, "ekranda nima bor", "ekrandagini o'qi", "ekranni o'qi") -> {
                if (JarvisAiClient.configured(context)) JarvisAgentOrchestrator.handle(context, q, reply)
                else {
                    val text = JarvisAccessibilityService.visibleText()
                    if (text.isBlank()) done(reply, "Ekrandagi matnni o‘qiy olmadim.") else done(reply, "Ekranda: ${text.take(900)}")
                }
            }

            containsAny(q, "oxirgi xabarni o'qi", "oxirgi xabar nima", "kim yozdi", "so'nggi xabarni o'qi") -> {
                val m = JarvisNotificationService.latest()
                if (m == null) done(reply, "O‘qiy oladigan xabar topilmadi.") else done(reply, "${m.title} yozdi: ${m.text}")
            }
            (q.contains("oxirgi xabarga") || q.contains("so'nggi xabarga")) && q.contains("javob ber") -> prepareReply(q, reply)

            containsAny(q, "aloqaga o't", "aloqani och", "kontaktlarni och") -> launchWithReply(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), "Kontaktlarni ochyapman.", reply)
            containsAny(q, "telefonni och", "telefonga o't", "raqam ter") -> launchWithReply(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), "Telefonni ochyapman.", reply)
            containsAny(q, "xabarni och", "xabarlarni och", "smsni och") -> launchWithReply(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), "Xabarlarni ochyapman.", reply)
            containsAny(q, "kamerani och", "kamera och", "kameraga o't", "rasmga ol") -> launchWithReply(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Kamerani ochyapman.", reply)
            containsAny(q, "sozlamani och", "sozlamalarni och", "sozlamalarga o't") -> launchWithReply(context, Intent(Settings.ACTION_SETTINGS), "Sozlamalarni ochyapman.", reply)
            containsAny(q, "wifi ni och", "wifi och", "wi-fi ni och") -> launchWithReply(context, Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi sozlamalarini ochyapman.", reply)
            containsAny(q, "bluetoothni och", "blutusni och") -> launchWithReply(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth sozlamalarini ochyapman.", reply)
            containsAny(q, "faylni och", "fayllarni och", "faylga o't") -> launchWithReply(context, Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, "Fayllarni ochyapman.", reply)
            containsAny(q, "galereyani och", "galereyaga o't", "rasmlarni och") -> launchWithReply(context, Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), "Galereyani ochyapman.", reply)
            containsAny(q, "kalkulyatorni och", "hisoblagichni och") -> launchWithReply(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR), "Kalkulyatorni ochyapman.", reply)
            containsAny(q, "budilnikni och", "alarmni och") -> launchWithReply(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), "Budilnikni ochyapman.", reply)
            containsAny(q, "fonarni yoq", "chiroqni yoq", "flashni yoq") -> setTorch(context, true, reply)
            containsAny(q, "fonarni o'chir", "chiroqni o'chir", "flashni o'chir") -> setTorch(context, false, reply)
            containsAny(q, "ovozni ko'tar", "ovozni baland qil") -> changeVolume(context, AudioManager.ADJUST_RAISE, "Ovozni ko‘taryapman.", reply)
            containsAny(q, "ovozni pasaytir", "ovozni kamaytir") -> changeVolume(context, AudioManager.ADJUST_LOWER, "Ovozni pasaytiryapman.", reply)

            q.contains("youtube") && containsAny(q, "qidir", "izla", "top") -> searchWeb(context, q, true, reply)
            q.contains("google") && containsAny(q, "qidir", "izla", "top") -> searchWeb(context, q, false, reply)
            containsAny(q, "xaritadan", "xaritada") && containsAny(q, "qidir", "top", "ko'rsat") -> searchMaps(context, q, reply)

            containsAny(q, "youtube'ni och", "youtube ni och", "youtubega o't") -> openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
            containsAny(q, "telegramni och", "telegram ni och", "telegramga o't") -> openKnownApp(context, "org.telegram.messenger", "Telegram", reply)
            containsAny(q, "whatsappni och", "whatsappga o't", "vatsapni och") -> openKnownApp(context, "com.whatsapp", "WhatsApp", reply)
            containsAny(q, "instagramni och", "instagramga o't") -> openKnownApp(context, "com.instagram.android", "Instagram", reply)
            containsAny(q, "mapsni och", "xaritani och", "xaritaga o't") -> openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
            containsAny(q, "chromeni och", "brauzerni och", "chromega o't") -> openKnownApp(context, "com.android.chrome", "Chrome", reply)
            containsAny(q, "play marketni och", "play storeni och", "marketni och") -> openKnownApp(context, "com.android.vending", "Play Store", reply)

            containsAny(q, "internetdan", "google'dan", "google dan") && containsAny(q, "qidir", "izla", "top") -> searchWeb(context, q, false, reply)
            isOpenAppCommand(q) -> openInstalledAppBySpokenName(context, q, reply)
            else -> JarvisAgentOrchestrator.handle(context, q, reply)
        }
    }

    private fun prepareReply(q: String, reply: (String) -> Unit): Boolean {
        val latest = JarvisNotificationService.latest() ?: return done(reply, "Oxirgi xabar topilmadi.")
        if (!JarvisNotificationService.hasReplyAction()) return done(reply, "${latest.title} xabariga bildirishnoma orqali javob berib bo‘lmaydi.")
        var text = q.substringAfter("xabarga", "").substringBeforeLast("javob ber", "").trim()
        text = text.removePrefix("shu ").removeSuffix("deb").trim().trim('"', '\'', ',', '.')
        if (text.isBlank()) return done(reply, "Qanday javob yuborishni ayting.")
        pendingReplyText = text
        return done(reply, "${latest.title} ga: $text. Shu javobni yuboraymi? Tasdiqlayman deng.")
    }

    private fun accessibility(reply: (String) -> Unit, success: String, action: () -> Boolean): Boolean {
        if (!JarvisAccessibilityService.available()) return done(reply, "JARVIS telefon boshqaruv ruxsati yoqilmagan.")
        return if (action()) done(reply, success) else done(reply, "Bu amalni hozir bajara olmadim.")
    }

    private fun searchWeb(context: Context, q: String, youtube: Boolean, reply: (String) -> Unit): Boolean {
        val words = if (youtube) listOf("youtube'dan", "youtube dan", "youtube", "qidir", "izla", "top", "menga") else listOf("internetdan", "google'dan", "google dan", "google", "qidir", "izla", "top", "menga")
        val term = cleanQuery(q, words)
        if (term.isBlank()) return openKnownApp(context, if (youtube) "com.google.android.youtube" else "com.android.chrome", if (youtube) "YouTube" else "Chrome", reply)
        val url = if (youtube) "https://www.youtube.com/results?search_query=${Uri.encode(term)}" else "https://www.google.com/search?q=${Uri.encode(term)}"
        return launchWithReply(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), if (youtube) "YouTube'dan $term bo‘yicha qidiryapman." else "Internetdan $term bo‘yicha qidiryapman.", reply)
    }

    private fun searchMaps(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val term = cleanQuery(q, listOf("xaritadan", "xaritada", "google maps", "maps", "ko'rsat", "qidir", "top", "och", "menga"))
        if (term.isBlank()) return openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
        return launchWithReply(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(term)}")), "Xaritadan $term joyini ochyapman.", reply)
    }

    private fun setTorch(context: Context, enabled: Boolean, reply: (String) -> Unit): Boolean = try {
        val manager = context.getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return done(reply, "Bu telefonda fonar topilmadi.")
        manager.setTorchMode(id, enabled)
        done(reply, if (enabled) "Fonar yoqildi." else "Fonar o‘chirildi.")
    } catch (_: Exception) { done(reply, "Fonarni boshqara olmadim.") }

    private fun changeVolume(context: Context, direction: Int, message: String, reply: (String) -> Unit): Boolean = try {
        context.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        done(reply, message)
    } catch (_: Exception) { false }

    private fun openInstalledAppBySpokenName(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val target = extractAppTarget(q)
        if (target.length < 2) return false
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val best = context.packageManager.queryIntentActivities(launcher, 0)
            .map { it to normalize(it.loadLabel(context.packageManager).toString()) }
            .filter { (_, label) -> label.contains(target) || target.contains(label) }
            .minByOrNull { (_, label) -> kotlin.math.abs(label.length - target.length) } ?: return false
        val label = best.first.loadLabel(context.packageManager).toString()
        val intent = context.packageManager.getLaunchIntentForPackage(best.first.activityInfo.packageName) ?: return false
        return launchWithReply(context, intent, "$label ilovasini ochyapman.", reply)
    }

    private fun openKnownApp(context: Context, packageName: String, label: String, reply: (String) -> Unit): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return done(reply, "$label ilovasi telefonda topilmadi.")
        return launchWithReply(context, intent, "$label ilovasini ochyapman.", reply)
    }

    private fun launchWithReply(context: Context, intent: Intent, text: String, reply: (String) -> Unit): Boolean {
        reply(text)
        return launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }

    private fun done(reply: (String) -> Unit, text: String): Boolean { reply(text); return true }
    private fun isOpenAppCommand(q: String): Boolean = containsAny(q, "ga o't", "ga ot", "ga kir", "ni och", "ishga tushir", "ilovasini och", "ilovani och")

    private fun extractAppTarget(value: String): String {
        var result = value
        listOf("ilovasini och", "ilovani och", "ishga tushir", "ga o't", "ga ot", "ga kir", "ni och", "och").forEach { result = result.replace(it, " ") }
        return result.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.')
    }

    private fun cleanQuery(value: String, words: List<String>): String {
        var result = value
        words.sortedByDescending { it.length }.forEach { result = result.replace(it, " ") }
        return result.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.')
    }

    fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('’', '\'').replace('`', '\'').replace('ʻ', '\'').replace('ʼ', '\'')
        .replace("g‘", "g'").replace("o‘", "o'")
        .replace(Regex("\\s+"), " ").trim()

    private fun containsAny(value: String, vararg keys: String): Boolean = keys.any { value.contains(it) }
}
