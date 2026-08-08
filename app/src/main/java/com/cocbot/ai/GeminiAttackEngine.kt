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
    val deployFunnelFirst: Boolean,
    val heroDeploymentDelaySec: Int,
    val spellPositions: List<String>,
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
            Analisis gambar desa lawan ini dan berikan rencana serangan otomatis.
            
            Loot Terbaca:
            - Gold: $goldLoot
            - Elixir: $elixirLoot
            - Dark Elixir: $darkElixirLoot
            - Preferred Strategy: ${BotConfig.aiStrategyPreset}
            - Min Gold Target: ${BotConfig.minGoldTarget}
            - Min Elixir Target: ${BotConfig.minElixirTarget}

            Berikan output HANYA dalam format JSON valid tanpa markdown formatting sebagai berikut:
            {
              "shouldAttack": true/false,
              "estimatedStars": 1 hingga 3,
              "reason": "Alasan singkat analisis layout dan loot",
              "attackDirection": "BOTTOM_LEFT" atau "BOTTOM_RIGHT" atau "TOP_LEFT" atau "TOP_RIGHT",
              "deployFunnelFirst": true/false,
              "heroDeploymentDelaySec": 3,
              "spellPositions": ["Core Rage", "Freeze Air Defense"],
              "notes": "Rekomendasi khusus"
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

            BotLogger.info("🤖 [GEMINI AI VISION] Mengirim tangkapan layar desa ke Gemini 3.5 Flash...")

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
                        BotLogger.info("✅ [GEMINI AI RESPONSE] $aiText")
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
            GeminiAttackPlan(
                shouldAttack = obj.optBoolean("shouldAttack", true),
                estimatedStars = obj.optInt("estimatedStars", 2),
                reason = obj.optString("reason", "Disetujui oleh Gemini AI Vision"),
                attackDirection = obj.optString("attackDirection", "BOTTOM_LEFT"),
                deployFunnelFirst = obj.optBoolean("deployFunnelFirst", true),
                heroDeploymentDelaySec = obj.optInt("heroDeploymentDelaySec", 3),
                spellPositions = emptyList(),
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
            deployFunnelFirst = true,
            heroDeploymentDelaySec = 3,
            spellPositions = listOf("Core Rage"),
            notes = "Rule-based AI Fallback Plan"
        )
    }
}
