package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MeasurementUnit
import com.localmacrotracker.app.data.model.MeasurementUnits
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.model.UnitKind
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
    // calories/protein/carbs/fat are the PER-SERVING base values. The logged total is
    // base * factor, where the factor depends on the chosen amount AND unit: a count
    // unit scales by the amount directly, while a weight/volume unit scales by how the
    // entered amount compares to the food's reference weight/volume for one serving.
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

    // Grams / millilitres in ONE serving of this food (null when the food has no such
    // reference, e.g. a per-serving-only item). These enable weight/volume scaling.
    private val _refWeightGrams = MutableStateFlow<Double?>(null)
    val refWeightGrams: StateFlow<Double?> = _refWeightGrams.asStateFlow()

    private val _refVolumeMl = MutableStateFlow<Double?>(null)
    val refVolumeMl: StateFlow<Double?> = _refVolumeMl.asStateFlow()

    // The food's "count" unit (serving / piece / slice …) used when not measuring by mass.
    private val _servingUnitLabel = MutableStateFlow("serving")

    /** Units offered in the editor: the count unit, plus weight/volume units the food supports. */
    val availableUnits: StateFlow<List<MeasurementUnit>> =
        combine(_refWeightGrams, _refVolumeMl, _servingUnitLabel) { w, v, serving ->
            buildList {
                add(MeasurementUnit(serving, serving, UnitKind.COUNT))
                if (w != null && w > 0) {
                    addAll(MeasurementUnits.ALL.filter {
                        it.kind == UnitKind.WEIGHT && it.label in WEIGHT_UNIT_LABELS
                    })
                }
                if (v != null && v > 0) {
                    addAll(MeasurementUnits.ALL.filter {
                        it.kind == UnitKind.VOLUME && it.label in VOLUME_UNIT_LABELS
                    })
                }
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            listOf(MeasurementUnit("serving", "serving", UnitKind.COUNT))
        )

    // Portion factor (in "servings") for the current amount + unit.
    private val factor: StateFlow<Double> =
        combine(_quantity, _unit, _refWeightGrams, _refVolumeMl) { amt, lbl, _, _ ->
            factorFor(amt, lbl)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    // Live-scaled totals. Calories rounded to the nearest whole number.
    val scaledCalories: StateFlow<Int> = combine(_calories, factor) { c, f ->
        (c * f).roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val scaledProtein: StateFlow<Double> = combine(_protein, factor) { p, f ->
        p * f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val scaledCarbs: StateFlow<Double> = combine(_carbs, factor) { c, f ->
        c * f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val scaledFat: StateFlow<Double> = combine(_fat, factor) { f0, f ->
        f0 * f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = foodLogDao.getEntryById(entryId)
            if (loaded != null) {
                val saved = loaded.linkedSavedFoodId?.let { savedFoodDao.getFoodById(it) }
                val qty = loaded.quantity.takeIf { it > 0.0 } ?: 1.0
                val totalCal = loaded.caloriesExact ?: loaded.caloriesMin ?: 0.0
                val totalPro = loaded.proteinExact ?: loaded.proteinMin ?: 0.0
                val totalCarb = loaded.carbsExact ?: loaded.carbsMin ?: 0.0
                val totalFat = loaded.fatExact ?: loaded.fatMin ?: 0.0
                _displayName.value = loaded.displayNameSnapshot
                _servingUnitLabel.value = countUnitLabel(loaded.unit)

                if (saved != null) {
                    // Canonical per-serving base + reference measures from the saved food.
                    _calories.value = saved.calories
                    _protein.value = saved.proteinGrams
                    _carbs.value = saved.carbsGrams
                    _fat.value = saved.fatGrams
                    _refWeightGrams.value = saved.servingWeightGrams
                    _refVolumeMl.value = saved.servingVolumeMl

                    // How many servings the entry currently represents — derived from the
                    // calorie ratio so it's robust to however unit/quantity was stored.
                    val servings = if (saved.calories > 0) totalCal / saved.calories else qty
                    val savedUnit = MeasurementUnits.ALL.firstOrNull { it.label.equals(loaded.unit, true) }
                    when {
                        savedUnit?.kind == UnitKind.WEIGHT && (saved.servingWeightGrams ?: 0.0) > 0 -> {
                            _unit.value = savedUnit.label
                            _quantity.value = servings * saved.servingWeightGrams!! / (savedUnit.grams ?: 1.0)
                        }
                        savedUnit?.kind == UnitKind.VOLUME && (saved.servingVolumeMl ?: 0.0) > 0 -> {
                            _unit.value = savedUnit.label
                            _quantity.value = servings * saved.servingVolumeMl!! / (savedUnit.milliliters ?: 1.0)
                        }
                        (saved.servingWeightGrams ?: 0.0) > 0 -> {
                            _unit.value = "g"
                            _quantity.value = servings * saved.servingWeightGrams!!
                        }
                        (saved.servingVolumeMl ?: 0.0) > 0 -> {
                            _unit.value = "ml"
                            _quantity.value = servings * saved.servingVolumeMl!!
                        }
                        else -> {
                            _unit.value = _servingUnitLabel.value
                            _quantity.value = servings
                        }
                    }
                } else {
                    // No linked food: per-serving base from the entry totals, count scaling only.
                    _calories.value = totalCal / qty
                    _protein.value = totalPro / qty
                    _carbs.value = totalCarb / qty
                    _fat.value = totalFat / qty
                    _refWeightGrams.value = null
                    _refVolumeMl.value = null
                    _unit.value = _servingUnitLabel.value
                    _quantity.value = qty
                }
            }
            // Publish the entry last, so the screen only leaves its loading state once every
            // field above is populated (its input buffers seed from these values once).
            _entry.value = loaded
        }
    }

    fun setDisplayName(name: String) { _displayName.value = name }
    fun setCalories(value: Double) { _calories.value = value }
    fun setProtein(value: Double) { _protein.value = value }
    fun setCarbs(value: Double) { _carbs.value = value }
    fun setFat(value: Double) { _fat.value = value }
    fun setQuantity(value: Double) { _quantity.value = value }

    /** Switch units, converting the amount so the same number of servings stays logged. */
    fun setUnit(value: String) {
        if (value.equals(_unit.value, true)) return
        val servings = factorFor(_quantity.value, _unit.value)
        _unit.value = value
        _quantity.value = amountForServings(servings, value)
    }

    fun saveEntry() {
        val current = _entry.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Persist the live-scaled totals (base * factor). Calories rounded to the
                // nearest whole number per the user's preference.
                val amount = _quantity.value
                val f = factorFor(amount, _unit.value)
                val updated = current.copy(
                    displayNameSnapshot = _displayName.value,
                    quantity = amount,
                    unit = _unit.value,
                    caloriesExact = (_calories.value * f).roundToInt().toDouble(),
                    proteinExact = _protein.value * f,
                    carbsExact = _carbs.value * f,
                    fatExact = _fat.value * f,
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
                // Dedup by name so "Save to Database" on an already-saved food reuses it.
                val savedFoodId = savedFoodDao.getFoodByExactName(name)?.id
                    ?: savedFoodDao.insertFood(
                        SavedFoodEntity(
                            displayName = name,
                            originalName = current.originalNameSnapshot,
                            category = FoodCategory.PREMADE_FOOD.name,
                            searchIndexText = name.lowercase(),
                            servingText = current.servingTextSnapshot,
                            servingWeightGrams = _refWeightGrams.value,
                            servingVolumeMl = _refVolumeMl.value,
                            calories = _calories.value,
                            proteinGrams = _protein.value,
                            carbsGrams = _carbs.value,
                            fatGrams = _fat.value,
                            exactnessType = "EXACT",
                            sourceType = current.sourceTypeSnapshot ?: SourceType.MANUAL.name
                        )
                    )
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

    /** Portion factor (in servings) for an amount expressed in the given unit. */
    private fun factorFor(amount: Double, unitLabel: String): Double {
        val u = MeasurementUnits.ALL.firstOrNull { it.label.equals(unitLabel, true) }
        return when (u?.kind) {
            UnitKind.WEIGHT -> {
                val ref = _refWeightGrams.value
                if (ref != null && ref > 0) amount * (u.grams ?: 1.0) / ref else amount
            }
            UnitKind.VOLUME -> {
                val ref = _refVolumeMl.value
                if (ref != null && ref > 0) amount * (u.milliliters ?: 1.0) / ref else amount
            }
            else -> amount // COUNT unit or unknown label → amount is the serving count
        }
    }

    /** Amount, expressed in [unitLabel], that corresponds to the given number of servings. */
    private fun amountForServings(servings: Double, unitLabel: String): Double {
        val u = MeasurementUnits.ALL.firstOrNull { it.label.equals(unitLabel, true) }
        return when (u?.kind) {
            UnitKind.WEIGHT -> {
                val ref = _refWeightGrams.value
                if (ref != null && ref > 0) servings * ref / (u.grams ?: 1.0) else servings
            }
            UnitKind.VOLUME -> {
                val ref = _refVolumeMl.value
                if (ref != null && ref > 0) servings * ref / (u.milliliters ?: 1.0) else servings
            }
            else -> servings
        }
    }

    /** The food's count unit: keep a count label as-is, but map a weight/volume label to "serving". */
    private fun countUnitLabel(label: String): String {
        val u = MeasurementUnits.ALL.firstOrNull { it.label.equals(label, true) }
        return if (u == null || u.kind == UnitKind.COUNT) label.ifBlank { "serving" } else "serving"
    }

    private companion object {
        val WEIGHT_UNIT_LABELS = setOf("g", "oz", "lb", "kg")
        val VOLUME_UNIT_LABELS = setOf("ml", "fl oz", "cup", "tbsp", "tsp", "L")
    }
}
