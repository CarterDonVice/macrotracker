package com.localmacrotracker.app.domain

import org.junit.Assert.*
import org.junit.Test

class OcrLabelParserTest {

    private val parser = OcrLabelParserImpl()

    @Test
    fun `parse extracts calories from standard label`() {
        val ocr = """
            Nutrition Facts
            Serving Size 1 cup (240g)
            Calories 250
            Total Fat 12g
            Total Carbohydrate 31g
            Protein 8g
        """.trimIndent()
        val result = parser.parse(ocr)
        assertEquals(250.0, result.calories!!, 0.001)
        assertEquals(12.0, result.fatGrams!!, 0.001)
        assertEquals(31.0, result.carbsGrams!!, 0.001)
        assertEquals(8.0, result.proteinGrams!!, 0.001)
    }

    @Test
    fun `parse extracts serving weight from label`() {
        val ocr = """
            Serving Size 2 cookies (28g)
            Calories 140
            Total Fat 7g
            Total Carbohydrate 18g
            Protein 2g
        """.trimIndent()
        val result = parser.parse(ocr)
        assertEquals(28.0, result.servingWeightGrams!!, 0.001)
        assertEquals(140.0, result.calories!!, 0.001)
    }

    @Test
    fun `parse extracts fiber and sodium`() {
        val ocr = """
            Calories 200
            Total Fat 5g
            Total Carbohydrate 35g
            Dietary Fiber 4g
            Sodium 480mg
            Protein 6g
        """.trimIndent()
        val result = parser.parse(ocr)
        assertEquals(4.0, result.nutrients["FIBTG"]!!, 0.001)
        assertEquals(480.0, result.nutrients["NA"]!!, 0.001)
    }

    @Test
    fun `parse returns nulls for missing fields`() {
        val ocr = "Nutrition Facts"
        val result = parser.parse(ocr)
        assertNull(result.calories)
        assertNull(result.proteinGrams)
        assertNull(result.carbsGrams)
        assertNull(result.fatGrams)
    }
}
