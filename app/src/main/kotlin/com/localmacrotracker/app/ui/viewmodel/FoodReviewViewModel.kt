package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class FoodReviewViewModel @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val savedFoodDao: SavedFoodDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>("entry_id") ?: 0L

    private val _entry = MutableStateFlow<FoodLogEntryEntity?>(null)
    val entry: StateFlow<FoodLogEntryEntity?> = _entry.asStateFlow()

    // Editable fields.
    // calories/protein/carbs/fat below are the PER-SERVING base values. The logged
    // total is base * quantity, recomputed live whenever the serving amount changes.
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _calories = MutableStateFlow(0.0)
    val calories: StateFlow<Double> = _calories.asStateFlow()

    private val _protein = MutableStateFlow(0.0)
    val protein: StateFlow<Double> = _protein.asStateFlow()

    private val _carbs = MutableStateFlow(0.0)
    val carbs: StateFlow<Double> = _carbs.asStateFlow()

    private val _fat = MutableStateFlow(0.0)
    val fat: StateFlow<Double> = _fat.asStateFlow()

    private val _quantity = MutableStateFlow(1.0)
    val quantity: StateFlow<Double> = _quantity.asStateFlow()

    private val _unit = MutableStateFlow("serving")
    val unit: StateFlow<String> = _unit.asStateFlow()

    // Live-scaled totals (base * quantity). Calories rounded to the nearest whole number.
    val scaledCalories: StateFlow<Int> = combine(_calories, _quantity) { c, q ->
        (c * q).roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scaledProtein: StateFlow<Double> = combine(_protein, _quantity) { p, q ->
        p * q
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val scaledCarbs: StateFlow<Double> = combine(_carbs, _quantity) { c, q ->
        c * q
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val scaledFat: StateFlow<Double> = combine(_fat, _quantity) { f, q ->
        f * q
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = foodLogDao.getEntryById(entryId)
            _entry.value = loaded
            if (loaded != null) {
                // Stored macros are the total for the logged quantity. Divide back out to
                // a per-serving base so we can re-scale live when the user edits the amount.
                val qty = loaded.quantity.takeIf { it > 0.0 } ?: 1.0
                val totalCal = loaded.caloriesExact ?: loaded.caloriesMin ?: 0.0
                val totalPro = loaded.proteinExact ?: loaded.proteinMin ?: 0.0
                val totalCarb = loaded.carbsExact ?: loaded.carbsMin ?: 0.0
                val totalFat = loaded.fatExact ?: loaded.fatMin ?: 0.0
                _displayName.value = loaded.displayNameSnapshot
                _calories.value = totalCal / qty
                _protein.value = totalPro / qty
                _carbs.value = totalCarb / qty
                _fat.value = totalFat / qty
                _quantity.value = loaded.quantity
                _unit.value = loaded.unit
            }
        }
    }

    fun setDisplayName(name: String) { _displayName.value = name }
    fun setCalories(value: Double) { _calories.value = value }
    fun setProtein(value: Double) { _protein.value = value }
    fun setCarbs(value: Double) { _carbs.value = value }
    fun setFat(value: Double) { _fat.value = value }
    fun setQuantity(value: Double) { _quantity.value = value }
    fun setUnit(value: String) { _unit.value = value }

    fun saveEntry() {
        val current = _entry.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Persist the live-scaled totals (base * quantity). Calories rounded to
                // the nearest whole number per the user's preference.
                val qty = _quantity.value
                val updated = current.copy(
                    displayNameSnapshot = _displayName.value,
                    quantity = qty,
                    unit = _unit.value,
                    caloriesExact = (_calories.value * qty).roundToInt().toDouble(),
                    proteinExact = _protein.value * qty,
                    carbsExact = _carbs.value * qty,
                    fatExact = _fat.value * qty,
                    caloriesMin = null,
                    caloriesMax = null,
                    proteinMin = null,
                    proteinMax = null,
                    carbsMin = null,
                    carbsMax = null,
                    fatMin = null,
                    fatMax = null,
                    isEstimated = false,
                    updatedAt = System.currentTimeMillis()
                )
                foodLogDao.updateEntry(updated)
                _entry.value = updated
                _saveSuccess.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveLinkedFood() {
        val current = _entry.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val name = _displayName.value.ifBlank { current.displayNameSnapshot }
                val newSavedFood = SavedFoodEntity(
                    displayName = name,
                    originalName = current.originalNameSnapshot,
                    category = FoodCategory.PREMADE_FOOD.name,
                    searchIndexText = name.lowercase(),
                    servingText = current.servingTextSnapshot,
                    calories = _calories.value,
                    proteinGrams = _protein.value,
                    carbsGrams = _carbs.value,
                    fatGrams = _fat.value,
                    exactnessType = "EXACT",
                    sourceType = current.sourceTypeSnapshot ?: SourceType.MANUAL.name
                )
                val savedFoodId = savedFoodDao.insertFood(newSavedFood)
                foodLogDao.linkEntryToSavedFood(
                    entryId = current.id,
                    savedFoodId = savedFoodId
                )
                val linked = current.copy(
                    linkedSavedFoodId = savedFoodId,
                    needsManualSaveReminder = false,
                    updatedAt = System.currentTimeMillis()
                )
                _entry.value = linked
                _saveSuccess.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}
