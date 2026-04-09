package com.localmacrotracker.app.data.model

enum class FoodCategory(val displayName: String) {
    PREMADE_FOOD("Premade Food"),
    RECIPE_MEAL_PREP("Recipe / Meal Prep"),
    LABELESS_FOOD("Labeless Food");

    companion object {
        fun fromString(s: String): FoodCategory =
            values().firstOrNull { it.name == s } ?: PREMADE_FOOD
    }
}
