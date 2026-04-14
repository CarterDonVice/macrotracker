package com.localmacrotracker.app.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParsedFoodItem(
    @SerialName("food_name") val foodName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    @SerialName("weight_g") val weightG: Double? = null,
    @SerialName("weight_oz") val weightOz: Double? = null,
    val preparation: String? = null,
    val leanness: String? = null,
    @SerialName("cooked_or_raw") val cookedOrRaw: String? = null,
    val part: String? = null,
    @SerialName("fat_content") val fatContent: String? = null,
    val brand: String? = null
)
