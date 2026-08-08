package com.cocbot

import android.graphics.PointF

object BotConfig {

    // Gemini AI Configuration
    var geminiApiKey: String = ""
    var aiStrategyPreset: String = "Spam Electro Dragon + Balloons" // Options: "Spam Electro Dragon + Balloons", "BARCH Dead Base Farmer", "Gowipe Ground Smash", "Custom AI Gemini Decided"
    var autoAiAttackOnFound: Boolean = true
    var autoNextIfLootLow: Boolean = true

    // Minimum Loot Target Criteria
    var minGoldTarget: Int = 200000
    var minElixirTarget: Int = 200000
    var minDarkElixirTarget: Int = 1000

    // Navigation Coordinates for Full Automation Loop
    val BTN_HOME_ATTACK = PointF(120f, 650f)
    val BTN_FIND_MATCH = PointF(1200f, 550f)
    val BTN_NEXT_BASE = PointF(1530f, 540f)
    val BTN_ATTACK_CONFIRM = PointF(1400f, 600f)
    val BTN_END_BATTLE = PointF(150f, 540f)
    val BTN_OKAY = PointF(800f, 500f)
    val BTN_RETURN_HOME = PointF(150f, 540f)

    // Deployment Lines
    val DEPLOY_BOTTOM_LEFT_START = PointF(300f, 500f)
    val DEPLOY_BOTTOM_LEFT_END = PointF(800f, 650f)

    val DEPLOY_BOTTOM_RIGHT_START = PointF(800f, 650f)
    val DEPLOY_BOTTOM_RIGHT_END = PointF(1300f, 500f)

    val DEPLOY_TOP_LEFT_START = PointF(300f, 500f)
    val DEPLOY_TOP_LEFT_END = PointF(800f, 100f)

    val DEPLOY_TOP_RIGHT_START = PointF(800f, 100f)
    val DEPLOY_TOP_RIGHT_END = PointF(1300f, 500f)

    // Troop Slot Buttons (Horizontal Bar at bottom of battle screen)
    val SLOT_0 = PointF(120f, 660f)
    val SLOT_1 = PointF(200f, 660f)
    val SLOT_2 = PointF(280f, 660f)
    val SLOT_3 = PointF(360f, 660f)
    val SLOT_4 = PointF(440f, 660f)
    val SLOT_5 = PointF(520f, 660f)
    val SLOT_6 = PointF(600f, 660f)
    val SLOT_7 = PointF(680f, 660f)
    val SLOT_8 = PointF(760f, 660f)
    val SLOT_9 = PointF(840f, 660f)

    var delayBetweenWavesMs: Long = 400L
    var endBattleWaitSec: Int = 120
}
