package com.cocbot.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LootData(
    val gold: Long = 0,
    val elixir: Long = 0,
    val darkElixir: Long = 0
) {
    fun meetsTarget(minGold: Long, minElixir: Long, minDark: Long): Boolean {
        return gold >= minGold || elixir >= minElixir || darkElixir >= minDark
    }

    override fun toString() = "G: ${"%,d".format(gold)} | E: ${"%,d".format(elixir)} | DE: ${"%,d".format(darkElixir)}"
}

class LootScanner {

    companion object {
        private const val TAG = "LootScanner"

        // Koordinat area loot di layar scouting (landscape 1612x720)
        // "Rampasan Tersedia" muncul di kiri atas saat scouting
        val REGION_LOOT = Rect(0, 60, 280, 220)
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Scan loot dari screenshot layar scouting
     */
    suspend fun scanLoot(screenshot: Bitmap): LootData = suspendCancellableCoroutine { cont ->
        try {
            // Crop area loot
            val cropped = Bitmap.createBitmap(
                screenshot,
                REGION_LOOT.left,
                REGION_LOOT.top,
                REGION_LOOT.width(),
                REGION_LOOT.height()
            )

            val image = InputImage.fromBitmap(cropped, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    Log.d(TAG, "OCR result: $text")
                    val loot = parseLootText(text)
                    Log.d(TAG, "Parsed loot: $loot")
                    if (cont.isActive) cont.resume(loot)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    if (cont.isActive) cont.resume(LootData())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error", e)
            if (cont.isActive) cont.resume(LootData())
        }
    }

    /**
     * Parse teks OCR jadi angka loot
     * Format COC: "118 215" atau "118.215" atau "118,215"
     */
    private fun parseLootText(text: String): LootData {
        val numbers = mutableListOf<Long>()

        // Cari semua angka (termasuk yang ada spasi/titik/koma sebagai separator ribuan)
        val pattern = Regex("""[\d][\d\s.,]{2,}[\d]""")
        val matches = pattern.findAll(text)

        for (match in matches) {
            val clean = match.value
                .replace(" ", "")
                .replace(".", "")
                .replace(",", "")
                .trim()
            try {
                val num = clean.toLong()
                if (num in 100..99_999_999) {
                    numbers.add(num)
                }
            } catch (e: NumberFormatException) {
                // skip
            }
        }

        Log.d(TAG, "Numbers found: $numbers")

        // COC urutan: Gold, Elixir, Dark Elixir
        return LootData(
            gold = numbers.getOrElse(0) { 0 },
            elixir = numbers.getOrElse(1) { 0 },
            darkElixir = numbers.getOrElse(2) { 0 }
        )
    }

    fun close() {
        recognizer.close()
    }
}
