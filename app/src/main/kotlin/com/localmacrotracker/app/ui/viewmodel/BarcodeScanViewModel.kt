package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.providers.OpenFoodFactsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val savedFoodDao: SavedFoodDao,
    private val foodLogDao: FoodLogDao,
    private val openFoodFactsProvider: OpenFoodFactsProvider
) : ViewModel() {

    sealed class ScanResult {
        object Idle : ScanResult()
        /** Barcode found in local DB. `food` is the full saved entity for display. */
        data class Found(
            val savedFoodId: Long,
            val name: String,
            val food: SavedFoodEntity
        ) : ScanResult()
        /** Barcode found via API — not yet saved locally. Flattened for screen convenience. */
        data class NewFood(
            val candidate: FoodCandidate,
            val foodName: String,
            val servingText: String?,
            val calories: Double,
            val protein: Double,
            val carbs: Double,
            val fat: Double
        ) : ScanResult()
        object NotFound : ScanResult()
        data class Error(val message: String) : ScanResult()
    }

    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()

    // Screen-facing overloads that take raw String params (from Navigation args)
    fun onBarcodeScanned(barcode: String) {
        onBarcodeScanned(barcode, null, null)
    }

    /** Add already-found (local DB) food to log. Called from Found state. */
    fun addToLog(mealSection: String, logDate: String) {
        val result = _scanResult.value as? ScanResult.Found ?: return
        viewModelScope.launch {
            try {
                val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
                val ms = MealSection.fromName(mealSection)
                val food = savedFoodDao.getFoodById(result.savedFoodId) ?: return@launch
                val entry = FoodLogEntryEntity(
                    logDate = date.toString(),
                    mealSection = ms.name,
                    linkedSavedFoodId = food.id,
                    displayNameSnapshot = food.displayName,
                    servingTextSnapshot = food.servingText,
                    quantity = 1.0,
                    unit = "serving",
                    caloriesExact = food.calories,
                    proteinExact = food.proteinGrams,
                    carbsExact = food.carbsGrams,
                    fatExact = food.fatGrams,
                    isEstimated = false,
                    needsManualSaveReminder = false,
                    sourceTypeSnapshot = food.sourceType
                )
                foodLogDao.insertEntry(entry)
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error(e.message ?: "Failed to add to log")
            }
        }
    }

    /** Add new externally-found food to log, optionally saving first. Called from NewFood state. */
    fun saveAndAddToLog(mealSection: String, logDate: String, save: Boolean) {
        val result = _scanResult.value as? ScanResult.NewFood ?: return
        val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
        val ms = MealSection.fromName(mealSection)
        addToLog(result.candidate, ms, date, save)
    }

    fun reset() { resetScanResult() }

    fun onBarcodeScanned(barcode: String, mealSection: MealSection?, logDate: LocalDate?) {
        viewModelScope.launch {
            try {
                // 1. Check local DB first
                val local = savedFoodDao.getFoodByBarcode(barcode)
                if (local != null) {
                    _scanResult.value = ScanResult.Found(
                        savedFoodId = local.id,
                        name = local.displayName,
                        food = local
                    )
                    return@launch
                }

                // 2. Try OpenFoodFacts barcode lookup
                val candidate = openFoodFactsProvider.lookup(barcode)
                if (candidate != null) {
                    _scanResult.value = ScanResult.NewFood(
                        candidate = candidate,
                        foodName = candidate.displayName,
                        servingText = candidate.servingText,
                        calories = candidate.calories,
                        protein = candidate.proteinGrams,
                        carbs = candidate.carbsGrams,
                        fat = candidate.fatGrams
                    )
                } else {
                    _scanResult.value = ScanResult.NotFound
                }
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error(e.message ?: "Barcode lookup failed")
            }
        }
    }

    fun addToLog(
        candidate: FoodCandidate,
        mealSection: MealSection,
        logDate: LocalDate,
        save: Boolean
    ) {
        viewModelScope.launch {
            try {
                val savedFoodId: Long? = if (save) {
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
                        exactnessType = candidate.exactnessType.name,
                        sourceType = candidate.sourceType.name,
                        sourceUrl = candidate.sourceUrl
                    )
                    savedFoodDao.insertFood(entity)
                } else {
                    null
                }

                val entry = FoodLogEntryEntity(
                    logDate = logDate.toString(),
                    mealSection = mealSection.name,
                    linkedSavedFoodId = savedFoodId,
                    displayNameSnapshot = candidate.displayName,
                    originalNameSnapshot = candidate.originalName,
                    servingTextSnapshot = candidate.servingText,
                    quantity = 1.0,
                    unit = "serving",
                    caloriesExact = candidate.calories,
                    proteinExact = candidate.proteinGrams,
                    carbsExact = candidate.carbsGrams,
                    fatExact = candidate.fatGrams,
                    isEstimated = false,
                    needsManualSaveReminder = !save,
                    sourceTypeSnapshot = candidate.sourceType.name
                )
                foodLogDao.insertEntry(entry)
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error(e.message ?: "Failed to add to log")
            }
        }
    }

    fun resetScanResult() {
        _scanResult.value = ScanResult.Idle
    }
}
