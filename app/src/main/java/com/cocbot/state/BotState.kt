package com.cocbot.state

enum class BotState {
    IDLE,
    SANDBOX_ACTIVE,
    NETWORK_ISOLATED,
    DEPLOYING_SANDBOX,
    SIMULATING_BATTLE,
    PAUSED,
    ERROR
}

data class SandboxSession(
    val startTime: Long = System.currentTimeMillis(),
    var totalSimulations: Int = 0,
    var isRootActive: Boolean = false,
    var isNetworkIsolated: Boolean = false
)
