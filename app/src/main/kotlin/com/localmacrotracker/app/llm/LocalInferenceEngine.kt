package com.localmacrotracker.app.llm

import com.localmacrotracker.app.llm.model.CandidateSelection
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
     * PROMPT 1 — Search Planner.
     * Input: raw user description.
     * Returns: [PlannerOutput] or null on invalid JSON / model failure.
     */
    suspend fun runPlanner(userInput: String): PlannerOutput?

    /**
     * PROMPT 2 — Candidate Chooser.
     * Input: item context + JSON array of candidates.
     * Returns: [CandidateSelection] or null on failure.
     */
    suspend fun runCandidateChooser(itemContext: String, candidatesJson: String): CandidateSelection?

    /**
     * PROMPT 3 — Range Estimator.
     * Input: item context + reason why estimation is needed.
     * Returns: [RangeResult] or null on failure.
     */
    suspend fun runRangeEstimator(itemContext: String, reason: String): RangeResult?
}
