package com.localmacrotracker.app.llm

import android.content.Context
import android.util.Log
import com.localmacrotracker.app.data.prefs.AppPreferences
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClaudeEngine"
private const val CLAUDE_MODEL = "claude-haiku-4-5"
private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val MAX_TOKENS = 1024

@Singleton
class ClaudeInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val prefs: AppPreferences
) {
    private val promptTemplate: String by lazy {
        try {
            context.assets.open("prompts/search_planner_prompt.txt").bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load prompt asset", e)
            ""
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    sealed class ParseResult {
        object NoApiKey : ParseResult()
        object NetworkError : ParseResult()
        object ParseFailed : ParseResult()
        data class ParsedItems(val items: List<ParsedFoodItem>) : ParseResult()
    }

    suspend fun parseFoods(userInput: String): ParseResult = withContext(Dispatchers.IO) {
        val apiKey = prefs.claudeApiKey.first()
        if (apiKey.isBlank()) {
            Log.w(TAG, "Claude API key not configured")
            return@withContext ParseResult.NoApiKey
        }

        val prompt = promptTemplate.replace("{{USER_INPUT}}", userInput)
        Log.d(TAG, "parseFoods → Claude: ${userInput.take(200)}")

        val rawResponse = callClaudeApi(apiKey, prompt)
            ?: return@withContext ParseResult.NetworkError

        val textContent = try {
            val envelope = json.decodeFromString<ClaudeResponse>(rawResponse)
            envelope.content.firstOrNull { it.type == "text" }?.text
                ?: return@withContext ParseResult.NetworkError.also {
                    Log.e(TAG, "No text block in Claude response")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Claude envelope", e)
            return@withContext ParseResult.NetworkError
        }
        Log.d(TAG, "Claude text content: ${textContent.take(500)}")

        // Strip markdown fences
        val cleaned = textContent
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        // If the top-level token is an object, handle error/recipe/single-food cases
        val firstBrace = cleaned.indexOf('{')
        val firstBracket = cleaned.indexOf('[')
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            val obj = LlmOutputParser.extractFirstJsonObject(cleaned)
            if (obj != null) {
                if (obj.contains("\"error\"")) {
                    val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
                    Log.d(TAG, "Claude returned error type: $reason")
                    return@withContext ParseResult.ParsedItems(emptyList())
                }
                val result = LlmOutputParser.parseFoodItems("[$obj]")
                if (result != null) return@withContext ParseResult.ParsedItems(result)
            }
        }

        // Extract outermost JSON array and parse
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        val extracted = if (start != -1 && end > start) cleaned.substring(start, end + 1) else cleaned

        var result = LlmOutputParser.parseFoodItems(extracted)
        if (result == null) result = LlmOutputParser.parseFoodItems("[$extracted]")

        if (result == null) {
            Log.w(TAG, "All parse attempts failed. Claude text:\n$textContent")
            ParseResult.ParseFailed
        } else {
            Log.d(TAG, "parseFoods: ${result.size} item(s): ${result.map { it.foodName }}")
            ParseResult.ParsedItems(result)
        }
    }

    private suspend fun callClaudeApi(apiKey: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                ClaudeRequest(
                    model = CLAUDE_MODEL,
                    maxTokens = MAX_TOKENS,
                    messages = listOf(ClaudeMessage(role = "user", content = prompt))
                )
            )

            val request = Request.Builder()
                .url(CLAUDE_ENDPOINT)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Claude API ${response.code}: ${responseBody?.take(300)}")
                        null
                    } else responseBody
                }
            } catch (e: Exception) {
                Log.e(TAG, "Claude API call failed", e)
                null
            }
        }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val messages: List<ClaudeMessage>
    )

    @Serializable
    private data class ClaudeMessage(val role: String, val content: String)

    @Serializable
    private data class ClaudeResponse(val content: List<ClaudeContentBlock>)

    @Serializable
    private data class ClaudeContentBlock(val type: String, val text: String? = null)
}
