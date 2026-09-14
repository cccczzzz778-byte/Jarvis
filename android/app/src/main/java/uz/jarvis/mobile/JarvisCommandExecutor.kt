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

        if (containsAny(q, "tasdiqlayman", "tasdiqla", "ha yubor", "yuboraver") && pendingReplyText != null) {
            val text = pendingReplyText!!
            pendingReplyText = null
            return if (JarvisNotificationService.replyToLatest(text)) {
                done(reply, "Javob yuborildi.")
            } else done(reply, "Javob yuborilmadi. Oxirgi bildirishnomada tezkor javob imkoniyati yo‘q.")
        }
        if (containsAny(q, "bekor qil", "yuborma", "to'xtat") && pendingReplyText != null) {
            pendingReplyText = null
            return done(reply, "Javob yuborish bekor qilindi.")
        }

        return when {
            containsAny(q, "salom", "assalomu alaykum", "assalom") -> done(reply, "Va alaykum assalom. Xizmatingizga tayyorman.")
            containsAny(q, "shu yerdamisan", "bormisan", "eshityapsanmi", "aloqadamisan", "aloqaga chiq") -> done(reply, "Ha, eshityapman. Buyruq berishingiz mumkin.")
            containsAny(q, "kimsan", "isming nima", "sen kimsan") -> done(reply, "Men JARVIS Mobile. Telefonni ovoz bilan boshqarishda yordam beraman.")
            containsAny(q, "nima qila olasan", "imkoniyatlaring", "nimalar qilasan", "yordam") -> done(reply, "Ilovalarni ochaman, telefon bo‘ylab orqaga va bosh ekranga o‘taman, ekranni suraman, tugmalarni nomi bilan bosaman, matn yozaman, oxirgi xabarni o‘qiyman va tasdiqingizdan keyin tezkor javob yuboraman.")
            containsAny(q, "soat nechi", "vaqt nechi", "hozir soat", "soatni ayt") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                done(reply, "Hozir soat $value.")
            }
            containsAny(q, "bugun sana", "bugungi sana", "sana nechi", "sanani ayt") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                done(reply, "Bugungi sana $value.")
            }

            containsAny(q, "bosh ekranga o't", "bosh ekranga qayt", "home ga o't", "uy ekraniga o't") -> accessibility(reply, "Bosh ekranga o‘tdim.") { JarvisAccessibilityService.home() }
            containsAny(q, "orqaga qayt", "orqaga o't", "bir qadam orqaga", "back") -> accessibility(reply, "Orqaga qaytdim.") { JarvisAccessibilityService.back() }
            containsAny(q, "oxirgi ilovalarni och", "recentni och", "recent apps", "ochiq ilovalarni ko'rsat") -> accessibility(reply, "Ochiq ilovalarni ko‘rsatyapman.") { JarvisAccessibilityService.recents() }
            containsAny(q, "bildirishnomalarni och", "notificationni och", "xabarnomalarni och") -> accessibility(reply, "Bildirishnomalarni ochyapman.") { JarvisAccessibilityService.notifications() }
            containsAny(q, "tezkor sozlamalarni och", "quick settings", "yuqori panelni och") -> accessibility(reply, "Tezkor sozlamalarni ochyapman.") { JarvisAccessibilityService.quickSettings() }
            containsAny(q, "tepaga sur", "yuqoriga sur", "pastdagi narsani ko'rsat") -> accessibility(reply, "Tepaga surdim.") { JarvisAccessibilityService.swipeUp() }
            containsAny(q, "pastga sur", "quyiga sur", "yuqoridagi narsani ko'rsat") -> accessibility(reply, "Pastga surdim.") { JarvisAccessibilityService.swipeDown() }
            containsAny(q, "chapga sur", "chap tomonga sur") -> accessibility(reply, "Chapga surdim.") { JarvisAccessibilityService.swipeLeft() }
            containsAny(q, "o'ngga sur", "ongga sur", "o'ng tomonga sur") -> accessibility(reply, "O‘ngga surdim.") { JarvisAccessibilityService.swipeRight() }
            containsAny(q, "o'rtasini bos", "ekran o'rtasini bos", "markazni bos") -> accessibility(reply, "Ekran markazini bosdim.") { JarvisAccessibilityService.tapCenter() }
            q.endsWith("ni bos") || q.endsWith(" tugmasini bos") -> {
                val target = q.removeSuffix(" tugmasini bos").removeSuffix("ni bos").trim().trim('"', '\'', ',', '.')
                if (target.isBlank()) false else accessibility(reply, "$target tugmasini bosishga harakat qildim.") { JarvisAccessibilityService.clickText(target) }
            }
            q.startsWith("shu joyga yoz ") || q.startsWith("matn yoz ") -> {
                val text = q.removePrefix("shu joyga yoz ").removePrefix("matn yoz ").trim().trim('"')
                if (text.isBlank()) false else accessibility(reply, "Matnni yozdim.") { JarvisAccessibilityService.typeText(text) }
            }
            containsAny(q, "ekranda nima bor", "ekrandagini o'qi", "ekranni o'qi") -> {
                val text = JarvisAccessibilityService.visibleText()
                if (text.isBlank()) done(reply, "Ekrandagi matnni o‘qiy olmadim. JARVIS boshqaruv ruxsatini tekshiring.")
                else done(reply, "Ekranda: ${text.take(700)}")
            }

            containsAny(q, "oxirgi xabarni o'qi", "oxirgi xabar nima", "kim yozdi", "so'nggi xabarni o'qi") -> {
                val message = JarvisNotificationService.latest()
                if (message == null) done(reply, "Hozircha o‘qiy oladigan xabar topilmadi. Bildirishnomalarga kirish ruxsatini tekshiring.")
                else done(reply, "${message.title} yozdi: ${message.text}")
            }
            q.contains("oxirgi xabarga") && q.contains("javob ber") -> prepareReply(q, reply)
            q.contains("so'nggi xabarga") && q.contains("javob ber") -> prepareReply(q, reply)

            containsAny(q, "aloqaga o't", "aloqaga ot", "aloqani och", "kontaktga o't", "kontaktlarni och", "kontaktga kir") -> {
                reply("Kontaktlarni ochyapman.")
                launch(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }
            containsAny(q, "telefonni och", "telefonga o't", "telefonga kir", "raqam ter", "qo'ng'iroqni och") -> {
                reply("Telefonni ochyapman.")
                launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            }
            containsAny(q, "xabarni och", "xabarlarni och", "xabarga o't", "smsni och") -> {
                reply("Xabarlarni ochyapman.")
                launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            }
            containsAny(q, "kamerani och", "kamera och", "kameraga o't", "kameraga kir", "rasmga ol") -> {
                reply("Kamerani ochyapman.")
                launch(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            }
            containsAny(q, "sozlamani och", "sozlamalarni och", "sozlamalarga o't", "nastroykani och", "settingsni och") -> {
                reply("Sozlamalarni ochyapman.")
                launch(context, Intent(Settings.ACTION_SETTINGS))
            }
            containsAny(q, "wifi ni och", "wifi och", "wi-fi ni och") -> {
                reply("Wi-Fi sozlamalarini ochyapman.")
                launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            containsAny(q, "bluetoothni och", "bluetooth ni och", "blutusni och") -> {
                reply("Bluetooth sozlamalarini ochyapman.")
                launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            containsAny(q, "faylni och", "fayllarni och", "faylga o't", "fayl menejerni och") -> {
                reply("Fayllarni ochyapman.")
                launch(context, Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" })
            }
            containsAny(q, "galereyani och", "galereyaga o't", "rasmlarni och") -> {
                reply("Galereyani ochyapman.")
                launch(context, Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            }
            containsAny(q, "kalkulyatorni och", "kalkulyatorga o't", "hisoblagichni och") -> {
                reply("Kalkulyatorni ochyapman.")
                launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
            }
            containsAny(q, "budilnikni och", "budilnikka o't", "signalni och", "alarmni och") -> {
                reply("Budilnikni ochyapman.")
                launch(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }
            containsAny(q, "fonarni yoq", "fanarni yoq", "chiroqni yoq", "flashni yoq") -> setTorch(context, true, reply)
            containsAny(q, "fonarni o'chir", "fanarni o'chir", "chiroqni o'chir", "flashni o'chir") -> setTorch(context, false, reply)
            containsAny(q, "ovozni ko'tar", "ovozni baland qil", "volume up") -> changeVolume(context, AudioManager.ADJUST_RAISE, "Ovozni ko‘taryapman.", reply)
            containsAny(q, "ovozni pasaytir", "ovozni kamaytir", "volume down") -> changeVolume(context, AudioManager.ADJUST_LOWER, "Ovozni pasaytiryapman.", reply)

            q.contains("youtube") && containsAny(q, "qidir", "izla", "top") -> searchYouTube(context, q, reply)
            q.contains("google") && containsAny(q, "qidir", "izla", "top") -> searchGoogle(context, q, reply)
            containsAny(q, "xaritadan", "xaritada") && containsAny(q, "qidir", "top", "ko'rsat", "och") -> searchMaps(context, q, reply)

            containsAny(q, "youtube'ni och", "youtube ni och", "youtubega o't", "youtubega kir") -> openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
            containsAny(q, "telegramni och", "telegram ni och", "telegramga o't", "telegramga kir") -> openKnownApp(context, "org.telegram.messenger", "Telegram", reply)
            containsAny(q, "whatsappni och", "whatsapp ni och", "whatsappga o't", "vatsapni och", "vatsapga o't") -> openKnownApp(context, "com.whatsapp", "WhatsApp", reply)
            containsAny(q, "instagramni och", "instagram ni och", "instagramga o't", "instagramga kir") -> openKnownApp(context, "com.instagram.android", "Instagram", reply)
            containsAny(q, "mapsni och", "xaritani och", "xaritaga o't", "xaritaga kir") -> openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
            containsAny(q, "chromeni och", "chrome ni och", "brauzerni och", "chromega o't") -> openKnownApp(context, "com.android.chrome", "Chrome", reply)
            containsAny(q, "play marketni och", "play storeni och", "play store ni och", "marketni och") -> openKnownApp(context, "com.android.vending", "Play Store", reply)

            containsAny(q, "internetdan", "google'dan", "google dan") && containsAny(q, "qidir", "izla", "top") -> searchGoogle(context, q, reply)
            isOpenAppCommand(q) -> openInstalledAppBySpokenName(context, q, reply)
            else -> false
        }
    }

    private fun prepareReply(q: String, reply: (String) -> Unit): Boolean {
        val latest = JarvisNotificationService.latest()
            ?: return done(reply, "Oxirgi xabar topilmadi. Bildirishnomalarga kirish ruxsatini tekshiring.")
        if (!JarvisNotificationService.hasReplyAction()) return done(reply, "${latest.title} xabariga bildirishnoma orqali javob berib bo‘lmaydi.")
        var text = q.substringAfter("xabarga", "").substringBeforeLast("javob ber", "").trim()
        text = text.removePrefix("shu ").removeSuffix("deb").trim().trim('"', '\'', ',', '.')
        if (text.isBlank()) return done(reply, "Qanday javob yuborishni ayting. Masalan: oxirgi xabarga hozir boraman deb javob ber.")
        pendingReplyText = text
        return done(reply, "${latest.title} ga: $text. Shu javobni yuboraymi? Tasdiqlayman deng.")
    }

    private fun accessibility(reply: (String) -> Unit, success: String, action: () -> Boolean): Boolean {
        if (!JarvisAccessibilityService.available()) return done(reply, "JARVIS telefon boshqaruv ruxsati yoqilmagan. Ilovadan maxsus imkoniyatlar ruxsatini yoqing.")
        return if (action()) done(reply, success) else done(reply, "Bu amalni hozir bajara olmadim.")
    }

    private fun searchYouTube(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val term = cleanQuery(q, listOf("youtube'dan", "youtube dan", "youtube", "qidir", "izla", "top", "menga"))
        if (term.isBlank()) return openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
        reply("YouTube'dan $term bo‘yicha qidiryapman.")
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(term)}")))
    }

    private fun searchGoogle(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val term = cleanQuery(q, listOf("internetdan", "google'dan", "google dan", "google", "qidir", "izla", "top", "menga"))
        if (term.isBlank()) return openKnownApp(context, "com.android.chrome", "Chrome", reply)
        reply("Internetdan $term bo‘yicha qidiryapman.")
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(term)}")))
    }

    private fun searchMaps(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val term = cleanQuery(q, listOf("xaritadan", "xaritada", "google maps", "maps", "ko'rsat", "qidir", "top", "och", "menga"))
        if (term.isBlank()) return openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
        reply("Xaritadan $term joyini ochyapman.")
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(term)}")))
    }

    private fun setTorch(context: Context, enabled: Boolean, reply: (String) -> Unit): Boolean = try {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return done(reply, "Bu telefonda fonar topilmadi.")
        manager.setTorchMode(cameraId, enabled)
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
        val candidates = context.packageManager.queryIntentActivities(launcher, 0)
        val best = candidates.map { info -> info to normalize(info.loadLabel(context.packageManager).toString()) }
            .filter { (_, label) -> label.contains(target) || target.contains(label) }
            .minByOrNull { (_, label) -> kotlin.math.abs(label.length - target.length) } ?: return false
        val label = best.first.loadLabel(context.packageManager).toString()
        val intent = context.packageManager.getLaunchIntentForPackage(best.first.activityInfo.packageName) ?: return false
        reply("$label ilovasini ochyapman.")
        return launch(context, intent)
    }

    private fun openKnownApp(context: Context, packageName: String, label: String, reply: (String) -> Unit): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) return done(reply, "$label ilovasi telefonda topilmadi.")
        reply("$label ilovasini ochyapman.")
        return launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }

    private fun done(reply: (String) -> Unit, text: String): Boolean { reply(text); return true }

    private fun isOpenAppCommand(q: String): Boolean = containsAny(q, "ga o't", "ga ot", "ga kir", "ni och", "ni ishga tushir", "ishga tushir", "ilovasini och", "ilovani och")

    private fun extractAppTarget(value: String): String {
        var result = value
        listOf("ilovasini och", "ilovani och", "ni ishga tushir", "ishga tushir", "ga o't", "ga ot", "ga kir", "ni och", "och").forEach { result = result.replace(it, " ") }
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
