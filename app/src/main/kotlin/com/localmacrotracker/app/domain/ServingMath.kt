package com.localmacrotracker.app.domain

import kotlin.math.roundToInt

/**
 * Deterministic serving math. All calculations done in app code, never in LLM.
 *
 * Base quantities from providers are stored per-serving (or per 100g when noted).
 * scaleMacros() applies a factor to get the actual consumed amount.
 */
object ServingMath {

    data class MacroSet(
        val calories: Double,
        val proteinGrams: Double,
        val carbsGrams: Double,
        val fatGrams: Double
    ) {
        fun scale(factor: Double) = MacroSet(
            calories = calories * factor,
            proteinGrams = proteinGrams * factor,
            carbsGrams = carbsGrams * factor,
            fatGrams = fatGrams * factor
        )

        fun roundedCalories(): Int = calories.roundToInt()
    }

    /**
     * Returns the scale factor to convert from the source serving to the user's requested amount.
     *
     * @param sourceWeightGrams weight of one source serving in grams (null if unknown)
     * @param requestedQuantity numeric quantity the user wants
     * @param requestedUnit unit the user specified
     * @return scale factor, or 1.0 if conversion is not possible
     */
    fun computePortionFactor(
        sourceWeightGrams: Double?,
        requestedQuantity: Double,
        requestedUnit: String
    ): Double {
        if (requestedQuantity <= 0.0) return 1.0
        val requestedGrams = toGrams(requestedQuantity, requestedUnit) ?: return requestedQuantity
        val sourceGrams = sourceWeightGrams ?: return requestedQuantity
        if (sourceGrams <= 0.0) return requestedQuantity
        return requestedGrams / sourceGrams
    }

    /**
     * Convert a quantity + unit to grams.
     * Returns null if the unit cannot be converted to grams (e.g., "servings").
     */
    fun toGrams(quantity: Double, unit: String): Double? {
        return when (unit.lowercase().trim()) {
            "g", "gram", "grams" -> quantity
            "kg", "kilogram", "kilograms" -> quantity * 1000.0
            "oz", "ounce", "ounces" -> quantity * 28.3495
            "lb", "lbs", "pound", "pounds" -> quantity * 453.592
            "mg", "milligram", "milligrams" -> quantity / 1000.0
            "ml", "milliliter", "milliliters", "mL" -> quantity // approx. 1g per ml for water-like foods
            "l", "liter", "liters" -> quantity * 1000.0
            "cup", "cups" -> quantity * 236.588
            "tbsp", "tablespoon", "tablespoons" -> quantity * 14.7868
            "tsp", "teaspoon", "teaspoons" -> quantity * 4.92892
            "fl oz", "fluid ounce", "fluid ounces" -> quantity * 29.5735
            else -> null // servings, pieces, items — scale by quantity directly
        }
    }

    /**
     * Parse fuzzy quantity text into a numeric value.
     * "half" → 0.5, "1/3" → 0.333, "about 2" → 2.0, etc.
     */
    fun parseFuzzyQuantity(text: String): Double? {
        val trimmed = text.trim().lowercase()
        return when {
            trimmed == "half" || trimmed == "a half" -> 0.5
            trimmed == "quarter" || trimmed == "a quarter" -> 0.25
            trimmed == "third" || trimmed == "a third" -> 1.0 / 3.0
            trimmed == "whole" || trimmed == "entire" || trimmed == "a" || trimmed == "an" -> 1.0
            trimmed == "double" -> 2.0
            trimmed == "triple" -> 3.0
            else -> {
                // Strip fuzzy prefixes
                val cleaned = trimmed
                    .removePrefix("about ")
                    .removePrefix("roughly ")
                    .removePrefix("approximately ")
                    .removePrefix("around ")
                    .removePrefix("~")
                    .trim()

                // Try fraction like "1/2", "2/3"
                val fractionMatch = Regex("""^(\d+)\s*/\s*(\d+)$""").find(cleaned)
                if (fractionMatch != null) {
                    val num = fractionMatch.groupValues[1].toDoubleOrNull() ?: return null
                    val den = fractionMatch.groupValues[2].toDoubleOrNull() ?: return null
                    if (den == 0.0) return null
                    return num / den
                }

                // Try mixed number "1 1/2"
                val mixedMatch = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)$""").find(cleaned)
                if (mixedMatch != null) {
                    val whole = mixedMatch.groupValues[1].toDoubleOrNull() ?: return null
                    val num = mixedMatch.groupValues[2].toDoubleOrNull() ?: return null
                    val den = mixedMatch.groupValues[3].toDoubleOrNull() ?: return null
                    if (den == 0.0) return null
                    return whole + num / den
                }

                cleaned.toDoubleOrNull()
            }
        }
    }

    /** Apply a portion factor to a macro set and return scaled values. */
    fun scaleMacros(base: MacroSet, factor: Double): MacroSet = base.scale(factor)

    /** Sum a list of macro sets (for daily totals or recipe totals). */
    fun sumMacros(macros: List<MacroSet>): MacroSet = macros.fold(
        MacroSet(0.0, 0.0, 0.0, 0.0)
    ) { acc, m ->
        MacroSet(
            calories = acc.calories + m.calories,
            proteinGrams = acc.proteinGrams + m.proteinGrams,
            carbsGrams = acc.carbsGrams + m.carbsGrams,
            fatGrams = acc.fatGrams + m.fatGrams
        )
    }
}
