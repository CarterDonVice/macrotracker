package com.localmacrotracker.app.domain

import com.localmacrotracker.app.data.db.entities.RecipeIngredientEntity
import org.junit.Assert.*
import org.junit.Test

class RecipeCalculatorTest {

    private fun ingredient(cal: Double, pro: Double, carb: Double, fat: Double) =
        RecipeIngredientEntity(
            recipeId = 1,
            displayNameSnapshot = "Test",
            quantity = 1.0,
            unit = "serving",
            calories = cal,
            protein = pro,
            carbs = carb,
            fat = fat
        )

    @Test
    fun `calculate totals correctly for single ingredient`() {
        val ingredients = listOf(ingredient(300.0, 25.0, 40.0, 10.0))
        val result = RecipeCalculator.calculate(ingredients, servingsMade = 1.0)
        assertEquals(300.0, result.totalCalories, 0.001)
        assertEquals(300.0, result.perServingCalories, 0.001)
        assertEquals(25.0, result.perServingProtein, 0.001)
    }

    @Test
    fun `calculate divides by servings correctly`() {
        val ingredients = listOf(
            ingredient(600.0, 50.0, 80.0, 20.0),
            ingredient(600.0, 50.0, 80.0, 20.0)
        )
        val result = RecipeCalculator.calculate(ingredients, servingsMade = 4.0)
        assertEquals(1200.0, result.totalCalories, 0.001)
        assertEquals(300.0, result.perServingCalories, 0.001)
        assertEquals(25.0, result.perServingProtein, 0.001)
        assertEquals(40.0, result.perServingCarbs, 0.001)
        assertEquals(10.0, result.perServingFat, 0.001)
    }

    @Test
    fun `calculate handles zero servings without division by zero`() {
        val ingredients = listOf(ingredient(400.0, 30.0, 50.0, 15.0))
        val result = RecipeCalculator.calculate(ingredients, servingsMade = 0.0)
        // Falls back to divisor = 1.0
        assertEquals(400.0, result.perServingCalories, 0.001)
    }

    @Test
    fun `calculate empty ingredients list returns zeros`() {
        val result = RecipeCalculator.calculate(emptyList(), servingsMade = 4.0)
        assertEquals(0.0, result.totalCalories, 0.001)
        assertEquals(0.0, result.perServingCalories, 0.001)
    }

    @Test
    fun `scaleIngredient applies gram conversion`() {
        // 100g source serving, user requests 50g
        val scaled = RecipeCalculator.scaleIngredient(
            baseCalories = 200.0, baseProtein = 20.0, baseCarbs = 30.0, baseFat = 8.0,
            quantity = 50.0, unit = "g", sourceWeightGrams = 100.0
        )
        assertEquals(100.0, scaled.calories, 0.001)
        assertEquals(10.0, scaled.proteinGrams, 0.001)
        assertEquals(15.0, scaled.carbsGrams, 0.001)
        assertEquals(4.0, scaled.fatGrams, 0.001)
    }
}
