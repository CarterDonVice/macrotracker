package com.localmacrotracker.app.data.model

enum class SourceType {
    LOCAL_SAVED,
    USDA,
    OPEN_FOOD_FACTS,
    BRAND_PAGE,
    RESTAURANT_PAGE,
    GROCERY_PAGE,
    BARCODE,
    OCR,
    MANUAL;

    companion object {
        fun fromString(s: String): SourceType =
            values().firstOrNull { it.name == s } ?: MANUAL
    }
}
