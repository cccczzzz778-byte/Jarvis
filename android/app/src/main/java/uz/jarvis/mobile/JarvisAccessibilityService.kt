package uz.jarvis.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var current: JarvisAccessibilityService? = null

        fun available(): Boolean = current != null
        fun home(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun back(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun recents(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
        fun notifications(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun quickSettings(): Boolean = current?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true

        fun swipeUp(): Boolean = current?.swipe(0.5f, 0.78f, 0.5f, 0.25f) == true
        fun swipeDown(): Boolean = current?.swipe(0.5f, 0.25f, 0.5f, 0.78f) == true
        fun swipeLeft(): Boolean = current?.swipe(0.82f, 0.5f, 0.18f, 0.5f) == true
        fun swipeRight(): Boolean = current?.swipe(0.18f, 0.5f, 0.82f, 0.5f) == true
        fun tapCenter(): Boolean = current?.tap(0.5f, 0.5f) == true
        fun clickText(text: String): Boolean = current?.clickByText(text) == true
        fun typeText(text: String): Boolean = current?.setTextIntoFocusedField(text) == true
        fun visibleText(): String = current?.collectVisibleText().orEmpty()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels * x1, dm.heightPixels * y1)
            lineTo(dm.widthPixels * x2, dm.heightPixels * y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 420))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun tap(x: Float, y: Float): Boolean {
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(dm.widthPixels * x, dm.heightPixels * y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickByText(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(value)
        for (node in nodes) {
            var candidate: AccessibilityNodeInfo? = node
            repeat(5) {
                if (candidate?.isClickable == true && candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                candidate = candidate?.parent
            }
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                val dm = resources.displayMetrics
                val px = rect.centerX().toFloat() / dm.widthPixels
                val py = rect.centerY().toFloat() / dm.heightPixels
                if (tap(px, py)) return true
            }
        }
        return false
    }

    private fun setTextIntoFocusedField(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(root)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val parts = linkedSetOf<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || parts.size >= 40) return
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return parts.joinToString(". ").take(1800)
    }
}
