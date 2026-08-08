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
    val gold: Int = 0,
    val elixir: Int = 0,
    val darkElixir: Int = 0
)

class LootScanner {

    companion object {
        private const val TAG = "LootScanner"
        val REGION_LOOT = Rect(0, 60, 280, 220)

        private val recognizer by lazy {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        suspend fun scanLoot(screenshot: Bitmap): LootData = suspendCancellableCoroutine { cont ->
            try {
                val cropped = Bitmap.createBitmap(
                    screenshot,
                    REGION_LOOT.left.coerceAtLeast(0),
                    REGION_LOOT.top.coerceAtLeast(0),
                    REGION_LOOT.width().coerceAtMost(screenshot.width - REGION_LOOT.left),
                    REGION_LOOT.height().coerceAtMost(screenshot.height - REGION_LOOT.top)
                )

                val image = InputImage.fromBitmap(cropped, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        val loot = parseLootText(text)
                        if (cont.isActive) cont.resume(loot)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed: ${e.message}")
                        if (cont.isActive) cont.resume(LootData())
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Scan error: ${e.message}")
                if (cont.isActive) cont.resume(LootData())
            }
        }

        private fun parseLootText(text: String): LootData {
            val numbers = mutableListOf<Int>()
            val pattern = Regex("""[\d][\d\s.,]{2,}[\d]""")
            val matches = pattern.findAll(text)

            for (match in matches) {
                val clean = match.value
                    .replace(" ", "")
                    .replace(".", "")
                    .replace(",", "")
                    .trim()
                try {
                    val num = clean.toInt()
                    if (num in 100..99_999_999) {
                        numbers.add(num)
                    }
                } catch (e: Exception) {
                    // skip
                }
            }

            return LootData(
                gold = numbers.getOrElse(0) { 0 },
                elixir = numbers.getOrElse(1) { 0 },
                darkElixir = numbers.getOrElse(2) { 0 }
            )
        }
    }
}
