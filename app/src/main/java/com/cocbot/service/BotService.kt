package com.cocbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cocbot.*
import com.cocbot.state.*
import com.cocbot.vision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BotService : Service() {
    companion object {
        private const val NOTIF_ID = 1002
        private const val CH = "bot"
        private var instance: BotService? = null
        fun getInstance() = instance
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, BotService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, BotService::class.java))
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var tmgr: TemplateManager
    private lateinit var loot: LootScanner

    private val _state = MutableStateFlow(BotState.IDLE)
    val state: StateFlow<BotState> = _state
    private val _session = MutableStateFlow(FarmSession())
    val session: StateFlow<FarmSession> = _session
    private val _loot = MutableStateFlow(LootData())
    val currentLoot: StateFlow<LootData> = _loot

    private var job: Job? = null
    private var paused = false
    private var matchNum = 0
    private var nextTaps = 0
    private var battleStart = 0L
    private var troopsWaitStart = 0L

    override fun onCreate() {
        super.onCreate(); instance = this
        tmgr = TemplateManager(this); loot = LootScanner()
        createChannel()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        startForeground(NOTIF_ID, buildNotif()); return START_STICKY
    }

    fun startBot() {
        if (job?.isActive == true) return
        paused = false; _session.value = FarmSession()
        BotLogger.system("Bot farming dimulai")
        job = scope.launch { loop() }
    }

    fun stopBot() {
        job?.cancel(); _state.value = BotState.IDLE
        val s = _session.value
        BotLogger.system("Bot dihentikan. Total match: ${s.totalMatches}")
        BotLogger.rekap("TOTAL -> G:${"%,d".format(s.totalGold)} E:${"%,d".format(s.totalElixir)} DE:${s.totalDarkElixir}")
    }

    fun pauseBot() { paused = true; _state.value = BotState.PAUSED; BotLogger.system("Bot di-pause") }
    fun resumeBot() { paused = false; BotLogger.system("Bot dilanjutkan") }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (paused) { delay(1000); continue }

            val acc = AccessibilityBot.instance
            if (acc == null) { BotLogger.error("AccessibilityService tidak aktif!"); delay(3000); continue }

            val ss = ScreenCaptureService.getInstance()?.captureScreen()
            if (ss == null) { BotLogger.error("Gagal ambil screenshot"); delay(2000); continue }

            // Cek obstacle dulu setiap cycle
            handleObstacles(ss, acc)

            when (_state.value) {
                BotState.IDLE -> _state.value = BotState.CHECKING_HOME
                BotState.CHECKING_HOME -> doCheckHome(acc)
                BotState.COLLECTING -> doCollect(ss, acc)
                BotState.OPENING_ATTACK -> doOpenAttack(acc)
                BotState.FINDING_MATCH -> doFindMatch(acc)
                BotState.SEARCHING -> doSearching()
                BotState.SCOUTING -> doScouting(ss, acc)
                BotState.DEPLOYING_TROOPS -> doDeployTroops(acc)
                BotState.WAITING_BATTLE -> doWaitBattle(ss, acc)
                BotState.READING_RESULT -> doReadResult(ss, acc)
                BotState.RETURNING_HOME -> doReturnHome(acc)
                BotState.UPGRADING_WALL -> doUpgradeWall(ss, acc)
                BotState.WAITING_TROOPS -> doWaitTroops()
                BotState.ERROR -> { BotLogger.error("Recovery..."); delay(5000); _state.value = BotState.CHECKING_HOME }
                BotState.PAUSED -> delay(1000)
            }
            delay(300)
        }
    }

    // Cek layar error/obstacle
    private suspend fun handleObstacles(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        if (tmgr.isVisible(ss, Template.RELOAD)) {
            BotLogger.warning("Koneksi error, tap reload...")
            val r = tmgr.findTemplate(ss, Template.RELOAD)
            if (r.found) acc.tap(r.position) else acc.tap(BotConfig.BTN_OKAY)
            delay(3000)
        }
        if (tmgr.isVisible(ss, Template.TRY_AGAIN)) {
            BotLogger.warning("Try again detected, tap...")
            val r = tmgr.findTemplate(ss, Template.TRY_AGAIN)
            if (r.found) acc.tap(r.position) else acc.tap(BotConfig.BTN_OKAY)
            delay(3000)
        }
        if (tmgr.isVisible(ss, Template.STAR_BONUS)) {
            BotLogger.info("Star bonus popup, menutup...")
            acc.tap(BotConfig.BTN_CLOSE_X)
            delay(1000)
        }
    }

    private suspend fun doCheckHome(acc: AccessibilityBot) {
        BotLogger.info("Tombol Home ditemukan.")
        delay(BotConfig.randomDelay())
        _state.value = if (BotConfig.autoCollect) BotState.COLLECTING else BotState.OPENING_ATTACK
    }

    private suspend fun doCollect(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        BotLogger.info("Auto collect resources...")
        // Cari dan tap collector yang penuh
        val goldCol = tmgr.findTemplate(ss, Template.GOLD_COLLECTOR)
        if (goldCol.found) { acc.tap(goldCol.position); delay(500) }
        val elixirCol = tmgr.findTemplate(ss, Template.ELIXIR_COLLECTOR)
        if (elixirCol.found) { acc.tap(elixirCol.position); delay(500) }
        val darkCol = tmgr.findTemplate(ss, Template.DARK_COLLECTOR)
        if (darkCol.found) { acc.tap(darkCol.position); delay(500) }
        delay(BotConfig.randomDelay())
        _state.value = BotState.OPENING_ATTACK
    }

    private suspend fun doOpenAttack(acc: AccessibilityBot) {
        BotLogger.info("Men-tap tombol Serang!")
        acc.tap(BotConfig.BTN_ATTACK)
        delay(BotConfig.delayMenuLoad)
        _state.value = BotState.FINDING_MATCH
    }

    private suspend fun doFindMatch(acc: AccessibilityBot) {
        BotLogger.menu("Memulai Cari Lawan Tanding...")
        acc.tap(BotConfig.BTN_FIND_MATCH)
        delay(BotConfig.delayMatchLoad)
        nextTaps = 0
        _state.value = BotState.SEARCHING
    }

    private suspend fun doSearching() {
        BotLogger.info("Mencari lawan...")
        delay(3000)
        _state.value = BotState.SCOUTING
    }

    private suspend fun doScouting(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        if (BotConfig.enableLootFilter) {
            BotLogger.info("Memindai loot lawan...")
            val ld = loot.scanLoot(ss)
            _loot.value = ld
            BotLogger.scan("Loot: $ld")

            val ok = if (BotConfig.useAnyResource) {
                ld.gold >= BotConfig.minGoldTarget ||
                ld.elixir >= BotConfig.minElixirTarget ||
                (BotConfig.minDarkElixirTarget > 0 && ld.darkElixir >= BotConfig.minDarkElixirTarget)
            } else {
                ld.gold >= BotConfig.minGoldTarget && ld.elixir >= BotConfig.minElixirTarget
            }

            if (!ok && nextTaps < BotConfig.maxNextTaps) {
                nextTaps++
                BotLogger.info("Loot kurang, skip ($nextTaps/${BotConfig.maxNextTaps})")
                acc.tap(BotConfig.BTN_NEXT)
                delay(BotConfig.delayMatchLoad)
                return
            }
            if (nextTaps >= BotConfig.maxNextTaps) BotLogger.warning("Max skip, serang paksa!")
            else BotLogger.info("Loot OK! Serang!")
        }

        nextTaps = 0
        BotLogger.action("Men-tap Serang! (konfirmasi)")
        acc.tap(BotConfig.BTN_ATTACK_CONFIRM)
        delay(BotConfig.delayMenuLoad)
        _state.value = BotState.DEPLOYING_TROOPS
    }

    private suspend fun doDeployTroops(acc: AccessibilityBot) {
        matchNum++; battleStart = System.currentTimeMillis()
        val pre = BotConfig.randomDelay()
        BotLogger.antibot("Jeda organik: ${String.format("%.3f", pre/1000.0)} detik...")
        delay(pre)
        BotLogger.action("Deploy pasukan ke 4 sisi")
        when (BotConfig.attackStrategy) {
            AttackStrategy.ALL_SIDES -> acc.deployAllSides(BotConfig.troopsPerSide)
            AttackStrategy.TOP_BOTTOM -> deployTopBottom(acc)
            AttackStrategy.LEFT_RIGHT -> deployLeftRight(acc)
            AttackStrategy.SMART_ZONE -> acc.deployAllSides(BotConfig.troopsPerSide)
            AttackStrategy.SANDBOX_ATTACK -> {
                BotLogger.info("🎮 [SANDBOX MODE] Deploying custom army & levels...")
                acc.deploySandboxArmy(BotConfig.sandboxUnits)
            }
        }
        _state.value = BotState.WAITING_BATTLE
    }

    private suspend fun deployTopBottom(acc: AccessibilityBot) {
        val n = BotConfig.troopsPerSide * 2
        val tStep = (BotConfig.DEPLOY_TOP_END.x - BotConfig.DEPLOY_TOP_START.x) / n
        for (i in 0 until n) { acc.tap(BotConfig.DEPLOY_TOP_START.x + tStep * i, BotConfig.DEPLOY_TOP_START.y); delay(120) }
        delay(300)
        val bStep = (BotConfig.DEPLOY_BOTTOM_END.x - BotConfig.DEPLOY_BOTTOM_START.x) / n
        for (i in 0 until n) { acc.tap(BotConfig.DEPLOY_BOTTOM_START.x + bStep * i, BotConfig.DEPLOY_BOTTOM_START.y); delay(120) }
    }

    private suspend fun deployLeftRight(acc: AccessibilityBot) {
        val n = BotConfig.troopsPerSide * 2
        val lStep = (BotConfig.DEPLOY_LEFT_END.y - BotConfig.DEPLOY_LEFT_START.y) / n
        for (i in 0 until n) { acc.tap(BotConfig.DEPLOY_LEFT_START.x, BotConfig.DEPLOY_LEFT_START.y + lStep * i); delay(120) }
        delay(300)
        val rStep = (BotConfig.DEPLOY_RIGHT_END.y - BotConfig.DEPLOY_RIGHT_START.y) / n
        for (i in 0 until n) { acc.tap(BotConfig.DEPLOY_RIGHT_START.x, BotConfig.DEPLOY_RIGHT_START.y + rStep * i); delay(120) }
    }

    private suspend fun doWaitBattle(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        val elapsed = (System.currentTimeMillis() - battleStart) / 1000
        BotLogger.wait("Menunggu battle... ${elapsed}s")

        if (BotConfig.attackStrategy == AttackStrategy.SANDBOX_ATTACK) {
            if (elapsed >= BotConfig.sandboxEndBattleDelaySeconds) {
                BotLogger.info("🎮 [SANDBOX MODE] Durasi latihan selesai (${BotConfig.sandboxEndBattleDelaySeconds}s), mengakhiri pertarungan...")
                acc.tap(BotConfig.BTN_END_BATTLE)
                delay(1000)
                acc.tap(BotConfig.BTN_OKAY)
                delay(2000)
                _state.value = BotState.READING_RESULT
                return
            }
        }

        if (tmgr.isVisible(ss, Template.BATTLE_RESULT)) {
            BotLogger.info("Battle selesai!")
            _state.value = BotState.READING_RESULT
            return
        }
        if (tmgr.isVisible(ss, Template.BTN_RETURN_HOME)) {
            BotLogger.info("Return home terdeteksi!")
            _state.value = BotState.READING_RESULT
            return
        }
        if (elapsed > BotConfig.waitBattleSeconds) {
            BotLogger.warning("Timeout, surrender...")
            acc.tap(BotConfig.BTN_END_BATTLE); delay(1500)
            acc.tap(BotConfig.BTN_OKAY); delay(2000)
            _state.value = BotState.READING_RESULT
            return
        }
        delay(BotConfig.delayBattleCheck.toLong())
    }

    private suspend fun doReadResult(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        BotLogger.info("Memindai statistik loot dari layar Battle Result...")
        val ld = loot.scanLoot(ss)
        val result = FarmResult(matchNum, ld.gold, ld.elixir, ld.darkElixir)
        val sess = _session.value; sess.addResult(result); _session.value = sess
        BotLogger.rekap("Pertandingan #$matchNum -> G:+${"%,d".format(ld.gold)} E:+${"%,d".format(ld.elixir)} DE:+${ld.darkElixir}")

        // Tap return home, retry beberapa kali
        repeat(3) {
            acc.tap(BotConfig.BTN_RETURN_HOME)
            delay(800)
        }
        delay(BotConfig.randomDelay())
        BotLogger.info("Selesai serang. Counter saat ini: $matchNum")
        BotLogger.wait("Menunggu 3 detik di Home sebelum langkah selanjutnya...")
        delay(3000)
        _state.value = BotState.CHECKING_HOME
    }

    private suspend fun doReturnHome(acc: AccessibilityBot) {
        repeat(3) { acc.tap(BotConfig.BTN_RETURN_HOME); delay(800) }
        delay(3000); _state.value = BotState.CHECKING_HOME
    }

    private suspend fun doUpgradeWall(ss: android.graphics.Bitmap, acc: AccessibilityBot) {
        BotLogger.upgrade("Upgrade wall...")
        val wb = tmgr.findTemplate(ss, Template.WALL_BUILDER)
        if (wb.found) { acc.tap(wb.position); delay(1000) }
        val wi = tmgr.findTemplate(ss, Template.WALL_ITEM)
        if (wi.found) { acc.tap(wi.position); delay(500) }
        acc.tap(BotConfig.BTN_OKAY); delay(1000)
        _state.value = BotState.OPENING_ATTACK
    }

    private suspend fun doWaitTroops() {
        if (troopsWaitStart == 0L) troopsWaitStart = System.currentTimeMillis()
        val elapsed = (System.currentTimeMillis() - troopsWaitStart) / 1000
        BotLogger.wait("Menunggu troops... ${elapsed}s")
        if (elapsed > BotConfig.waitTroopsSeconds) { troopsWaitStart = 0L; _state.value = BotState.OPENING_ATTACK }
        delay(10000)
    }

    override fun onDestroy() { scope.cancel(); loot.close(); instance = null; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CH, "Bot", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    private fun buildNotif() = NotificationCompat.Builder(this, CH)
        .setContentTitle("COC Bot").setContentText("Farming aktif")
        .setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build()
}
