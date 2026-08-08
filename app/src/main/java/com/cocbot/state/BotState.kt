package com.cocbot.state

enum class BotState {
    IDLE,
    SCOUTING_BASE,
    ANALYZING_WITH_GEMINI,
    GEMINI_APPROVED,
    GEMINI_REJECTED,
    DEPLOYING_AI_ATTACK,
    IN_BATTLE,
    WAITING_HERO_ABILITIES,
    ENDING_BATTLE,
    PAUSED,
    ERROR
}

data class BotSessionStats(
    val startTime: Long = System.currentTimeMillis(),
    var totalBasesScouted: Int = 0,
    var totalAttacksExecuted: Int = 0,
    var totalGoldLooted: Long = 0,
    var totalElixirLooted: Long = 0,
    var lastAiDecision: String = "Idle"
)
