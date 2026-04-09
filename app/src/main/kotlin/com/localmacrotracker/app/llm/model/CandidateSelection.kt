package com.localmacrotracker.app.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CandidateSelection(
    val version: Int = 1,
    @SerialName("item_index") val itemIndex: Int,
    @SerialName("selected_candidate_id") val selectedCandidateId: String,
    @SerialName("selection_type") val selectionType: String, // exact | estimated | no_match
    @SerialName("requires_serving_math") val requiresServingMath: Boolean = false,
    @SerialName("portion_factor") val portionFactor: Double = 1.0,
    @SerialName("final_display_name") val finalDisplayName: String,
    @SerialName("final_category") val finalCategory: String = "",
    @SerialName("confidence_reason_code") val confidenceReasonCode: String = "",
    @SerialName("should_prompt_manual_save") val shouldPromptManualSave: Boolean = false,
    val notes: String? = null
)
