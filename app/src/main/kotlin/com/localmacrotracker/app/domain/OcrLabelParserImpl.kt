package com.localmacrotracker.app.domain

import android.util.Log
import com.localmacrotracker.app.data.network.OcrLabelParser
import com.localmacrotracker.app.data.network.ParsedNutritionDraft
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OcrLabelParser"

@Singleton
class OcrLabelParserImpl @Inject constructor() : OcrLabelParser {

    private data class MacroResult(
        var calories: Double? = null,
        var protein: Double? = null,
        var carbs: Double? = null,
        var fat: Double? = null
    ) {
        fun isComplete() = calories != null && protein != null && carbs != null && fat != null
    }

    override fun parse(ocrText: String): ParsedNutritionDraft {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = MacroResult()
        var servingText: String? = null
        var servingWeightGrams: Double? = null
        val nutrients = mutableMapOf<String, Double>()

        // ── First pass: structured line-by-line matching ───────────────────
        for (i in lines.indices) {
            val raw = lines[i]
            val line = raw.lowercase()
            val nextRaw = lines.getOrNull(i + 1)

            // Serving size — "Serving Size 2/3 cup (55g)", "Serv. size 30 g", "Per serving"
            if (servingText == null && isServingSizeLine(line)) {
                servingText = raw
                // Capture grams when present (often in parentheses) for later scaling.
                Regex("""(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
                    .find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?.let { servingWeightGrams = it }
            }

            // Calories — value may sit on the same line, the next line, or (big-font
            // 2020-style labels) the line just before the "Calories" label.
            if (result.calories == null && isCalorieLine(line)) {
                result.calories = extractNumber(raw)
                    ?: nextRaw?.let { extractStandaloneNumber(it) }
                    ?: lines.getOrNull(i - 1)?.let { extractStandaloneNumber(it) }
            }

            // Total Fat — "Total Fat 12g", "Fat 12g", "12g Fat"
            if (result.fat == null && isTotalFatLine(line)) {
                result.fat = extractNumber(raw)
                    ?: nextRaw?.let { extractStandaloneNumber(it) }
            }

            // Total Carbohydrate — "Total Carbohydrate", "Carbs", "Total Carbs"
            if (result.carbs == null && isTotalCarbsLine(line)) {
                result.carbs = extractNumber(raw)
                    ?: nextRaw?.let { extractStandaloneNumber(it) }
            }

            // Protein
            if (result.protein == null && isProteinLine(line)) {
                result.protein = extractNumber(raw)
                    ?: nextRaw?.let { extractStandaloneNumber(it) }
            }

            // Dietary Fiber
            if (line.startsWith("dietary fiber") || line.startsWith("fiber")) {
                extractNumber(raw)?.let { nutrients["FIBTG"] = it }
            }

            // Sugars
            if (line.startsWith("total sugars") || line.startsWith("sugars") || line == "sugar") {
                extractNumber(raw)?.let { nutrients["SUGAR"] = it }
            }

            // Sodium
            if (line.startsWith("sodium")) {
                extractNumber(raw)?.let { nutrients["NA"] = it }
            }

            // Cholesterol
            if (line.startsWith("cholesterol")) {
                extractNumber(raw)?.let { nutrients["CHOLE"] = it }
            }
        }

        // ── Second pass: broader substring scan for still-missing macros ──
        if (!result.isComplete()) {
            for (raw in lines) {
                val line = raw.lowercase()
                if (result.calories == null &&
                    (line.contains("calorie") || line.contains(" cal ") || line.endsWith(" cal")) &&
                    !line.contains("from fat")
                ) {
                    result.calories = extractNumber(raw)
                }
                if (result.fat == null && line.contains("total fat") &&
                    !line.contains("saturated") && !line.contains("trans")
                ) {
                    result.fat = extractNumber(raw)
                }
                if (result.carbs == null &&
                    (line.contains("total carb") || line.contains("carbohydrate"))
                ) {
                    result.carbs = extractNumber(raw)
                }
                if (result.protein == null && line.contains("protein")) {
                    result.protein = extractNumber(raw)
                }
            }
        }

        Log.d(TAG, "OCR parsed: cal=${result.calories}, pro=${result.protein}, carb=${result.carbs}, fat=${result.fat}")

        return ParsedNutritionDraft(
            suggestedName = null,
            servingText = servingText,
            servingWeightGrams = servingWeightGrams,
            calories = result.calories,
            proteinGrams = result.protein,
            carbsGrams = result.carbs,
            fatGrams = result.fat,
            nutrients = nutrients,
            rawOcrText = ocrText
        )
    }

    // ── Line classifiers ───────────────────────────────────────────────────

    /** Matches serving-size lines across common label wordings. */
    private fun isServingSizeLine(line: String): Boolean =
        line.contains("serving size") || line.contains("serv. size") ||
            line.contains("serv size") || line.contains("serving:") ||
            line.startsWith("per serving") || line.startsWith("serving ") ||
            (line.startsWith("serving") && line.any { it.isDigit() })

    /** Matches calorie lines; excludes "Calories from Fat" and per-container counts. */
    private fun isCalorieLine(line: String): Boolean {
        if (line.contains("from fat")) return false
        if (line.contains("per container") || line.contains("servings per")) return false
        return line.contains("calories") || line.contains("calorie") ||
            line.startsWith("cal ") || line.endsWith(" cal") || line == "cal"
    }

    /**
     * Matches total-fat lines.
     * Excludes: "Saturated Fat", "Trans Fat", "Calories from Fat", "Polyunsaturated Fat", etc.
     */
    private fun isTotalFatLine(line: String): Boolean {
        if (line.contains("saturated") || line.contains("trans") ||
            line.contains("from") || line.contains("poly") || line.contains("mono")
        ) return false
        return line.startsWith("total fat") || line.startsWith("fat total") ||
            line == "fat" || (line.startsWith("fat") && line.length < 12)
    }

    /** Matches total carb lines. */
    private fun isTotalCarbsLine(line: String) =
        line.startsWith("total carb") || line.startsWith("carbohydrate") ||
            line.startsWith("carbs") || line == "carb"

    /** Matches protein lines. */
    private fun isProteinLine(line: String) =
        line.startsWith("protein")

    // ── Number extractors ─────────────────────────────────────────────────

    /**
     * Extracts a numeric value from a mixed label+number line.
     *
     * Handles all standard label formats:
     *   "Calories 250"     → 250
     *   "Calories: 250"    → 250
     *   "250 Calories"     → 250  (number-first)
     *   "Total Fat 12g"    → 12
     *   "12g Total Fat"    → 12   (number-first with unit)
     *   "Sodium 480mg"     → 480
     *   "Protein 25g"      → 25
     */
    private fun extractNumber(line: String): Double? {
        val s = line.trim()

        // Strategy 1: number at end with optional unit
        //   "Total Fat 12g", "Calories 250", "Protein: 25g"
        val trailingRe = Regex(
            """(\d+(?:\.\d+)?)\s*(?:g|mg|mcg|kcal|cal)?\s*$""",
            RegexOption.IGNORE_CASE
        )
        trailingRe.find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        // Strategy 2: number at start followed by label text
        //   "250 Calories", "12g Total Fat"
        val leadingRe = Regex(
            """^(\d+(?:\.\d+)?)\s*(?:g|mg|mcg|kcal|cal)?\s+\S""",
            RegexOption.IGNORE_CASE
        )
        leadingRe.find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

        return null
    }

    /**
     * Extracts a number from a line that contains ONLY a number (with optional unit).
     * Used when the label and value are on separate lines.
     *   "250", "12g", "25 g", "480mg" → respective doubles
     */
    private fun extractStandaloneNumber(line: String): Double? =
        Regex(
            """^\s*(\d+(?:\.\d+)?)\s*(?:g|mg|mcg|kcal|cal)?\s*$""",
            RegexOption.IGNORE_CASE
        ).find(line)?.groupValues?.get(1)?.toDoubleOrNull()
}
