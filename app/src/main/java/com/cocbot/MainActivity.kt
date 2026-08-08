package com.cocbot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cocbot.service.*
import com.cocbot.state.BotState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLoot: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnPause: Button
    private lateinit var tabContent: LinearLayout

    // Untuk pilih gambar dari galeri
    private var currentTemplateTarget: String = ""
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            saveTemplateFromUri(uri, currentTemplateTarget)
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            ScreenCaptureService.start(this, result.resultCode, data)
            android.os.Handler(mainLooper).postDelayed({
                BotService.getInstance()?.startBot()
                if (Settings.canDrawOverlays(this)) FloatingWindowService.start(this)
            }, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        observeBot()
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
                text = "⚔️ COC Bot"
                textSize = 20f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v2.0"
                textSize = 11f
                setTextColor(Color.parseColor("#888888"))
            })
        })

        // Tab bar
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
        }
        val tabs = listOf("START", "SETTINGS", "ARMY", "SANDBOX", "KALIBRASI")
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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(tabContent)

        tabBtns[0].setOnClickListener { showStartTab(); highlightTab(tabBtns, 0) }
        tabBtns[1].setOnClickListener { showSettingsTab(); highlightTab(tabBtns, 1) }
        tabBtns[2].setOnClickListener { showArmyTab(); highlightTab(tabBtns, 2) }
        tabBtns[3].setOnClickListener { showSandboxTab(); highlightTab(tabBtns, 3) }
        tabBtns[4].setOnClickListener { showCalibrationTab(); highlightTab(tabBtns, 4) }

        showStartTab()
        highlightTab(tabBtns, 0)
        return root
    }

    private fun highlightTab(btns: List<Button>, idx: Int) {
        btns.forEachIndexed { i, btn ->
            btn.setTextColor(if (i == idx) Color.parseColor("#FF6B35") else Color.parseColor("#888888"))
        }
    }

    private fun showStartTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("REQUIRED PERMISSIONS"))
        val overlayOk = Settings.canDrawOverlays(this)
        ll.addView(permissionRow("Overlay (Floating Window)", overlayOk, "Grant") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        val accOk = AccessibilityBot.instance != null
        ll.addView(permissionRow("Accessibility Service", accOk, "Buka Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        tvStatus = TextView(this).apply {
            text = "Status: IDLE"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF88"))
            setPadding(0, 16, 0, 4)
        }
        ll.addView(tvStatus)

        tvStats = TextView(this).apply {
            text = "🏆 Match: 0  💛 Gold: 0  💜 Elixir: 0"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 8, 12, 8)
        }
        ll.addView(tvStats)

        tvLoot = TextView(this).apply {
            text = "🎯 Scan: -"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 4, 12, 8)
        }
        ll.addView(tvLoot)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 8)
        }
        btnRow.addView(Button(this).apply {
            text = "▶ START"
            setBackgroundColor(Color.parseColor("#00AA44"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { startBot() }
        })
        btnPause = Button(this).apply {
            text = "⏸ PAUSE"
            setBackgroundColor(Color.parseColor("#FF8800"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { togglePause() }
        }
        btnRow.addView(btnPause)
        btnRow.addView(Button(this).apply {
            text = "⏹ STOP"
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { stopBot() }
        })
        ll.addView(btnRow)

        // Launch Clash of Clans & Quick Sandbox Buttons
        val launchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 12)
        }
        launchRow.addView(Button(this).apply {
            text = "⚔️ BUKA CLASH OF CLANS"
            setBackgroundColor(Color.parseColor("#4A0E4E"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { launchClashOfClans() }
        })
        launchRow.addView(Button(this).apply {
            text = "🎮 SANDBOX PRACTICE"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { launchSandboxPractice() }
        })
        ll.addView(launchRow)

        ll.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "📋 LOG"
                textSize = 12f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@MainActivity).apply {
                text = "Clear"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.parseColor("#AAAAAA"))
                layoutParams = LinearLayout.LayoutParams(120, 60)
                setOnClickListener { BotLogger.clear() }
            })
        })

        tvLog = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
            setBackgroundColor(Color.parseColor("#0d0d1a"))
            setPadding(12, 12, 12, 12)
            typeface = Typeface.MONOSPACE
        }
        scrollLog = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0d0d1a"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600)
            addView(tvLog)
        }
        ll.addView(scrollLog)
        sv.addView(ll)
        tabContent.addView(sv)
        observeBot()
    }

    private fun showSettingsTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("1. LOOTING TARGET"))
        val etGold = inputRow(ll, "Min Gold", BotConfig.minGoldTarget.toString())
        val etElixir = inputRow(ll, "Min Elixir", BotConfig.minElixirTarget.toString())
        val etDark = inputRow(ll, "Min Dark Elixir", BotConfig.minDarkElixirTarget.toString())

        val cbAny = CheckBox(this).apply {
            text = "Gold OR Elixir (salah satu cukup)"
            isChecked = BotConfig.useAnyResource
            setTextColor(Color.WHITE)
        }
        ll.addView(cbAny)

        ll.addView(sectionHeader("2. ATTACK STRATEGY"))
        val strategies = listOf("All Sides (4 sisi)", "Top Bottom", "Left Right", "🎮 Sandbox Attack Mode")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, strategies).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setBackgroundColor(Color.parseColor("#16213e"))
            setSelection(when(BotConfig.attackStrategy) {
                AttackStrategy.TOP_BOTTOM -> 1
                AttackStrategy.LEFT_RIGHT -> 2
                AttackStrategy.SANDBOX_ATTACK -> 3
                else -> 0
            })
        }
        ll.addView(spinner)

        val etSandboxDelay = inputRow(ll, "Sandbox Auto-End Delay (Detik)", BotConfig.sandboxEndBattleDelaySeconds.toString())

        ll.addView(sectionHeader("3. WALL UPGRADE"))
        val cbWall = CheckBox(this).apply {
            text = "Auto Upgrade Wall"
            isChecked = BotConfig.autoWallUpgrade
            setTextColor(Color.WHITE)
        }
        ll.addView(cbWall)

        ll.addView(sectionHeader("4. AUTO END BATTLE"))
        val cbEnd = CheckBox(this).apply {
            text = "Auto End If Loot Empty"
            isChecked = BotConfig.autoEndBattle
            setTextColor(Color.WHITE)
        }
        ll.addView(cbEnd)

        ll.addView(sectionHeader("5. MISCELLANEOUS"))
        val cbCollect = CheckBox(this).apply {
            text = "Auto Claim Collectors"
            isChecked = BotConfig.autoCollect
            setTextColor(Color.WHITE)
        }
        ll.addView(cbCollect)

        val etDelay = inputRow(ll, "Max Random Delay (detik)", BotConfig.maxRandomDelay.toString())

        ll.addView(Button(this).apply {
            text = "💾 SIMPAN SETTINGS"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                BotConfig.minGoldTarget = etGold.text.toString().toLongOrNull() ?: 300_000L
                BotConfig.minElixirTarget = etElixir.text.toString().toLongOrNull() ?: 300_000L
                BotConfig.minDarkElixirTarget = etDark.text.toString().toLongOrNull() ?: 0L
                BotConfig.useAnyResource = cbAny.isChecked
                BotConfig.autoWallUpgrade = cbWall.isChecked
                BotConfig.autoEndBattle = cbEnd.isChecked
                BotConfig.autoCollect = cbCollect.isChecked
                BotConfig.attackStrategy = when (spinner.selectedItemPosition) {
                    1 -> AttackStrategy.TOP_BOTTOM
                    2 -> AttackStrategy.LEFT_RIGHT
                    3 -> AttackStrategy.SANDBOX_ATTACK
                    else -> AttackStrategy.ALL_SIDES
                }
                BotConfig.sandboxEndBattleDelaySeconds = etSandboxDelay.text.toString().toIntOrNull() ?: 25
                BotConfig.maxRandomDelay = etDelay.text.toString().toIntOrNull() ?: 5
                BotConfig.delayMaxMs = (BotConfig.maxRandomDelay * 1000).toLong()
                BotLogger.system("Settings disimpan")
                Toast.makeText(this@MainActivity, "Settings tersimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showArmyTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(TextView(this).apply {
            text = "ARMY SETUP"
            textSize = 14f
            setTextColor(Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })
        ll.addView(TextView(this).apply {
            text = "Tap slot to pick from gallery"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 16)
        })

        // TROOPS
        ll.addView(sectionHeader("⚔️ TROOPS"))
        ll.addView(armySlotRow(listOf("Troop 1", "Troop 2", "Troop 3", "Troop 4")))

        // HEROES
        ll.addView(sectionHeader("👑 HEROES (Maks: 4)"))
        ll.addView(armySlotRow(listOf("Hero 1", "Hero 2", "Hero 3", "Hero 4")))
        ll.addView(armySlotRow(listOf("Hero 5", "Hero 6", "", "")))

        // SPELLS
        ll.addView(sectionHeader("🧪 SPELLS"))
        ll.addView(armySlotRow(listOf("Spell 1", "Spell 2", "", "")))

        // SIEGE
        ll.addView(sectionHeader("🎯 SIEGE MACHINE"))
        ll.addView(armySlotRow(listOf("Siege", "", "", "")))

        // Troops per sisi
        ll.addView(sectionHeader("Deploy Settings"))
        ll.addView(TextView(this).apply {
            text = "Troops per sisi:"
            textSize = 13f
            setTextColor(Color.WHITE)
        })

        val troopsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val tvTroops = TextView(this).apply {
            text = BotConfig.troopsPerSide.toString()
            textSize = 22f
            setTextColor(Color.parseColor("#FFD700"))
            setPadding(24, 0, 24, 0)
        }
        troopsRow.addView(Button(this).apply {
            text = "-"
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(90, 90)
            setOnClickListener {
                if (BotConfig.troopsPerSide > 1) { BotConfig.troopsPerSide--; tvTroops.text = BotConfig.troopsPerSide.toString() }
            }
        })
        troopsRow.addView(tvTroops)
        troopsRow.addView(Button(this).apply {
            text = "+"
            setBackgroundColor(Color.parseColor("#00AA44"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(90, 90)
            setOnClickListener { BotConfig.troopsPerSide++; tvTroops.text = BotConfig.troopsPerSide.toString() }
        })
        ll.addView(troopsRow)

        ll.addView(TextView(this).apply {
            text = "Info: Bot akan deploy ${BotConfig.troopsPerSide} troops di tiap sisi (atas, bawah, kiri, kanan)"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showSandboxTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(TextView(this).apply {
            text = "🎮 SANDBOX ATTACK MODE"
            textSize = 16f
            setTextColor(Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })

        ll.addView(TextView(this).apply {
            text = "Simulasi serang saat kunjungi (visit) desa lain tanpa kehilangan pasukan asli."
            textSize = 11f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, 12)
        })

        val cbVisitSandbox = CheckBox(this).apply {
            text = "⚡ Otomatis Mode Sandbox saat Kunjungi / Visit Desa"
            isChecked = BotConfig.autoSandboxOnVisit
            setTextColor(Color.WHITE)
            textSize = 13f
            setOnCheckedChangeListener { _, isChecked ->
                BotConfig.autoSandboxOnVisit = isChecked
                if (isChecked) {
                    BotConfig.attackStrategy = AttackStrategy.SANDBOX_ATTACK
                }
            }
        }
        ll.addView(cbVisitSandbox)

        val etDuration = inputRow(ll, "Durasi Simulasi Auto-End (Detik)", BotConfig.sandboxEndBattleDelaySeconds.toString())

        ll.addView(sectionHeader("⚔️ PENGATURAN PASUKAN & LEVEL (MANUAL)"))

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

        ll.addView(Button(this).apply {
            text = "💾 SIMPAN KONFIGURASI SANDBOX"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                BotConfig.autoSandboxOnVisit = cbVisitSandbox.isChecked
                BotConfig.sandboxEndBattleDelaySeconds = etDuration.text.toString().toIntOrNull() ?: 25
                if (BotConfig.autoSandboxOnVisit) {
                    BotConfig.attackStrategy = AttackStrategy.SANDBOX_ATTACK
                }
                BotLogger.system("Pengaturan Sandbox tersimpan!")
                Toast.makeText(this@MainActivity, "Pengaturan Sandbox tersimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun armySlotRow(labels: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
            labels.forEach { label ->
                val slot = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#16213e"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 4
                    }
                    setPadding(4, 8, 4, 8)
                }

                if (label.isNotEmpty()) {
                    // Image placeholder
                    val imgView = ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(80, 80)
                        setBackgroundColor(Color.parseColor("#0d0d1a"))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        // Load dari template jika ada
                        val templateName = label.lowercase().replace(" ", "") + ".png"
                        loadTemplateImage(this, templateName)
                    }
                    slot.addView(imgView)

                    slot.addView(TextView(this@MainActivity).apply {
                        text = label
                        textSize = 10f
                        setTextColor(Color.parseColor("#888888"))
                        gravity = Gravity.CENTER
                    })

                    // Counter row
                    val counterRow = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setPadding(0, 4, 0, 0)
                    }
                    counterRow.addView(Button(this@MainActivity).apply {
                        text = "-"; textSize = 10f
                        setBackgroundColor(Color.parseColor("#CC0000"))
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(50, 50)
                    })
                    counterRow.addView(TextView(this@MainActivity).apply {
                        text = "1"; textSize = 12f
                        setTextColor(Color.WHITE)
                        setPadding(8, 0, 8, 0)
                        gravity = Gravity.CENTER
                    })
                    counterRow.addView(Button(this@MainActivity).apply {
                        text = "+"; textSize = 10f
                        setBackgroundColor(Color.parseColor("#00AA44"))
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(50, 50)
                    })
                    slot.addView(counterRow)
                } else {
                    // Empty slot
                    slot.addView(ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(80, 80)
                        setBackgroundColor(Color.parseColor("#0d0d1a"))
                        setImageResource(android.R.drawable.ic_menu_add)
                    })
                }
                addView(slot)
            }
        }
    }

    private fun loadTemplateImage(iv: ImageView, name: String) {
        try {
            // Cek dulu di custom templates dir
            val customFile = File(getExternalFilesDir(null), "templates/$name")
            if (customFile.exists()) {
                iv.setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                return
            }
            // Fallback ke assets
            val bmp = assets.open("templates/$name").use { BitmapFactory.decodeStream(it) }
            iv.setImageBitmap(bmp)
        } catch (e: Exception) {
            iv.setBackgroundColor(Color.parseColor("#16213e"))
        }
    }

    private fun showCalibrationTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(TextView(this).apply {
            text = "CALIBRATION ASSETS"
            textSize = 14f
            setTextColor(Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
        })
        ll.addView(TextView(this).apply {
            text = "Tap GAMBAR untuk ganti template dengan screenshot COC kamu"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 4, 0, 16)
        })

        ll.addView(sectionHeader("NAVIGATION"))
        listOf(
            "attack.png" to "Main attack sword icon",
            "find_match.png" to "Find opponent button",
            "attack2.png" to "Confirm attack button",
            "next.png" to "Skip enemy base",
            "returnhome.png" to "Go back to base",
            "searching.png" to "Loading/searching cloud"
        ).forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("OBSTACLE"))
        listOf(
            "reload.png" to "Connection error reload",
            "try_again.png" to "Connection retry button",
            "okay.png" to "Generic confirm OK",
            "cancel.png" to "Generic cancel",
            "x_close.png" to "Popup close button",
            "star_bonus.png" to "Star bonus popup header"
        ).forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("COLLECTOR TAP"))
        listOf(
            "gold_col.png" to "Gold collector icon",
            "elixir_col.png" to "Elixir collector icon",
            "dark_col.png" to "Dark elixir drill icon"
        ).forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("SMART ATTACK"))
        listOf(
            "gold1.png" to "Target Gold Musuh 1",
            "gold2.png" to "Target Gold Musuh 2",
            "gold3.png" to "Target Gold Musuh 3",
            "gold4.png" to "Target Gold Musuh 4",
            "elixir1.png" to "Target Elixir Musuh 1",
            "elixir2.png" to "Target Elixir Musuh 2",
            "elixir3.png" to "Target Elixir Musuh 3",
            "elixir4.png" to "Target Elixir Musuh 4",
            "de1.png" to "Target Dark Elixir Musuh 1",
            "de2.png" to "Target Dark Elixir Musuh 2",
            "battle_result.png" to "Battle result screen"
        ).forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("WALL UPGRADE"))
        listOf(
            "wall_builder.png" to "Open builder/upgrade menu",
            "wall_item.png" to "Wall in upgrade list"
        ).forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun templateRow(name: String, desc: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 10, 12, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
            gravity = Gravity.CENTER_VERTICAL

            // Thumbnail
            val imgView = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).apply { marginEnd = 12 }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#0d0d1a"))
            }
            loadTemplateImage(imgView, name)
            addView(imgView)

            // Text info
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = desc
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                })
                addView(TextView(this@MainActivity).apply {
                    // Cek apakah sudah custom
                    val customFile = File(getExternalFilesDir(null), "templates/$name")
                    text = if (customFile.exists()) "✅ Custom Asset" else "Default Asset (Auto Resize)"
                    textSize = 10f
                    setTextColor(if (customFile.exists()) Color.parseColor("#00FF88") else Color.parseColor("#FF6B35"))
                })
            })

            // Tombol Gambar
            addView(Button(this@MainActivity).apply {
                text = "📷 Gambar"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#334466"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(180, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    currentTemplateTarget = name
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    imagePickerLauncher.launch(intent)
                }
            })
        }
    }

    private fun saveTemplateFromUri(uri: Uri, templateName: String) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Simpan ke external files dir
            val dir = File(getExternalFilesDir(null), "templates")
            dir.mkdirs()
            val file = File(dir, templateName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Toast.makeText(this, "Template '$templateName' berhasil disimpan!", Toast.LENGTH_SHORT).show()
            BotLogger.system("Template $templateName diupdate dari galeri")

            // Refresh tab kalibrasi
            showCalibrationTab()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan template: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#FF6B35"))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 16, 0, 8)
    }

    private fun inputRow(parent: LinearLayout, label: String, default: String): EditText {
        parent.addView(TextView(this).apply {
            text = label; textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 2)
        })
        return EditText(this).apply {
            setText(default); textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(12, 12, 12, 12)
        }.also { parent.addView(it) }
    }

    private fun permissionRow(name: String, granted: Boolean, btnText: String, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (granted) Color.parseColor("#0d2e1a") else Color.parseColor("#2e1a0d"))
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "${if (granted) "✅" else "❌"} $name"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!granted) {
                addView(Button(this@MainActivity).apply {
                    text = btnText; textSize = 11f
                    setBackgroundColor(Color.parseColor("#FF6B35"))
                    setTextColor(Color.WHITE)
                    setOnClickListener { action() }
                })
            }
        }
    }

    private fun launchClashOfClans() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
        if (launchIntent != null) {
            startActivity(launchIntent)
            BotLogger.system("Membuka game Clash of Clans...")
        } else {
            Toast.makeText(this, "Aplikasi Clash of Clans (com.supercell.clashofclans) tidak ditemukan di perangkat ini!", Toast.LENGTH_LONG).show()
            BotLogger.error("Clash of Clans tidak terinstall.")
        }
    }

    private fun launchSandboxPractice() {
        BotConfig.attackStrategy = AttackStrategy.SANDBOX_ATTACK
        BotConfig.autoSandboxOnVisit = true
        BotLogger.info("🎮 [SANDBOX MODE] Mode Latihan Sandbox Diaktifkan!")
        startBot()
        launchClashOfClans()
        Toast.makeText(this, "Mode Sandbox Aktif! Silakan Kunjungi (Visit) Desa Target di Clash of Clans untuk latihan.", Toast.LENGTH_LONG).show()
    }

    private fun startBot() {
        if (AccessibilityBot.instance == null) {
            Toast.makeText(this, "Aktifkan COC Bot Accessibility dulu!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        BotService.start(this)
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    private fun stopBot() {
        BotService.getInstance()?.stopBot()
        BotService.stop(this)
        ScreenCaptureService.stop(this)
        FloatingWindowService.stop(this)
    }

    private fun togglePause() {
        val bot = BotService.getInstance() ?: return
        if (bot.state.value == BotState.PAUSED) { bot.resumeBot(); btnPause.text = "⏸ PAUSE" }
        else { bot.pauseBot(); btnPause.text = "▶ RESUME" }
    }

    private fun observeBot() {
        if (!::tvLog.isInitialized) return
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { logs ->
                tvLog.text = logs.takeLast(80).joinToString("\n") { "[${it.timestamp}] [${it.level.name}] ${it.message}" }
                if (::scrollLog.isInitialized) scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { if (::tvStatus.isInitialized) tvStatus.text = "Status: $it" }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.session?.collectLatest {
                if (::tvStats.isInitialized)
                    tvStats.text = "🏆${it.totalMatches} 💛${"%,d".format(it.totalGold)} 💜${"%,d".format(it.totalElixir)} 🖤${it.totalDarkElixir}"
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.currentLoot?.collectLatest {
                if (::tvLoot.isInitialized)
                    tvLoot.text = "🎯 G:${"%,d".format(it.gold)} E:${"%,d".format(it.elixir)} DE:${it.darkElixir}"
            }
        }
    }
}
