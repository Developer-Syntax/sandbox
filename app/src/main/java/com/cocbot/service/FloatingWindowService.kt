package com.cocbot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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

    private fun createFloatingWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 200
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE0B0E14"))
            setPadding(12, 12, 12, 12)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = "🤖 GEMINI AI BOT"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggle = Button(this).apply {
            text = "▼"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(60, 44)
        }

        header.addView(tvTitle)
        header.addView(btnToggle)
        root.addView(header)

        val tvAiStatus = TextView(this).apply {
            text = "STATUS: IDLE"
            textSize = 10f
            setTextColor(Color.parseColor("#FFD700"))
            setPadding(0, 4, 0, 4)
        }
        root.addView(tvAiStatus)

        // Expanded Control Panel
        val btnPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 8, 0, 0)
        }

        val btnScanNow = Button(this).apply {
            text = "👁️ GEMINI VISION SCAN"
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setOnClickListener {
                val screen = ScreenCaptureService.getInstance()?.captureScreen()
                BotService.getInstance()?.triggerSingleAiAnalysisAndAttack(screen)
                Toast.makeText(this@FloatingWindowService, "🤖 Memulai analisis Gemini AI Vision...", Toast.LENGTH_SHORT).show()
            }
        }
        btnPanel.addView(btnScanNow)

        val btnStartBot = Button(this).apply {
            text = "⚔️ START AI AUTO ATTACK"
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#00C853"))
            setOnClickListener {
                BotService.getInstance()?.startBot()
                Toast.makeText(this@FloatingWindowService, "🤖 Bot Auto Attack Dimulai!", Toast.LENGTH_SHORT).show()
            }
        }
        btnPanel.addView(btnStartBot)

        val btnStopBot = Button(this).apply {
            text = "🛑 STOP AI BOT"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D50000"))
            setOnClickListener {
                BotService.getInstance()?.stopBot()
                Toast.makeText(this@FloatingWindowService, "🛑 Bot Dihentikan", Toast.LENGTH_SHORT).show()
            }
        }
        btnPanel.addView(btnStopBot)

        val btnOpenCoc = Button(this).apply {
            text = "⚔️ OPEN CLASH OF CLANS"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6A1B9A"))
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this@FloatingWindowService, "Clash of Clans tidak ditemukan!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnPanel.addView(btnOpenCoc)

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

        scope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                tvAiStatus.text = "STATUS: $state"
                tvAiStatus.setTextColor(
                    when (state) {
                        BotState.IN_BATTLE -> Color.parseColor("#00E5FF")
                        BotState.DEPLOYING_AI_ATTACK -> Color.parseColor("#00FF88")
                        BotState.ANALYZING_WITH_GEMINI -> Color.parseColor("#FFD700")
                        else -> Color.parseColor("#AAAAAA")
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wm.removeView(floatView)
        instance = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
