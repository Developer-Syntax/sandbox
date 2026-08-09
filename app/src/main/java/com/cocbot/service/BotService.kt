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
                    if (elapsed >= 15 && _state.value != BotState.WAITING_HERO_ABILITIES) {
                        _state.value = BotState.WAITING_HERO_ABILITIES
                        BotLogger.info("⚡ [AUTO HERO SKILL] Mengaktifkan Skill Hero (King/Queen/Warden/RC)...")
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
        BotLogger.info("⚔️ [AI AUTO ATTACK] Memulai eksekusi penyerangan otomatis (${plan.attackDirection})...")
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

        suspend fun swipeTroopBarRightToLeft() {
            val start = BotConfig.TROOP_BAR_SWIPE_RIGHT_TO_LEFT_START
            val end = BotConfig.TROOP_BAR_SWIPE_RIGHT_TO_LEFT_END
            BotLogger.info("👉 [SWIPE BAR PASUKAN] Menggeser baris pasukan ke kanan untuk membuka slot tersembunyi (spells/pasukan tambahan)...")
            if (AccessibilityBot.instance != null) {
                AccessibilityBot.instance?.swipe(start.x, start.y, end.x, end.y, 300)
            } else if (RootShell.isRootAvailable) {
                RootShell.inputSwipe(start.x, start.y, end.x, end.y, 300)
            }
            delay(400)
        }

        suspend fun swipeTroopBarLeftToRight() {
            val start = BotConfig.TROOP_BAR_SWIPE_LEFT_TO_RIGHT_START
            val end = BotConfig.TROOP_BAR_SWIPE_LEFT_TO_RIGHT_END
            BotLogger.info("👈 [SWIPE BAR PASUKAN] Menggeser kembali baris pasukan ke posisi semula...")
            if (AccessibilityBot.instance != null) {
                AccessibilityBot.instance?.swipe(start.x, start.y, end.x, end.y, 300)
            } else if (RootShell.isRootAvailable) {
                RootShell.inputSwipe(start.x, start.y, end.x, end.y, 300)
            }
            delay(400)
        }

        var isScrolledRight = false

        suspend fun dumpSlot(slotIndex: Int, repeatCount: Int = 18, isSpell: Boolean = false) {
            // Auto swipe right-to-left if slotIndex >= 8 and not scrolled yet
            if (slotIndex >= 8 && !isScrolledRight) {
                swipeTroopBarRightToLeft()
                isScrolledRight = true
            }

            val slotPoint = BotConfig.getSlotPoint(if (isScrolledRight && slotIndex >= 8) slotIndex - 4 else slotIndex) ?: return
            tapPoint(slotPoint)
            delay(150)

            if (isSpell) {
                // Drop spell in enemy core / strategic area
                for (k in 0 until repeatCount.coerceAtMost(3)) {
                    val offsetX = (Math.random() * 80 - 40).toFloat()
                    val offsetY = (Math.random() * 80 - 40).toFloat()
                    tapPoint(centerPos.x + offsetX, centerPos.y + offsetY)
                    delay(120)
                }
            } else {
                // Rapidly tap along the deployment line to deploy ALL troops/heroes
                for (i in 0 until repeatCount) {
                    val frac = (i % 8) / 7f
                    val px = startPos.x + (endPos.x - startPos.x) * frac
                    val py = startPos.y + (endPos.y - startPos.y) * frac
                    tapPoint(px, py)
                    delay(60) // 60ms rapid tap
                }
            }
            delay(120)
        }

        // Wave 1: Deploy Funneling & Siege Machine
        BotLogger.info("🚀 Wave 1: Deploy Funnel & Siege Machine (Slots: ${plan.funnelSlots.joinToString()})...")
        for (slotIdx in plan.funnelSlots) {
            dumpSlot(slotIdx, repeatCount = 12)
        }
        delay(400)

        // Wave 2: Deploy Main Army
        BotLogger.info("🐉 Wave 2: Deploy Main Army (Slots: ${plan.mainArmySlots.joinToString()})...")
        for (slotIdx in plan.mainArmySlots) {
            dumpSlot(slotIdx, repeatCount = 20)
        }
        delay(400)

        // Wave 3: Deploy Heroes
        BotLogger.info("👑 Wave 3: Deploy Heroes (Slots: ${plan.heroSlots.joinToString()})...")
        for (slotIdx in plan.heroSlots) {
            dumpSlot(slotIdx, repeatCount = 8)
        }
        delay(400)

        // Wave 4: Deploy Spells
        BotLogger.info("✨ Wave 4: Deploy Spells (Slots: ${plan.spellSlots.joinToString()})...")
        for (slotIdx in plan.spellSlots) {
            dumpSlot(slotIdx, repeatCount = 3, isSpell = true)
        }
        delay(400)

        // Wave 5: FINAL CLEANUP SWEEP WITH AUTO-SWIPE (Sweep ALL slots 0 to 12 to guarantee 100% troop deployment)
        BotLogger.info("🧹 Wave 5: Final Sweep - Memastikan 100% sisa pasukan & spell di seluruh slot dikerahkan!")
        if (isScrolledRight) {
            swipeTroopBarLeftToRight()
            isScrolledRight = false
        }
        // Sweep visible left slots 0..7
        for (sIdx in 0..7) {
            dumpSlot(sIdx, repeatCount = 8)
        }
        // Swipe right to reveal rightmost slots (spells / extra troops)
        swipeTroopBarRightToLeft()
        isScrolledRight = true
        for (sIdx in 8..12) {
            dumpSlot(sIdx, repeatCount = 8)
        }
        // Reset scroll position
        swipeTroopBarLeftToRight()
        isScrolledRight = false

        BotLogger.info("🔥 [AI AUTO ATTACK COMPLETE] 100% Pasukan, Hero, & Spell Telah Dikerahkan!")
    }

    private suspend fun triggerHeroAbilities() {
        for (slot in listOf(BotConfig.SLOT_4, BotConfig.SLOT_5, BotConfig.SLOT_6, BotConfig.SLOT_7)) {
            tapPoint(slot)
            delay(300)
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
            .setContentText("Status: ${_state.value} | Strategy: ${BotConfig.aiStrategyPreset}")
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
