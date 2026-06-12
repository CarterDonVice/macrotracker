package com.localmacrotracker.app.data.network.providers

import android.util.Log
import com.localmacrotracker.app.BuildConfig
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.NutrientInfo
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.FoodLookupProvider
import com.localmacrotracker.app.data.network.api.UsdaApi
import com.localmacrotracker.app.data.network.api.UsdaFood
import com.localmacrotracker.app.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UsdaProvider"

// USDA nutrient IDs for core macros
private const val NUTRIENT_ENERGY = 1008
private const val NUTRIENT_PROTEIN = 1003
private const val NUTRIENT_CARBS = 1005
private const val NUTRIENT_FAT = 1004
private const val NUTRIENT_FIBER = 1079
private const val NUTRIENT_SUGAR = 2000
private const val NUTRIENT_SODIUM = 1093

@Singleton
class UsdaProvider @Inject constructor(
    private val api: UsdaApi,
    private val prefs: AppPreferences
) : FoodLookupProvider {

    override val providerName = "usda"

    override suspend fun search(query: String): List<FoodCandidate> {
        // Prefer a user-supplied override, else fall back to the baked-in key.
        val apiKey = prefs.usdaApiKey.first()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.USDA_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "No USDA API key available")
            return emptyList()
        }
        return try {
            val response = api.searchFoods(query = query, apiKey = apiKey)
            response.foods.mapNotNull { food -> mapToCandidate(food) }
        } catch (e: Exception) {
            Log.e(TAG, "USDA search failed for '$query'", e)
            emptyList()
        }
    }

    private fun mapToCandidate(food: UsdaFood): FoodCandidate? {
        val protein = food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_PROTEIN }?.value ?: 0.0
        val carbs = food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_CARBS }?.value ?: 0.0
        val fat = food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_FAT }?.value ?: 0.0
        // Some Foundation/SR entries omit the Energy nutrient — derive kcal from macros
        // (4/4/9) instead of dropping the food. Only drop if there's no usable data at all.
        val calories = food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_ENERGY }?.value
            ?: food.foodNutrients.firstOrNull {
                it.nutrientName?.contains("Energy", true) == true &&
                    it.unitName?.equals("KCAL", true) == true
            }?.value
            ?: (protein * 4 + carbs * 4 + fat * 9).takeIf { it > 0 }
            ?: return null

        val nutrients = buildList {
            food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_FIBER }?.value?.let {
                add(NutrientInfo("FIBTG", "Fiber", it, "g"))
            }
            food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_SUGAR }?.value?.let {
                add(NutrientInfo("SUGAR", "Sugar", it, "g"))
            }
            food.foodNutrients.firstOrNull { it.nutrientId == NUTRIENT_SODIUM }?.value?.let {
                add(NutrientInfo("NA", "Sodium", it, "mg"))
            }
        }

        val name = buildString {
            append(food.description)
            if (!food.brandName.isNullOrBlank()) append(" (${food.brandName})")
        }

        return FoodCandidate(
            id = "usda_${food.fdcId}",
            displayName = name,
            originalName = food.description,
            sourceType = SourceType.USDA,
            sourceUrl = "https://fdc.nal.usda.gov/fdc-app.html#/food-details/${food.fdcId}/nutrients",
            servingText = if (food.servingSize != null && food.servingSizeUnit != null)
                "${food.servingSize} ${food.servingSizeUnit}" else "100g",
            servingWeightGrams = food.servingSize?.takeIf {
                food.servingSizeUnit?.lowercase()?.contains("g") == true
            } ?: 100.0,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            exactnessType = ExactnessType.EXACT,
            nutrients = nutrients
        )
    }
}
