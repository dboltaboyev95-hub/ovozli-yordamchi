package uz.yordamchi.ovoz

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Eshitilgan gapni tushunib, kerakli amalni bajaradi. Natija matnini qaytaradi yoki null (tushunilmadi). */
class CommandProcessor(private val ctx: Context, private val speak: (String) -> Unit) {

    data class AppEntry(val label: String, val key: String, val pkg: String)

    private var apps: List<AppEntry> = emptyList()

    companion object {
        fun norm(s: String): String = s.lowercase()
            .replace(Regex("[‘’ʻʼ`'´]"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        private val cyr = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
            'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
            'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
            'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q",
            'ғ' to "g", 'ҳ' to "h"
        )

        fun latin(s: String): String = buildString { for (c in s) append(cyr[c] ?: c.toString()) }

        private val aliases = mapOf(
            "yutub" to "youtube", "yutube" to "youtube", "insta" to "instagram", "instagramm" to "instagram",
            "vatsap" to "whatsapp", "vatsapp" to "whatsapp", "telegramm" to "telegram", "tiktok" to "tiktok",
            "kamera" to "camera", "galereya" to "gallery", "sozlamalar" to "settings", "nastroyki" to "settings",
            "xrom" to "chrome", "gugl" to "google", "karta" to "maps", "kalkulyator" to "calculator"
        )

        fun lev(a: String, b: String): Int {
            val d = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = d[0]; d[0] = i
                for (j in 1..b.length) {
                    val tmp = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return d[b.length]
        }

        fun stripNi(s: String): String = s.split(" ").filter { it != "ni" }
            .joinToString(" ") { if (it.length > 4 && it.endsWith("ni")) it.dropLast(2) else it }
    }

    fun loadApps() {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(i, 0).map {
            val label = it.loadLabel(pm).toString()
            AppEntry(label, latin(norm(label)), it.activityInfo.packageName)
        }
    }

    private fun acc(block: (PhoneControlService) -> Unit, ok: String): String {
        val s = PhoneControlService.instance ?: return "Avval Maxsus imkoniyatlarda xizmatni yoqing"
        block(s); return ok
    }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (PhoneControlService.instance ?: ctx).startActivity(i)
    }

    fun handle(raw: String): String? {
        val t = norm(raw)
        if (t.isEmpty()) return null
        val words = t.split(" ")
        fun has(vararg k: String) = k.any { t.contains(it) }
        fun word(vararg k: String) = k.any { it in words }

        return try {
            when {
                has("soat necha", "который час", "what time") -> {
                    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    speak(now); "Soat $now"
                }
                has("chiroqni yoq", "chiroq yoq", "fonarni yoq", "включи фонарик", "фонарик включ", "flashlight on") -> torch(true)
                has("chiroqni ochir", "chiroq ochir", "fonarni ochir", "выключи фонарик", "фонарик выключ", "flashlight off") -> torch(false)
                has("ovozni balandlat", "ovozni kotar", "ovozni kucha", "громче", "volume up") -> volume(true)
                has("ovozni pasaytir", "ovozni kamaytir", "тише", "volume down") -> volume(false)
                has("qongiroq", "позвони", "call ") -> call(words)
                has("qidir", "найди", "search") -> search(words)
                word("orqaga", "назад", "back") -> acc({ it.global(AccessibilityService.GLOBAL_ACTION_BACK) }, "Orqaga")
                has("bosh ekran", "домой", "go home") || word("uyga") ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_HOME) }, "Bosh ekran")
                has("oxirgi ilova", "недавн", "recent") ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_RECENTS) }, "Oxirgi ilovalar")
                has("bildirishnoma", "уведомлен", "notification") ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }, "Bildirishnomalar")
                has("tez sozlama", "быстрые настройки", "quick settings") ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }, "Tez sozlamalar")
                has("skrinshot", "ekran rasmi", "скриншот", "screenshot") && Build.VERSION.SDK_INT >= 28 ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, "Skrinshot olindi")
                has("qulfla", "заблокируй", "lock screen") && Build.VERSION.SDK_INT >= 28 ->
                    acc({ it.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }, "Qulflandi")
                word("pastga", "вниз") || has("scroll down") -> acc({ it.scroll("down") }, "Pastga")
                word("yuqoriga", "tepaga", "вверх") || has("scroll up") -> acc({ it.scroll("up") }, "Yuqoriga")
                word("chapga", "влево") -> acc({ it.scroll("left") }, "Chapga")
                word("ongga", "вправо") -> acc({ it.scroll("right") }, "O'ngga")
                words.first() in setOf("bos", "нажми", "click") || words.last() == "bos" -> click(words)
                words.first() in setOf("yoz", "напиши", "type") -> type(words.drop(1).joinToString(" "), raw)
                else -> open(words)
            }
        } catch (e: Exception) {
            "Xato: ${e.message}"
        }
    }

    private fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Chiroq topilmadi"
        cm.setTorchMode(id, on)
        return if (on) "Chiroq yoqildi" else "Chiroq o'chirildi"
    }

    private fun volume(up: Boolean): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        return if (up) "Ovoz balandladi" else "Ovoz pasaydi"
    }

    private fun call(words: List<String>): String {
        val skip = setOf("qongiroq", "qil", "qiling", "qilgin", "позвони", "call", "ga")
        val name = words.filter { it !in skip }
            .joinToString(" ") { if (it.length > 4 && it.endsWith("ga")) it.dropLast(2) else it }
        if (name.isBlank()) return "Kimga qo'ng'iroq qilay?"

        val number = if (name.replace(" ", "").all { it.isDigit() || it == '+' }) name.replace(" ", "")
        else findContact(name) ?: return "Kontakt topilmadi: $name"

        val canCall = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        launch(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        return "Qo'ng'iroq: $name"
    }

    private fun findContact(name: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val keys = listOf(name, name.take(maxOf(3, name.length - 1)))
        for (k in keys) {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$k%"), null
            )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        }
        return null
    }

    private fun search(words: List<String>): String {
        val q = words.filter { it !in setOf("qidir", "qidiring", "найди", "search", "internetdan") }.joinToString(" ")
        if (q.isBlank()) return "Nimani qidiray?"
        try {
            launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        }
        return "Qidiryapman: $q"
    }

    private fun click(words: List<String>): String {
        val target = stripNi(words.filter { it !in setOf("bos", "нажми", "click", "tugmani", "на") }.joinToString(" "))
        if (target.isBlank()) return "Nimani bosay?"
        val s = PhoneControlService.instance ?: return "Avval Maxsus imkoniyatlarda xizmatni yoqing"
        return if (s.clickText(target)) "Bosildi: $target" else "Ekranda topilmadi: $target"
    }

    private fun type(text: String, raw: String): String {
        if (text.isBlank()) return "Nima yozay?"
        // Asl gapdan (katta-kichik harf, belgilar bilan) birinchi so'zdan keyingi qismni olamiz
        val original = raw.trim().substringAfter(' ', text)
        val s = PhoneControlService.instance ?: return "Avval Maxsus imkoniyatlarda xizmatni yoqing"
        return if (s.typeText(original)) "Yozildi" else "Avval matn maydonini tanlang"
    }

    private fun open(words: List<String>): String? {
        val openWords = setOf("och", "oching", "ochib", "ochgin", "открой", "открыть", "запусти", "open", "launch")
        if (words.none { it in openWords }) return null
        val filler = setOf("ber", "bering", "ilovasini", "ilovani", "dasturini", "ilova", "dastur", "iltimos",
            "пожалуйста", "приложение", "app", "the")
        val q = latin(stripNi(words.filter { it !in openWords && it !in filler }.joinToString(" ")))
        if (q.isBlank()) return "Qaysi ilovani ochay?"
        val app = findApp(q) ?: return "Ilova topilmadi: $q"
        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return "Ochib bo'lmadi: ${app.label}"
        launch(i)
        return "Ochilmoqda: ${app.label}"
    }

    private fun findApp(q0: String): AppEntry? {
        if (apps.isEmpty()) loadApps()
        val qs = listOfNotNull(q0, aliases[q0])
        for (q in qs) apps.firstOrNull { it.key == q }?.let { return it }
        for (q in qs) apps.firstOrNull { it.key.contains(q) || (it.key.length >= 3 && q.contains(it.key)) }?.let { return it }
        val q = qs.last()
        val best = apps.minByOrNull { lev(it.key, q) } ?: return null
        return if (lev(best.key, q) <= maxOf(2, q.length / 3)) best else null
    }
}
