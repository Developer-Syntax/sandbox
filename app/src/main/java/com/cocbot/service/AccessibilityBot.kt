package com.cocbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccessibilityBot : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityBot"
        var instance: AccessibilityBot? = null
    }

    private val gestureMutex = Mutex()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityBot terhubung")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    suspend fun tap(x: Float, y: Float, duration: Long = 60): Boolean {
        Log.d(TAG, "[ACTION] Tap di (${x.toInt()}, ${y.toInt()})")
        return performGesture(x, y, x, y, duration)
    }

    suspend fun tap(point: PointF, duration: Long = 60): Boolean {
        return tap(point.x, point.y, duration)
    }

    suspend fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300
    ): Boolean {
        Log.d(TAG, "[ACTION] Swipe (${startX.toInt()},${startY.toInt()}) -> (${endX.toInt()},${endY.toInt()})")
        return performGesture(startX, startY, endX, endY, duration)
    }

    suspend fun deployTroops(
        count: Int,
        areaLeft: Float, areaTop: Float,
        areaRight: Float, areaBottom: Float,
        delayMs: Long = 100
    ) {
        Log.d(TAG, "[ACTION] Deploy $count troops di area ($areaLeft,$areaTop)-($areaRight,$areaBottom)")
        repeat(count) {
            val x = areaLeft + Math.random().toFloat() * (areaRight - areaLeft)
            val y = areaTop + Math.random().toFloat() * (areaBottom - areaTop)
            tap(x, y)
            delay(delayMs)
        }
    }

    private suspend fun performGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): Boolean = gestureMutex.withLock {
        suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Gesture dibatalkan oleh OS")
                    if (cont.isActive) cont.resume(false)
                }
            }, null)

            if (!dispatched) {
                Log.e(TAG, "Gesture gagal di-dispatch ke AccessibilityService")
                if (cont.isActive) cont.resume(false)
            }
        }
    }
}

