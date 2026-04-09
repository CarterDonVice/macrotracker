package com.localmacrotracker.app.data.network

import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.llm.model.PlannerItem

/** Common interface for any source that can return food candidates. */
interface FoodLookupProvider {
    val providerName: String
    suspend fun search(query: String, plannerItem: PlannerItem? = null): List<FoodCandidate>
}

/** Provider that can look up a specific barcode. */
interface BarcodeLookupProvider {
    suspend fun lookup(barcode: String): FoodCandidate?
}

/** Parsed result from OCR label recognition. */
data class ParsedNutritionDraft(
    val suggestedName: String? = null,
    val servingText: String? = null,
    val servingWeightGrams: Double? = null,
    val calories: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val nutrients: Map<String, Double> = emptyMap(),
    val rawOcrText: String = ""
)

/** Parses raw OCR text from a nutrition label. */
interface OcrLabelParser {
    fun parse(ocrText: String): ParsedNutritionDraft
}
