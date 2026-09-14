package uz.jarvis.mobile

import android.app.AlarmManager
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

    fun execute(context: Context, raw: String, reply: (String) -> Unit): Boolean {
        val q = normalize(raw)
        if (q.isBlank()) return false

        return when {
            containsAny(q, "salom", "assalomu alaykum", "assalom") -> done(reply, "Va alaykum assalom. Xizmatingizga tayyorman.")
            containsAny(q, "shu yerdamisan", "shu yerdami", "bormisan", "eshityapsanmi", "aloqadamisan", "aloqaga chiq") -> done(reply, "Ha, eshityapman. Buyruq berishingiz mumkin.")
            containsAny(q, "kimsan", "isming nima", "sen kimsan") -> done(reply, "Men JARVIS Mobile, o‘zbekcha ovozli yordamchiman.")
            containsAny(q, "nima qila olasan", "imkoniyatlaring", "nimalar qilasan", "yordam") -> done(reply, "Telefon ilovalarini ochaman, Google, YouTube va xaritada qidiraman, kontaktlar, telefon, kamera, xabarlar, sozlamalar, fonar, kalkulyator, galereya va boshqa ilovalarga o‘taman.")
            containsAny(q, "soat nechi", "vaqt nechi", "hozir soat", "soatni ayt") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                done(reply, "Hozir soat $value.")
            }
            containsAny(q, "bugun sana", "bugungi sana", "sana nechi", "sanani ayt") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                done(reply, "Bugungi sana $value.")
            }

            containsAny(q, "aloqaga o't", "aloqaga ot", "aloqaga o'tish", "aloqani och", "kontaktga o't", "kontaktga ot", "kontaktlarni och", "kontaktga kir", "kontaktni och") -> {
                reply("Kontaktlarni ochyapman.")
                launch(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }
            containsAny(q, "telefonni och", "telefonga o't", "telefonga ot", "telefonga kir", "raqam ter", "qo'ng'iroqni och") -> {
                reply("Telefonni ochyapman.")
                launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            }
            containsAny(q, "xabarni och", "xabarlarni och", "xabar ga o't", "xabarga o't", "xabarga ot", "smsni och", "smsga o't", "smsga ot") -> {
                reply("Xabarlarni ochyapman.")
                launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            }
            containsAny(q, "kamerani och", "kamera och", "kameraga o't", "kameraga ot", "kameraga kir", "rasmga ol") -> {
                reply("Kamerani ochyapman.")
                launch(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            }
            containsAny(q, "sozlamani och", "sozlamalarni och", "sozlamalarga o't", "sozlamalarga ot", "nastroykani och", "settingsni och") -> {
                reply("Sozlamalarni ochyapman.")
                launch(context, Intent(Settings.ACTION_SETTINGS))
            }
            containsAny(q, "wifi ni och", "wifi och", "wi-fi ni och", "internet sozlamasini och") -> {
                reply("Wi-Fi sozlamalarini ochyapman.")
                launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            containsAny(q, "bluetoothni och", "bluetooth ni och", "blutusni och", "blutus och") -> {
                reply("Bluetooth sozlamalarini ochyapman.")
                launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            containsAny(q, "faylni och", "fayllarni och", "faylga o't", "faylga ot", "fayl menejerni och") -> {
                reply("Fayllarni ochyapman.")
                launch(context, Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                })
            }
            containsAny(q, "galereyani och", "galereyaga o't", "galereyaga ot", "rasmlarni och") -> {
                reply("Galereyani ochyapman.")
                launch(context, Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            }
            containsAny(q, "kalkulyatorni och", "kalkulyatorga o't", "kalkulyatorga ot", "hisoblagichni och") -> {
                reply("Kalkulyatorni ochyapman.")
                launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
            }
            containsAny(q, "budilnikni och", "budilnikka o't", "budilnikka ot", "signalni och", "alarmni och") -> {
                reply("Budilnikni ochyapman.")
                launch(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }
            containsAny(q, "fonarni yoq", "fanarni yoq", "chiroqni yoq", "flashni yoq") -> setTorch(context, true, reply)
            containsAny(q, "fonarni o'chir", "fanarni o'chir", "chiroqni o'chir", "flashni o'chir") -> setTorch(context, false, reply)
            containsAny(q, "ovozni ko'tar", "ovozni baland qil", "volume up") -> changeVolume(context, AudioManager.ADJUST_RAISE, "Ovozni ko‘taryapman.", reply)
            containsAny(q, "ovozni pasaytir", "ovozni kamaytir", "volume down") -> changeVolume(context, AudioManager.ADJUST_LOWER, "Ovozni pasaytiryapman.", reply)

            q.contains("youtube") && containsAny(q, "qidir", "izla", "top") -> {
                val term = cleanQuery(q, listOf("youtube'dan", "youtube dan", "youtube", "qidir", "izla", "top", "menga"))
                if (term.isBlank()) openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
                else {
                    reply("YouTube'dan $term bo‘yicha qidiryapman.")
                    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(term)}")))
                }
            }
            q.contains("google") && containsAny(q, "qidir", "izla", "top") -> {
                val term = cleanQuery(q, listOf("google'dan", "google dan", "google", "qidir", "izla", "top", "menga"))
                if (term.isBlank()) openKnownApp(context, "com.android.chrome", "Chrome", reply)
                else {
                    reply("Google'dan $term bo‘yicha qidiryapman.")
                    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(term)}")))
                }
            }
            containsAny(q, "xaritadan", "xaritada") && containsAny(q, "qidir", "top", "ko'rsat", "och") -> {
                val term = cleanQuery(q, listOf("xaritadan", "xaritada", "google maps", "maps", "ko'rsat", "qidir", "top", "och", "menga"))
                if (term.isBlank()) openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
                else {
                    reply("Xaritadan $term joyini ochyapman.")
                    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(term)}")))
                }
            }

            containsAny(q, "youtube'ni och", "youtube ni och", "youtubega o't", "youtubega ot", "youtubega kir") -> openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
            containsAny(q, "telegramni och", "telegram ni och", "telegramga o't", "telegramga ot", "telegramga kir") -> openKnownApp(context, "org.telegram.messenger", "Telegram", reply)
            containsAny(q, "whatsappni och", "whatsapp ni och", "whatsappga o't", "whatsappga ot", "vatsapni och", "vatsapga o't", "vatsapga ot") -> openKnownApp(context, "com.whatsapp", "WhatsApp", reply)
            containsAny(q, "instagramni och", "instagram ni och", "instagramga o't", "instagramga ot", "instagramga kir") -> openKnownApp(context, "com.instagram.android", "Instagram", reply)
            containsAny(q, "mapsni och", "xaritani och", "xaritaga o't", "xaritaga ot", "xaritaga kir") -> openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
            containsAny(q, "chromeni och", "chrome ni och", "brauzerni och", "chromega o't", "chromega ot") -> openKnownApp(context, "com.android.chrome", "Chrome", reply)
            containsAny(q, "play marketni och", "play storeni och", "play store ni och", "marketni och") -> openKnownApp(context, "com.android.vending", "Play Store", reply)

            containsAny(q, "internetdan", "google'dan", "google dan") && containsAny(q, "qidir", "izla", "top") -> {
                val term = cleanQuery(q, listOf("internetdan", "google'dan", "google dan", "qidir", "izla", "top", "menga"))
                if (term.isNotBlank()) {
                    reply("Internetdan $term bo‘yicha qidiryapman.")
                    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(term)}")))
                } else false
            }

            isOpenAppCommand(q) -> openInstalledAppBySpokenName(context, q, reply)
            else -> false
        }
    }

    private fun setTorch(context: Context, enabled: Boolean, reply: (String) -> Unit): Boolean {
        return try {
            val manager = context.getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return done(reply, "Bu telefonda fonar topilmadi.")
            manager.setTorchMode(cameraId, enabled)
            done(reply, if (enabled) "Fonar yoqildi." else "Fonar o‘chirildi.")
        } catch (_: Exception) {
            done(reply, "Fonarni boshqarish uchun kamera ruxsatini tekshiring.")
        }
    }

    private fun changeVolume(context: Context, direction: Int, message: String, reply: (String) -> Unit): Boolean {
        return try {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            done(reply, message)
        } catch (_: Exception) { false }
    }

    private fun openInstalledAppBySpokenName(context: Context, q: String, reply: (String) -> Unit): Boolean {
        val target = extractAppTarget(q)
        if (target.length < 2) return false
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = context.packageManager.queryIntentActivities(launcher, 0)
        val best = candidates
            .map { info -> info to normalize(info.loadLabel(context.packageManager).toString()) }
            .filter { (_, label) -> label.contains(target) || target.contains(label) }
            .minByOrNull { (_, label) -> kotlin.math.abs(label.length - target.length) }
            ?: return false
        val label = best.first.loadLabel(context.packageManager).toString()
        val packageName = best.first.activityInfo.packageName
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        reply("$label ilovasini ochyapman.")
        return launch(context, intent)
    }

    private fun openKnownApp(context: Context, packageName: String, label: String, reply: (String) -> Unit): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) return done(reply, "$label ilovasi telefonda topilmadi.")
        reply("$label ilovasini ochyapman.")
        launch(context, intent)
        return true
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }

    private fun done(reply: (String) -> Unit, text: String): Boolean {
        reply(text)
        return true
    }

    private fun isOpenAppCommand(q: String): Boolean =
        containsAny(q, "ga o't", "ga ot", "ga kir", "ni och", "ni ishga tushir", "ishga tushir", "ilovasini och", "ilovani och")

    private fun extractAppTarget(value: String): String {
        var result = value
        listOf("ilovasini och", "ilovani och", "ni ishga tushir", "ishga tushir", "ga o't", "ga ot", "ga kir", "ni och", "och").forEach {
            result = result.replace(it, " ")
        }
        return result.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.')
    }

    private fun cleanQuery(value: String, words: List<String>): String {
        var result = value
        words.sortedByDescending { it.length }.forEach { result = result.replace(it, " ") }
        return result.replace(Regex("\\s+"), " ").trim().trim('-', ':', ',', '.')
    }

    fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace('`', '\'')
        .replace('ʻ', '\'')
        .replace('ʼ', '\'')
        .replace("g‘", "g'")
        .replace("o‘", "o'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, vararg keys: String): Boolean = keys.any { value.contains(it) }
}
