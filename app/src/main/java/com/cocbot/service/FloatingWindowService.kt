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
import com.cocbot.root.RootShell
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
            setBackgroundColor(Color.parseColor("#EE101020"))
            setPadding(12, 12, 12, 12)
        }

        // Header - title & toggle button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = "🎮 XMOD SANDBOX"
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggle = Button(this).apply {
            text = "▼"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334466"))
            layoutParams = LinearLayout.LayoutParams(60, 44)
        }

        header.addView(tvTitle)
        header.addView(btnToggle)
        root.addView(header)

        val tvRootState = TextView(this).apply {
            text = "ROOT: ${if (RootShell.isRootAvailable) "⚡ GRANTED (SU)" else "❌ NO ROOT"}"
            textSize = 10f
            setTextColor(if (RootShell.isRootAvailable) Color.parseColor("#00FF88") else Color.parseColor("#FF4444"))
            setPadding(0, 4, 0, 4)
        }
        root.addView(tvRootState)

        val tvNetState = TextView(this).apply {
            text = "NET: ${if (BotConfig.isNetworkBlocked) "🔒 ISOLATED (SANDBOX)" else "🌐 ONLINE"}"
            textSize = 10f
            setTextColor(if (BotConfig.isNetworkBlocked) Color.parseColor("#FFD700") else Color.parseColor("#888888"))
        }
        root.addView(tvNetState)

        // Expanded Control Panel
        val btnPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 8, 0, 0)
        }

        val btnBlockNet = Button(this).apply {
            text = "🔒 CUT NETWORK (ISOLATE)"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            setOnClickListener {
                BotService.getInstance()?.enableNetworkIsolation()
                tvNetState.text = "NET: 🔒 ISOLATED (SANDBOX)"
                tvNetState.setTextColor(Color.parseColor("#FFD700"))
            }
        }
        btnPanel.addView(btnBlockNet)

        val btnRestoreNet = Button(this).apply {
            text = "🔓 RESTORE NETWORK"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00AA44"))
            setOnClickListener {
                BotService.getInstance()?.restoreNetwork()
                tvNetState.text = "NET: 🌐 ONLINE"
                tvNetState.setTextColor(Color.parseColor("#888888"))
            }
        }
        btnPanel.addView(btnRestoreNet)

        val btnReveal = Button(this).apply {
            text = "👁️ REVEAL TRAPS & TESLAS"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#553399"))
            setOnClickListener {
                RootShell.applyTrapRevealPatch()
                Toast.makeText(this@FloatingWindowService, "Trap & Tesla Patch Executed!", Toast.LENGTH_SHORT).show()
            }
        }
        btnPanel.addView(btnReveal)

        val btnDeploy = Button(this).apply {
            text = "⚔️ DEPLOY SANDBOX ARMY"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setOnClickListener {
                BotService.getInstance()?.triggerSandboxDeployment()
            }
        }
        btnPanel.addView(btnDeploy)

        val btnOpenCoc = Button(this).apply {
            text = "⚔️ LAUNCH CLASH OF CLANS"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A0E4E"))
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    BotLogger.system("Opening Clash of Clans...")
                } else {
                    Toast.makeText(this@FloatingWindowService, "Clash of Clans not found!", Toast.LENGTH_SHORT).show()
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
                tvNetState.text = "STATUS: $state | NET: ${if (BotConfig.isNetworkBlocked) "🔒 ISOLATED" else "🌐 ONLINE"}"
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
