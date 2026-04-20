package com.localmacrotracker.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.api.OFFProduct
import com.localmacrotracker.app.data.network.api.OpenFoodFactsApi
import com.localmacrotracker.app.data.network.api.UsdaApi
import com.localmacrotracker.app.data.network.api.UsdaFood
import com.localmacrotracker.app.data.prefs.AppPreferences
import com.localmacrotracker.app.llm.ClaudeInferenceEngine
import com.localmacrotracker.app.llm.model.ParsedFoodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "LabelessFoodVM"
private const val FALLBACK_USDA_KEY = "uZnfYQldxzrRhjLyTJ78LTeGS8zuYq5UE7vmE6t4"

@HiltViewModel
class LabelessFoodViewModel @Inject constructor(
    private val claudeEngine: ClaudeInferenceEngine,
    private val usdaApi: UsdaApi,
    private val offApi: OpenFoodFactsApi,
    private val foodLogDao: FoodLogDao,
    private val prefs: AppPreferences
) : ViewModel() {

    data class ConfirmedEntry(
        val displayName: String,
        val brand: String? = null,
        val servingSize: String? = null,
        val quantity: Double,
        val unit: String,
        val weightG: Double? = null,
        val weightOz: Double? = null,
        val preparation: String? = null,
        val leanness: String? = null,
        val part: String? = null,
        val fatContent: String? = null,
        val calories: Double?,
        val proteinGrams: Double?,
        val carbsGrams: Double?,
        val fatGrams: Double?,
        val needsManualEntry: Boolean
    )

    sealed class UiState {
        object Idle : UiState()
        data class Working(val stage: String) : UiState()
        object Confirmation : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _confirmedEntries = MutableStateFlow<List<ConfirmedEntry>>(emptyList())
    val confirmedEntries: StateFlow<List<ConfirmedEntry>> = _confirmedEntries.asStateFlow()

    private var pendingMealSection: MealSection = MealSection.BREAKFAST
    private var pendingLogDate: LocalDate = LocalDate.now()

    fun submit(input: String, mealSection: MealSection, logDate: LocalDate) {
        pendingMealSection = mealSection
        pendingLogDate = logDate
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Working("Parsing your food description…")
                Log.d(TAG, "Calling Claude for: $input")

                val parsedItems: List<ParsedFoodItem> = when (val result = claudeEngine.parseFoods(input)) {
                    is ClaudeInferenceEngine.ParseResult.NoApiKey -> {
                        _uiState.value = UiState.Error(
                            "Claude API key not set. Add it in Settings → API Keys."
                        )
                        return@launch
                    }
                    is ClaudeInferenceEngine.ParseResult.NetworkError -> {
                        _uiState.value = UiState.Error(
                            "Network error reaching Claude. Check your connection and try again."
                        )
                        return@launch
                    }
                    is ClaudeInferenceEngine.ParseResult.ParseFailed -> {
                        _uiState.value = UiState.Error(
                            "Could not parse foods. Try describing one food at a time."
                        )
                        return@launch
                    }
                    is ClaudeInferenceEngine.ParseResult.ParsedItems -> {
                        if (result.items.isEmpty()) {
                            _uiState.value = UiState.Error("No foods found. Please be more specific.")
                            return@launch
                        }
                        result.items
                    }
                }

                Log.d(TAG, "Parsed ${parsedItems.size} food item(s): ${parsedItems.map { it.foodName }}")

                _uiState.value = UiState.Working("Looking up nutrition data…")
                val apiKey = prefs.usdaApiKey.first()?.takeIf { it.isNotBlank() } ?: FALLBACK_USDA_KEY

                val entries = parsedItems.map { item ->
                    async { lookupItem(item, apiKey) }
                }.awaitAll()

                Log.d(TAG, "Confirmation entries (${entries.size}): ${entries.map { it.displayName }}")
                _confirmedEntries.value = entries
                _uiState.value = UiState.Confirmation
            } catch (e: Throwable) {
                Log.e(TAG, "submit failed", e)
                _uiState.value = UiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun updateEntry(index: Int, updated: ConfirmedEntry) {
        _confirmedEntries.update { list ->
            list.toMutableList().also { if (index in it.indices) it[index] = updated }
        }
    }

    fun confirmAndSave() {
        viewModelScope.launch {
            try {
                val rows = _confirmedEntries.value.map { entry ->
                    val brandedName = listOfNotNull(entry.brand, entry.displayName).joinToString(" ")
                    FoodLogEntryEntity(
                        logDate = pendingLogDate.toString(),
                        mealSection = pendingMealSection.name,
                        displayNameSnapshot = brandedName,
                        originalNameSnapshot = entry.displayName,
                        servingTextSnapshot = entry.servingSize ?: "${entry.quantity} ${entry.unit}",
                        quantity = entry.quantity,
                        unit = entry.unit,
                        caloriesExact = entry.calories,
                        proteinExact = entry.proteinGrams,
                        carbsExact = entry.carbsGrams,
                        fatExact = entry.fatGrams,
                        isEstimated = entry.needsManualEntry,
                        needsManualSaveReminder = entry.needsManualEntry,
                        sourceTypeSnapshot = if (entry.needsManualEntry) SourceType.MANUAL.name
                        else SourceType.USDA.name
                    )
                }
                foodLogDao.insertEntries(rows)
                _confirmedEntries.value = emptyList()
                _uiState.value = UiState.Idle
            } catch (e: Throwable) {
                Log.e(TAG, "confirmAndSave failed", e)
                _uiState.value = UiState.Error(e.message ?: "Failed to save entries")
            }
        }
    }

    fun cancel() {
        _confirmedEntries.value = emptyList()
        _uiState.value = UiState.Idle
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    // ── Lookup logic ─────────────────────────────────────────────────────────

    private suspend fun lookupItem(item: ParsedFoodItem, apiKey: String): ConfirmedEntry {
        val offQuery = if (item.brand != null) "${item.brand} ${item.foodName}" else null
        val usdaQuery = buildUsdaQuery(item)

        val offDeferred = offQuery?.let { q -> viewModelScope.async { trySearchOff(q) } }
        val usdaDeferred = viewModelScope.async { trySearchUsda(usdaQuery, apiKey) }

        val offResult = offDeferred?.await()
        val usdaResult = usdaDeferred.await()

        val candidate = if (item.brand != null) (offResult ?: usdaResult)
                        else (usdaResult ?: offResult)

        return if (candidate != null) {
            val scale = computeScale(item, candidate)
            ConfirmedEntry(
                displayName = item.foodName,
                brand = item.brand,
                servingSize = candidate.servingText,
                quantity = item.quantity ?: 1.0,
                unit = item.unit ?: "serving",
                weightG = item.weightG,
                weightOz = item.weightOz,
                preparation = item.preparation,
                leanness = item.leanness,
                part = item.part,
                fatContent = item.fatContent,
                calories = candidate.calories * scale,
                proteinGrams = candidate.proteinGrams * scale,
                carbsGrams = candidate.carbsGrams * scale,
                fatGrams = candidate.fatGrams * scale,
                needsManualEntry = false
            )
        } else {
            ConfirmedEntry(
                displayName = item.foodName,
                brand = item.brand,
                servingSize = null,
                quantity = item.quantity ?: 1.0,
                unit = item.unit ?: "serving",
                weightG = item.weightG,
                weightOz = item.weightOz,
                preparation = item.preparation,
                leanness = item.leanness,
                part = item.part,
                fatContent = item.fatContent,
                calories = null,
                proteinGrams = null,
                carbsGrams = null,
                fatGrams = null,
                needsManualEntry = true
            )
        }
    }

    private fun computeScale(item: ParsedFoodItem, candidate: FoodCandidate): Double {
        val weightG = item.weightG ?: item.weightOz?.let { it * 28.3495 }
        return if (weightG != null) {
            weightG / (candidate.servingWeightGrams ?: 100.0)
        } else {
            item.quantity ?: 1.0
        }
    }

    private fun buildUsdaQuery(item: ParsedFoodItem): String =
        listOfNotNull(item.foodName, item.preparation, item.leanness, item.part, item.cookedOrRaw)
            .joinToString(" ")

    private suspend fun trySearchOff(query: String): FoodCandidate? = try {
        Log.d(TAG, "OFF search → '$query'")
        val response = offApi.searchProducts(query = query, pageSize = 3)
        val match = response.products.firstOrNull { p ->
            p.productName != null &&
                (p.nutriments?.caloriesPerServing != null || p.nutriments?.caloriesPer100g != null)
        }?.let { mapOffCandidate(it) }
        Log.d(TAG, "OFF candidate: ${match?.displayName ?: "none"}")
        match
    } catch (e: Exception) {
        Log.w(TAG, "OFF search failed for '$query'", e)
        null
    }

    private suspend fun trySearchUsda(query: String, apiKey: String): FoodCandidate? = try {
        Log.d(TAG, "USDA search → '$query'")
        val response = usdaApi.searchFoods(query = query, apiKey = apiKey, pageSize = 3)
        val match = response.foods.firstOrNull { food ->
            food.foodNutrients.any {
                it.nutrientId == 1008 || it.nutrientName?.contains("Energy", ignoreCase = true) == true
            }
        }?.let { mapUsdaCandidate(it) }
        Log.d(TAG, "USDA candidate: ${match?.displayName ?: "none"}")
        match
    } catch (e: Exception) {
        Log.w(TAG, "USDA search failed for '$query'", e)
        null
    }

    private fun mapOffCandidate(p: OFFProduct): FoodCandidate? {
        val name = p.productName?.takeIf { it.isNotBlank() } ?: return null
        val nm = p.nutriments ?: return null
        val calories = nm.caloriesPerServing ?: nm.caloriesPer100g ?: return null
        val usesPer100g = nm.caloriesPerServing == null
        return FoodCandidate(
            id = "off_${name.hashCode()}",
            displayName = name,
            originalName = name,
            sourceType = SourceType.OPEN_FOOD_FACTS,
            servingText = if (usesPer100g) "100g" else p.servingSize ?: "1 serving",
            servingWeightGrams = if (usesPer100g) 100.0 else null,
            calories = calories,
            proteinGrams = nm.proteinPerServing ?: nm.proteinPer100g ?: 0.0,
            carbsGrams = nm.carbsPerServing ?: nm.carbsPer100g ?: 0.0,
            fatGrams = nm.fatPerServing ?: nm.fatPer100g ?: 0.0,
            exactnessType = ExactnessType.EXACT
        )
    }

    private fun mapUsdaCandidate(food: UsdaFood): FoodCandidate? {
        val calories = food.foodNutrients.firstOrNull { it.nutrientId == 1008 }?.value
            ?: food.foodNutrients.firstOrNull { it.nutrientName?.contains("Energy", true) == true }?.value
            ?: return null
        return FoodCandidate(
            id = "usda_${food.fdcId}",
            displayName = food.description,
            originalName = food.description,
            sourceType = SourceType.USDA,
            servingText = if (food.servingSize != null && food.servingSizeUnit != null)
                "${food.servingSize} ${food.servingSizeUnit}" else "100g",
            servingWeightGrams = food.servingSize
                ?.takeIf { food.servingSizeUnit?.contains("g", ignoreCase = true) == true }
                ?: 100.0,
            calories = calories,
            proteinGrams = food.foodNutrients.firstOrNull { it.nutrientId == 1003 }?.value ?: 0.0,
            carbsGrams = food.foodNutrients.firstOrNull { it.nutrientId == 1005 }?.value ?: 0.0,
            fatGrams = food.foodNutrients.firstOrNull { it.nutrientId == 1004 }?.value ?: 0.0,
            exactnessType = ExactnessType.EXACT
        )
    }
}
