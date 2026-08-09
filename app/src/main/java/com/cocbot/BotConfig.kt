package com.cocbot

import android.content.res.Resources
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min

object BotConfig {

    // Gemini AI Configuration
    var geminiApiKey: String = ""
    var autoAiAttackOnFound: Boolean = true
    var autoNextIfLootLow: Boolean = true

    // Minimum Loot Target Criteria
    var minGoldTarget: Int = 200000
    var minElixirTarget: Int = 200000
    var minDarkElixirTarget: Int = 1000

    var delayBetweenWavesMs: Long = 400L
    var endBattleWaitSec: Int = 120

    // Dynamic Resolution Helper
    fun getScreenLandscapeWidth(): Float {
        val dm = Resources.getSystem().displayMetrics
        return max(dm.widthPixels, dm.heightPixels).toFloat()
    }

    fun getScreenLandscapeHeight(): Float {
        val dm = Resources.getSystem().displayMetrics
        return min(dm.widthPixels, dm.heightPixels).toFloat()
    }

    fun getRelPoint(pctX: Float, pctY: Float): PointF {
        val w = getScreenLandscapeWidth()
        val h = getScreenLandscapeHeight()
        return PointF(w * pctX, h * pctY)
    }

    // Navigation Buttons (Percentage based)
    val BTN_HOME_ATTACK get() = getRelPoint(0.08f, 0.88f)
    val BTN_FIND_MATCH get() = getRelPoint(0.82f, 0.65f)
    val BTN_NEXT_BASE get() = getRelPoint(0.92f, 0.78f)
    val BTN_ATTACK_CONFIRM get() = getRelPoint(0.85f, 0.80f)
    val BTN_END_BATTLE get() = getRelPoint(0.08f, 0.78f)
    val BTN_OKAY get() = getRelPoint(0.50f, 0.70f)
    val BTN_RETURN_HOME get() = getRelPoint(0.10f, 0.88f)

    // Troop Slot Bar Buttons (Bottom Horizontal Bar)
    val SLOT_0 get() = getRelPoint(0.08f, 0.90f)
    val SLOT_1 get() = getRelPoint(0.14f, 0.90f)
    val SLOT_2 get() = getRelPoint(0.20f, 0.90f)
    val SLOT_3 get() = getRelPoint(0.26f, 0.90f)
    val SLOT_4 get() = getRelPoint(0.32f, 0.90f)
    val SLOT_5 get() = getRelPoint(0.38f, 0.90f)
    val SLOT_6 get() = getRelPoint(0.44f, 0.90f)
    val SLOT_7 get() = getRelPoint(0.50f, 0.90f)
    val SLOT_8 get() = getRelPoint(0.56f, 0.90f)
    val SLOT_9 get() = getRelPoint(0.62f, 0.90f)

    fun getSlotPoint(index: Int): PointF? {
        val pctX = 0.08f + (index * 0.06f)
        return if (index in 0..12) getRelPoint(pctX, 0.90f) else null
    }

    // Troop Bar Horizontal Scrolling Coordinates
    val TROOP_BAR_SWIPE_RIGHT_TO_LEFT_START get() = getRelPoint(0.70f, 0.90f)
    val TROOP_BAR_SWIPE_RIGHT_TO_LEFT_END get() = getRelPoint(0.20f, 0.90f)

    val TROOP_BAR_SWIPE_LEFT_TO_RIGHT_START get() = getRelPoint(0.20f, 0.90f)
    val TROOP_BAR_SWIPE_LEFT_TO_RIGHT_END get() = getRelPoint(0.70f, 0.90f)

    // Deployment Lines (In map arena)
    val DEPLOY_BOTTOM_LEFT_START get() = getRelPoint(0.20f, 0.65f)
    val DEPLOY_BOTTOM_LEFT_END get() = getRelPoint(0.50f, 0.82f)

    val DEPLOY_BOTTOM_RIGHT_START get() = getRelPoint(0.50f, 0.82f)
    val DEPLOY_BOTTOM_RIGHT_END get() = getRelPoint(0.80f, 0.65f)

    val DEPLOY_TOP_LEFT_START get() = getRelPoint(0.20f, 0.65f)
    val DEPLOY_TOP_LEFT_END get() = getRelPoint(0.50f, 0.25f)

    val DEPLOY_TOP_RIGHT_START get() = getRelPoint(0.50f, 0.25f)
    val DEPLOY_TOP_RIGHT_END get() = getRelPoint(0.80f, 0.65f)
}

