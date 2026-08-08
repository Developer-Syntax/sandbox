package com.cocbot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cocbot.root.RootShell
import com.cocbot.service.*
import com.cocbot.state.BotState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvRootStatus: TextView
    private lateinit var tvNetStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var tabContent: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        observeBot()
        RootShell.checkRootPermission()
    }

    private fun buildUI(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(16, 20, 16, 20)
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "🎮 COC ROOT SANDBOX MOD"
                textSize = 18f
                setTextColor(Color.parseColor("#FF6B35"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v3.0 (XMOD EDITION)"
                textSize = 10f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
            })
        })

        // Tab bar
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
        }
        val tabs = listOf("SANDBOX ENGINE", "ARMY BUILDER", "ROOT LOGS", "SETTINGS")
        val tabBtns = tabs.map { name ->
            Button(this).apply {
                text = name
                textSize = 10f
                setTextColor(Color.parseColor("#888888"))
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

        tabBtns[0].setOnClickListener { showSandboxEngineTab(); highlightTab(tabBtns, 0) }
        tabBtns[1].setOnClickListener { showArmyBuilderTab(); highlightTab(tabBtns, 1) }
        tabBtns[2].setOnClickListener { showRootLogsTab(); highlightTab(tabBtns, 2) }
        tabBtns[3].setOnClickListener { showSettingsTab(); highlightTab(tabBtns, 3) }

        showSandboxEngineTab()
        highlightTab(tabBtns, 0)
        return root
    }

    private fun highlightTab(btns: List<Button>, idx: Int) {
        btns.forEachIndexed { i, btn ->
            btn.setTextColor(if (i == idx) Color.parseColor("#FF6B35") else Color.parseColor("#888888"))
        }
    }

    private fun showSandboxEngineTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // System Root Status Section
        ll.addView(sectionHeader("🔑 SUPERUSER ROOT ENGINE STATUS"))

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        tvRootStatus = TextView(this).apply {
            text = if (RootShell.isRootAvailable) "⚡ ROOT ACCESS: GRANTED (su verified)" else "❌ ROOT ACCESS: NOT GRANTED / DENIED"
            textSize = 13f
            setTextColor(if (RootShell.isRootAvailable) Color.parseColor("#00FF88") else Color.parseColor("#FF4444"))
            setTypeface(null, Typeface.BOLD)
        }
        rootCard.addView(tvRootStatus)

        tvNetStatus = TextView(this).apply {
            text = if (BotConfig.isNetworkBlocked) "🔒 NETWORK STATUS: ISOLATED (SANDBOX ACTIVE)" else "🌐 NETWORK STATUS: ONLINE (REAL GAME)"
            textSize = 12f
            setTextColor(if (BotConfig.isNetworkBlocked) Color.parseColor("#FFD700") else Color.parseColor("#AAAAAA"))
            setPadding(0, 8, 0, 8)
        }
        rootCard.addView(tvNetStatus)

        val btnCheckRoot = Button(this).apply {
            text = "⚡ TEST & GRANT ROOT PERMISSION (SU)"
            setBackgroundColor(Color.parseColor("#334466"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val ok = RootShell.checkRootPermission()
                tvRootStatus.text = if (ok) "⚡ ROOT ACCESS: GRANTED (su verified)" else "❌ ROOT ACCESS: NOT GRANTED / DENIED"
                tvRootStatus.setTextColor(if (ok) Color.parseColor("#00FF88") else Color.parseColor("#FF4444"))
                Toast.makeText(this@MainActivity, if (ok) "Root Permission OK!" else "Root Denied!", Toast.LENGTH_SHORT).show()
            }
        }
        rootCard.addView(btnCheckRoot)
        ll.addView(rootCard)

        // Sandbox Controls
        ll.addView(sectionHeader("🎮 XMOD SANDBOX ATTACK FEATURES"))

        val cbAutoBlock = CheckBox(this).apply {
            text = "🔒 Otomatis Putus Koneksi (Iptables) saat Visit / Scouting Base"
            isChecked = BotConfig.autoBlockNetworkOnVisit
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnCheckedChangeListener { _, isChecked ->
                BotConfig.autoBlockNetworkOnVisit = isChecked
            }
        }
        ll.addView(cbAutoBlock)

        val cbTraps = CheckBox(this).apply {
            text = "👁️ Buka Jebakan Tersembunyi (Reveal Traps & Teslas)"
            isChecked = BotConfig.revealTrapsAndTeslas
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnCheckedChangeListener { _, isChecked ->
                BotConfig.revealTrapsAndTeslas = isChecked
                if (isChecked) {
                    RootShell.applyTrapRevealPatch()
                }
            }
        }
        ll.addView(cbTraps)

        // Action Buttons
        val btnRowNet = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 8)
        }
        btnRowNet.addView(Button(this).apply {
            text = "🔒 CUT NETWORK (ISOLATE)"
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener {
                BotService.getInstance()?.enableNetworkIsolation()
                tvNetStatus.text = "🔒 NETWORK STATUS: ISOLATED (SANDBOX ACTIVE)"
                tvNetStatus.setTextColor(Color.parseColor("#FFD700"))
            }
        })
        btnRowNet.addView(Button(this).apply {
            text = "🔓 RESTORE NETWORK"
            setBackgroundColor(Color.parseColor("#00AA44"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                BotService.getInstance()?.restoreNetwork()
                tvNetStatus.text = "🌐 NETWORK STATUS: ONLINE (REAL GAME)"
                tvNetStatus.setTextColor(Color.parseColor("#AAAAAA"))
            }
        })
        ll.addView(btnRowNet)

        // Start Floating Window & CoC Launch
        ll.addView(sectionHeader("🚀 QUICK LAUNCH & OVERLAY MENU"))

        val launchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 12)
        }
        launchRow.addView(Button(this).apply {
            text = "⚔️ OPEN CLASH OF CLANS"
            setBackgroundColor(Color.parseColor("#4A0E4E"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { launchClashOfClans() }
        })
        launchRow.addView(Button(this).apply {
            text = "🎮 START SANDBOX MOD"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { launchSandboxPractice() }
        })
        ll.addView(launchRow)

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showArmyBuilderTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("⚔️ CONFIG SIMULATION ARMY & HERO LEVELS"))

        BotConfig.sandboxUnits.forEach { unit ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#16213e"))
                setPadding(12, 8, 12, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
            }

            val cbUnit = CheckBox(this).apply {
                isChecked = unit.enabled
                setOnCheckedChangeListener { _, isChecked -> unit.enabled = isChecked }
            }
            row.addView(cbUnit)

            val tvTitle = TextView(this).apply {
                text = "${unit.name}\n(${unit.type})"
                textSize = 11f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            row.addView(tvTitle)

            val tvLvl = TextView(this).apply {
                text = "Lvl ${unit.level}"
                textSize = 11f
                setTextColor(Color.parseColor("#FFD700"))
                setPadding(8, 0, 8, 0)
            }
            val btnMinusLvl = Button(this).apply {
                text = "-"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    if (unit.level > 1) {
                        unit.level--
                        tvLvl.text = "Lvl ${unit.level}"
                    }
                }
            }
            val btnPlusLvl = Button(this).apply {
                text = "+"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    unit.level++
                    tvLvl.text = "Lvl ${unit.level}"
                }
            }
            row.addView(btnMinusLvl)
            row.addView(tvLvl)
            row.addView(btnPlusLvl)

            val tvCnt = TextView(this).apply {
                text = "x${unit.count}"
                textSize = 11f
                setTextColor(Color.parseColor("#00FF88"))
                setPadding(8, 0, 8, 0)
            }
            val btnMinusCnt = Button(this).apply {
                text = "-"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    if (unit.count > 0) {
                        unit.count--
                        tvCnt.text = "x${unit.count}"
                    }
                }
            }
            val btnPlusCnt = Button(this).apply {
                text = "+"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(60, 60)
                setOnClickListener {
                    unit.count++
                    tvCnt.text = "x${unit.count}"
                }
            }
            row.addView(btnMinusCnt)
            row.addView(tvCnt)
            row.addView(btnPlusCnt)

            ll.addView(row)
        }

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showRootLogsTab() {
        tabContent.removeAllViews()
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "📋 ROOT TERMINAL LOGS"
                textSize = 13f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@MainActivity).apply {
                text = "Clear"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(120, 60)
                setOnClickListener { BotLogger.clear() }
            })
        })

        tvLog = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#050510"))
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

    private fun showSettingsTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("⚙️ REQUIRED PERMISSIONS"))
        val overlayOk = Settings.canDrawOverlays(this)
        ll.addView(permissionRow("Overlay (Floating Mod Window)", overlayOk, "Grant") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        val accOk = AccessibilityBot.instance != null
        ll.addView(permissionRow("Accessibility Service (Troop Deployer)", accOk, "Open Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val etTimer = inputRow(ll, "Simulation Auto-End Delay (Seconds)", BotConfig.sandboxEndBattleDelaySeconds.toString())

        ll.addView(Button(this).apply {
            text = "💾 SAVE SETTINGS"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                BotConfig.sandboxEndBattleDelaySeconds = etTimer.text.toString().toIntOrNull() ?: 30
                Toast.makeText(this@MainActivity, "Settings Saved!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(Color.parseColor("#FF6B35"))
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
                setTextColor(if (ok) Color.parseColor("#00FF88") else Color.parseColor("#FF4444"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!ok) {
                addView(Button(this@MainActivity).apply {
                    text = btnLabel
                    textSize = 10f
                    setBackgroundColor(Color.parseColor("#333355"))
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
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(et)
        parent.addView(row)
        return et
    }

    private fun launchClashOfClans() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
        if (launchIntent != null) {
            startActivity(launchIntent)
            BotLogger.system("Opening Clash of Clans...")
        } else {
            Toast.makeText(this, "Clash of Clans (com.supercell.clashofclans) not found!", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchSandboxPractice() {
        RootShell.checkRootPermission()
        BotService.start(this)
        if (Settings.canDrawOverlays(this)) {
            FloatingWindowService.start(this)
        }
        BotService.getInstance()?.startBot()
        launchClashOfClans()
        Toast.makeText(this, "🎮 Root Sandbox Mode Activated! Visit any village in Clash of Clans to practice.", Toast.LENGTH_LONG).show()
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
    }
}
