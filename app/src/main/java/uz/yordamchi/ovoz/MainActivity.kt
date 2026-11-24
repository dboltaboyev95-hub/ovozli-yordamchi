package uz.yordamchi.ovoz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var accBtn: Button
    private val handler = Handler(Looper.getMainLooper())

    private val help = """
        BUYRUQLAR (o'zbekcha / ruscha / inglizcha):

        • "Telegramni och", "YouTube och", "открой камеру"
        • "Orqaga", "Bosh ekran", "Oxirgi ilovalar"
        • "Pastga", "Yuqoriga", "Chapga", "O'ngga" (aylantirish)
        • "Yuborish bos", "bos Sozlamalar" (ekrandagi tugma)
        • "Yoz salom qalaysan" (tanlangan maydonga yozadi)
        • "Onamga qo'ng'iroq qil", "позвони Ахмаду"
        • "Ob-havo qidir"
        • "Chiroqni yoq" / "Chiroqni o'chir"
        • "Ovozni balandlat" / "Ovozni pasaytir"
        • "Bildirishnomalar", "Tez sozlamalar"
        • "Skrinshot", "Qulfla", "Soat necha"
        • "To'xta" — tinglashni o'chiradi

        Eslatma: tilni o'zgartirsangiz, tinglashni qayta ishga tushiring.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = 40
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p * 2, p, p)
        }
        fun tv(t: String, size: Float = 16f) = TextView(this).apply {
            text = t; textSize = size; setPadding(0, 16, 0, 16)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        root.addView(tv("🎙 Ovozli Yordamchi", 26f))
        status = tv("Holat: to'xtatilgan")
        root.addView(status)

        root.addView(btn("1. Ruxsatlarni berish") { askPerms() })
        accBtn = btn("2. Maxsus imkoniyatni yoqish") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(accBtn)

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val langs = listOf("O'zbekcha" to "uz-UZ", "Русский" to "ru-RU", "English" to "en-US")
        val current = prefs.getString("lang", "uz-UZ")
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        langs.forEachIndexed { idx, (name, _) ->
            rg.addView(RadioButton(this).apply { text = name; id = 100 + idx })
        }
        rg.check(100 + langs.indexOfFirst { it.second == current }.coerceAtLeast(0))
        rg.setOnCheckedChangeListener { _, id -> prefs.edit().putString("lang", langs[id - 100].second).apply() }
        root.addView(tv("Til:"))
        root.addView(rg)

        toggle = btn("3. ▶ Tinglashni boshlash") { toggleService() }
        root.addView(toggle)
        root.addView(tv(help, 15f))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        VoiceService.onStatus = { status.text = "Holat: $it"; refresh() }
        refresh()
    }

    override fun onPause() {
        VoiceService.onStatus = null
        super.onPause()
    }

    private fun refresh() {
        accBtn.text = if (PhoneControlService.instance != null) "2. ✅ Maxsus imkoniyat yoqilgan"
        else "2. Maxsus imkoniyatni yoqish"
        toggle.text = if (VoiceService.running) "3. ⏹ Tinglashni to'xtatish" else "3. ▶ Tinglashni boshlash"
    }

    private fun askPerms() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun toggleService() {
        if (VoiceService.running) {
            stopService(Intent(this, VoiceService::class.java))
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                status.text = "Holat: avval mikrofonga ruxsat bering"
                askPerms(); return
            }
            startForegroundService(Intent(this, VoiceService::class.java))
        }
        handler.postDelayed({ refresh() }, 500)
    }
}
