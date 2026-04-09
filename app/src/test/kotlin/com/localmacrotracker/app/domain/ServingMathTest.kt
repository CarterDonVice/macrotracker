package com.localmacrotracker.app.domain

import org.junit.Assert.*
import org.junit.Test

class ServingMathTest {

    @Test
    fun `toGrams returns correct value for grams`() {
        assertEquals(100.0, ServingMath.toGrams(100.0, "g"), 0.001)
        assertEquals(100.0, ServingMath.toGrams(100.0, "grams"), 0.001)
    }

    @Test
    fun `toGrams converts ounces correctly`() {
        val expected = 1 * 28.3495
        assertEquals(expected, ServingMath.toGrams(1.0, "oz")!!, 0.001)
    }

    @Test
    fun `toGrams converts pounds correctly`() {
        val expected = 1 * 453.592
        assertEquals(expected, ServingMath.toGrams(1.0, "lb")!!, 0.001)
    }

    @Test
    fun `toGrams converts cups correctly`() {
        val expected = 2 * 236.588
        assertEquals(expected, ServingMath.toGrams(2.0, "cups")!!, 0.01)
    }

    @Test
    fun `toGrams converts tablespoon correctly`() {
        val expected = 3 * 14.7868
        assertEquals(expected, ServingMath.toGrams(3.0, "tbsp")!!, 0.001)
    }

    @Test
    fun `toGrams converts teaspoon correctly`() {
        val expected = 2 * 4.92892
        assertEquals(expected, ServingMath.toGrams(2.0, "tsp")!!, 0.001)
    }

    @Test
    fun `toGrams returns null for servings unit`() {
        assertNull(ServingMath.toGrams(1.0, "serving"))
        assertNull(ServingMath.toGrams(1.0, "servings"))
        assertNull(ServingMath.toGrams(1.0, "piece"))
    }

    @Test
    fun `parseFuzzyQuantity parses half`() {
        assertEquals(0.5, ServingMath.parseFuzzyQuantity("half")!!, 0.001)
        assertEquals(0.5, ServingMath.parseFuzzyQuantity("a half")!!, 0.001)
    }

    @Test
    fun `parseFuzzyQuantity parses quarter`() {
        assertEquals(0.25, ServingMath.parseFuzzyQuantity("quarter")!!, 0.001)
    }

    @Test
    fun `parseFuzzyQuantity parses simple fraction`() {
        assertEquals(0.333, ServingMath.parseFuzzyQuantity("1/3")!!, 0.001)
        assertEquals(0.5, ServingMath.parseFuzzyQuantity("1/2")!!, 0.001)
        assertEquals(0.75, ServingMath.parseFuzzyQuantity("3/4")!!, 0.001)
    }

    @Test
    fun `parseFuzzyQuantity parses mixed number`() {
        assertEquals(1.5, ServingMath.parseFuzzyQuantity("1 1/2")!!, 0.001)
        assertEquals(2.333, ServingMath.parseFuzzyQuantity("2 1/3")!!, 0.001)
    }

    @Test
    fun `parseFuzzyQuantity strips fuzzy prefixes`() {
        assertEquals(2.0, ServingMath.parseFuzzyQuantity("about 2")!!, 0.001)
        assertEquals(3.0, ServingMath.parseFuzzyQuantity("roughly 3")!!, 0.001)
        assertEquals(1.5, ServingMath.parseFuzzyQuantity("approximately 1.5")!!, 0.001)
    }

    @Test
    fun `parseFuzzyQuantity parses plain number`() {
        assertEquals(1.0, ServingMath.parseFuzzyQuantity("1")!!, 0.001)
        assertEquals(2.5, ServingMath.parseFuzzyQuantity("2.5")!!, 0.001)
    }

    @Test
    fun `scaleMacros applies factor correctly`() {
        val base = ServingMath.MacroSet(200.0, 20.0, 30.0, 10.0)
        val scaled = ServingMath.scaleMacros(base, 0.5)
        assertEquals(100.0, scaled.calories, 0.001)
        assertEquals(10.0, scaled.proteinGrams, 0.001)
        assertEquals(15.0, scaled.carbsGrams, 0.001)
        assertEquals(5.0, scaled.fatGrams, 0.001)
    }

    @Test
    fun `sumMacros adds correctly`() {
        val m1 = ServingMath.MacroSet(100.0, 10.0, 20.0, 5.0)
        val m2 = ServingMath.MacroSet(150.0, 15.0, 25.0, 8.0)
        val sum = ServingMath.sumMacros(listOf(m1, m2))
        assertEquals(250.0, sum.calories, 0.001)
        assertEquals(25.0, sum.proteinGrams, 0.001)
        assertEquals(45.0, sum.carbsGrams, 0.001)
        assertEquals(13.0, sum.fatGrams, 0.001)
    }

    @Test
    fun `computePortionFactor scales grams correctly`() {
        // Source: 100g serving; User wants 50g → factor 0.5
        val factor = ServingMath.computePortionFactor(100.0, 50.0, "g")
        assertEquals(0.5, factor, 0.001)
    }

    @Test
    fun `computePortionFactor handles null source weight`() {
        // Falls back to numeric quantity directly
        val factor = ServingMath.computePortionFactor(null, 2.0, "serving")
        assertEquals(2.0, factor, 0.001)
    }
}
