package com.localmacrotracker.app.data.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface UsdaApi {
    @GET("fdc/v1/foods/search")
    suspend fun searchFoods(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("pageSize") pageSize: Int = 10,
        @Query("dataType") dataType: String = "Foundation,SR Legacy,Branded"
    ): UsdaSearchResponse
}

@Serializable
data class UsdaSearchResponse(
    val foods: List<UsdaFood> = emptyList(),
    val totalHits: Int = 0
)

@Serializable
data class UsdaFood(
    val fdcId: Int,
    val description: String,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val dataType: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList()
)

@Serializable
data class UsdaNutrient(
    val nutrientId: Int? = null,
    val nutrientName: String? = null,
    @SerialName("nutrientNumber") val nutrientNumber: String? = null,
    val unitName: String? = null,
    val value: Double? = null
)
