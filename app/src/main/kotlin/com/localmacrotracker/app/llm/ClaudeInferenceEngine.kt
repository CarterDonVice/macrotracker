package com.localmacrotracker.app.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.localmacrotracker.app.data.prefs.AppPreferences
import com.localmacrotracker.app.llm.model.CandidateSelection
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import com.localmacrotracker.app.llm.model.PlannerOutput
import com.localmacrotracker.app.llm.model.RangeResult
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
) : LocalInferenceEngine {

    override var status: ModelStatus = ModelStatus.READY
        private set

    private val parserPrompt: String by lazy {
        loadPromptAsset("prompts/search_planner_prompt.txt")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── LocalInferenceEngine ──────────────────────────────────────────────

    /** Claude is a cloud API — always ready, no model file to load. */
    override suspend fun loadModel(modelUri: Uri): Boolean = true

    /** Nothing to release for a cloud API. */
    override fun unloadModel() {}

    override suspend fun runFoodParser(userInput: String): List<ParsedFoodItem>? =
        withContext(Dispatchers.IO) {
            val apiKey = prefs.claudeApiKey.first()
            if (apiKey.isBlank()) {
                Log.e(TAG, "Claude API key is not set")
                return@withContext null
            }

            val prompt = parserPrompt.replace("{{USER_INPUT}}", userInput)
            Log.d(TAG, "runFoodParser → Claude: ${userInput.take(200)}")

            val rawResponse = callClaudeApi(apiKey, prompt) ?: return@withContext null
            Log.d(TAG, "Claude raw response: ${rawResponse.take(600)}")

            // Unwrap the Claude response envelope to get the text content block
            val textContent = try {
                val envelope = json.decodeFromString<ClaudeResponse>(rawResponse)
                envelope.content.firstOrNull { it.type == "text" }?.text
                    ?: return@withContext null.also { Log.e(TAG, "No text block in Claude response") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Claude envelope", e)
                return@withContext null
            }
            Log.d(TAG, "Claude text content: ${textContent.take(500)}")

            // Strip any markdown fences, then extract the outermost JSON array
            val cleaned = textContent
                .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            val extracted = if (start != -1 && end > start) cleaned.substring(start, end + 1) else cleaned
            Log.d(TAG, "runFoodParser PASS 1 extracted: ${extracted.take(400)}")

            // Pass 1: parse as JSON array
            var result = LlmOutputParser.parseFoodItems(extracted)

            // Pass 2: model may have returned a bare object — wrap in [] and retry
            if (result == null) {
                val wrapped = "[$extracted]"
                Log.d(TAG, "runFoodParser PASS 2 (wrapped): ${wrapped.take(400)}")
                result = LlmOutputParser.parseFoodItems(wrapped)
            }

            if (result == null) {
                Log.w(TAG, "All parse attempts failed. Claude text was:\n$textContent")
            } else {
                Log.d(TAG, "runFoodParser: ${result.size} item(s): ${result.map { it.foodName }}")
            }
            result
        }

    // Legacy orchestrator methods — not used with Claude backend
    override suspend fun runPlanner(userInput: String): PlannerOutput? = null
    override suspend fun runCandidateChooser(itemContext: String, candidatesJson: String): CandidateSelection? = null
    override suspend fun runRangeEstimator(itemContext: String, reason: String): RangeResult? = null

    // ── Private helpers ───────────────────────────────────────────────────

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

    private fun loadPromptAsset(assetPath: String): String = try {
        context.assets.open(assetPath).bufferedReader().readText()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load prompt asset: $assetPath", e)
        ""
    }

    // ── Request / response DTOs ───────────────────────────────────────────

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val messages: List<ClaudeMessage>
    )

    @Serializable
    private data class ClaudeMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ClaudeResponse(
        val content: List<ClaudeContentBlock>
    )

    @Serializable
    private data class ClaudeContentBlock(
        val type: String,
        val text: String? = null
    )
}
