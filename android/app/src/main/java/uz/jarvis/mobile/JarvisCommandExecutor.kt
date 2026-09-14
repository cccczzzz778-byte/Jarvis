package uz.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object JarvisCommandExecutor {

    fun execute(context: Context, raw: String, reply: (String) -> Unit): Boolean {
        val q = normalize(raw)
        if (q.isBlank()) return false

        return when {
            containsAny(q, "salom", "assalomu alaykum") -> done(reply, "Va alaykum assalom. Xizmatingizga tayyorman.")
            containsAny(q, "shu yerdamisan", "bormisan", "eshityapsanmi") -> done(reply, "Ha, eshityapman. Buyruq berishingiz mumkin.")
            containsAny(q, "kimsan", "isming nima") -> done(reply, "Men JARVIS Mobile, o‘zbekcha ovozli yordamchiman.")
            containsAny(q, "nima qila olasan", "imkoniyatlaring", "yordam") -> done(reply, "Ilovalarni ochaman, Google, YouTube va xaritada qidiraman, aloqa, telefon, kamera, xabarlar, sozlamalar va boshqa o‘rnatilgan ilovalarga o‘taman.")
            containsAny(q, "soat nechi", "vaqt nechi", "hozir soat") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                done(reply, "Hozir soat $value.")
            }
            containsAny(q, "bugun sana", "bugungi sana", "sana nechi") -> {
                val value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                done(reply, "Bugungi sana $value.")
            }

            containsAny(q, "aloqaga o't", "aloqani och", "kontaktga o't", "kontaktlarni och", "kontaktga kir") -> {
                reply("Aloqalarni ochyapman.")
                launch(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }
            containsAny(q, "telefonni och", "telefonга o't", "telefon ga o't", "raqam ter") -> {
                reply("Telefonni ochyapman.")
                launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            }
            containsAny(q, "xabarni och", "xabarlarni och", "smsni och", "sms ga o't") -> {
                reply("Xabarlarni ochyapman.")
                launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            }
            containsAny(q, "kamerani och", "kamera och", "kameraga o't", "kameraga kir") -> {
                reply("Kamerani ochyapman.")
                launch(context, Intent("android.media.action.IMAGE_CAPTURE"))
            }
            containsAny(q, "sozlamani och", "sozlamalarga o't", "nastroykani och", "settingsni och") -> {
                reply("Sozlamalarni ochyapman.")
                launch(context, Intent(Settings.ACTION_SETTINGS))
            }
            containsAny(q, "faylni och", "fayllarni och", "faylga o't") -> {
                reply("Fayllarni ochyapman.")
                launch(context, Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                })
            }

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

            containsAny(q, "youtube'ni och", "youtube ni och", "youtubega o't", "youtubega kir") -> openKnownApp(context, "com.google.android.youtube", "YouTube", reply)
            containsAny(q, "telegramni och", "telegramga o't", "telegramga kir") -> openKnownApp(context, "org.telegram.messenger", "Telegram", reply)
            containsAny(q, "whatsappni och", "whatsappga o't", "vatsapni och", "vatsapga o't") -> openKnownApp(context, "com.whatsapp", "WhatsApp", reply)
            containsAny(q, "instagramni och", "instagramga o't", "instagramga kir") -> openKnownApp(context, "com.instagram.android", "Instagram", reply)
            containsAny(q, "mapsni och", "xaritani och", "xaritaga o't", "xaritaga kir") -> openKnownApp(context, "com.google.android.apps.maps", "Xarita", reply)
            containsAny(q, "chromeni och", "brauzerni och", "chromega o't") -> openKnownApp(context, "com.android.chrome", "Chrome", reply)

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
        if (intent == null) {
            reply("$label ilovasi telefonda topilmadi.")
            return true
        }
        reply("$label ilovasini ochyapman.")
        launch(context, intent)
        return true
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun done(reply: (String) -> Unit, text: String): Boolean {
        reply(text)
        return true
    }

    private fun isOpenAppCommand(q: String): Boolean =
        containsAny(q, "ga o't", "ga ot", "ga kir", "ni och", "ishga tushir", "ilovasini och", "ilovani och")

    private fun extractAppTarget(value: String): String {
        var result = value
        listOf("ilovasini och", "ilovani och", "ishga tushir", "ga o't", "ga ot", "ga kir", "ni och", "och").forEach {
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
        .trim()

    private fun containsAny(value: String, vararg keys: String): Boolean = keys.any { value.contains(it) }
}
