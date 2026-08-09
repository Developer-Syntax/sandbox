package com.cocbot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.*
import com.cocbot.BotConfig
import com.cocbot.BotLogger
import com.cocbot.state.BotState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FloatingWindowService : Service() {
    companion object {
        private var instance: FloatingWindowService? = null
        fun getInstance() = instance
        fun start(ctx: Context) = ctx.startService(Intent(ctx, FloatingWindowService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, FloatingWindowService::class.java))
    }

    private lateinit var wm: WindowManager
    private lateinit var floatView: View
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var expanded = false
    private var initX = 0; private var initY = 0
    private var initTouchX = 0f; private var initTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWindow()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun makeOverlayBg(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#F00A0E17"))
            setStroke((1.5f * density).toInt(), Color.parseColor("#00F0FF"))
            cornerRadius = 16f * density
        }
    }

    private fun makeBtnBg(startColor: Int, endColor: Int): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
        }
    }

    private fun createFloatingWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10; y = 200
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeOverlayBg()
            setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
            elevation = dpToPx(8f).toFloat()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = "⚡ GEMINI BOT"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggle = TextView(this).apply {
            text = "▼"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = makeBtnBg(Color.parseColor("#1A2638"), Color.parseColor("#25364F"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(28f), dpToPx(28f))
        }

        header.addView(tvTitle)
        header.addView(btnToggle)
        root.addView(header)

        val tvAiStatus = TextView(this).apply {
            text = "STATUS: IDLE"
            textSize = 9f
            setTextColor(Color.parseColor("#00FF88"))
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))
        }
        root.addView(tvAiStatus)

        // Expanded Control Panel
        val btnPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(6f), 0, 0)
        }

        fun addButton(label: String, startColor: Int, endColor: Int, isBlackText: Boolean = false, onClick: () -> Unit) {
            val btn = Button(this).apply {
                text = label
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isBlackText) Color.BLACK else Color.WHITE)
                background = makeBtnBg(startColor, endColor)
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(180f),
                    dpToPx(36f)
                ).apply {
                    topMargin = dpToPx(4f)
                }
                setOnClickListener { onClick() }
            }
            btnPanel.addView(btn)
        }

        addButton("👁️ GEMINI VISION SCAN", Color.parseColor("#00B0FF"), Color.parseColor("#00838F")) {
            val screen = ScreenCaptureService.getInstance()?.captureScreen()
            BotService.getInstance()?.triggerSingleAiAnalysisAndAttack(screen)
            Toast.makeText(this@FloatingWindowService, "🤖 Memulai analisis Gemini AI Vision...", Toast.LENGTH_SHORT).show()
        }

        addButton("⚡ DEPLOY ALL ARMY", Color.parseColor("#FFC107"), Color.parseColor("#FF8F00"), isBlackText = true) {
            BotService.getInstance()?.triggerForceDeployNow()
            Toast.makeText(this@FloatingWindowService, "⚡ Mengerahkan seluruh pasukan!", Toast.LENGTH_SHORT).show()
        }

        addButton("⚔️ START AI AUTO ATTACK", Color.parseColor("#00E676"), Color.parseColor("#00B0FF")) {
            BotService.getInstance()?.startBot()
            Toast.makeText(this@FloatingWindowService, "🤖 Bot Auto Attack Dimulai!", Toast.LENGTH_SHORT).show()
        }

        addButton("🛑 STOP AI BOT", Color.parseColor("#FF1744"), Color.parseColor("#D50000")) {
            BotService.getInstance()?.stopBot()
            Toast.makeText(this@FloatingWindowService, "🛑 Bot Dihentikan", Toast.LENGTH_SHORT).show()
        }

        addButton("⚔️ OPEN CLASH OF CLANS", Color.parseColor("#7C4DFF"), Color.parseColor("#512DA8")) {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Toast.makeText(this@FloatingWindowService, "Clash of Clans tidak ditemukan!", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(btnPanel)
        floatView = root

        btnToggle.setOnClickListener {
            expanded = !expanded
            btnPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            btnToggle.text = if (expanded) "▲" else "▼"
        }

        // Drag support
        root.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - initTouchX).toInt()
                    params.y = initY + (event.rawY - initTouchY).toInt()
                    wm.updateViewLayout(floatView, params)
                    true
                }
                else -> false
            }
        }

        wm.addView(floatView, params)

        // Live Log Observation for Floating Overlay
        scope.launch {
            BotLogger.logs.collectLatest { list ->
                val lastEntry = list.lastOrNull()
                if (lastEntry != null) {
                    tvAiStatus.text = "[${lastEntry.timestamp}] ${lastEntry.message.take(28)}"
                }
            }
        }

        // Live Service State Monitoring Loop
        scope.launch {
            while (isActive) {
                val service = BotService.getInstance()
                if (service != null) {
                    val job = launch {
                        service.state.collectLatest { state ->
                            tvAiStatus.setTextColor(
                                when (state) {
                                    BotState.IN_BATTLE -> Color.parseColor("#FF1744")
                                    BotState.DEPLOYING_AI_ATTACK -> Color.parseColor("#00FF88")
                                    BotState.ANALYZING_WITH_GEMINI -> Color.parseColor("#FFC107")
                                    else -> Color.parseColor("#00F0FF")
                                }
                            )
                        }
                    }
                    while (BotService.getInstance() == service && isActive) {
                        delay(1000)
                    }
                    job.cancel()
                } else {
                    delay(1000)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (::floatView.isInitialized) {
            try { wm.removeView(floatView) } catch (e: Exception) {}
        }
        instance = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}

