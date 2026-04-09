package com.localmacrotracker.app.data.network.providers

import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.FoodLookupProvider
import com.localmacrotracker.app.llm.model.PlannerItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSavedFoodProvider @Inject constructor(
    private val savedFoodDao: SavedFoodDao
) : FoodLookupProvider {

    override val providerName = "saved_foods"

    override suspend fun search(query: String, plannerItem: PlannerItem?): List<FoodCandidate> {
        if (query.isBlank()) return emptyList()
        val results = savedFoodDao.searchFoods(query.trim()).first()
        return results.map { entity ->
            FoodCandidate(
                id = "saved_${entity.id}",
                displayName = entity.displayName,
                originalName = entity.originalName,
                barcode = entity.barcode,
                sourceType = SourceType.LOCAL_SAVED,
                servingText = entity.servingText,
                servingWeightGrams = entity.servingWeightGrams,
                servingVolumeMl = entity.servingVolumeMl,
                calories = entity.calories,
                proteinGrams = entity.proteinGrams,
                carbsGrams = entity.carbsGrams,
                fatGrams = entity.fatGrams,
                exactnessType = ExactnessType.fromString(entity.exactnessType),
                isLocalSaved = true,
                savedFoodId = entity.id
            )
        }
    }

    suspend fun lookupByBarcode(barcode: String): FoodCandidate? {
        val entity = savedFoodDao.getFoodByBarcode(barcode) ?: return null
        return FoodCandidate(
            id = "saved_${entity.id}",
            displayName = entity.displayName,
            originalName = entity.originalName,
            barcode = entity.barcode,
            sourceType = SourceType.LOCAL_SAVED,
            servingText = entity.servingText,
            servingWeightGrams = entity.servingWeightGrams,
            calories = entity.calories,
            proteinGrams = entity.proteinGrams,
            carbsGrams = entity.carbsGrams,
            fatGrams = entity.fatGrams,
            exactnessType = ExactnessType.fromString(entity.exactnessType),
            isLocalSaved = true,
            savedFoodId = entity.id
        )
    }
}
