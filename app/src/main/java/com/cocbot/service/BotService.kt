package com.cocbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cocbot.*
import com.cocbot.root.RootShell
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

    private val _state = MutableStateFlow(BotState.IDLE)
    val state: StateFlow<BotState> = _state
    private val _session = MutableStateFlow(SandboxSession())
    val session: StateFlow<SandboxSession> = _session

    private var job: Job? = null
    private var paused = false
    private var battleStart = 0L

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
        _session.value = SandboxSession(isRootActive = RootShell.isRootAvailable)
        BotLogger.system("🎮 [SANDBOX ENGINE] Root Sandbox Engine Dimulai")
        if (BotConfig.autoBlockNetworkOnVisit) {
            enableNetworkIsolation()
        }
        if (BotConfig.revealTrapsAndTeslas) {
            RootShell.applyTrapRevealPatch()
        }
        job = scope.launch { loop() }
    }

    fun stopBot() {
        job?.cancel()
        _state.value = BotState.IDLE
        restoreNetwork()
        BotLogger.system("🎮 [SANDBOX ENGINE] Engine Dihentikan & Network Di-restore")
    }

    fun enableNetworkIsolation() {
        val ok = RootShell.blockCoCNetwork()
        BotConfig.isNetworkBlocked = ok
        if (ok) {
            _state.value = BotState.NETWORK_ISOLATED
            BotLogger.info("⚡ [SANDBOX MODE] Network Isolatat/Offline Active! Anda aman berlatih tanpa kehilangan pasukan.")
        } else {
            BotLogger.warning("⚠️ Network Isolation gagal! Pastikan izin Superuser (su) sudah diberikan.")
        }
    }

    fun restoreNetwork() {
        RootShell.restoreCoCNetwork()
        BotConfig.isNetworkBlocked = false
        _state.value = BotState.IDLE
    }

    fun pauseBot() {
        paused = true
        _state.value = BotState.PAUSED
        BotLogger.system("Engine Paused")
    }

    fun resumeBot() {
        paused = false
        BotLogger.system("Engine Resumed")
    }

    fun triggerSandboxDeployment() {
        scope.launch {
            val acc = AccessibilityBot.instance
            if (acc != null) {
                _state.value = BotState.DEPLOYING_SANDBOX
                BotLogger.info("🎮 [SANDBOX DEPLOY] Deploying Sandbox Army & Heroes...")
                acc.deploySandboxArmy(BotConfig.sandboxUnits)
                battleStart = System.currentTimeMillis()
                _state.value = BotState.SIMULATING_BATTLE
            } else {
                BotLogger.error("Accessibility Service belum aktif!")
            }
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
                BotState.NETWORK_ISOLATED -> {
                    // Watching for practice battle
                    delay(1000)
                }
                BotState.DEPLOYING_SANDBOX -> {
                    delay(500)
                }
                BotState.SIMULATING_BATTLE -> {
                    val elapsed = (System.currentTimeMillis() - battleStart) / 1000
                    if (elapsed >= BotConfig.sandboxEndBattleDelaySeconds) {
                        BotLogger.info("🎮 [SANDBOX AUTO-END] Durasi simulasi ${BotConfig.sandboxEndBattleDelaySeconds}s selesai!")
                        val acc = AccessibilityBot.instance
                        if (acc != null) {
                            acc.tap(BotConfig.BTN_END_BATTLE)
                            delay(1000)
                            acc.tap(BotConfig.BTN_OKAY)
                        }
                        _state.value = BotState.NETWORK_ISOLATED
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

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CH, "Sandbox Service", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("🎮 CoC Root Sandbox Engine")
            .setContentText("Status: ${if (BotConfig.isNetworkBlocked) "ISOLATED (OFFLINE SANDBOX)" else "READY"}")
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
