package com.localmacrotracker.app.data.network.providers

import android.util.Log
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.NutrientInfo
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.BarcodeLookupProvider
import com.localmacrotracker.app.data.network.FoodLookupProvider
import com.localmacrotracker.app.data.network.api.OFFProduct
import com.localmacrotracker.app.data.network.api.OpenFoodFactsApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OFFProvider"

@Singleton
class OpenFoodFactsProvider @Inject constructor(
    private val api: OpenFoodFactsApi
) : FoodLookupProvider, BarcodeLookupProvider {

    override val providerName = "open_food_facts"

    override suspend fun search(query: String): List<FoodCandidate> {
        return try {
            val response = api.searchProducts(query = query)
            response.products.mapNotNull { product -> mapToCandidate(product, source = "search") }
        } catch (e: Exception) {
            Log.e(TAG, "OFF search failed for '$query'", e)
            emptyList()
        }
    }

    override suspend fun lookup(barcode: String): FoodCandidate? {
        return try {
            val response = api.getProductByBarcode(barcode)
            if (response.status != 1 || response.product == null) return null
            mapToCandidate(response.product, barcode = barcode, source = "barcode")
        } catch (e: Exception) {
            Log.e(TAG, "OFF barcode lookup failed for '$barcode'", e)
            null
        }
    }

    private fun mapToCandidate(product: OFFProduct, barcode: String? = null, source: String = ""): FoodCandidate? {
        val name = product.productName?.takeIf { it.isNotBlank() } ?: return null
        val nm = product.nutriments ?: return null

        // Prefer per-serving values, fall back to per-100g
        val calories = nm.caloriesPerServing ?: nm.caloriesPer100g ?: return null
        val protein = nm.proteinPerServing ?: nm.proteinPer100g ?: 0.0
        val carbs = nm.carbsPerServing ?: nm.carbsPer100g ?: 0.0
        val fat = nm.fatPerServing ?: nm.fatPer100g ?: 0.0

        val usesPer100g = nm.caloriesPerServing == null

        val nutrients = buildList {
            (nm.fiberPerServing ?: nm.fiberPer100g)?.let { add(NutrientInfo("FIBTG", "Fiber", it, "g")) }
            (nm.sugarsPerServing)?.let { add(NutrientInfo("SUGAR", "Sugar", it, "g")) }
            (nm.sodiumPerServing ?: nm.sodiumPer100g)?.let { add(NutrientInfo("NA", "Sodium", it * 1000, "mg")) }
        }

        val brand = product.brands?.split(",")?.firstOrNull()?.trim()
        val displayName = if (brand != null) "$name ($brand)" else name

        return FoodCandidate(
            id = "off_${name.hashCode()}_${barcode ?: source}",
            displayName = displayName,
            originalName = name,
            barcode = barcode,
            sourceType = if (barcode != null) SourceType.BARCODE else SourceType.OPEN_FOOD_FACTS,
            servingText = if (usesPer100g) "100g" else product.servingSize ?: "1 serving",
            servingWeightGrams = if (usesPer100g) 100.0 else null,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            exactnessType = ExactnessType.EXACT,
            nutrients = nutrients
        )
    }
}
