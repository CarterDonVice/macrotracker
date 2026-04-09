package com.localmacrotracker.app.llm

import org.junit.Assert.*
import org.junit.Test

class LlmOutputParserTest {

    // --- extractFirstJsonObject ---

    @Test
    fun `extractFirstJsonObject extracts clean JSON`() {
        val input = """{"version": 1, "items": []}"""
        val result = LlmOutputParser.extractFirstJsonObject(input)
        assertEquals(input, result)
    }

    @Test
    fun `extractFirstJsonObject extracts JSON from prose wrapper`() {
        val input = """Here is the result: {"version": 1, "foo": "bar"} extra text"""
        val result = LlmOutputParser.extractFirstJsonObject(input)
        assertEquals("""{"version": 1, "foo": "bar"}""", result)
    }

    @Test
    fun `extractFirstJsonObject returns null when no JSON object`() {
        assertNull(LlmOutputParser.extractFirstJsonObject("no json here"))
        assertNull(LlmOutputParser.extractFirstJsonObject(""))
    }

    @Test
    fun `extractFirstJsonObject handles nested objects`() {
        val input = """{"outer": {"inner": 1}, "other": 2}"""
        val result = LlmOutputParser.extractFirstJsonObject(input)
        assertEquals(input, result)
    }

    @Test
    fun `extractFirstJsonObject handles strings containing braces`() {
        val input = """{"note": "this {has} braces", "value": 42}"""
        val result = LlmOutputParser.extractFirstJsonObject(input)
        assertEquals(input, result)
    }

    // --- parsePlannerOutput ---

    @Test
    fun `parsePlannerOutput parses valid planner JSON`() {
        val json = """
        {
          "version": 1,
          "entry_mode": "labeless_food",
          "raw_input": "medium red apple",
          "items": [
            {
              "item_index": 0,
              "raw_fragment": "medium red apple",
              "normalized_display_name": "Red Apple",
              "food_category": "generic_single_food",
              "search_query": "medium red apple",
              "source_order": ["saved_foods", "usda"],
              "should_decompose": false,
              "allow_estimate": true,
              "numeric_quantity": 1.0,
              "expected_exactness": "exact_if_found"
            }
          ]
        }
        """.trimIndent()
        val result = LlmOutputParser.parsePlannerOutput(json)
        assertNotNull(result)
        assertEquals(1, result!!.items.size)
        assertEquals("generic_single_food", result.items[0].foodCategory)
        assertEquals("medium red apple", result.items[0].searchQuery)
        assertFalse(result.items[0].shouldDecompose)
    }

    @Test
    fun `parsePlannerOutput returns null for invalid JSON`() {
        assertNull(LlmOutputParser.parsePlannerOutput("not json"))
        assertNull(LlmOutputParser.parsePlannerOutput("{incomplete"))
    }

    // --- parseCandidateSelection ---

    @Test
    fun `parseCandidateSelection parses valid JSON`() {
        val json = """
        {
          "version": 1,
          "item_index": 0,
          "selected_candidate_id": "usda_123",
          "selection_type": "exact",
          "requires_serving_math": false,
          "portion_factor": 1.0,
          "final_display_name": "Red Apple",
          "final_category": "generic_single_food",
          "confidence_reason_code": "api_exact",
          "should_prompt_manual_save": false
        }
        """.trimIndent()
        val result = LlmOutputParser.parseCandidateSelection(json)
        assertNotNull(result)
        assertEquals("usda_123", result!!.selectedCandidateId)
        assertEquals("exact", result.selectionType)
        assertEquals(1.0, result.portionFactor, 0.001)
    }

    @Test
    fun `parseCandidateSelection returns null for empty string`() {
        assertNull(LlmOutputParser.parseCandidateSelection(""))
    }

    // --- parseRangeResult ---

    @Test
    fun `parseRangeResult parses valid JSON`() {
        val json = """
        {
          "version": 1,
          "item_index": 0,
          "calories_min": 350,
          "calories_max": 430,
          "protein_min_g": 20,
          "protein_max_g": 26,
          "carbs_min_g": 30,
          "carbs_max_g": 42,
          "fat_min_g": 10,
          "fat_max_g": 18,
          "range_reason": "source_uncertain"
        }
        """.trimIndent()
        val result = LlmOutputParser.parseRangeResult(json)
        assertNotNull(result)
        assertEquals(350.0, result!!.caloriesMin, 0.001)
        assertEquals(430.0, result.caloriesMax, 0.001)
        assertEquals("source_uncertain", result.rangeReason)
    }
}
