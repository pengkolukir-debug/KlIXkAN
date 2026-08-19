package com.cowalskiiw2026.autoklix.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Layanan Aksesibilitas: satu-satunya komponen yang benar-benar
 * mensimulasikan sentuhan (tap / long-press / swipe) & mengisi teks
 * ke aplikasi lain, memakai dispatchGesture (API resmi Android).
 */
class AutoKlixAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* tidak dipakai */ }
    override fun onInterrupt() { /* tidak dipakai */ }

    fun performTap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60L)
        dispatch(stroke, callback)
    }

    fun performLongPress(x: Float, y: Float, durationMs: Long, callback: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(200L))
        dispatch(stroke, callback)
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, callback: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
        dispatch(stroke, callback)
    }

    /**
     * Bot autofill 2 huruf acak: tap dulu ke titik (agar field fokus), lalu
     * isi teks langsung ke kolom yang sedang fokus lewat ACTION_SET_TEXT.
     * Pencarian node fokus memakai findFocus(FOCUS_INPUT) — API resmi yang
     * meminta LANGSUNG ke sistem node input mana yang sedang fokus saat ini
     * (jauh lebih andal lintas-aplikasi dibanding menelusuri manual pohon
     * rootInActiveWindow, yang sering gagal karena flag isFocused tidak
     * selalu terset benar oleh aplikasi pihak ketiga).
     * Jika ACTION_SET_TEXT gagal, dicoba lagi lewat clipboard+paste sebagai cadangan.
     */
    fun performRandomTextInput(x: Float, y: Float, text: String, callback: (Boolean) -> Unit) {
        performTap(x, y) { tapped ->
            if (!tapped) {
                callback(false)
                return@performTap
            }
            mainHandler.postDelayed({
                val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: findFocusedEditableNode(rootInActiveWindow)
                if (node == null) {
                    callback(false)
                    return@postDelayed
                }
                val setTextOk = setTextDirectly(node, text)
                if (setTextOk) {
                    callback(true)
                } else {
                    val pasted = pasteTextIntoNode(node, text)
                    callback(pasted)
                }
            }, 350L)
        }
    }

    private fun setTextDirectly(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            false
        }
    }

    private fun pasteTextIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("autoklix", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            false
        }
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isFocused && root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, callback: (Boolean) -> Unit) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback(false)
            }
        }, mainHandler)
        if (!ok) callback(false)
    }

    companion object {
        @Volatile var instance: AutoKlixAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
