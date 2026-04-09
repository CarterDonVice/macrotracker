package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.SavedFoodDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.model.FoodCategory
import com.localmacrotracker.app.data.model.MealSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SavedFoodsViewModel @Inject constructor(
    private val savedFoodDao: SavedFoodDao,
    private val foodLogDao: FoodLogDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FoodCategory?>(null)
    val selectedCategory: StateFlow<FoodCategory?> = _selectedCategory.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<SavedFoodEntity>> = combine(_query, _selectedCategory) { q, cat ->
        Pair(q, cat)
    }.flatMapLatest { (q, cat) ->
        when {
            q.isBlank() && cat == null -> savedFoodDao.getAllFoods()
            q.isBlank() && cat != null -> savedFoodDao.getFoodsByCategory(cat.name)
            q.isNotBlank() && cat == null -> savedFoodDao.searchFoods(q)
            else -> savedFoodDao.searchFoodsByCategory(q, cat!!.name)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setCategory(cat: FoodCategory?) {
        _selectedCategory.value = cat
    }

    fun addToLog(food: SavedFoodEntity, mealSection: String, logDate: String) {
        viewModelScope.launch {
            val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
            val ms = MealSection.fromName(mealSection)
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
        }
    }

    fun deleteFood(id: Long) {
        viewModelScope.launch {
            savedFoodDao.deleteFoodById(id)
        }
    }
}
