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

    data class LabelDraft(
        val servingText: String = "",
        val calories: String = "",
        val protein: String = "",
        val carbs: String = "",
        val fat: String = ""
    )

    private val _draft = MutableStateFlow(LabelDraft())
    val draft: StateFlow<LabelDraft> = _draft.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun setDraft(draft: LabelDraft) {
        _draft.value = draft
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
    }

    /** Called by screen after OCR text is extracted. Parses and sets draft. */
    fun parseOcrText(ocrText: String) {
        val parsed = ocrLabelParser.parse(ocrText)
        if (_displayName.value.isBlank()) {
            _displayName.value = parsed.suggestedName ?: ""
        }
        _draft.value = LabelDraft(
            servingText = parsed.servingText ?: "",
            calories = parsed.calories?.toString() ?: "",
            protein = parsed.proteinGrams?.toString() ?: "",
            carbs = parsed.carbsGrams?.toString() ?: "",
            fat = parsed.fatGrams?.toString() ?: ""
        )
    }

    /** Alias used by some screen variants. */
    fun setDraftFromOcr(ocrText: String) = parseOcrText(ocrText)

    fun saveAndAddToLog(mealSection: String, logDate: String, save: Boolean) {
        val ms = MealSection.fromName(mealSection)
        val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
        saveAndAddToLog(ms, date, save)
    }

    fun saveAndAddToLog(mealSection: MealSection, logDate: LocalDate, save: Boolean) {
        val currentDraft = _draft.value
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val name = _displayName.value.ifBlank { "Unknown Food" }
                val calories = currentDraft.calories.toDoubleOrNull() ?: 0.0
                val protein = currentDraft.protein.toDoubleOrNull() ?: 0.0
                val carbs = currentDraft.carbs.toDoubleOrNull() ?: 0.0
                val fat = currentDraft.fat.toDoubleOrNull() ?: 0.0
                val servingText = currentDraft.servingText.takeIf { it.isNotBlank() }

                val savedFoodId: Long? = if (save) {
                    val entity = SavedFoodEntity(
                        displayName = name,
                        category = FoodCategory.PREMADE_FOOD.name,
                        searchIndexText = name.lowercase(),
                        servingText = servingText,
                        servingWeightGrams = null,
                        calories = calories,
                        proteinGrams = protein,
                        carbsGrams = carbs,
                        fatGrams = fat,
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
                    servingTextSnapshot = servingText,
                    quantity = 1.0,
                    unit = "serving",
                    caloriesExact = calories,
                    proteinExact = protein,
                    carbsExact = carbs,
                    fatExact = fat,
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
