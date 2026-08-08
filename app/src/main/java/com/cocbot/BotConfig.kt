package com.cocbot

import android.graphics.PointF

object BotConfig {

    // Koordinat landscape 1612x720
    val BTN_ATTACK = PointF(100f, 648f)
    val BTN_FIND_MATCH = PointF(281f, 528f)
    val BTN_NEXT = PointF(1427f, 500f)
    val BTN_ATTACK_CONFIRM = PointF(1320f, 642f)
    val BTN_END_BATTLE = PointF(159f, 544f)
    val BTN_RETURN_HOME = PointF(814f, 624f)
    val BTN_OKAY = PointF(760f, 500f)
    val BTN_CLOSE_X = PointF(1390f, 55f)

    // Deploy area
    val DEPLOY_TOP_START = PointF(300f, 75f)
    val DEPLOY_TOP_END = PointF(1300f, 75f)
    val DEPLOY_BOTTOM_START = PointF(300f, 560f)
    val DEPLOY_BOTTOM_END = PointF(1300f, 560f)
    val DEPLOY_LEFT_START = PointF(75f, 150f)
    val DEPLOY_LEFT_END = PointF(75f, 480f)
    val DEPLOY_RIGHT_START = PointF(1537f, 150f)
    val DEPLOY_RIGHT_END = PointF(1537f, 480f)

    // Loot filter
    var minGoldTarget = 300_000L
    var minElixirTarget = 300_000L
    var minDarkElixirTarget = 0L
    var useAnyResource = true
    var enableLootFilter = true
    var maxNextTaps = 8

    // Attack strategy
    var attackStrategy = AttackStrategy.ALL_SIDES
    var troopsPerSide = 5
    var sandboxEndBattleDelaySeconds = 25
    var autoSandboxOnVisit = true

    // Sandbox Units & Manual Level Configuration
    val sandboxUnits = mutableListOf(
        SandboxUnit("Barbarian", UnitType.TROOP, level = 10, count = 30, enabled = true, slotIndex = 0),
        SandboxUnit("Archer", UnitType.TROOP, level = 10, count = 30, enabled = true, slotIndex = 1),
        SandboxUnit("Giant", UnitType.TROOP, level = 10, count = 10, enabled = true, slotIndex = 2),
        SandboxUnit("Wall Breaker", UnitType.TROOP, level = 9, count = 6, enabled = true, slotIndex = 3),
        SandboxUnit("Wizard", UnitType.TROOP, level = 10, count = 12, enabled = true, slotIndex = 4),
        SandboxUnit("Dragon", UnitType.TROOP, level = 9, count = 4, enabled = true, slotIndex = 5),
        SandboxUnit("P.E.K.K.A", UnitType.TROOP, level = 9, count = 2, enabled = false, slotIndex = 6),
        SandboxUnit("Electro Dragon", UnitType.TROOP, level = 5, count = 2, enabled = false, slotIndex = 7),
        SandboxUnit("Barbarian King", UnitType.HERO, level = 85, count = 1, enabled = true, slotIndex = 8),
        SandboxUnit("Archer Queen", UnitType.HERO, level = 85, count = 1, enabled = true, slotIndex = 9),
        SandboxUnit("Grand Warden", UnitType.HERO, level = 60, count = 1, enabled = true, slotIndex = 10),
        SandboxUnit("Royal Champion", UnitType.HERO, level = 35, count = 1, enabled = true, slotIndex = 11),
        SandboxUnit("Rage Spell", UnitType.SPELL, level = 6, count = 3, enabled = true, slotIndex = 12),
        SandboxUnit("Freeze Spell", UnitType.SPELL, level = 7, count = 3, enabled = true, slotIndex = 13)
    )

    // Collector
    var autoCollect = true

    // Wall upgrade
    var autoWallUpgrade = false
    var maxGoldForWall = 4_000_000L
    var maxElixirForWall = 4_000_000L

    // Battle
    var autoEndBattle = false
    var waitBattleSeconds = 200
    var waitTroopsSeconds = 600

    // Timing
    var delayMenuLoad = 1500L
    var delayMatchLoad = 4000L
    var delayBattleCheck = 2000
    var delayMinMs = 400L
    var delayMaxMs = 1000L
    var maxRandomDelay = 5

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}

enum class AttackStrategy {
    ALL_SIDES,      // Deploy 4 sisi
    SMART_ZONE,     // Pilih zona terbaik
    TOP_BOTTOM,     // Atas bawah saja
    LEFT_RIGHT,     // Kiri kanan saja
    SANDBOX_ATTACK  // Sandbox attack mode (simulasi/latihan tanpa ending)
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

