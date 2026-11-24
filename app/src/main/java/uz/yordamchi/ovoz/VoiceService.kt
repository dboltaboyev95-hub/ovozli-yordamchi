package uz.yordamchi.ovoz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/** Fonda doimiy tinglaydigan xizmat. */
class VoiceService : Service(), RecognitionListener {

    companion object {
        const val ACTION_STOP = "uz.yordamchi.ovoz.STOP"
        @Volatile var running = false
        var onStatus: ((String) -> Unit)? = null
    }

    private var recognizer: SpeechRecognizer? = null
    private lateinit var processor: CommandProcessor
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lang = "uz-UZ"
    private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        processor = CommandProcessor(this) { speak(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(); return START_NOT_STICKY
        }
        lang = getSharedPreferences("cfg", MODE_PRIVATE).getString("lang", "uz-UZ") ?: "uz-UZ"
        startFg()
        if (!active) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                status("Telefonda nutqni aniqlash xizmati yo'q (Google ilovasini o'rnating)")
                stopSelf(); return START_NOT_STICKY
            }
            active = true; running = true
            tts = TextToSpeech(this) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    val r = tts?.setLanguage(Locale.forLanguageTag(lang))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.getDefault()
                }
            }
            processor.loadApps()
            createRecognizer()
            listen()
            status("Tinglayapman ($lang)")
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("voice", "Ovozli yordamchi", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "voice")
            .setContentTitle("Ovozli yordamchi tinglayapti")
            .setContentText("Buyruq ayting. To'xtatish: \"to'xta\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPi)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "To'xtatish", stopPi
                ).build()
            )
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
    }

    private fun listen() {
        if (!active) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognizer?.startListening(i)
        } catch (e: Exception) {
            createRecognizer(); restart(1000)
        }
    }

    private fun restart(delay: Long = 250) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delay)
    }

    private fun isStop(s: String): Boolean {
        val t = CommandProcessor.norm(s)
        val w = t.split(" ")
        return t.contains("toxta") || "stop" in w || "стоп" in w || t.contains("хватит")
    }

    override fun onResults(results: Bundle?) {
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        var delay = 250L
        if (list.isNotEmpty()) {
            status("Eshitdim: ${list[0]}")
            if (list.any { isStop(it) }) {
                stopSelf(); return
            }
            for (s in list) {
                val msg = processor.handle(s) ?: continue
                status(msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (msg.startsWith("Soat")) delay = 2500
                break
            }
        }
        restart(delay)
    }

    override fun onError(error: Int) {
        if (!active) return
        when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                createRecognizer(); restart(600)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                status("Mikrofon ruxsati yo'q"); stopSelf()
            }
            12, 13 -> { // til qo'llab-quvvatlanmaydi / yuklanmagan
                status("$lang tili mavjud emas. Boshqa tilni tanlang yoki Google'da til paketini yuklang")
                restart(4000)
            }
            else -> restart(300)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }

    private fun status(s: String) {
        handler.post { onStatus?.invoke(s) }
    }

    override fun onDestroy() {
        active = false; running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        tts?.shutdown(); tts = null
        onStatus?.invoke("To'xtatildi")
        super.onDestroy()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
