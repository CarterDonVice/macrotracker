package com.localmacrotracker.app.llm

import com.localmacrotracker.app.llm.model.CandidateSelection
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import com.localmacrotracker.app.llm.model.PlannerOutput
import com.localmacrotracker.app.llm.model.RangeResult

/** Status of the local LLM backend. */
enum class ModelStatus {
    NOT_CONFIGURED,
    LOADING,
    READY,
    LOAD_FAILED
}

/**
 * Abstraction over any local text inference backend.
 * Swap the implementation in [MediaPipeInferenceEngine] to change the runtime.
 * The engine NEVER performs network calls. It only processes text and returns JSON.
 */
interface LocalInferenceEngine {
    val status: ModelStatus

    /** Load the model from the persisted URI. Suspends until ready or failed. */
    suspend fun loadModel(modelUri: android.net.Uri): Boolean

    /** Unload/release the model from memory. */
    fun unloadModel()

    /**
     * Food Parser — primary entry point for labeless food entry.
     * Input: raw user description of what they ate.
     * Returns: list of [ParsedFoodItem] (one per distinct food), or null on failure.
     */
    suspend fun runFoodParser(userInput: String): List<ParsedFoodItem>?

    // Legacy orchestrator methods kept for backward compatibility
    suspend fun runPlanner(userInput: String): PlannerOutput?
    suspend fun runCandidateChooser(itemContext: String, candidatesJson: String): CandidateSelection?
    suspend fun runRangeEstimator(itemContext: String, reason: String): RangeResult?
}
