package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.OcrLabelParser
import com.localmacrotracker.app.data.network.ParsedNutritionDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class NutritionLabelViewModel @Inject constructor(
    private val savedFoodDao: SavedFoodDao,
    private val foodLogDao: FoodLogDao,
    private val ocrLabelParser: OcrLabelParser
) : ViewModel() {

    private val _draft = MutableStateFlow<ParsedNutritionDraft?>(null)
    val draft: StateFlow<ParsedNutritionDraft?> = _draft.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun setDraft(draft: ParsedNutritionDraft) {
        _draft.value = draft
        if (_displayName.value.isBlank()) {
            _displayName.value = draft.suggestedName ?: ""
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
    }

    /** Called by screen after OCR text is extracted. Parses and sets draft. */
    fun parseOcrText(ocrText: String) {
        val parsed = ocrLabelParser.parse(ocrText)
        setDraft(parsed)
    }

    /** Alias used by some screen variants. */
    fun setDraftFromOcr(ocrText: String) = parseOcrText(ocrText)

    fun saveAndAddToLog(mealSection: MealSection, logDate: LocalDate, save: Boolean) {
        val currentDraft = _draft.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val name = _displayName.value.ifBlank { currentDraft.suggestedName ?: "Unknown Food" }

                val savedFoodId: Long? = if (save) {
                    val entity = SavedFoodEntity(
                        displayName = name,
                        category = FoodCategory.PREMADE_FOOD.name,
                        searchIndexText = name.lowercase(),
                        servingText = currentDraft.servingText,
                        servingWeightGrams = currentDraft.servingWeightGrams,
                        calories = currentDraft.calories ?: 0.0,
                        proteinGrams = currentDraft.proteinGrams ?: 0.0,
                        carbsGrams = currentDraft.carbsGrams ?: 0.0,
                        fatGrams = currentDraft.fatGrams ?: 0.0,
                        exactnessType = "EXACT",
                        sourceType = SourceType.OCR.name
                    )
                    savedFoodDao.insertFood(entity)
                } else {
                    null
                }

                val entry = FoodLogEntryEntity(
                    logDate = logDate.toString(),
                    mealSection = mealSection.name,
                    linkedSavedFoodId = savedFoodId,
                    displayNameSnapshot = name,
                    servingTextSnapshot = currentDraft.servingText,
                    quantity = 1.0,
                    unit = "serving",
                    caloriesExact = currentDraft.calories,
                    proteinExact = currentDraft.proteinGrams,
                    carbsExact = currentDraft.carbsGrams,
                    fatExact = currentDraft.fatGrams,
                    isEstimated = false,
                    needsManualSaveReminder = !save,
                    sourceTypeSnapshot = SourceType.OCR.name
                )
                foodLogDao.insertEntry(entry)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
