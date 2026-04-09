package com.localmacrotracker.app.data.model

enum class MealSection(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks");

    companion object {
        fun fromName(name: String): MealSection =
            values().firstOrNull { it.name == name } ?: BREAKFAST
    }
}
