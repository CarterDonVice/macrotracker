package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.network.providers.LocalSavedFoodProvider
import com.localmacrotracker.app.data.network.providers.OpenFoodFactsProvider
import com.localmacrotracker.app.data.network.providers.UsdaProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val localProvider: LocalSavedFoodProvider,
    private val usdaProvider: UsdaProvider,
    private val offProvider: OpenFoodFactsProvider,
    private val foodLogDao: FoodLogDao,
    private val savedFoodDao: SavedFoodDao
) : ViewModel() {

    data class SearchState(
        val query: String = "",
        val localResults: List<FoodCandidate> = emptyList(),
        val remoteResults: List<FoodCandidate> = emptyList(),
        val isLoadingLocal: Boolean = false,
        val isLoadingRemote: Boolean = false,
        val error: String? = null
    )

    sealed class AddResult {
        object Idle : AddResult()
        object Success : AddResult()
        data class Error(val message: String) : AddResult()
    }

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _addResult = MutableStateFlow<AddResult>(AddResult.Idle)
    val addResult: StateFlow<AddResult> = _addResult.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300L)
            _state.update { it.copy(isLoadingLocal = true, isLoadingRemote = true) }

            launch {
                val local = try { localProvider.search(query) } catch (_: Exception) { emptyList() }
                _state.update { it.copy(localResults = local, isLoadingLocal = false) }
            }

            launch {
                val usda = try { usdaProvider.search(query) } catch (_: Exception) { emptyList() }
                val off = try { offProvider.search(query) } catch (_: Exception) { emptyList() }
                _state.update {
                    it.copy(
                        remoteResults = (usda + off).distinctBy { c -> c.id },
                        isLoadingRemote = false
                    )
                }
            }
        }
    }

    fun addToLog(
        candidate: FoodCandidate,
        quantity: Double,
        mealSection: MealSection,
        logDate: LocalDate,
        saveFood: Boolean
    ) {
        viewModelScope.launch {
            try {
                val savedFoodId: Long? = when {
                    candidate.isLocalSaved -> candidate.savedFoodId
                    saveFood -> {
                        val entity = SavedFoodEntity(
                            displayName = candidate.displayName,
                            originalName = candidate.originalName,
                            category = FoodCategory.PREMADE_FOOD.name,
                            barcode = candidate.barcode,
                            searchIndexText = candidate.displayName.lowercase(),
                            servingText = candidate.servingText,
                            servingWeightGrams = candidate.servingWeightGrams,
                            servingVolumeMl = candidate.servingVolumeMl,
                            calories = candidate.calories,
                            proteinGrams = candidate.proteinGrams,
                            carbsGrams = candidate.carbsGrams,
                            fatGrams = candidate.fatGrams,
                            exactnessType = ExactnessType.EXACT.name,
                            sourceType = candidate.sourceType.name,
                            sourceUrl = candidate.sourceUrl
                        )
                        savedFoodDao.insertFood(entity)
                    }
                    else -> null
                }

                val entry = FoodLogEntryEntity(
                    logDate = logDate.toString(),
                    mealSection = mealSection.name,
                    linkedSavedFoodId = savedFoodId,
                    displayNameSnapshot = candidate.displayName,
                    originalNameSnapshot = candidate.originalName,
                    servingTextSnapshot = candidate.servingText,
                    quantity = quantity,
                    unit = "serving",
                    caloriesExact = candidate.calories * quantity,
                    proteinExact = candidate.proteinGrams * quantity,
                    carbsExact = candidate.carbsGrams * quantity,
                    fatExact = candidate.fatGrams * quantity,
                    isEstimated = false,
                    needsManualSaveReminder = !saveFood && !candidate.isLocalSaved,
                    sourceTypeSnapshot = candidate.sourceType.name
                )
                foodLogDao.insertEntry(entry)
                _addResult.value = AddResult.Success
            } catch (e: Exception) {
                _addResult.value = AddResult.Error(e.message ?: "Failed to add to log")
            }
        }
    }

    fun resetAddResult() { _addResult.value = AddResult.Idle }
}
