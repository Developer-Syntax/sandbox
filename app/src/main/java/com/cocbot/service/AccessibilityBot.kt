package com.cocbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccessibilityBot : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityBot"
        var instance: AccessibilityBot? = null
    }

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

    /**
     * Tap di koordinat tertentu
     */
    suspend fun tap(x: Float, y: Float, duration: Long = 50): Boolean {
        Log.d(TAG, "[ACTION] Tap di (${x.toInt()}, ${y.toInt()})")
        return performGesture(x, y, x, y, duration)
    }

    suspend fun tap(point: PointF, duration: Long = 50): Boolean {
        return tap(point.x, point.y, duration)
    }

    /**
     * Swipe dari satu titik ke titik lain
     */
    suspend fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300
    ): Boolean {
        Log.d(TAG, "[ACTION] Swipe (${startX.toInt()},${startY.toInt()}) -> (${endX.toInt()},${endY.toInt()})")
        return performGesture(startX, startY, endX, endY, duration)
    }

    /**
     * Long press
     */
    suspend fun longPress(x: Float, y: Float, duration: Long = 800): Boolean {
        Log.d(TAG, "[ACTION] Long press di (${x.toInt()}, ${y.toInt()})")
        return performGesture(x, y, x, y, duration)
    }

    /**
     * Deploy pasukan — tap acak di area tertentu
     * Untuk COC, biasanya deploy di sekitar border base musuh
     */
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

    /**
     * Deploy di 4 sisi base musuh (strategi umum farming)
     * Screen COC 720x1612, area game kira-kira tengah layar
     */
    suspend fun deployAllSides(troopsPerSide: Int = 5) {
        val margin = 80f

        // Top side
        Log.d(TAG, "[ACTION] Deploy sisi atas")
        deployTroops(troopsPerSide, 200f, margin, 520f, margin + 50f)
        delay(200)

        // Bottom side
        Log.d(TAG, "[ACTION] Deploy sisi bawah")
        deployTroops(troopsPerSide, 200f, 900f, 520f, 950f)
        delay(200)

        // Left side
        Log.d(TAG, "[ACTION] Deploy sisi kiri")
        deployTroops(troopsPerSide, margin, 300f, margin + 50f, 700f)
        delay(200)

    // Right side
        Log.d(TAG, "[ACTION] Deploy sisi kanan")
        deployTroops(troopsPerSide, 620f, 300f, 670f, 700f)
    }

    /**
     * Deploy Sandbox Army berdasarkan konfigurasi manual (unit, level, jumlah)
     */
    suspend fun deploySandboxArmy(units: List<com.cocbot.SandboxUnit>) {
        Log.d(TAG, "[SANDBOX] Memulai deploy simulasi pasukan sandbox...")
        val startSlotX = 120f
        val slotWidth = 90f
        val slotY = 650f

        units.filter { it.enabled && it.count > 0 }.forEach { unit ->
            val slotX = startSlotX + (unit.slotIndex * slotWidth)
            Log.d(TAG, "[SANDBOX] Pilih ${unit.name} (Lvl ${unit.level}, Jumlah: ${unit.count}) di slot #${unit.slotIndex}")
            
            // Tap slot pasukan/hero/spell di battle bar
            tap(slotX, slotY)
            delay(150)

            when (unit.type) {
                com.cocbot.UnitType.TROOP -> {
                    // Deploy troops tersebar di perimeter
                    deployTroops(
                        count = unit.count,
                        areaLeft = 150f,
                        areaTop = 100f,
                        areaRight = 1350f,
                        areaBottom = 520f,
                        delayMs = 80
                    )
                }
                com.cocbot.UnitType.HERO -> {
                    // Deploy hero & aktifkan ability jika perlu
                    tap(700f + (unit.slotIndex * 40f), 350f)
                    delay(300)
                    // Tap slot lagi untuk ability
                    tap(slotX, slotY)
                }
                com.cocbot.UnitType.SPELL -> {
                    // Drop spell di area tengah base
                    repeat(unit.count) { i ->
                        tap(600f + (i * 120f), 300f + (i * 80f))
                        delay(250)
                    }
                }
            }
            delay(200)
        }
    }

    private suspend fun performGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): Boolean = suspendCancellableCoroutine { cont ->
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
                Log.w(TAG, "Gesture dibatalkan")
                if (cont.isActive) cont.resume(false)
            }
        }, null)

        if (!dispatched) {
            Log.e(TAG, "Gesture gagal di-dispatch")
            if (cont.isActive) cont.resume(false)
        }
    }

    private suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
}
