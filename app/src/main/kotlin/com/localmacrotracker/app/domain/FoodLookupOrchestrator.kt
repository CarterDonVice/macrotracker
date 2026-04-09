package com.localmacrotracker.app.domain

import android.util.Log
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.FoodLookupProvider
import com.localmacrotracker.app.data.network.providers.*
import com.localmacrotracker.app.llm.LocalInferenceEngine
import com.localmacrotracker.app.llm.ModelStatus
import com.localmacrotracker.app.llm.model.PlannerItem
import com.localmacrotracker.app.llm.model.PlannerOutput
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FoodLookupOrchestrator"

@Singleton
class FoodLookupOrchestrator @Inject constructor(
    private val localProvider: LocalSavedFoodProvider,
    private val usdaProvider: UsdaProvider,
    private val offProvider: OpenFoodFactsProvider,
    private val brandPageProvider: BrandPageProvider,
    private val restaurantPageProvider: RestaurantPageProvider,
    private val groceryPageProvider: GroceryPageProvider,
    private val inferenceEngine: LocalInferenceEngine
) {

    private val providerMap: Map<String, FoodLookupProvider> = mapOf(
        "saved_foods" to localProvider,
        "usda" to usdaProvider,
        "open_food_facts" to offProvider,
        "brand_page" to brandPageProvider,
        "restaurant_page" to restaurantPageProvider,
        "grocery_page" to groceryPageProvider
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Full pipeline for labeless / multi-food text input.
     * Returns one draft FoodLogEntryEntity per resolved item.
     */
    suspend fun resolveTextInput(
        userInput: String,
        mealSection: MealSection,
        logDate: LocalDate
    ): List<FoodLogEntryEntity> {
        // Step 1: Run search planner (LLM) or fall back to simple single-item plan
        val plan = if (inferenceEngine.status == ModelStatus.READY) {
            inferenceEngine.runPlanner(userInput)
        } else null

        val effectivePlan = plan ?: buildFallbackPlan(userInput)

        // Step 2: Resolve each item
        return effectivePlan.items.map { item ->
            resolveItem(item, mealSection, logDate)
        }
    }

    private suspend fun resolveItem(
        item: PlannerItem,
        mealSection: MealSection,
        logDate: LocalDate
    ): FoodLogEntryEntity {
        val candidates = mutableListOf<FoodCandidate>()

        // Execute provider chain in order specified by planner
        for (sourceKey in item.sourceOrder) {
            val provider = providerMap[sourceKey] ?: continue
            try {
                val results = provider.search(item.searchQuery, item)
                candidates.addAll(results)
                // If we have a strong local saved match, stop searching
                if (sourceKey == "saved_foods" && results.any { it.isLocalSaved }) break
            } catch (e: Exception) {
                Log.w(TAG, "Provider $sourceKey failed for '${item.searchQuery}'", e)
            }
            if (candidates.size >= 10) break  // enough candidates to choose from
        }

        if (candidates.isEmpty()) {
            return buildManualDraft(item, mealSection, logDate)
        }

        // Step 3: Run candidate chooser (LLM) or pick best heuristically
        val chosen = if (inferenceEngine.status == ModelStatus.READY && candidates.size > 1) {
            val itemContext = buildItemContext(item)
            val candidatesJson = json.encodeToString(
                candidates.take(5).map { candidateToMap(it) }
            )
            val selection = inferenceEngine.runCandidateChooser(itemContext, candidatesJson)
            if (selection?.selectionType == "no_match") {
                return buildManualDraft(item, mealSection, logDate)
            }
            candidates.firstOrNull { it.id == selection?.selectedCandidateId }
                ?.let { candidate ->
                    val factor = selection?.portionFactor ?: 1.0
                    if (factor != 1.0) scaledCandidate(candidate, factor) else candidate
                }
                ?: candidates.first()
        } else {
            // Heuristic: local saved → exact API → first result
            candidates.firstOrNull { it.isLocalSaved }
                ?: candidates.firstOrNull { it.exactnessType == ExactnessType.EXACT }
                ?: candidates.first()
        }

        // Step 4: Apply quantity scaling from planner
        val portionFactor = item.portionFactorHint
            ?: ServingMath.computePortionFactor(
                chosen.servingWeightGrams,
                item.numericQuantity,
                item.unitHint ?: "serving"
            )

        val scaled = if (portionFactor != 1.0) scaledCandidate(chosen, portionFactor) else chosen

        // Step 5: Determine exactness
        val isEstimated = scaled.exactnessType == ExactnessType.ESTIMATED
        val needsReminder = !scaled.isLocalSaved

        return if (isEstimated) {
            // Run range estimator if LLM available
            val range = if (inferenceEngine.status == ModelStatus.READY) {
                inferenceEngine.runRangeEstimator(
                    buildItemContext(item),
                    "source_uncertain"
                )
            } else null

            FoodLogEntryEntity(
                logDate = logDate.toString(),
                mealSection = mealSection.name,
                displayNameSnapshot = scaled.displayName,
                originalNameSnapshot = scaled.originalName,
                servingTextSnapshot = scaled.servingText,
                quantity = item.numericQuantity,
                unit = item.unitHint ?: "serving",
                caloriesMin = range?.caloriesMin ?: scaled.calories * 0.9,
                caloriesMax = range?.caloriesMax ?: scaled.calories * 1.1,
                proteinMin = range?.proteinMinG ?: scaled.proteinGrams * 0.9,
                proteinMax = range?.proteinMaxG ?: scaled.proteinGrams * 1.1,
                carbsMin = range?.carbsMinG ?: scaled.carbsGrams * 0.9,
                carbsMax = range?.carbsMaxG ?: scaled.carbsGrams * 1.1,
                fatMin = range?.fatMinG ?: scaled.fatGrams * 0.9,
                fatMax = range?.fatMaxG ?: scaled.fatGrams * 1.1,
                isEstimated = true,
                needsManualSaveReminder = needsReminder,
                sourceTypeSnapshot = scaled.sourceType.name
            )
        } else {
            FoodLogEntryEntity(
                logDate = logDate.toString(),
                mealSection = mealSection.name,
                linkedSavedFoodId = scaled.savedFoodId,
                displayNameSnapshot = scaled.displayName,
                originalNameSnapshot = scaled.originalName,
                servingTextSnapshot = scaled.servingText,
                quantity = item.numericQuantity,
                unit = item.unitHint ?: "serving",
                caloriesExact = scaled.calories,
                proteinExact = scaled.proteinGrams,
                carbsExact = scaled.carbsGrams,
                fatExact = scaled.fatGrams,
                isEstimated = false,
                needsManualSaveReminder = needsReminder,
                sourceTypeSnapshot = scaled.sourceType.name
            )
        }
    }

    private fun scaledCandidate(candidate: FoodCandidate, factor: Double): FoodCandidate =
        candidate.copy(
            calories = candidate.calories * factor,
            proteinGrams = candidate.proteinGrams * factor,
            carbsGrams = candidate.carbsGrams * factor,
            fatGrams = candidate.fatGrams * factor
        )

    private fun buildManualDraft(
        item: PlannerItem,
        mealSection: MealSection,
        logDate: LocalDate
    ): FoodLogEntryEntity = FoodLogEntryEntity(
        logDate = logDate.toString(),
        mealSection = mealSection.name,
        displayNameSnapshot = item.normalizedDisplayName.ifBlank { item.rawFragment },
        servingTextSnapshot = item.quantityText,
        quantity = item.numericQuantity,
        unit = item.unitHint ?: "serving",
        caloriesExact = null,
        proteinExact = null,
        carbsExact = null,
        fatExact = null,
        isEstimated = false,
        needsManualSaveReminder = true,
        sourceTypeSnapshot = SourceType.MANUAL.name
    )

    private fun buildFallbackPlan(userInput: String): PlannerOutput {
        return PlannerOutput(
            version = 1,
            entryMode = "labeless_food",
            rawInput = userInput,
            items = listOf(
                PlannerItem(
                    itemIndex = 0,
                    rawFragment = userInput,
                    normalizedDisplayName = userInput.trim(),
                    foodCategory = "generic_single_food",
                    searchQuery = userInput.trim(),
                    sourceOrder = listOf("saved_foods", "usda", "open_food_facts"),
                    numericQuantity = 1.0
                )
            )
        )
    }

    private fun buildItemContext(item: PlannerItem): String =
        "Food: ${item.normalizedDisplayName}, " +
                "quantity: ${item.quantityText ?: item.numericQuantity} ${item.unitHint ?: ""}, " +
                "category: ${item.foodCategory}, " +
                "restaurant: ${item.restaurantName ?: "none"}, " +
                "brand: ${item.brandName ?: "none"}"

    private fun candidateToMap(c: FoodCandidate): Map<String, Any?> = mapOf(
        "id" to c.id,
        "displayName" to c.displayName,
        "sourceType" to c.sourceType.name,
        "calories" to c.calories,
        "protein" to c.proteinGrams,
        "carbs" to c.carbsGrams,
        "fat" to c.fatGrams,
        "exactnessType" to c.exactnessType.name,
        "isLocalSaved" to c.isLocalSaved,
        "servingText" to c.servingText
    )
}
