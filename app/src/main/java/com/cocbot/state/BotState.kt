package com.cocbot.state

enum class BotState {
    IDLE, CHECKING_HOME, COLLECTING,
    OPENING_ATTACK, FINDING_MATCH, SEARCHING,
    SCOUTING, DEPLOYING_TROOPS, WAITING_BATTLE,
    READING_RESULT, RETURNING_HOME,
    UPGRADING_WALL, WAITING_TROOPS,
    ERROR, PAUSED
}

data class FarmResult(
    val matchNumber: Int,
    val goldGained: Long,
    val elixirGained: Long,
    val darkElixirGained: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class FarmSession(
    val startTime: Long = System.currentTimeMillis(),
    var totalMatches: Int = 0,
    var totalGold: Long = 0,
    var totalElixir: Long = 0,
    var totalDarkElixir: Long = 0,
    val results: MutableList<FarmResult> = mutableListOf()
) {
    fun addResult(r: FarmResult) {
        results.add(r)
        totalMatches++
        totalGold += r.goldGained
        totalElixir += r.elixirGained
        totalDarkElixir += r.darkElixirGained
    }
}
