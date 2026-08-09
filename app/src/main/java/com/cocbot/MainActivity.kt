package com.cocbot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
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
    private lateinit var tvScoutedVal: TextView
    private lateinit var tvAttacksVal: TextView
    private lateinit var tvGoldVal: TextView
    private lateinit var tvElixirVal: TextView
    private lateinit var tvLastDecision: TextView

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

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun makeCardBg(
        fillColor: Int = Color.parseColor("#121722"),
        strokeColor: Int = Color.parseColor("#1F2A3E"),
        strokeWidthDp: Float = 1.5f,
        radiusDp: Float = 16f
    ): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke((strokeWidthDp * density).toInt(), strokeColor)
            cornerRadius = radiusDp * density
        }
    }

    private fun makeGradientBtn(
        startColor: Int,
        endColor: Int,
        radiusDp: Float = 12f
    ): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
        }
    }

    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080B10"))
        }

        // Header View
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D121D"))
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dpToPx(4f).toFloat()
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleContainer.addView(TextView(this).apply {
            text = "⚡ GEMINI AI VISION"
            textSize = 17f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
        })

        titleContainer.addView(TextView(this).apply {
            text = "Clash of Clans Auto Attack Bot v4.0"
            textSize = 10f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, dpToPx(2f), 0, 0)
        })

        header.addView(titleContainer)

        val badgeOnline = TextView(this).apply {
            text = "🟢 READY"
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
            setTypeface(null, Typeface.BOLD)
            background = makeCardBg(
                fillColor = Color.parseColor("#0D2A1F"),
                strokeColor = Color.parseColor("#00FF88"),
                strokeWidthDp = 1f,
                radiusDp = 12f
            )
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
        }
        header.addView(badgeOnline)
        root.addView(header)

        // Custom Capsule Tab Bar
        val tabBarCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = makeCardBg(
                fillColor = Color.parseColor("#101622"),
                strokeColor = Color.parseColor("#1F293D"),
                strokeWidthDp = 1f,
                radiusDp = 20f
            )
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(8f))
            }
        }

        val tabs = listOf("DASHBOARD", "STRATEGY", "SYSTEM API", "TERMINAL")
        val tabBtns = tabs.map { name ->
            Button(this).apply {
                text = name
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#8B949E"))
                background = null
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(38f), 1f)
            }
        }
        tabBtns.forEach { tabBarCard.addView(it) }
        root.addView(tabBarCard)

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

    private fun highlightTab(btns: List<Button>, activeIdx: Int) {
        btns.forEachIndexed { i, btn ->
            if (i == activeIdx) {
                btn.setTextColor(Color.parseColor("#00F0FF"))
                btn.background = makeCardBg(
                    fillColor = Color.parseColor("#1A2638"),
                    strokeColor = Color.parseColor("#00F0FF"),
                    strokeWidthDp = 1.2f,
                    radiusDp = 16f
                )
            } else {
                btn.setTextColor(Color.parseColor("#8B949E"))
                btn.background = null
            }
        }
    }

    private fun sectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(4f), dpToPx(12f), dpToPx(4f), dpToPx(6f))
        }
    }

    private fun showAiBotTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(12f))
        }

        // Live Bot Status HUD Card
        ll.addView(sectionHeader("📊 LIVE AI BOT DASHBOARD & STATS"))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#121722"),
                strokeColor = Color.parseColor("#1F2A3E"),
                strokeWidthDp = 1.5f,
                radiusDp = 18f
            )
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(12f)) }
        }

        tvAiStatus = TextView(this).apply {
            text = "STATUS: IDLE"
            textSize = 15f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
        }
        statusCard.addView(tvAiStatus)

        // 2x2 Stats Grid Card
        val statsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(12f), 0, dpToPx(8f))
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val chipScouted = makeStatChip("🔍 Scouted", "0 Base", Color.parseColor("#00F0FF"))
        tvScoutedVal = chipScouted.second
        row1.addView(chipScouted.first)

        val chipAttacks = makeStatChip("⚔️ Attacks", "0 Battle", Color.parseColor("#00FF88"))
        tvAttacksVal = chipAttacks.second
        row1.addView(chipAttacks.first)

        statsGrid.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f)
            }
        }

        val chipGold = makeStatChip("🪙 Gold Looted", "0", Color.parseColor("#FFD700"))
        tvGoldVal = chipGold.second
        row2.addView(chipGold.first)

        val chipElixir = makeStatChip("💧 Elixir Looted", "0", Color.parseColor("#E040FB"))
        tvElixirVal = chipElixir.second
        row2.addView(chipElixir.first)

        statsGrid.addView(row2)
        statusCard.addView(statsGrid)

        tvLastDecision = TextView(this).apply {
            text = "💡 Last AI Decision: Standing by for auto attack launch..."
            textSize = 10f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, dpToPx(4f), 0, 0)
        }
        statusCard.addView(tvLastDecision)

        ll.addView(statusCard)

        // Action Command Grid
        ll.addView(sectionHeader("🚀 COMMAND & CONTROLS"))

        // Large Primary Start Button
        val btnStart = Button(this).apply {
            text = "🤖 START GEMINI AI BOT"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBtn(Color.parseColor("#00E676"), Color.parseColor("#00B0FF"), 14f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48f)).apply {
                bottomMargin = dpToPx(10f)
            }
            setOnClickListener { launchAiBot() }
        }
        ll.addView(btnStart)

        val btnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dpToPx(10f))
        }

        btnRow2.addView(Button(this).apply {
            text = "⚡ FORCE DEPLOY NOW"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = makeGradientBtn(Color.parseColor("#FFC107"), Color.parseColor("#FF8F00"), 12f)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44f), 1f).apply { marginEnd = dpToPx(6f) }
            setOnClickListener {
                BotService.getInstance()?.triggerForceDeployNow()
                Toast.makeText(this@MainActivity, "⚡ Mengerahkan seluruh pasukan!", Toast.LENGTH_SHORT).show()
            }
        })

        btnRow2.addView(Button(this).apply {
            text = "👁️ GEMINI SCAN"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBtn(Color.parseColor("#00B0FF"), Color.parseColor("#00838F"), 12f)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44f), 1f)
            setOnClickListener {
                val screen = ScreenCaptureService.getInstance()?.captureScreen()
                BotService.getInstance()?.triggerSingleAiAnalysisAndAttack(screen)
                Toast.makeText(this@MainActivity, "🤖 Memulai analisis Gemini AI...", Toast.LENGTH_SHORT).show()
            }
        })
        ll.addView(btnRow2)

        val btnRow3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnRow3.addView(Button(this).apply {
            text = "⚔️ OPEN CLASH OF CLANS"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBtn(Color.parseColor("#7C4DFF"), Color.parseColor("#512DA8"), 12f)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44f), 1f).apply { marginEnd = dpToPx(6f) }
            setOnClickListener { launchClashOfClans() }
        })

        btnRow3.addView(Button(this).apply {
            text = "🛑 STOP BOT"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBtn(Color.parseColor("#FF1744"), Color.parseColor("#D50000"), 12f)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44f), 1f)
            setOnClickListener {
                BotService.getInstance()?.stopBot()
                Toast.makeText(this@MainActivity, "Bot Dihentikan", Toast.LENGTH_SHORT).show()
            }
        })
        ll.addView(btnRow3)

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun makeStatChip(title: String, initialVal: String, accentColor: Int): Pair<LinearLayout, TextView> {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#0B0F18"),
                strokeColor = Color.parseColor("#182232"),
                strokeWidthDp = 1f,
                radiusDp = 12f
            )
            setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(4f)
                marginStart = dpToPx(4f)
            }
        }

        chip.addView(TextView(this).apply {
            text = title
            textSize = 9f
            setTextColor(Color.parseColor("#8B949E"))
        })

        val tvVal = TextView(this).apply {
            text = initialVal
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dpToPx(2f), 0, 0)
        }
        chip.addView(tvVal)

        return Pair(chip, tvVal)
    }

    private fun showStrategyTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(12f))
        }

        ll.addView(sectionHeader("🎯 TARGET LOOT GOALS"))

        val lootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#121722"),
                strokeColor = Color.parseColor("#1F2A3E"),
                strokeWidthDp = 1.5f,
                radiusDp = 16f
            )
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(12f)) }
        }

        val etMinGold = inputRowStyled(lootCard, "🪙 Minimum Gold Target", BotConfig.minGoldTarget.toString())
        val etMinElixir = inputRowStyled(lootCard, "💧 Minimum Elixir Target", BotConfig.minElixirTarget.toString())
        val etMinDark = inputRowStyled(lootCard, "🧪 Minimum Dark Elixir", BotConfig.minDarkElixirTarget.toString())

        ll.addView(lootCard)

        ll.addView(sectionHeader("🤖 DYNAMIC AI STRATEGY ENGINE"))

        val stratCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#121722"),
                strokeColor = Color.parseColor("#00F0FF"),
                strokeWidthDp = 1.5f,
                radiusDp = 16f
            )
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(12f)) }
        }

        stratCard.addView(TextView(this).apply {
            text = "⚡ 100% DYNAMIC AI ARMY ANALYSIS"
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
        })

        stratCard.addView(TextView(this).apply {
            text = "Template strategi preset telah DINOAPKAN/DIHAPUS.\nGemini AI Vision secara otomatis akan menganalisis jenis pasukan, hero, dan spell yang kamu bawa di baris slot pertempuran, lalu menyusun taktik penyerangan mandiri secara real-time berdasarkan layout desa lawan."
            textSize = 10.5f
            setTextColor(Color.parseColor("#C9D1D9"))
            setPadding(0, dpToPx(6f), 0, 0)
        })

        ll.addView(stratCard)

        ll.addView(Button(this).apply {
            text = "💾 SAVE LOOT TARGET SETTINGS"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = makeGradientBtn(Color.parseColor("#00F0FF"), Color.parseColor("#00A3FF"), 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46f))
            setOnClickListener {
                BotConfig.minGoldTarget = etMinGold.text.toString().toIntOrNull() ?: 200000
                BotConfig.minElixirTarget = etMinElixir.text.toString().toIntOrNull() ?: 200000
                BotConfig.minDarkElixirTarget = etMinDark.text.toString().toIntOrNull() ?: 1000
                Toast.makeText(this@MainActivity, "Target Loot Disimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun inputRowStyled(parent: LinearLayout, label: String, defaultVal: String): EditText {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(6f), 0, dpToPx(6f))
        }

        container.addView(TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.parseColor("#C9D1D9"))
            setPadding(0, 0, 0, dpToPx(4f))
        })

        val et = EditText(this).apply {
            setText(defaultVal)
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
            background = makeCardBg(
                fillColor = Color.parseColor("#0B0F19"),
                strokeColor = Color.parseColor("#1F293D"),
                strokeWidthDp = 1f,
                radiusDp = 10f
            )
            setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
        }

        container.addView(et)
        parent.addView(container)
        return et
    }

    private fun showApiAndPermissionsTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(12f))
        }

        ll.addView(sectionHeader("🔑 GEMINI AI API CONFIGURATION"))

        val apiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#121722"),
                strokeColor = Color.parseColor("#1F2A3E"),
                strokeWidthDp = 1.5f,
                radiusDp = 16f
            )
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(12f)) }
        }

        val etApiKey = inputRowStyled(apiCard, "Gemini API Key (Leave empty to use built-in key)", BotConfig.geminiApiKey)

        apiCard.addView(Button(this).apply {
            text = "💾 SAVE API KEY"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = makeGradientBtn(Color.parseColor("#00FF88"), Color.parseColor("#00B0FF"), 10f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(42f)).apply {
                topMargin = dpToPx(8f)
            }
            setOnClickListener {
                BotConfig.geminiApiKey = etApiKey.text.toString().trim()
                Toast.makeText(this@MainActivity, "Gemini API Key Disimpan!", Toast.LENGTH_SHORT).show()
            }
        })
        ll.addView(apiCard)

        ll.addView(sectionHeader("⚙️ REQUIRED SYSTEM PERMISSIONS"))

        val permCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#121722"),
                strokeColor = Color.parseColor("#1F2A3E"),
                strokeWidthDp = 1.5f,
                radiusDp = 16f
            )
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(16f))
        }

        val overlayOk = Settings.canDrawOverlays(this)
        permCard.addView(permissionRow("Floating Mod Overlay Window", overlayOk, "Grant") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val accOk = AccessibilityBot.instance != null
        permCard.addView(permissionRow("Accessibility Auto Touch Service", accOk, "Open Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val screenCapOk = ScreenCaptureService.getInstance() != null
        permCard.addView(permissionRow("Screen Capture / MediaProjection", screenCapOk, "Grant Capture") {
            requestScreenCapturePermission()
        })

        val rootOk = RootShell.isRootAvailable
        permCard.addView(permissionRow("Superuser Root (Fallback Only)", rootOk, "Test SU") {
            RootShell.checkRootPermission()
            Toast.makeText(this@MainActivity, if (RootShell.isRootAvailable) "Root Detected" else "Mode Non-Root Aktif (Normal)", Toast.LENGTH_SHORT).show()
        })

        ll.addView(permCard)
        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun permissionRow(name: String, ok: Boolean, btnLabel: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8f), 0, dpToPx(8f))

            val tvInfo = TextView(this@MainActivity).apply {
                text = name
                textSize = 11f
                setTextColor(Color.parseColor("#E6EDF3"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tvInfo)

            val badgeStatus = TextView(this@MainActivity).apply {
                text = if (ok) "ACTIVE 🟢" else "REQUIRED 🔴"
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (ok) Color.parseColor("#00FF88") else Color.parseColor("#FF1744"))
                background = makeCardBg(
                    fillColor = if (ok) Color.parseColor("#0D2A1F") else Color.parseColor("#32131A"),
                    strokeColor = if (ok) Color.parseColor("#00FF88") else Color.parseColor("#FF1744"),
                    strokeWidthDp = 1f,
                    radiusDp = 8f
                )
                setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
            }
            addView(badgeStatus)

            if (!ok) {
                addView(Button(this@MainActivity).apply {
                    text = btnLabel
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = makeGradientBtn(Color.parseColor("#1F293D"), Color.parseColor("#2A3852"), 8f)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(90f), dpToPx(34f)).apply {
                        marginStart = dpToPx(8f)
                    }
                    setOnClickListener { onClick() }
                })
            }
        }
    }

    private fun showLogsTab() {
        tabContent.removeAllViews()
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(12f))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(8f))
        }

        topBar.addView(TextView(this).apply {
            text = "📋 REALTIME GEMINI AI TERMINAL"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        topBar.addView(Button(this).apply {
            text = "Clear"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = makeCardBg(
                fillColor = Color.parseColor("#1A2232"),
                strokeColor = Color.parseColor("#2C3A54"),
                strokeWidthDp = 1f,
                radiusDp = 8f
            )
            layoutParams = LinearLayout.LayoutParams(dpToPx(60f), dpToPx(32f))
            setOnClickListener {
                BotLogger.clear()
                if (::tvLog.isInitialized) tvLog.text = ""
            }
        })
        ll.addView(topBar)

        val termBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeCardBg(
                fillColor = Color.parseColor("#04080F"),
                strokeColor = Color.parseColor("#00F0FF33"),
                strokeWidthDp = 1.5f,
                radiusDp = 14f
            )
            setPadding(dpToPx(10f), dpToPx(10f), dpToPx(10f), dpToPx(10f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        tvLog = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
            setTypeface(Typeface.MONOSPACE)
        }

        scrollLog = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(tvLog)
        }
        termBox.addView(scrollLog)
        ll.addView(termBox)

        tabContent.addView(ll)
        updateLogText(BotLogger.logs.value)
    }

    private fun updateLogText(list: List<LogEntry>) {
        if (::tvLog.isInitialized) {
            val sb = StringBuilder()
            list.takeLast(200).forEach { entry ->
                sb.append("[${entry.timestamp}] [${entry.level}] ${entry.message}\n")
            }
            tvLog.text = sb.toString()
            if (::scrollLog.isInitialized) {
                scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
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
                updateLogText(list)
            }
        }
        lifecycleScope.launch {
            while (true) {
                val service = BotService.getInstance()
                if (service != null) {
                    val job1 = launch {
                        service.state.collectLatest { state ->
                            if (::tvAiStatus.isInitialized) {
                                tvAiStatus.text = "STATUS: $state"
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
                    }
                    val job2 = launch {
                        service.stats.collectLatest { stats ->
                            if (::tvScoutedVal.isInitialized) {
                                tvScoutedVal.text = "${stats.totalBasesScouted} Base"
                                tvAttacksVal.text = "${stats.totalAttacksExecuted} Battle"
                                tvGoldVal.text = "${stats.totalGoldLooted}"
                                tvElixirVal.text = "${stats.totalElixirLooted}"
                                tvLastDecision.text = "💡 Last AI Decision: ${stats.lastAiDecision}"
                            }
                        }
                    }
                    while (BotService.getInstance() == service) {
                        kotlinx.coroutines.delay(1000)
                    }
                    job1.cancel()
                    job2.cancel()
                } else {
                    if (::tvAiStatus.isInitialized) {
                        tvAiStatus.text = "STATUS: OFF (Bot Inactive)"
                        tvAiStatus.setTextColor(Color.parseColor("#8B949E"))
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }
}

