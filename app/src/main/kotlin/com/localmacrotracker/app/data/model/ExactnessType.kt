package com.localmacrotracker.app.data.model

enum class ExactnessType {
    EXACT,
    ESTIMATED;

    companion object {
        fun fromString(s: String): ExactnessType =
            values().firstOrNull { it.name == s } ?: ESTIMATED
    }
}
