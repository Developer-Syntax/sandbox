package com.cocbot

import android.graphics.PointF

object BotConfig {

    // Sandbox Engine Configuration
    var isRootEnabled: Boolean = true
    var autoBlockNetworkOnVisit: Boolean = true
    var revealTrapsAndTeslas: Boolean = true
    var isNetworkBlocked: Boolean = false
    var sandboxEndBattleDelaySeconds: Int = 30

    // Sandbox Army & Hero Configuration
    val sandboxUnits = mutableListOf(
        SandboxUnit("Barbarian", UnitType.TROOP, level = 10, count = 30, enabled = true, slotIndex = 0),
        SandboxUnit("Archer", UnitType.TROOP, level = 10, count = 30, enabled = true, slotIndex = 1),
        SandboxUnit("Giant", UnitType.TROOP, level = 10, count = 10, enabled = true, slotIndex = 2),
        SandboxUnit("Wall Breaker", UnitType.TROOP, level = 9, count = 6, enabled = true, slotIndex = 3),
        SandboxUnit("Wizard", UnitType.TROOP, level = 10, count = 12, enabled = true, slotIndex = 4),
        SandboxUnit("Dragon", UnitType.TROOP, level = 9, count = 4, enabled = true, slotIndex = 5),
        SandboxUnit("P.E.K.K.A", UnitType.TROOP, level = 9, count = 2, enabled = true, slotIndex = 6),
        SandboxUnit("Electro Dragon", UnitType.TROOP, level = 5, count = 2, enabled = true, slotIndex = 7),
        SandboxUnit("Barbarian King", UnitType.HERO, level = 85, count = 1, enabled = true, slotIndex = 8),
        SandboxUnit("Archer Queen", UnitType.HERO, level = 85, count = 1, enabled = true, slotIndex = 9),
        SandboxUnit("Grand Warden", UnitType.HERO, level = 60, count = 1, enabled = true, slotIndex = 10),
        SandboxUnit("Royal Champion", UnitType.HERO, level = 35, count = 1, enabled = true, slotIndex = 11),
        SandboxUnit("Rage Spell", UnitType.SPELL, level = 6, count = 3, enabled = true, slotIndex = 12),
        SandboxUnit("Freeze Spell", UnitType.SPELL, level = 7, count = 3, enabled = true, slotIndex = 13)
    )

    // Touch Coordinates for Sandbox Deployment
    val BTN_END_BATTLE = PointF(159f, 544f)
    val BTN_OKAY = PointF(760f, 500f)

    val DEPLOY_TOP_START = PointF(300f, 75f)
    val DEPLOY_TOP_END = PointF(1300f, 75f)
    val DEPLOY_BOTTOM_START = PointF(300f, 560f)
    val DEPLOY_BOTTOM_END = PointF(1300f, 560f)
    val DEPLOY_LEFT_START = PointF(75f, 150f)
    val DEPLOY_LEFT_END = PointF(75f, 480f)
    val DEPLOY_RIGHT_START = PointF(1537f, 150f)
    val DEPLOY_RIGHT_END = PointF(1537f, 480f)

    var delayMinMs = 200L
    var delayMaxMs = 500L

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}

enum class UnitType {
    TROOP,
    HERO,
    SPELL
}

data class SandboxUnit(
    val name: String,
    val type: UnitType,
    var level: Int,
    var count: Int,
    var enabled: Boolean,
    var slotIndex: Int
)
