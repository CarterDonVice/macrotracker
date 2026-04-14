package com.localmacrotracker.app.llm

import android.util.Log
import com.localmacrotracker.app.llm.model.CandidateSelection
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import com.localmacrotracker.app.llm.model.PlannerOutput
import com.localmacrotracker.app.llm.model.RangeResult
import kotlinx.serialization.json.Json

private val TAG = "LlmOutputParser"

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Parses JSON returned by the local LLM.
 * Handles both JSON objects and JSON arrays; extracts the first valid structure
 * from output that may contain prose around the JSON.
 */
object LlmOutputParser {

    fun parseFoodItems(raw: String): List<ParsedFoodItem>? {
        return tryParseArray(raw) { lenientJson.decodeFromString<List<ParsedFoodItem>>(it) }
    }

    fun parsePlannerOutput(raw: String): PlannerOutput? {
        return tryParseObject(raw) { lenientJson.decodeFromString<PlannerOutput>(it) }
    }

    fun parseCandidateSelection(raw: String): CandidateSelection? {
        return tryParseObject(raw) { lenientJson.decodeFromString<CandidateSelection>(it) }
    }

    fun parseRangeResult(raw: String): RangeResult? {
        return tryParseObject(raw) { lenientJson.decodeFromString<RangeResult>(it) }
    }

    private fun <T> tryParseObject(raw: String, block: (String) -> T): T? {
        val cleaned = extractFirstJsonObject(raw) ?: return null.also {
            Log.w(TAG, "No JSON object found in LLM output: ${raw.take(200)}")
        }
        return try {
            block(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "JSON object parse failed: ${e.message}\nInput: ${cleaned.take(300)}")
            null
        }
    }

    private fun <T> tryParseArray(raw: String, block: (String) -> T): T? {
        val cleaned = extractFirstJsonArray(raw) ?: return null.also {
            Log.w(TAG, "No JSON array found in LLM output: ${raw.take(200)}")
        }
        return try {
            block(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "JSON array parse failed: ${e.message}\nInput: ${cleaned.take(300)}")
            null
        }
    }

    fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        return extractBalanced(text, start, '{', '}')
    }

    fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start == -1) return null
        return extractBalanced(text, start, '[', ']')
    }

    private fun extractBalanced(text: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            when {
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                !inString && c == open -> depth++
                !inString && c == close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
