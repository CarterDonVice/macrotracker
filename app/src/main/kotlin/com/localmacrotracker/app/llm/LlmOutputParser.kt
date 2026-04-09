package com.localmacrotracker.app.llm

import android.util.Log
import com.localmacrotracker.app.llm.model.CandidateSelection
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
 * Parses strict JSON returned by the local LLM.
 * If the model produces prose around the JSON, we attempt to extract the first
 * JSON object from the output before deserializing.
 */
object LlmOutputParser {

    fun parsePlannerOutput(raw: String): PlannerOutput? {
        return tryParse(raw) { lenientJson.decodeFromString<PlannerOutput>(it) }
    }

    fun parseCandidateSelection(raw: String): CandidateSelection? {
        return tryParse(raw) { lenientJson.decodeFromString<CandidateSelection>(it) }
    }

    fun parseRangeResult(raw: String): RangeResult? {
        return tryParse(raw) { lenientJson.decodeFromString<RangeResult>(it) }
    }

    private fun <T> tryParse(raw: String, block: (String) -> T): T? {
        val cleaned = extractFirstJsonObject(raw) ?: return null.also {
            Log.w(TAG, "No JSON object found in LLM output: ${raw.take(200)}")
        }
        return try {
            block(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}\nInput: ${cleaned.take(300)}")
            null
        }
    }

    /**
     * Extracts the first complete JSON object from a string that may contain prose.
     * Handles single-level nesting for our known schemas.
     */
    fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escape = false

        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            when {
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
