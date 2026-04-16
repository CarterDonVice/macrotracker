package com.localmacrotracker.app.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.localmacrotracker.app.llm.model.CandidateSelection
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import com.localmacrotracker.app.llm.model.PlannerOutput
import com.localmacrotracker.app.llm.model.RangeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPipeEngine"
private const val MAX_TOKENS = 1024

@Singleton
class MediaPipeInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalInferenceEngine {

    private var llmInference: LlmInference? = null
    private var currentModelPath: String? = null

    override var status: ModelStatus = ModelStatus.NOT_CONFIGURED
        private set

    // Raw prompt templates — loaded once from assets
    private val parserPrompt: String by lazy { loadPromptAsset("prompts/search_planner_prompt.txt") }
    private val chooserPrompt: String by lazy { loadPromptAsset("prompts/candidate_chooser_prompt.txt") }
    private val estimatorPrompt: String by lazy { loadPromptAsset("prompts/range_estimator_prompt.txt") }

    override suspend fun loadModel(modelUri: Uri): Boolean = withContext(Dispatchers.IO) {
        status = ModelStatus.LOADING
        try {
            val modelPath = resolveUriToPath(modelUri)
                ?: return@withContext false.also {
                    Log.e(TAG, "Could not resolve model URI to path: $modelUri")
                    status = ModelStatus.LOAD_FAILED
                }

            currentModelPath = modelPath
            llmInference?.close()
            llmInference = buildInstance(modelPath)
            status = ModelStatus.READY
            Log.i(TAG, "Model loaded from $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            status = ModelStatus.LOAD_FAILED
            false
        }
    }

    override fun unloadModel() {
        llmInference?.close()
        llmInference = null
        currentModelPath = null
        status = ModelStatus.NOT_CONFIGURED
    }

    override suspend fun runFoodParser(userInput: String): List<ParsedFoodItem>? {
        val prompt = parserPrompt.replace("{{USER_INPUT}}", userInput)
        val raw = runInference(prompt) ?: run {
            Log.e(TAG, "runFoodParser: inference returned null")
            return null
        }
        // Always log the full raw output so we can see exactly what the model returned
        Log.d(TAG, "runFoodParser RAW OUTPUT (${raw.length} chars):\n$raw")

        // Pass 1: strip markdown code fences, then extract the outermost [...] array
        val cleaned = raw
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        val extracted = if (start != -1 && end > start) cleaned.substring(start, end + 1) else cleaned
        Log.d(TAG, "runFoodParser PASS 1 extracted: ${extracted.take(500)}")

        var result = LlmOutputParser.parseFoodItems(extracted)

        // Pass 2: if the model returned a bare object {} instead of an array,
        // wrap it in [] and try again
        if (result == null) {
            val wrapped = "[$extracted]"
            Log.d(TAG, "runFoodParser PASS 2 (wrapped): ${wrapped.take(500)}")
            result = LlmOutputParser.parseFoodItems(wrapped)
        }

        if (result == null) {
            Log.w(TAG, "runFoodParser: ALL parse attempts failed. Full raw output:\n$raw")
        } else {
            Log.d(TAG, "runFoodParser: parsed ${result.size} items: ${result.map { it.foodName }}")
        }
        return result
    }

    override suspend fun runPlanner(userInput: String): PlannerOutput? {
        val prompt = parserPrompt.replace("{{USER_INPUT}}", userInput)
        val raw = runInference(prompt) ?: return null
        return LlmOutputParser.parsePlannerOutput(raw)
    }

    override suspend fun runCandidateChooser(
        itemContext: String,
        candidatesJson: String
    ): CandidateSelection? {
        val prompt = chooserPrompt
            .replace("{{ITEM_CONTEXT}}", itemContext)
            .replace("{{CANDIDATES_JSON}}", candidatesJson)
        val raw = runInference(prompt) ?: return null
        return LlmOutputParser.parseCandidateSelection(raw)
    }

    override suspend fun runRangeEstimator(itemContext: String, reason: String): RangeResult? {
        val prompt = estimatorPrompt
            .replace("{{ITEM_CONTEXT}}", itemContext)
            .replace("{{REASON}}", reason)
        val raw = runInference(prompt) ?: return null
        return LlmOutputParser.parseRangeResult(raw)
    }

    private suspend fun runInference(prompt: String): String? = withContext(Dispatchers.Default) {
        val modelPath = currentModelPath ?: run {
            Log.w(TAG, "Inference called but no model path stored")
            return@withContext null
        }
        // Create a fresh LlmInference instance for each query.
        // This prevents context accumulation across calls which can overflow the
        // token budget and crash the model on subsequent lookups.
        val freshEngine = try {
            llmInference?.close()
            buildInstance(modelPath).also { llmInference = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create fresh inference instance", e)
            status = ModelStatus.LOAD_FAILED
            return@withContext null
        }
        // Gemma IT models require the chat template applied manually.
        val formatted = "<start_of_turn>user\n${prompt}<end_of_turn>\n<start_of_turn>model\n"
        return@withContext try {
            freshEngine.generateResponse(formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            null
        }
    }

    private fun buildInstance(modelPath: String): LlmInference {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    private fun resolveUriToPath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        return try {
            val cacheDir = File(context.cacheDir, "models").also { it.mkdirs() }
            val rawSegment = uri.lastPathSegment ?: "model.bin"
            val filename = rawSegment.substringAfterLast('/').substringAfterLast(':')
                .ifBlank { "model.bin" }
            val dest = File(cacheDir, filename)
            if (!dest.exists() || dest.length() == 0L) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model to cache", e)
            null
        }
    }

    private fun loadPromptAsset(assetPath: String): String {
        return try {
            context.assets.open(assetPath).bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load prompt asset: $assetPath", e)
            ""
        }
    }
}
