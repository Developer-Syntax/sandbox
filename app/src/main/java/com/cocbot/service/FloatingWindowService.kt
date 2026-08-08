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
            setBackgroundColor(Color.parseColor("#CC1a1a2e"))
            setPadding(8, 8, 8, 8)
        }

        // Header - drag handle + expand button
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val tvTitle = TextView(this).apply {
            text = "⚔️ COC Bot"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggle = Button(this).apply {
            text = "▼"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334466"))
            layoutParams = LinearLayout.LayoutParams(60, 40)
        }

        header.addView(tvTitle)
        header.addView(btnToggle)
        root.addView(header)

        // Status
        val tvStatus = TextView(this).apply {
            text = "Status: IDLE"
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
        }
        root.addView(tvStatus)

        // Stats
        val tvStats = TextView(this).apply {
            text = "🏆0 💛0 💜0"
            textSize = 10f
            setTextColor(Color.WHITE)
        }
        root.addView(tvStats)

        // Buttons panel (collapsed by default)
        val btnPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val btnRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val btnStart = Button(this).apply {
            text = "▶"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00AA44"))
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f).apply { marginEnd = 4 }
        }
        val btnPause = Button(this).apply {
            text = "⏸"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF8800"))
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f).apply { marginEnd = 4 }
        }
        val btnStop = Button(this).apply {
            text = "⏹"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f)
        }

        btnRow1.addView(btnStart); btnRow1.addView(btnPause); btnRow1.addView(btnStop)
        btnPanel.addView(btnRow1)

        // Loot info
        val tvLoot = TextView(this).apply {
            text = "🎯 -"
            textSize = 9f
            setTextColor(Color.parseColor("#FFD700"))
        }
        btnPanel.addView(tvLoot)

        root.addView(btnPanel)
        floatView = root

        // Toggle expand
        btnToggle.setOnClickListener {
            expanded = !expanded
            btnPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            btnToggle.text = if (expanded) "▲" else "▼"
        }

        // Button listeners
        btnStart.setOnClickListener { BotService.getInstance()?.startBot() }
        btnStop.setOnClickListener { BotService.getInstance()?.stopBot() }
        btnPause.setOnClickListener {
            val bot = BotService.getInstance() ?: return@setOnClickListener
            if (bot.state.value == BotState.PAUSED) { bot.resumeBot(); btnPause.text = "⏸" }
            else { bot.pauseBot(); btnPause.text = "▶" }
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

        // Observe state
        scope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                tvStatus.text = "Status: $state"
            }
        }
        scope.launch {
            BotService.getInstance()?.session?.collectLatest { sess ->
                tvStats.text = "🏆${sess.totalMatches} 💛${"%,d".format(sess.totalGold/1000)}K 💜${"%,d".format(sess.totalElixir/1000)}K"
            }
        }
        scope.launch {
            BotService.getInstance()?.currentLoot?.collectLatest { ld ->
                tvLoot.text = "🎯 G:${"%,d".format(ld.gold)} E:${"%,d".format(ld.elixir)}"
            }
        }
    }

    override fun onDestroy() { scope.cancel(); wm.removeView(floatView); instance = null; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
