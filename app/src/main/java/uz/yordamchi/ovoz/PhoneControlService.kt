package uz.yordamchi.ovoz

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Telefonni "qo'l bilan" boshqaradigan xizmat: orqaga, bosh ekran, aylantirish, bosish, yozish. */
class PhoneControlService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PhoneControlService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun global(action: Int): Boolean = performGlobalAction(action)

    private fun gesture(path: Path, ms: Long) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms))
            .build()
        dispatchGesture(g, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 350) {
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)
    }

    fun tap(x: Float, y: Float) {
        gesture(Path().apply { moveTo(x, y) }, 60)
    }

    fun scroll(dir: String) {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        when (dir) {
            "down" -> swipe(w / 2, h * 0.75f, w / 2, h * 0.25f)
            "up" -> swipe(w / 2, h * 0.25f, w / 2, h * 0.75f)
            "left" -> swipe(w * 0.85f, h / 2, w * 0.15f, h / 2)
            "right" -> swipe(w * 0.15f, h / 2, w * 0.85f, h / 2)
        }
    }

    /** Ekrandagi matni bor elementni bosadi (masalan "Yuborish", "Sozlamalar"). */
    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findAccessibilityNodeInfosByText(text).firstOrNull() ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        tap(r.exactCenterX(), r.exactCenterY())
        return true
    }

    /** Hozir tanlangan matn maydoniga yozadi. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
