package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.dao.RecipeDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.RecipeEntity
import com.localmacrotracker.app.data.db.entities.RecipeIngredientEntity
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.domain.RecipeCalculator
import com.localmacrotracker.app.domain.ServingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    private val foodLogDao: FoodLogDao
) : ViewModel() {

    private val _recipeName = MutableStateFlow("")
    val recipeName: StateFlow<String> = _recipeName.asStateFlow()

    private val _servingsMade = MutableStateFlow(1.0)
    val servingsMade: StateFlow<Double> = _servingsMade.asStateFlow()

    private val _ingredients = MutableStateFlow<List<RecipeIngredientEntity>>(emptyList())
    val ingredients: StateFlow<List<RecipeIngredientEntity>> = _ingredients.asStateFlow()

    val totals: StateFlow<RecipeCalculator.RecipeTotals> =
        combine(_ingredients, _servingsMade) { ingredients, servings ->
            RecipeCalculator.calculate(ingredients, servings)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecipeCalculator.calculate(emptyList(), 1.0)
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savedRecipeId = MutableStateFlow<Long?>(null)
    val savedRecipeId: StateFlow<Long?> = _savedRecipeId.asStateFlow()

    fun setName(name: String) {
        _recipeName.value = name
    }

    fun setServings(servings: Double) {
        _servingsMade.value = if (servings > 0.0) servings else 1.0
    }

    fun addIngredient(candidate: FoodCandidate, quantity: Double = 1.0, unit: String = "serving") {
        val scaled = ServingMath.computePortionFactor(
            sourceWeightGrams = candidate.servingWeightGrams,
            requestedQuantity = quantity,
            requestedUnit = unit
        ).let { factor ->
            ServingMath.scaleMacros(
                ServingMath.MacroSet(
                    calories = candidate.calories,
                    proteinGrams = candidate.proteinGrams,
                    carbsGrams = candidate.carbsGrams,
                    fatGrams = candidate.fatGrams
                ),
                factor
            )
        }

        val ingredient = RecipeIngredientEntity(
            recipeId = 0, // Will be assigned on save
            linkedSavedFoodId = candidate.savedFoodId,
            displayNameSnapshot = candidate.displayName,
            quantity = quantity,
            unit = unit,
            calories = scaled.calories,
            protein = scaled.proteinGrams,
            carbs = scaled.carbsGrams,
            fat = scaled.fatGrams
        )
        _ingredients.value = _ingredients.value + ingredient
    }

    /** Add an ingredient with manually typed values (no FoodCandidate required). */
    fun addManualIngredient(
        name: String,
        quantity: Double,
        unit: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double
    ) {
        val ingredient = com.localmacrotracker.app.data.db.entities.RecipeIngredientEntity(
            recipeId = 0L,  // assigned on save
            displayNameSnapshot = name,
            quantity = quantity,
            unit = unit,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat
        )
        _ingredients.value = _ingredients.value + ingredient
    }

    fun removeIngredient(idx: Int) {
        val current = _ingredients.value.toMutableList()
        if (idx in current.indices) {
            current.removeAt(idx)
            _ingredients.value = current
        }
    }

    fun updateIngredient(idx: Int, quantity: Double, unit: String) {
        val current = _ingredients.value.toMutableList()
        if (idx !in current.indices) return
        val existing = current[idx]

        // Recompute macros relative to the stored base macros by re-deriving from quantity=1 serving
        // We use existing macros at current quantity, then scale by new/old ratio if old qty > 0
        val oldQty = existing.quantity
        val factor = if (oldQty > 0.0) quantity / oldQty else quantity
        val updated = existing.copy(
            quantity = quantity,
            unit = unit,
            calories = existing.calories * factor,
            protein = existing.protein * factor,
            carbs = existing.carbs * factor,
            fat = existing.fat * factor
        )
        current[idx] = updated
        _ingredients.value = current
    }

    fun saveRecipe() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val t = totals.value
                val recipe = RecipeEntity(
                    displayName = _recipeName.value.ifBlank { "Untitled Recipe" },
                    servingsMade = _servingsMade.value,
                    perServingCalories = t.perServingCalories,
                    perServingProtein = t.perServingProtein,
                    perServingCarbs = t.perServingCarbs,
                    perServingFat = t.perServingFat
                )
                val recipeId = recipeDao.insertRecipe(recipe)
                val ingredientsWithRecipeId = _ingredients.value.map { it.copy(recipeId = recipeId) }
                recipeDao.insertIngredients(ingredientsWithRecipeId)
                _savedRecipeId.value = recipeId
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveAndAddToLog(mealSection: MealSection, logDate: LocalDate) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val t = totals.value
                val recipe = RecipeEntity(
                    displayName = _recipeName.value.ifBlank { "Untitled Recipe" },
                    servingsMade = _servingsMade.value,
                    perServingCalories = t.perServingCalories,
                    perServingProtein = t.perServingProtein,
                    perServingCarbs = t.perServingCarbs,
                    perServingFat = t.perServingFat
                )
                val recipeId = recipeDao.insertRecipe(recipe)
                val ingredientsWithRecipeId = _ingredients.value.map { it.copy(recipeId = recipeId) }
                recipeDao.insertIngredients(ingredientsWithRecipeId)
                _savedRecipeId.value = recipeId

                val entry = FoodLogEntryEntity(
                    logDate = logDate.toString(),
                    mealSection = mealSection.name,
                    displayNameSnapshot = recipe.displayName,
                    quantity = 1.0,
                    unit = "serving",
                    caloriesExact = t.perServingCalories,
                    proteinExact = t.perServingProtein,
                    carbsExact = t.perServingCarbs,
                    fatExact = t.perServingFat,
                    isEstimated = false,
                    needsManualSaveReminder = false,
                    sourceTypeSnapshot = SourceType.MANUAL.name
                )
                foodLogDao.insertEntry(entry)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetSavedRecipeId() {
        _savedRecipeId.value = null
    }
}
