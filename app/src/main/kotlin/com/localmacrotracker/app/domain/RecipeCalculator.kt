package com.localmacrotracker.app.domain

import com.localmacrotracker.app.data.db.entities.RecipeIngredientEntity

/**
 * Calculates total and per-serving recipe nutrition.
 * Deterministic math only — no LLM involvement.
 */
object RecipeCalculator {

    data class RecipeTotals(
        val totalCalories: Double,
        val totalProtein: Double,
        val totalCarbs: Double,
        val totalFat: Double,
        val perServingCalories: Double,
        val perServingProtein: Double,
        val perServingCarbs: Double,
        val perServingFat: Double,
        val servingsMade: Double
    )

    fun calculate(
        ingredients: List<RecipeIngredientEntity>,
        servingsMade: Double
    ): RecipeTotals {
        val totalCal = ingredients.sumOf { it.calories }
        val totalPro = ingredients.sumOf { it.protein }
        val totalCarb = ingredients.sumOf { it.carbs }
        val totalFat = ingredients.sumOf { it.fat }

        val divisor = if (servingsMade <= 0.0) 1.0 else servingsMade

        return RecipeTotals(
            totalCalories = totalCal,
            totalProtein = totalPro,
            totalCarbs = totalCarb,
            totalFat = totalFat,
            perServingCalories = totalCal / divisor,
            perServingProtein = totalPro / divisor,
            perServingCarbs = totalCarb / divisor,
            perServingFat = totalFat / divisor,
            servingsMade = servingsMade
        )
    }

    /**
     * Scale an ingredient's macros by the given quantity/unit relative to its source.
     */
    fun scaleIngredient(
        baseCalories: Double,
        baseProtein: Double,
        baseCarbs: Double,
        baseFat: Double,
        quantity: Double,
        unit: String,
        sourceWeightGrams: Double? = null
    ): ServingMath.MacroSet {
        val factor = ServingMath.computePortionFactor(sourceWeightGrams, quantity, unit)
        return ServingMath.scaleMacros(
            ServingMath.MacroSet(baseCalories, baseProtein, baseCarbs, baseFat),
            factor
        )
    }
}
