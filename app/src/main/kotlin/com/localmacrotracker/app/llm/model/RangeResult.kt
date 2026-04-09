package com.localmacrotracker.app.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RangeResult(
    val version: Int = 1,
    @SerialName("item_index") val itemIndex: Int,
    @SerialName("calories_min") val caloriesMin: Double,
    @SerialName("calories_max") val caloriesMax: Double,
    @SerialName("protein_min_g") val proteinMinG: Double,
    @SerialName("protein_max_g") val proteinMaxG: Double,
    @SerialName("carbs_min_g") val carbsMinG: Double,
    @SerialName("carbs_max_g") val carbsMaxG: Double,
    @SerialName("fat_min_g") val fatMinG: Double,
    @SerialName("fat_max_g") val fatMaxG: Double,
    @SerialName("range_reason") val rangeReason: String = "",
    val notes: String? = null
)
