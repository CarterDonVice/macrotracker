package com.localmacrotracker.app.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.localmacrotracker.app.llm.model.CandidateSelection
import com.localmacrotracker.app.llm.model.PlannerOutput
import com.localmacrotracker.app.llm.model.RangeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPipeEngine"
private const val MAX_TOKENS = 512     // ekv2048 model = 2048 total context; keep output small
private const val TEMPERATURE = 0.1f   // Near-deterministic for JSON output
private const val TOP_K = 40

@Singleton
class MediaPipeInferenceEngine @Inject constructor(
    private val context: Context
) : LocalInferenceEngine {

    private var llmInference: LlmInference? = null
    override var status: ModelStatus = ModelStatus.NOT_CONFIGURED
        private set

    // Raw prompt templates — loaded once from assets
    private val plannerPrompt: String by lazy { loadPromptAsset("prompts/search_planner_prompt.txt") }
    private val chooserPrompt: String by lazy { loadPromptAsset("prompts/candidate_chooser_prompt.txt") }
    private val estimatorPrompt: String by lazy { loadPromptAsset("prompts/range_estimator_prompt.txt") }

    override suspend fun loadModel(modelUri: Uri): Boolean = withContext(Dispatchers.IO) {
        status = ModelStatus.LOADING
        try {
            // Resolve URI to a local file path that MediaPipe can use
            val modelPath = resolveUriToPath(modelUri)
                ?: return@withContext false.also {
                    Log.e(TAG, "Could not resolve model URI to path: $modelUri")
                    status = ModelStatus.LOAD_FAILED
                }

            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setTemperature(TEMPERATURE)
                .setTopK(TOP_K)
                .build()

            llmInference?.close()
            llmInference = LlmInference.createFromOptions(context, options)
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
        status = ModelStatus.NOT_CONFIGURED
    }

    override suspend fun runPlanner(userInput: String): PlannerOutput? {
        val prompt = plannerPrompt.replace("{{USER_INPUT}}", userInput)
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
        val engine = llmInference ?: run {
            Log.w(TAG, "Inference called but model not loaded")
            return@withContext null
        }
        // Gemma IT models require the chat template applied manually —
        // MediaPipe LlmInference does not inject it automatically.
        val formatted = "<start_of_turn>user\n${prompt}<end_of_turn>\n<start_of_turn>model\n"
        return@withContext try {
            engine.generateResponse(formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            null
        }
    }

    private fun resolveUriToPath(uri: Uri): String? {
        // If it's already a file:// URI
        if (uri.scheme == "file") return uri.path

        // For content:// URIs, copy to a cache file for MediaPipe access
        return try {
            val cacheDir = File(context.cacheDir, "models").also { it.mkdirs() }
            val filename = uri.lastPathSegment ?: "model.bin"
            val dest = File(cacheDir, filename)
            // Skip copy if file already cached (avoids re-copying large model files on reload)
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
