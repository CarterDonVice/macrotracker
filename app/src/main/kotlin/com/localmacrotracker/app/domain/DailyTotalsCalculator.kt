package com.localmacrotracker.app.domain

import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity

/**
 * Calculates daily macro totals from a list of log entries.
 * If ALL entries are exact → exact totals.
 * If ANY entry is estimated → range totals.
 */
object DailyTotalsCalculator {

    data class ExactTotals(
        val calories: Double,
        val proteinGrams: Double,
        val carbsGrams: Double,
        val fatGrams: Double
    )

    data class RangeTotals(
        val caloriesMin: Double,
        val caloriesMax: Double,
        val proteinMin: Double,
        val proteinMax: Double,
        val carbsMin: Double,
        val carbsMax: Double,
        val fatMin: Double,
        val fatMax: Double
    )

    sealed class DailyTotals {
        data class Exact(val totals: ExactTotals) : DailyTotals()
        data class Range(val totals: RangeTotals) : DailyTotals()
        object Empty : DailyTotals()
    }

    fun calculate(entries: List<FoodLogEntryEntity>): DailyTotals {
        if (entries.isEmpty()) return DailyTotals.Empty

        val hasEstimated = entries.any { it.isEstimated }

        return if (!hasEstimated) {
            // All exact
            val calories = entries.sumOf { it.caloriesExact ?: 0.0 }
            val protein = entries.sumOf { it.proteinExact ?: 0.0 }
            val carbs = entries.sumOf { it.carbsExact ?: 0.0 }
            val fat = entries.sumOf { it.fatExact ?: 0.0 }
            DailyTotals.Exact(ExactTotals(calories, protein, carbs, fat))
        } else {
            // Mixed or all estimated — sum as ranges
            var calMin = 0.0; var calMax = 0.0
            var proMin = 0.0; var proMax = 0.0
            var carbMin = 0.0; var carbMax = 0.0
            var fatMin = 0.0; var fatMax = 0.0

            for (entry in entries) {
                if (entry.isEstimated) {
                    calMin += entry.caloriesMin ?: 0.0
                    calMax += entry.caloriesMax ?: 0.0
                    proMin += entry.proteinMin ?: 0.0
                    proMax += entry.proteinMax ?: 0.0
                    carbMin += entry.carbsMin ?: 0.0
                    carbMax += entry.carbsMax ?: 0.0
                    fatMin += entry.fatMin ?: 0.0
                    fatMax += entry.fatMax ?: 0.0
                } else {
                    // Exact entry contributes same value to min and max
                    val cal = entry.caloriesExact ?: 0.0
                    val pro = entry.proteinExact ?: 0.0
                    val carb = entry.carbsExact ?: 0.0
                    val fat = entry.fatExact ?: 0.0
                    calMin += cal; calMax += cal
                    proMin += pro; proMax += pro
                    carbMin += carb; carbMax += carb
                    fatMin += fat; fatMax += fat
                }
            }

            DailyTotals.Range(
                RangeTotals(calMin, calMax, proMin, proMax, carbMin, carbMax, fatMin, fatMax)
            )
        }
    }
}
