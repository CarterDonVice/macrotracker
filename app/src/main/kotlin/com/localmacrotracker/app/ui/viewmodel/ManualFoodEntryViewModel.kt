package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
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
    private val foodLogDao: FoodLogDao
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Saving : UiState()
        object Saved : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun save(
        mealSection: MealSection,
        logDate: LocalDate,
        name: String,
        servingText: String,
        calories: Double?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?
    ) {
        if (name.isBlank()) {
            _uiState.value = UiState.Error("Food name is required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Saving
            try {
                foodLogDao.insertEntry(
                    FoodLogEntryEntity(
                        logDate = logDate.toString(),
                        mealSection = mealSection.name,
                        displayNameSnapshot = name.trim(),
                        servingTextSnapshot = servingText.ifBlank { null },
                        quantity = 1.0,
                        unit = "serving",
                        caloriesExact = calories,
                        proteinExact = proteinGrams,
                        carbsExact = carbsGrams,
                        fatExact = fatGrams,
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
