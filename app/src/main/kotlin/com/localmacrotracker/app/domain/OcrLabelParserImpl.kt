package com.localmacrotracker.app.domain

import android.util.Log
import com.localmacrotracker.app.data.network.OcrLabelParser
import com.localmacrotracker.app.data.network.ParsedNutritionDraft
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OcrLabelParser"

@Singleton
class OcrLabelParserImpl @Inject constructor() : OcrLabelParser {

    override fun parse(ocrText: String): ParsedNutritionDraft {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var calories: Double? = null
        var protein: Double? = null
        var carbs: Double? = null
        var fat: Double? = null
        var servingText: String? = null
        var servingWeightGrams: Double? = null
        val nutrients = mutableMapOf<String, Double>()

        for (i in lines.indices) {
            val line = lines[i].lowercase()

            // Serving size
            if (line.contains("serving size") || line.contains("serving:")) {
                servingText = lines[i]
                val weightMatch = Regex("""(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
                    .find(lines[i])
                servingWeightGrams = weightMatch?.groupValues?.get(1)?.toDoubleOrNull()
            }

            // Calories — "Calories 250" or "250" on line after "Calories"
            if (line.startsWith("calories") && !line.contains("fat") && !line.contains("from")) {
                calories = extractTrailingNumber(lines[i])
                    ?: lines.getOrNull(i + 1)?.toDoubleOrNull()
            }

            // Total Fat
            if ((line.startsWith("total fat") || line == "fat") && fat == null) {
                fat = extractTrailingNumber(lines[i])
            }

            // Total Carbohydrate
            if ((line.startsWith("total carb") || line.startsWith("carbohydrate")) && carbs == null) {
                carbs = extractTrailingNumber(lines[i])
            }

            // Protein
            if (line.startsWith("protein") && protein == null) {
                protein = extractTrailingNumber(lines[i])
            }

            // Dietary Fiber
            if (line.startsWith("dietary fiber") || line.startsWith("fiber")) {
                extractTrailingNumber(lines[i])?.let { nutrients["FIBTG"] = it }
            }

            // Sugars
            if (line.startsWith("total sugars") || line.startsWith("sugars")) {
                extractTrailingNumber(lines[i])?.let { nutrients["SUGAR"] = it }
            }

            // Sodium
            if (line.startsWith("sodium")) {
                extractTrailingNumber(lines[i])?.let { nutrients["NA"] = it }
            }

            // Cholesterol
            if (line.startsWith("cholesterol")) {
                extractTrailingNumber(lines[i])?.let { nutrients["CHOLE"] = it }
            }
        }

        Log.d(TAG, "OCR parsed: cal=$calories, pro=$protein, carb=$carbs, fat=$fat")

        return ParsedNutritionDraft(
            suggestedName = null,
            servingText = servingText,
            servingWeightGrams = servingWeightGrams,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            nutrients = nutrients,
            rawOcrText = ocrText
        )
    }

    private fun extractTrailingNumber(line: String): Double? {
        // "Total Fat 12g" → 12.0 | "Sodium 480mg" → 480.0 | "Calories  250" → 250.0
        val match = Regex("""(\d+(?:\.\d+)?)\s*(?:mcg|mg|g|kcal)?$""", RegexOption.IGNORE_CASE)
            .find(line.trim())
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
