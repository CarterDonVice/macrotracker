package com.localmacrotracker.app.data.model

/**
 * A resolved food candidate returned by any lookup provider.
 * May be exact or estimated. Callers run the Candidate Chooser LLM
 * to pick the best from a list of these.
 */
data class FoodCandidate(
    val id: String,
    val displayName: String,
    val originalName: String? = null,
    val barcode: String? = null,
    val sourceType: SourceType,
    val sourceUrl: String? = null,
    val servingText: String? = null,
    val servingWeightGrams: Double? = null,
    val servingVolumeMl: Double? = null,
    val caloriesPer100g: Double? = null,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val exactnessType: ExactnessType = ExactnessType.EXACT,
    val nutrients: List<NutrientInfo> = emptyList(),
    /** When true, this came from the local saved foods DB. */
    val isLocalSaved: Boolean = false,
    val savedFoodId: Long? = null,
)
