package com.remote.gaming.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

class AccessibilityInputService : AccessibilityService() {

    companion object {
        var instance: AccessibilityInputService? = null
            private set
    }

    private val ongoingStrokes = ConcurrentHashMap<Int, Path>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for input dispatch
    }

    override fun onInterrupt() {
        instance = null
    }

    /**
     * Injects a Tap gesture at exact Android screen coordinates (x, y)
     */
    fun injectTap(x: Float, y: Float, durationMs: Long = 20) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Injects a Swipe/Drag gesture between two screen coordinates
     */
    fun injectSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 100
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches multi-touch stroke descriptions for dual analog and action buttons
     */
    fun injectMultiTouch(strokes: List<GestureDescription.StrokeDescription>) {
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        dispatchGesture(builder.build(), null, null)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}