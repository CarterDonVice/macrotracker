package com.localmacrotracker.app.data.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,brands,serving_size,nutriments,ingredients_text,image_url"
    ): OFFProductResponse

    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 10,
        @Query("fields") fields: String = "product_name,brands,serving_size,nutriments"
    ): OFFSearchResponse
}

@Serializable
data class OFFProductResponse(
    val status: Int = 0,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val product: OFFProduct? = null
)

@Serializable
data class OFFSearchResponse(
    val count: Int = 0,
    val products: List<OFFProduct> = emptyList()
)

@Serializable
data class OFFProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    val nutriments: OFFNutriments? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class OFFNutriments(
    @SerialName("energy-kcal_serving") val caloriesPerServing: Double? = null,
    @SerialName("energy-kcal_100g") val caloriesPer100g: Double? = null,
    @SerialName("proteins_serving") val proteinPerServing: Double? = null,
    @SerialName("proteins_100g") val proteinPer100g: Double? = null,
    @SerialName("carbohydrates_serving") val carbsPerServing: Double? = null,
    @SerialName("carbohydrates_100g") val carbsPer100g: Double? = null,
    @SerialName("fat_serving") val fatPerServing: Double? = null,
    @SerialName("fat_100g") val fatPer100g: Double? = null,
    @SerialName("fiber_serving") val fiberPerServing: Double? = null,
    @SerialName("fiber_100g") val fiberPer100g: Double? = null,
    @SerialName("sugars_serving") val sugarsPerServing: Double? = null,
    @SerialName("sodium_serving") val sodiumPerServing: Double? = null,
    @SerialName("sodium_100g") val sodiumPer100g: Double? = null
)
