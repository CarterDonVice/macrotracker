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

        var protein = nm.proteinPerServing ?: nm.proteinPer100g ?: 0.0
        var carbs = nm.carbsPerServing ?: nm.carbsPer100g ?: 0.0
        var fat = nm.fatPerServing ?: nm.fatPer100g ?: 0.0

        // Energy: prefer kcal, fall back to kJ (÷4.184), then derive from macros.
        val kcalServing = nm.caloriesPerServing
            ?: nm.energyKjPerServing?.div(KJ_PER_KCAL)
            ?: nm.energyPerServing?.div(KJ_PER_KCAL)
        val kcal100g = nm.caloriesPer100g
            ?: nm.energyKjPer100g?.div(KJ_PER_KCAL)
            ?: nm.energyPer100g?.div(KJ_PER_KCAL)

        var usesPer100g = kcalServing == null
        // Keep a product if it has a name and *any* usable nutrition; only drop true junk.
        var calories = kcalServing ?: kcal100g
            ?: (protein * 4 + carbs * 4 + fat * 9).takeIf { it > 0 }
            ?: return null

        // Per-100g data but a declared serving size (e.g. "30 g" or "2 cookies (28 g)"):
        // scale macros to the actual serving so users see per-serving values.
        var servingGrams: Double? = null
        var per100gScale = 1.0
        if (usesPer100g) {
            servingGrams = parseServingGrams(product.servingSize)
            if (servingGrams != null && servingGrams > 0) {
                per100gScale = servingGrams / 100.0
                calories *= per100gScale
                protein *= per100gScale
                carbs *= per100gScale
                fat *= per100gScale
                usesPer100g = false
            }
        }

        val nutrients = buildList {
            (nm.fiberPerServing ?: nm.fiberPer100g?.times(per100gScale))
                ?.let { add(NutrientInfo("FIBTG", "Fiber", it, "g")) }
            (nm.sugarsPerServing)?.let { add(NutrientInfo("SUGAR", "Sugar", it, "g")) }
            (nm.sodiumPerServing ?: nm.sodiumPer100g?.times(per100gScale))
                ?.let { add(NutrientInfo("NA", "Sodium", it * 1000, "mg")) }
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
            servingWeightGrams = if (usesPer100g) 100.0 else servingGrams,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            exactnessType = ExactnessType.EXACT,
            nutrients = nutrients
        )
    }

    companion object {
        private const val KJ_PER_KCAL = 4.184
        private val SERVING_GRAMS_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*g""", RegexOption.IGNORE_CASE)

        /** Extracts gram weight from serving strings like "30 g", "30g", or "2 cookies (28 g)". */
        fun parseServingGrams(servingSize: String?): Double? {
            if (servingSize.isNullOrBlank()) return null
            val match = SERVING_GRAMS_REGEX.find(servingSize) ?: return null
            return match.groupValues[1].replace(',', '.').toDoubleOrNull()
        }
    }
}
