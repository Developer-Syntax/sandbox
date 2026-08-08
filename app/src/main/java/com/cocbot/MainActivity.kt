package com.cocbot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cocbot.ai.GeminiAttackEngine
import com.cocbot.root.RootShell
import com.cocbot.service.*
import com.cocbot.state.BotState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvAiStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var tabContent: LinearLayout

    private val SCREEN_CAPTURE_REQUEST_CODE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        observeBot()
        RootShell.checkRootPermission()
    }

    private fun buildUI(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#090C10"))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(16, 20, 16, 20)
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "🤖 COC GEMINI AI AUTO ATTACK BOT"
                textSize = 17f
                setTextColor(Color.parseColor("#00E5FF"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v4.0 AI VISION"
                textSize = 10f
                setTextColor(Color.parseColor("#00FF88"))
                setTypeface(null, Typeface.BOLD)
            })
        })

        // Tab bar
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#21262D"))
        }
        val tabs = listOf("AI BOT", "STRATEGY", "API & PERMIT", "LOGS")
        val tabBtns = tabs.map { name ->
            Button(this).apply {
                text = name
                textSize = 10f
                setTextColor(Color.parseColor("#8B949E"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        tabBtns.forEach { tabBar.addView(it) }
        root.addView(tabBar)

        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(tabContent)

        tabBtns[0].setOnClickListener { showAiBotTab(); highlightTab(tabBtns, 0) }
        tabBtns[1].setOnClickListener { showStrategyTab(); highlightTab(tabBtns, 1) }
        tabBtns[2].setOnClickListener { showApiAndPermissionsTab(); highlightTab(tabBtns, 2) }
        tabBtns[3].setOnClickListener { showLogsTab(); highlightTab(tabBtns, 3) }

        showAiBotTab()
        highlightTab(tabBtns, 0)
        return root
    }

    private fun highlightTab(btns: List<Button>, idx: Int) {
        btns.forEachIndexed { i, btn ->
            btn.setTextColor(if (i == idx) Color.parseColor("#00E5FF") else Color.parseColor("#8B949E"))
        }
    }

    private fun showAiBotTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Status Card
        ll.addView(sectionHeader("📊 GEMINI AI BOT DASHBOARD"))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        tvAiStatus = TextView(this).apply {
            text = "STATUS: IDLE"
            textSize = 14f
            setTextColor(Color.parseColor("#00E5FF"))
            setTypeface(null, Typeface.BOLD)
        }
        statusCard.addView(tvAiStatus)

        tvStats = TextView(this).apply {
            text = "Bases Scouted: 0 | Attacks: 0\nLast AI Decision: Idle"
            textSize = 12f
            setTextColor(Color.parseColor("#C9D1D9"))
            setPadding(0, 8, 0, 8)
        }
        statusCard.addView(tvStats)

        ll.addView(statusCard)

        // Quick Controls
        ll.addView(sectionHeader("🚀 CONTROLS & QUICK LAUNCH"))

        val btnRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        btnRow1.addView(Button(this).apply {
            text = "🤖 START GEMINI AI BOT"
            setBackgroundColor(Color.parseColor("#00C853"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { launchAiBot() }
        })
        btnRow1.addView(Button(this).apply {
            text = "🛑 STOP BOT"
            setBackgroundColor(Color.parseColor("#D50000"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                BotService.getInstance()?.stopBot()
                Toast.makeText(this@MainActivity, "Bot Dihentikan", Toast.LENGTH_SHORT).show()
            }
        })
        ll.addView(btnRow1)

        val btnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 12)
        }
        btnRow2.addView(Button(this).apply {
            text = "👁️ TEST GEMINI VISION SCAN"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener {
                val screen = ScreenCaptureService.getInstance()?.captureScreen()
                BotService.getInstance()?.triggerSingleAiAnalysisAndAttack(screen)
                Toast.makeText(this@MainActivity, "🤖 Memulai analisis Gemini Vision...", Toast.LENGTH_SHORT).show()
            }
        })
        btnRow2.addView(Button(this).apply {
            text = "⚔️ OPEN CLASH OF CLANS"
            setBackgroundColor(Color.parseColor("#6A1B9A"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { launchClashOfClans() }
        })
        ll.addView(btnRow2)

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showStrategyTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("🎯 AI STRATEGY PRESETS & TARGET LOOT"))

        val etMinGold = inputRow(ll, "Minimum Gold Target", BotConfig.minGoldTarget.toString())
        val etMinElixir = inputRow(ll, "Minimum Elixir Target", BotConfig.minElixirTarget.toString())
        val etMinDark = inputRow(ll, "Minimum Dark Elixir Target", BotConfig.minDarkElixirTarget.toString())

        ll.addView(sectionHeader("⚔️ SELECT ATTACK STRATEGY"))

        val strategies = listOf(
            "Spam Electro Dragon + Balloons",
            "BARCH Dead Base Farmer",
            "Gowipe Ground Smash",
            "Custom Gemini AI Decided"
        )

        val spinnerStrategy = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, strategies)
            setSelection(strategies.indexOf(BotConfig.aiStrategyPreset).coerceAtLeast(0))
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(12, 12, 12, 12)
        }
        ll.addView(spinnerStrategy)

        ll.addView(Button(this).apply {
            text = "💾 SAVE STRATEGY SETTINGS"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                BotConfig.minGoldTarget = etMinGold.text.toString().toIntOrNull() ?: 200000
                BotConfig.minElixirTarget = etMinElixir.text.toString().toIntOrNull() ?: 200000
                BotConfig.minDarkElixirTarget = etMinDark.text.toString().toIntOrNull() ?: 1000
                BotConfig.aiStrategyPreset = spinnerStrategy.selectedItem.toString()
                Toast.makeText(this@MainActivity, "Konfigurasi Strategi Disimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showApiAndPermissionsTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("🔑 GEMINI API KEY CONFIGURATION"))

        val etApiKey = inputRow(ll, "Gemini API Key", BotConfig.geminiApiKey)
        
        ll.addView(Button(this).apply {
            text = "💾 SAVE API KEY"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                BotConfig.geminiApiKey = etApiKey.text.toString().trim()
                Toast.makeText(this@MainActivity, "Gemini API Key Disimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        ll.addView(sectionHeader("⚙️ REQUIRED SYSTEM PERMISSIONS"))
        
        val overlayOk = Settings.canDrawOverlays(this)
        ll.addView(permissionRow("Floating Mod Overlay Window", overlayOk, "Grant") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val accOk = AccessibilityBot.instance != null
        ll.addView(permissionRow("Accessibility Auto Touch Service (UTAMA - NON-ROOT)", accOk, "Open Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val screenCapOk = ScreenCaptureService.getInstance() != null
        ll.addView(permissionRow("Screen Capture / MediaProjection (NON-ROOT)", screenCapOk, "Grant Capture") {
            requestScreenCapturePermission()
        })

        val rootOk = RootShell.isRootAvailable
        ll.addView(permissionRow("Superuser Root (OPSIONAL / FALLBACK SAJA)", rootOk, "Test SU") {
            RootShell.checkRootPermission()
            Toast.makeText(this@MainActivity, if (RootShell.isRootAvailable) "Root detected" else "Mode Non-Root Aktif (Normal)", Toast.LENGTH_SHORT).show()
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showLogsTab() {
        tabContent.removeAllViews()
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "📋 REALTIME GEMINI AI TERMINAL LOGS"
                textSize = 13f
                setTextColor(Color.parseColor("#00E5FF"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@MainActivity).apply {
                text = "Clear"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#21262D"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(120, 60)
                setOnClickListener { BotLogger.clear() }
            })
        })

        tvLog = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
            setBackgroundColor(Color.parseColor("#040D14"))
            setPadding(12, 12, 12, 12)
            setTypeface(Typeface.MONOSPACE)
        }

        scrollLog = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            addView(tvLog)
        }
        ll.addView(scrollLog)

        tabContent.addView(ll)
    }

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
    }

    private fun permissionRow(name: String, ok: Boolean, btnLabel: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
            addView(TextView(this@MainActivity).apply {
                text = "${if (ok) "✅" else "❌"} $name"
                textSize = 11f
                setTextColor(if (ok) Color.parseColor("#00FF88") else Color.parseColor("#FF5555"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!ok) {
                addView(Button(this@MainActivity).apply {
                    text = btnLabel
                    textSize = 10f
                    setBackgroundColor(Color.parseColor("#21262D"))
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(180, 70)
                    setOnClickListener { onClick() }
                })
            }
        }
    }

    private fun inputRow(parent: LinearLayout, label: String, defaultVal: String): EditText {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val et = EditText(this).apply {
            setText(defaultVal)
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(et)
        parent.addView(row)
        return et
    }

    private fun requestScreenCapturePermission() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startService(intent)
            Toast.makeText(this, "Screen Capture Permitted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchClashOfClans() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
        if (launchIntent != null) {
            startActivity(launchIntent)
            BotLogger.system("Opening Clash of Clans...")
        } else {
            Toast.makeText(this, "Clash of Clans (com.supercell.clashofclans) tidak ditemukan!", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchAiBot() {
        BotService.start(this)
        if (Settings.canDrawOverlays(this)) {
            FloatingWindowService.start(this)
        }
        BotService.getInstance()?.startBot()
        launchClashOfClans()
        Toast.makeText(this, "🤖 Gemini AI Auto Attack Bot Aktif!", Toast.LENGTH_LONG).show()
    }

    private fun observeBot() {
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { list ->
                if (::tvLog.isInitialized) {
                    val sb = StringBuilder()
                    list.takeLast(100).forEach { entry ->
                        sb.append("[${entry.timestamp}] [${entry.level}] ${entry.message}\n")
                    }
                    tvLog.text = sb.toString()
                    if (::scrollLog.isInitialized) {
                        scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                if (::tvAiStatus.isInitialized) {
                    tvAiStatus.text = "STATUS: $state"
                }
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.stats?.collectLatest { stats ->
                if (::tvStats.isInitialized) {
                    tvStats.text = "Scouted: ${stats.totalBasesScouted} | Attacks: ${stats.totalAttacksExecuted}\nGold Looted: ${stats.totalGoldLooted} | Elixir Looted: ${stats.totalElixirLooted}\nLast Decision: ${stats.lastAiDecision}"
                }
            }
        }
    }
}
