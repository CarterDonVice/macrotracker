package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedFoodDetailViewModel @Inject constructor(
    private val savedFoodDao: SavedFoodDao
) : ViewModel() {

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName

    private val _servingText = MutableStateFlow("")
    val servingText: StateFlow<String> = _servingText

    private val _servingWeightGrams = MutableStateFlow("")
    val servingWeightGrams: StateFlow<String> = _servingWeightGrams

    private val _servingVolumeMl = MutableStateFlow("")
    val servingVolumeMl: StateFlow<String> = _servingVolumeMl

    private val _calories = MutableStateFlow("")
    val calories: StateFlow<String> = _calories

    private val _protein = MutableStateFlow("")
    val protein: StateFlow<String> = _protein

    private val _carbs = MutableStateFlow("")
    val carbs: StateFlow<String> = _carbs

    private val _fat = MutableStateFlow("")
    val fat: StateFlow<String> = _fat

    private val _barcode = MutableStateFlow<String?>(null)
    val barcode: StateFlow<String?> = _barcode

    private val _sourceType = MutableStateFlow<String?>(null)
    val sourceType: StateFlow<String?> = _sourceType

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess

    private var currentFood: SavedFoodEntity? = null

    fun loadFood(foodId: Long) {
        viewModelScope.launch {
            val food = savedFoodDao.getFoodById(foodId) ?: return@launch
            currentFood = food
            _displayName.value = food.displayName
            _servingText.value = food.servingText ?: ""
            _servingWeightGrams.value = food.servingWeightGrams?.toString() ?: ""
            _servingVolumeMl.value = food.servingVolumeMl?.toString() ?: ""
            _calories.value = food.calories.toString()
            _protein.value = food.proteinGrams.toString()
            _carbs.value = food.carbsGrams.toString()
            _fat.value = food.fatGrams.toString()
            _barcode.value = food.barcode
            _sourceType.value = food.sourceType
        }
    }

    fun setDisplayName(v: String) { _displayName.value = v }
    fun setServingText(v: String) { _servingText.value = v }
    fun setServingWeight(v: String) { _servingWeightGrams.value = v }
    fun setServingVolume(v: String) { _servingVolumeMl.value = v }
    fun setCalories(v: String) { _calories.value = v }
    fun setProtein(v: String) { _protein.value = v }
    fun setCarbs(v: String) { _carbs.value = v }
    fun setFat(v: String) { _fat.value = v }

    fun deleteFood() {
        val food = currentFood ?: return
        viewModelScope.launch {
            savedFoodDao.deleteFoodById(food.id)
            _deleteSuccess.value = true
        }
    }

    fun saveFood() {
        val food = currentFood ?: return
        _isSaving.value = true
        viewModelScope.launch {
            val updated = food.copy(
                displayName = _displayName.value.trim(),
                searchIndexText = _displayName.value.trim().lowercase(),
                servingText = _servingText.value.takeIf { it.isNotBlank() },
                servingWeightGrams = _servingWeightGrams.value.toDoubleOrNull(),
                servingVolumeMl = _servingVolumeMl.value.toDoubleOrNull(),
                calories = _calories.value.toDoubleOrNull() ?: food.calories,
                proteinGrams = _protein.value.toDoubleOrNull() ?: food.proteinGrams,
                carbsGrams = _carbs.value.toDoubleOrNull() ?: food.carbsGrams,
                fatGrams = _fat.value.toDoubleOrNull() ?: food.fatGrams,
                updatedAt = System.currentTimeMillis()
            )
            savedFoodDao.updateFood(updated)
            _isSaving.value = false
            _saveSuccess.value = true
        }
    }
}
