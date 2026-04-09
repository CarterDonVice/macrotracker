package com.localmacrotracker.app.domain

import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import org.junit.Assert.*
import org.junit.Test

class DailyTotalsCalculatorTest {

    private fun exactEntry(cal: Double, pro: Double, carb: Double, fat: Double) =
        FoodLogEntryEntity(
            logDate = "2025-01-01",
            mealSection = "BREAKFAST",
            displayNameSnapshot = "Test Food",
            quantity = 1.0,
            unit = "serving",
            caloriesExact = cal,
            proteinExact = pro,
            carbsExact = carb,
            fatExact = fat,
            isEstimated = false
        )

    private fun estimatedEntry(
        calMin: Double, calMax: Double,
        proMin: Double, proMax: Double,
        carbMin: Double, carbMax: Double,
        fatMin: Double, fatMax: Double
    ) = FoodLogEntryEntity(
        logDate = "2025-01-01",
        mealSection = "LUNCH",
        displayNameSnapshot = "Estimated Food",
        quantity = 1.0,
        unit = "serving",
        caloriesMin = calMin, caloriesMax = calMax,
        proteinMin = proMin, proteinMax = proMax,
        carbsMin = carbMin, carbsMax = carbMax,
        fatMin = fatMin, fatMax = fatMax,
        isEstimated = true
    )

    @Test
    fun `empty list returns Empty`() {
        val result = DailyTotalsCalculator.calculate(emptyList())
        assertTrue(result is DailyTotalsCalculator.DailyTotals.Empty)
    }

    @Test
    fun `all exact entries produce Exact totals`() {
        val entries = listOf(
            exactEntry(200.0, 20.0, 30.0, 8.0),
            exactEntry(300.0, 25.0, 40.0, 12.0)
        )
        val result = DailyTotalsCalculator.calculate(entries)
        assertTrue(result is DailyTotalsCalculator.DailyTotals.Exact)
        val totals = (result as DailyTotalsCalculator.DailyTotals.Exact).totals
        assertEquals(500.0, totals.calories, 0.001)
        assertEquals(45.0, totals.proteinGrams, 0.001)
        assertEquals(70.0, totals.carbsGrams, 0.001)
        assertEquals(20.0, totals.fatGrams, 0.001)
    }

    @Test
    fun `any estimated entry produces Range totals`() {
        val entries = listOf(
            exactEntry(200.0, 20.0, 30.0, 8.0),
            estimatedEntry(250.0, 350.0, 18.0, 25.0, 30.0, 40.0, 10.0, 15.0)
        )
        val result = DailyTotalsCalculator.calculate(entries)
        assertTrue(result is DailyTotalsCalculator.DailyTotals.Range)
        val r = (result as DailyTotalsCalculator.DailyTotals.Range).totals
        // exact 200 + estimated 250–350 = 450–550
        assertEquals(450.0, r.caloriesMin, 0.001)
        assertEquals(550.0, r.caloriesMax, 0.001)
        // exact 20 + estimated 18–25 = 38–45
        assertEquals(38.0, r.proteinMin, 0.001)
        assertEquals(45.0, r.proteinMax, 0.001)
    }

    @Test
    fun `all estimated entries produce Range totals`() {
        val entries = listOf(
            estimatedEntry(100.0, 120.0, 10.0, 12.0, 15.0, 18.0, 3.0, 5.0),
            estimatedEntry(200.0, 250.0, 20.0, 22.0, 25.0, 30.0, 8.0, 10.0)
        )
        val result = DailyTotalsCalculator.calculate(entries)
        assertTrue(result is DailyTotalsCalculator.DailyTotals.Range)
        val r = (result as DailyTotalsCalculator.DailyTotals.Range).totals
        assertEquals(300.0, r.caloriesMin, 0.001)
        assertEquals(370.0, r.caloriesMax, 0.001)
    }
}
