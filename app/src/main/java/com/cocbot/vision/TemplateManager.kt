package com.cocbot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log

enum class Template(val fileName: String) {
    HOME_SCREEN("home_screen.png"),
    BTN_ATTACK("attack.png"),
    BTN_FIND_MATCH("find_match.png"),
    BTN_NEXT("next.png"),
    BTN_ATTACK_CONFIRM("attack2.png"),
    BTN_END_BATTLE("end_battle.png"),
    BTN_RETURN_HOME("returnhome.png"),
    BTN_OKAY("okay.png"),
    BTN_CANCEL("cancel.png"),
    BTN_CLOSE_X("x_close.png"),
    BATTLE_RESULT("battle_result.png"),
    SEARCHING("searching.png"),
    RELOAD("reload.png"),
    TRY_AGAIN("try_again.png"),
    STAR_BONUS("star_bonus.png"),
    GOLD_COLLECTOR("gold_col.png"),
    ELIXIR_COLLECTOR("elixir_col.png"),
    DARK_COLLECTOR("dark_col.png"),
    WALL_BUILDER("wall_builder.png"),
    WALL_ITEM("wall_item.png"),
}

data class MatchResult(
    val found: Boolean,
    val position: PointF = PointF(0f, 0f),
    val confidence: Double = 0.0
)

class TemplateManager(private val context: Context) {
    private val TAG = "TemplateManager"
    private val cache = mutableMapOf<Template, Bitmap>()

    private fun load(template: Template): Bitmap? {
        cache[template]?.let { return it }
        return try {
            val bmp = context.assets.open("templates/${template.fileName}")
                .use { android.graphics.BitmapFactory.decodeStream(it) }
            cache[template] = bmp
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Gagal load: ${template.fileName}")
            null
        }
    }

    fun findTemplate(screenshot: Bitmap, template: Template, threshold: Double = 0.80): MatchResult {
        val tmpl = load(template) ?: return MatchResult(false)
        val sw = screenshot.width; val sh = screenshot.height
        val tw = tmpl.width; val th = tmpl.height
        if (tw > sw || th > sh) return MatchResult(false)

        val tmplPixels = IntArray(tw * th)
        tmpl.getPixels(tmplPixels, 0, tw, 0, 0, tw, th)

        var bestScore = 0.0; var bestX = 0; var bestY = 0
        val step = 4

        for (y in 0..(sh - th) step step) {
            for (x in 0..(sw - tw) step step) {
                val score = calcScore(screenshot, tmplPixels, x, y, tw, th)
                if (score > bestScore) { bestScore = score; bestX = x; bestY = y }
            }
        }

        return if (bestScore >= threshold) {
            Log.d(TAG, "[SCAN] ${template.fileName} terdeteksi (${"%.1f".format(bestScore * 100)}%)")
            MatchResult(true, PointF((bestX + tw/2).toFloat(), (bestY + th/2).toFloat()), bestScore)
        } else MatchResult(false)
    }

    private fun calcScore(ss: Bitmap, tmpl: IntArray, ox: Int, oy: Int, tw: Int, th: Int): Double {
        var match = 0; var total = 0; val step = 4
        for (ty in 0 until th step step) {
            for (tx in 0 until tw step step) {
                val tp = tmpl[ty * tw + tx]; val sp = ss.getPixel(ox + tx, oy + ty)
                val d = Math.abs(android.graphics.Color.red(tp) - android.graphics.Color.red(sp)) +
                        Math.abs(android.graphics.Color.green(tp) - android.graphics.Color.green(sp)) +
                        Math.abs(android.graphics.Color.blue(tp) - android.graphics.Color.blue(sp))
                if (d < 60) match++; total++
            }
        }
        return if (total == 0) 0.0 else match.toDouble() / total
    }

    fun isVisible(ss: Bitmap, t: Template, threshold: Double = 0.80) = findTemplate(ss, t, threshold).found

    fun findAny(ss: Bitmap, vararg templates: Template): Pair<Template?, MatchResult> {
        for (t in templates) { val r = findTemplate(ss, t); if (r.found) return Pair(t, r) }
        return Pair(null, MatchResult(false))
    }

    fun clearCache() { cache.values.forEach { it.recycle() }; cache.clear() }
}
