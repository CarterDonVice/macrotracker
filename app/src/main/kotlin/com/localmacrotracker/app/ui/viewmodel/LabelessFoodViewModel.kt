package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.domain.FoodLookupOrchestrator
import com.localmacrotracker.app.llm.LocalInferenceEngine
import com.localmacrotracker.app.llm.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LabelessFoodViewModel @Inject constructor(
    private val foodLookupOrchestrator: FoodLookupOrchestrator,
    private val foodLogDao: FoodLogDao,
    private val inferenceEngine: LocalInferenceEngine
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Processing : UiState()
        data class Results(val entries: List<FoodLogEntryEntity>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isModelReady: StateFlow<Boolean> = MutableStateFlow(inferenceEngine.status == ModelStatus.READY)

    fun submit(input: String, mealSection: MealSection, logDate: LocalDate) {
        viewModelScope.launch {
            _uiState.value = UiState.Processing
            try {
                val resolved = foodLookupOrchestrator.resolveTextInput(
                    userInput = input,
                    mealSection = mealSection,
                    logDate = logDate
                )
                val ids = foodLogDao.insertEntries(resolved)
                val inserted = resolved.mapIndexed { i, entry -> entry.copy(id = ids[i]) }
                _uiState.value = UiState.Results(inserted)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}
