package com.cocbot.ai

import android.graphics.Bitmap
import android.util.Base64
import com.cocbot.BotConfig
import com.cocbot.BotLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiAttackPlan(
    val shouldAttack: Boolean,
    val estimatedStars: Int,
    val reason: String,
    val attackDirection: String, // "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    val funnelSlots: List<Int>,
    val mainArmySlots: List<Int>,
    val heroSlots: List<Int>,
    val spellSlots: List<Int>,
    val detectedArmySummary: String,
    val notes: String
)

object GeminiAttackEngine {

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun Bitmap.toBase64Jpeg(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 75, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    suspend fun analyzeBaseAndPlanAttack(
        bitmap: Bitmap?,
        goldLoot: Int = 0,
        elixirLoot: Int = 0,
        darkElixirLoot: Int = 0
    ): GeminiAttackPlan = withContext(Dispatchers.IO) {
        val apiKey = BotConfig.geminiApiKey.ifEmpty {
            BotLogger.warning("⚠️ Gemini API Key belum diisi! Menggunakan fallback AI heuristic strategy.")
            return@withContext fallbackPlan(goldLoot, elixirLoot, darkElixirLoot)
        }

        val promptText = """
            Anda adalah Super AI Master Strategi Clash of Clans (CoC).
            Analisis gambar layar pertempuran ini dengan cermat!
            
            1. Periksa LAYOUT DESA LAWAN (Posisi Town Hall, Eagle Artillery, Air Defenses, Inferno Towers, X-Bows, Clan Castle).
            2. Periksa BAR PASUKAN DI BAGIAN BWAH LAYAR (Slot 0, 1, 2, 3, 4, 5, 6, 7, 8, 9).
               Identifikasi jenis pasukan, Siege Machine, Heroes, dan Spell yang dibawa.
            3. Tentukan apakah desa ini layak diserang berdasarkan target loot:
               - Gold Terbaca: $goldLoot (Target Min: ${BotConfig.minGoldTarget})
               - Elixir Terbaca: $elixirLoot (Target Min: ${BotConfig.minElixirTarget})
               - Dark Elixir Terbaca: $darkElixirLoot (Target Min: ${BotConfig.minDarkElixirTarget})
               - Preset Strategi: ${BotConfig.aiStrategyPreset}
            4. Tentukan urutan eksekusi penyerangan berdasarkan indeks slot (0 sampai 9):
               - funnelSlots: Slot untuk pembuka jalan / Siege Machine (contoh: [0, 1])
               - mainArmySlots: Slot untuk pasukan utama (contoh: [1, 2, 3])
               - heroSlots: Slot untuk Heroes (King, Queen, Warden, RC) (contoh: [4, 5, 6, 7])
               - spellSlots: Slot untuk Spell (Rage, Freeze, Heal, Poison) (contoh: [8, 9])
               - attackDirection: "BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_LEFT", atau "TOP_RIGHT"
               - detectedArmySummary: Rincian singkat pasukan yang terlihat di barisan bawah

            Berikan output HANYA dalam format JSON valid tanpa markdown formatting sebagai berikut:
            {
              "shouldAttack": true,
              "estimatedStars": 3,
              "reason": "Menyerang dari BOTTOM_LEFT mendekati Eagle Artillery dan Air Defense untuk maksimalkan nilai Electro Dragon.",
              "attackDirection": "BOTTOM_LEFT",
              "funnelSlots": [0],
              "mainArmySlots": [1, 2, 3],
              "heroSlots": [4, 5, 6, 7],
              "spellSlots": [8, 9],
              "detectedArmySummary": "10x Electro Dragon, 8x Balloon, King, Queen, Warden, RC, 3x Rage, 3x Freeze",
              "notes": "Luncurkan Electro Dragon & Balloons menyebar sepanjang garis bawah."
            }
        """.trimIndent()

        try {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Text part
            partsArray.put(JSONObject().put("text", promptText))

            // Image part if bitmap provided
            if (bitmap != null) {
                val base64Img = bitmap.toBase64Jpeg()
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Img)
                }
                partsArray.put(JSONObject().put("inlineData", inlineData))
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            BotLogger.info("🤖 [GEMINI AI VISION] Menganalisis layout desa & barisan pasukan di layar...")

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    BotLogger.error("❌ Gemini API Error (${response.code}): $respStr")
                    return@withContext fallbackPlan(goldLoot, elixirLoot, darkElixirLoot)
                }

                val jsonResp = JSONObject(respStr)
                val candidates = jsonResp.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candContent = candidates.getJSONObject(0).optJSONObject("content")
                    val candParts = candContent?.optJSONArray("parts")
                    if (candParts != null && candParts.length() > 0) {
                        val aiText = candParts.getJSONObject(0).optString("text")
                        BotLogger.info("✅ [GEMINI AI VISION DECISION] $aiText")
                        return@withContext parseAiPlan(aiText, goldLoot, elixirLoot)
                    }
                }
                fallbackPlan(goldLoot, elixirLoot, darkElixirLoot)
            }
        } catch (e: Exception) {
            BotLogger.error("❌ Gemini Vision Exception: ${e.message}")
            fallbackPlan(goldLoot, elixirLoot, darkElixirLoot)
        }
    }

    private fun parseAiPlan(jsonStr: String, gold: Int, elixir: Int): GeminiAttackPlan {
        return try {
            val cleanJson = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(cleanJson)

            fun parseSlotList(key: String, defaultList: List<Int>): List<Int> {
                val arr = obj.optJSONArray(key) ?: return defaultList
                val list = mutableListOf<Int>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getInt(i))
                }
                return if (list.isEmpty()) defaultList else list
            }

            GeminiAttackPlan(
                shouldAttack = obj.optBoolean("shouldAttack", true),
                estimatedStars = obj.optInt("estimatedStars", 2),
                reason = obj.optString("reason", "Disetujui oleh Gemini AI Vision"),
                attackDirection = obj.optString("attackDirection", "BOTTOM_LEFT"),
                funnelSlots = parseSlotList("funnelSlots", listOf(0, 1)),
                mainArmySlots = parseSlotList("mainArmySlots", listOf(1, 2, 3)),
                heroSlots = parseSlotList("heroSlots", listOf(4, 5, 6, 7)),
                spellSlots = parseSlotList("spellSlots", listOf(8, 9)),
                detectedArmySummary = obj.optString("detectedArmySummary", "Pasukan terdeteksi di slot 0-9"),
                notes = obj.optString("notes", "Auto AI attack execution")
            )
        } catch (e: Exception) {
            fallbackPlan(gold, elixir, 0)
        }
    }

    private fun fallbackPlan(gold: Int, elixir: Int, darkElixir: Int): GeminiAttackPlan {
        val meetsLoot = (gold >= BotConfig.minGoldTarget || elixir >= BotConfig.minElixirTarget)
        return GeminiAttackPlan(
            shouldAttack = meetsLoot,
            estimatedStars = if (meetsLoot) 2 else 0,
            reason = if (meetsLoot) "Loot memenuhi target (Gold: $gold, Elixir: $elixir)" else "Loot di bawah batas minimum",
            attackDirection = "BOTTOM_LEFT",
            funnelSlots = listOf(0, 1),
            mainArmySlots = listOf(1, 2, 3),
            heroSlots = listOf(4, 5, 6, 7),
            spellSlots = listOf(8, 9),
            detectedArmySummary = "Standard Army (Troops, Heroes & Spells di Slot 0-9)",
            notes = "Rule-based AI Fallback Plan"
        )
    }
}
