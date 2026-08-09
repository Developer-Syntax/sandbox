package com.cocbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cocbot.*
import com.cocbot.ai.GeminiAttackEngine
import com.cocbot.ai.GeminiAttackPlan
import com.cocbot.root.RootShell
import com.cocbot.state.*
import com.cocbot.vision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BotService : Service() {
    companion object {
        private const val NOTIF_ID = 1002
        private const val CH = "ai_bot"
        private var instance: BotService? = null
        fun getInstance() = instance
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, BotService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, BotService::class.java))
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var tmgr: TemplateManager

    private val _state = MutableStateFlow(BotState.IDLE)
    val state: StateFlow<BotState> = _state
    private val _stats = MutableStateFlow(BotSessionStats())
    val stats: StateFlow<BotSessionStats> = _stats

    private var job: Job? = null
    private var paused = false
    private var battleStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        tmgr = TemplateManager(this)
        createChannel()
        RootShell.checkRootPermission()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        startForeground(NOTIF_ID, buildNotif())
        return START_STICKY
    }

    fun startBot() {
        if (job?.isActive == true) return
        paused = false
        _stats.value = BotSessionStats()
        BotLogger.system("🤖 [GEMINI AI BOT] Bot Auto Attack Berbasis AI Gemini Dimulai")
        _state.value = BotState.SCOUTING_BASE
        job = scope.launch { loop() }
    }

    fun stopBot() {
        job?.cancel()
        _state.value = BotState.IDLE
        BotLogger.system("🤖 [GEMINI AI BOT] Bot Dihentikan")
    }

    fun pauseBot() {
        paused = true
        _state.value = BotState.PAUSED
        BotLogger.system("🤖 [GEMINI AI BOT] Bot Dipause")
    }

    fun resumeBot() {
        paused = false
        _state.value = BotState.SCOUTING_BASE
        BotLogger.system("🤖 [GEMINI AI BOT] Bot Dilanjutkan")
    }

    fun triggerSingleAiAnalysisAndAttack(bitmap: Bitmap?) {
        scope.launch {
            runAiAnalysisAndDeploy(bitmap)
        }
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (paused) {
                delay(1000)
                continue
            }

            when (_state.value) {
                BotState.IDLE -> delay(1000)
                BotState.SCOUTING_BASE -> {
                    BotLogger.info("🔎 [AUTO SCOUTING] Menganalisis layar permainan...")
                    val screenBitmap = ScreenCaptureService.getInstance()?.captureScreen()
                    
                    // Run AI Analysis on captured screen
                    runAiAnalysisAndDeploy(screenBitmap)
                }
                BotState.IN_BATTLE -> {
                    val elapsed = (System.currentTimeMillis() - battleStartTime) / 1000
                    if (elapsed >= 22 && _state.value != BotState.WAITING_HERO_ABILITIES) {
                        _state.value = BotState.WAITING_HERO_ABILITIES
                        BotLogger.info("⚡ [AUTO HERO SKILL] Mengaktifkan Skill Hero tepat saat bertempur di pusat desa lawan...")
                        triggerHeroAbilities()
                    }
                    if (elapsed >= BotConfig.endBattleWaitSec) {
                        BotLogger.info("⏳ [AUTO BATTLE END] Selesai bertempur. Kembali ke desa & memulai pencarian berikutnya...")
                        endBattle()
                    } else {
                        delay(1000)
                    }
                }
                BotState.PAUSED -> delay(1000)
                BotState.ERROR -> delay(2000)
                else -> delay(1000)
            }
        }
    }

    private suspend fun runAiAnalysisAndDeploy(screenBitmap: Bitmap?) {
        _state.value = BotState.ANALYZING_WITH_GEMINI
        BotLogger.info("🤖 [GEMINI VISION AI] Mengirim data desa ke Gemini 3.5 Flash...")

        // Read Loot via OCR if available
        var gold = 0; var elixir = 0; var darkElixir = 0
        if (screenBitmap != null) {
            val lootResult = LootScanner.scanLoot(screenBitmap)
            gold = lootResult.gold
            elixir = lootResult.elixir
            darkElixir = lootResult.darkElixir
            if (gold > 0 || elixir > 0) {
                BotLogger.info("💰 [OCR LOOT] Gold: $gold | Elixir: $elixir | Dark Elixir: $darkElixir")
            }
        }

        val aiPlan = GeminiAttackEngine.analyzeBaseAndPlanAttack(screenBitmap, gold, elixir, darkElixir)
        _stats.value.lastAiDecision = "${if (aiPlan.shouldAttack) "ATTACK" else "NEXT"} (${aiPlan.reason})"

        if (aiPlan.shouldAttack) {
            _state.value = BotState.GEMINI_APPROVED
            BotLogger.info("✅ [AI APPROVED] Strategy: ${aiPlan.attackDirection} | Target Stars: ${aiPlan.estimatedStars}★ | Reason: ${aiPlan.reason}")
            
            _state.value = BotState.DEPLOYING_AI_ATTACK
            executeGeminiAttackPlan(aiPlan)
            
            battleStartTime = System.currentTimeMillis()
            _stats.value.totalAttacksExecuted++
            _stats.value.totalGoldLooted += gold
            _stats.value.totalElixirLooted += elixir
            _state.value = BotState.IN_BATTLE
        } else {
            _state.value = BotState.GEMINI_REJECTED
            BotLogger.warning("⏭️ [AI NEXT] Skipped: ${aiPlan.reason}. Mencari lawan berikutnya...")
            _stats.value.totalBasesScouted++
            
            // Tap Next Base
            tapPoint(BotConfig.BTN_NEXT_BASE)
            delay(3000)
            _state.value = BotState.SCOUTING_BASE
        }
    }

    fun triggerForceDeployNow() {
        scope.launch {
            BotLogger.info("⚡ [FORCE DEPLOY] Mengerahkan seluruh pasukan, hero, dan spell secara langsung!")
            val forcePlan = GeminiAttackPlan(
                shouldAttack = true,
                estimatedStars = 3,
                reason = "Di-trigger manual oleh pengguna",
                attackDirection = "BOTTOM_LEFT",
                funnelSlots = listOf(0, 1),
                mainArmySlots = listOf(1, 2, 3),
                heroSlots = listOf(4, 5, 6, 7),
                spellSlots = listOf(8, 9),
                detectedArmySummary = "Force Deploy All Army",
                notes = "Manual trigger force deploy"
            )
            _state.value = BotState.DEPLOYING_AI_ATTACK
            executeGeminiAttackPlan(forcePlan)
            battleStartTime = System.currentTimeMillis()
            _state.value = BotState.IN_BATTLE
        }
    }

    private suspend fun executeGeminiAttackPlan(plan: GeminiAttackPlan) {
        BotLogger.info("⚔️ [AI STRATEGIC ATTACK] Memulai eksekusi taktis penyerangan AI (${plan.attackDirection})...")
        BotLogger.info("📦 [PASUKAN TERDETEKSI] ${plan.detectedArmySummary}")

        if (AccessibilityBot.instance == null && !RootShell.isRootAvailable) {
            BotLogger.error("❌ CRITICAL: Accessibility Service belum diaktifkan! Buka aplikasi > Tab API & PERMIT > Aktifkan Accessibility.")
        }

        val (startPos, endPos) = when (plan.attackDirection) {
            "TOP_LEFT" -> Pair(BotConfig.DEPLOY_TOP_LEFT_START, BotConfig.DEPLOY_TOP_LEFT_END)
            "TOP_RIGHT" -> Pair(BotConfig.DEPLOY_TOP_RIGHT_START, BotConfig.DEPLOY_TOP_RIGHT_END)
            "BOTTOM_RIGHT" -> Pair(BotConfig.DEPLOY_BOTTOM_RIGHT_START, BotConfig.DEPLOY_BOTTOM_RIGHT_END)
            else -> Pair(BotConfig.DEPLOY_BOTTOM_LEFT_START, BotConfig.DEPLOY_BOTTOM_LEFT_END)
        }

        val centerPos = BotConfig.getRelPoint(0.50f, 0.45f)
        var isScrolledRight = false

        suspend fun ensureBarPosition(needsRightScroll: Boolean) {
            if (needsRightScroll && !isScrolledRight) {
                val start = BotConfig.TROOP_BAR_SWIPE_RIGHT_TO_LEFT_START
                val end = BotConfig.TROOP_BAR_SWIPE_RIGHT_TO_LEFT_END
                BotLogger.info("👉 [SWIPE BAR PASUKAN] Menggeser baris pasukan untuk membuka slot spell/pasukan tersembunyi...")
                if (AccessibilityBot.instance != null) {
                    AccessibilityBot.instance?.swipe(start.x, start.y, end.x, end.y, 300)
                } else if (RootShell.isRootAvailable) {
                    RootShell.inputSwipe(start.x, start.y, end.x, end.y, 300)
                }
                isScrolledRight = true
                delay(350)
            } else if (!needsRightScroll && isScrolledRight) {
                val start = BotConfig.TROOP_BAR_SWIPE_LEFT_TO_RIGHT_START
                val end = BotConfig.TROOP_BAR_SWIPE_LEFT_TO_RIGHT_END
                BotLogger.info("👈 [SWIPE BAR PASUKAN] Mengembalikan baris pasukan ke posisi kiri...")
                if (AccessibilityBot.instance != null) {
                    AccessibilityBot.instance?.swipe(start.x, start.y, end.x, end.y, 300)
                } else if (RootShell.isRootAvailable) {
                    RootShell.inputSwipe(start.x, start.y, end.x, end.y, 300)
                }
                isScrolledRight = false
                delay(350)
            }
        }

        suspend fun selectSlotPoint(slotIndex: Int): android.graphics.PointF? {
            val isRightSlot = slotIndex >= 8
            ensureBarPosition(isRightSlot)
            val mappedIndex = if (isRightSlot) slotIndex - 4 else slotIndex
            return BotConfig.getSlotPoint(mappedIndex)
        }

        // --- PHASE 1: FUNNEL & SIEGE MACHINE (TIMED DEPLOYMENT) ---
        BotLogger.info("🚀 [PHASE 1: FUNNELING] Mengirim pasukan pembersih pinggir & Siege Machine...")
        for (slotIdx in plan.funnelSlots) {
            val slotP = selectSlotPoint(slotIdx) ?: continue
            tapPoint(slotP) // Select slot ONCE
            delay(120)
            // Tap 2 points on edges to clear outer trash buildings
            tapPoint(startPos.x, startPos.y)
            delay(100)
            tapPoint(endPos.x, endPos.y)
            delay(150)
        }
        // Strategic pause: Allow funneling units 2.5s to create clear path for main army
        BotLogger.info("⏳ [TIMING PAUSE] Menunggu 2.5 detik agar pasukan funnel membersihkan bangunan pinggir...")
        delay(2500)

        // --- PHASE 2: MAIN ARMY SPREAD (MAIN DPS & TANKS) ---
        BotLogger.info("🐉 [PHASE 2: MAIN ARMY] Mengirim seluruh pasukan utama menyebar di sepanjang garis serangan...")
        for (slotIdx in plan.mainArmySlots) {
            val slotP = selectSlotPoint(slotIdx) ?: continue
            tapPoint(slotP) // Select slot ONCE
            delay(100)
            // Spread deployment along attack boundary (8-10 points max, NO spammed empty taps)
            for (i in 0..8) {
                val frac = i / 8f
                val px = startPos.x + (endPos.x - startPos.x) * frac
                val py = startPos.y + (endPos.y - startPos.y) * frac
                tapPoint(px, py)
                delay(80)
            }
            delay(150)
        }
        // Strategic pause: Allow main army to draw defense aggro before deploying Heroes
        BotLogger.info("⏳ [TIMING PAUSE] Menunggu 2.0 detik agar pasukan utama menyerap serangan pertahanan...")
        delay(2000)

        // --- PHASE 3: HEROES DEPLOYMENT (CLEAN 1-TAP SLOT + 1-TAP MAP) ---
        BotLogger.info("👑 [PHASE 3: HEROES] Menurunkan para Hero di belakang pasukan utama...")
        for (slotIdx in plan.heroSlots) {
            val slotP = selectSlotPoint(slotIdx) ?: continue
            tapPoint(slotP) // Select hero slot ONCE ONLY!
            delay(120)
            // Deploy hero ONCE on map line (DO NOT tap hero slot again to avoid premature ability trigger!)
            val midX = (startPos.x + endPos.x) / 2f
            val midY = (startPos.y + endPos.y) / 2f
            tapPoint(midX, midY)
            delay(300)
        }

        // --- PHASE 4: DELAYED STRATEGIC SPELL CASTING ---
        // Waiting 6 seconds into main engagement before dropping Rage/Heal spells in base core
        BotLogger.info("⏳ [SPELL TIMING] Menunggu pasukan memasuki area inti sebelum menderaskan Mantra/Spell...")
        delay(6000)

        BotLogger.info("✨ [PHASE 4: SPELLS] Menderaskan Spell/Mantra di pusat pertahanan musuh...")
        for ((idx, slotIdx) in plan.spellSlots.withIndex()) {
            val slotP = selectSlotPoint(slotIdx) ?: continue
            tapPoint(slotP) // Select spell slot ONCE
            delay(150)
            
            // Offset target position slightly for multiple spells
            val offsetX = (if (idx % 2 == 0) -30f else 30f)
            val offsetY = (if (idx < 2) -20f else 20f)
            tapPoint(centerPos.x + offsetX, centerPos.y + offsetY)
            delay(1000) // 1s pause between spells for clean drop
        }

        // Ensure bar is reset to left so hero slots 4,5,6,7 are visible for later ability activation
        ensureBarPosition(needsRightScroll = false)

        BotLogger.info("🔥 [AI DEPLOYMENT FINISHED] Pasukan, Hero, & Spell telah dikerahkan secara taktis!")
    }

    private suspend fun triggerHeroAbilities() {
        BotLogger.info("⚡ [HERO ABILITIES] Mengaktifkan Skill Hero tepat saat bertempur di inti desa lawan!")
        for (slot in listOf(BotConfig.SLOT_4, BotConfig.SLOT_5, BotConfig.SLOT_6, BotConfig.SLOT_7)) {
            tapPoint(slot)
            delay(250)
        }
    }

    private suspend fun endBattle() {
        _state.value = BotState.ENDING_BATTLE
        BotLogger.info("🏁 [AUTO FINISH] Menyelesaikan pertempuran...")
        tapPoint(BotConfig.BTN_END_BATTLE)
        delay(1000)
        tapPoint(BotConfig.BTN_OKAY)
        delay(1500)
        tapPoint(BotConfig.BTN_RETURN_HOME)
        delay(3000)

        // Automatically start searching for next target base from Home Base
        if (BotConfig.autoNextIfLootLow) {
            BotLogger.info("🔁 [AUTO SEARCH] Memulai pencarian lawan berikutnya secara otomatis dari Desa...")
            tapPoint(BotConfig.BTN_HOME_ATTACK)
            delay(1500)
            tapPoint(BotConfig.BTN_FIND_MATCH)
            delay(4000)
        }

        _state.value = BotState.SCOUTING_BASE
    }

    private suspend fun tapPoint(x: Float, y: Float) {
        val acc = AccessibilityBot.instance
        if (acc != null) {
            acc.tap(x, y)
        } else if (RootShell.isRootAvailable) {
            RootShell.inputTap(x, y)
        } else {
            BotLogger.error("❌ GAGAL TAP (${x.toInt()}, ${y.toInt()}): Accessibility Service TIDAK AKTIF! Buka Pengaturan HP > Aksesibilitas > Aktifkan CoC Bot.")
        }
    }

    private suspend fun tapPoint(p: android.graphics.PointF) {
        tapPoint(p.x, p.y)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CH, "AI Bot Service", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("🤖 CoC Gemini AI Auto Attack Bot")
            .setContentText("Status: ${_state.value} | 100% Dynamic Gemini AI")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        stopBot()
        super.onDestroy()
    }
}
