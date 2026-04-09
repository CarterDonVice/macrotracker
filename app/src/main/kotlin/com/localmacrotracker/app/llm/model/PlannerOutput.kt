package com.localmacrotracker.app.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlannerOutput(
    val version: Int = 1,
    @SerialName("entry_mode") val entryMode: String,
    @SerialName("raw_input") val rawInput: String,
    val items: List<PlannerItem>
)

@Serializable
data class PlannerItem(
    @SerialName("item_index") val itemIndex: Int,
    @SerialName("raw_fragment") val rawFragment: String,
    @SerialName("normalized_display_name") val normalizedDisplayName: String,
    @SerialName("brand_name") val brandName: String? = null,
    @SerialName("restaurant_name") val restaurantName: String? = null,
    @SerialName("food_category") val foodCategory: String,
    @SerialName("search_query") val searchQuery: String,
    @SerialName("source_order") val sourceOrder: List<String> = listOf("saved_foods", "usda", "open_food_facts"),
    @SerialName("preferred_fallback_site_types") val preferredFallbackSiteTypes: List<String> = emptyList(),
    @SerialName("likely_domain_hint") val likelyDomainHint: String? = null,
    @SerialName("should_decompose") val shouldDecompose: Boolean = false,
    @SerialName("allow_estimate") val allowEstimate: Boolean = true,
    @SerialName("quantity_text") val quantityText: String? = null,
    @SerialName("numeric_quantity") val numericQuantity: Double = 1.0,
    @SerialName("unit_hint") val unitHint: String? = null,
    @SerialName("portion_factor_hint") val portionFactorHint: Double? = null,
    @SerialName("serving_basis_hint") val servingBasisHint: String? = null,
    @SerialName("expected_exactness") val expectedExactness: String = "exact_if_found",
    val notes: String? = null
)
