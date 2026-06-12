package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ManualFoodEntryViewModel @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val savedFoodDao: SavedFoodDao
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Saving : UiState()
        object Saved : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Saves a manually entered food to the reusable library AND logs it for the day.
     * Always saves to the library (duplicates allowed) so the food is searchable later.
     *
     * @param servingText human-readable serving, e.g. "10 g" or "1 cup"
     * @param unitLabel   the chosen unit's short label, stored on the log entry
     * @param servingWeightGrams grams in one serving when the unit is a weight unit
     * @param servingVolumeMl    ml in one serving when the unit is a volume unit
     */
    fun save(
        mealSection: MealSection,
        logDate: LocalDate,
        name: String,
        servingText: String?,
        unitLabel: String?,
        servingWeightGrams: Double?,
        servingVolumeMl: Double?,
        calories: Double?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?
    ) {
        if (name.isBlank()) {
            _uiState.value = UiState.Error("Food name is required.")
            return
        }
        val cleanName = name.trim()
        val cal = calories ?: 0.0
        val pro = proteinGrams ?: 0.0
        val carb = carbsGrams ?: 0.0
        val fat = fatGrams ?: 0.0

        viewModelScope.launch {
            _uiState.value = UiState.Saving
            try {
                val savedFoodId = savedFoodDao.insertFood(
                    SavedFoodEntity(
                        displayName = cleanName,
                        originalName = cleanName,
                        category = FoodCategory.PREMADE_FOOD.name,
                        searchIndexText = cleanName.lowercase(),
                        servingText = servingText,
                        servingWeightGrams = servingWeightGrams,
                        servingVolumeMl = servingVolumeMl,
                        calories = cal,
                        proteinGrams = pro,
                        carbsGrams = carb,
                        fatGrams = fat,
                        exactnessType = ExactnessType.EXACT.name,
                        sourceType = SourceType.MANUAL.name
                    )
                )
                foodLogDao.insertEntry(
                    FoodLogEntryEntity(
                        logDate = logDate.toString(),
                        mealSection = mealSection.name,
                        linkedSavedFoodId = savedFoodId,
                        displayNameSnapshot = cleanName,
                        servingTextSnapshot = servingText,
                        quantity = 1.0,
                        unit = unitLabel ?: "serving",
                        caloriesExact = cal,
                        proteinExact = pro,
                        carbsExact = carb,
                        fatExact = fat,
                        isEstimated = false,
                        needsManualSaveReminder = false,
                        sourceTypeSnapshot = SourceType.MANUAL.name
                    )
                )
                _uiState.value = UiState.Saved
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to save entry")
            }
        }
    }

    fun resetState() { _uiState.value = UiState.Idle }
}
