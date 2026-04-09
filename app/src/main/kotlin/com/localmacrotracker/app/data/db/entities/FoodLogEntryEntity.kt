package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A snapshot of a food added to the daily log.
 * Values are copied at add-time; they never change when SavedFoodEntity changes.
 */
@Entity(
    tableName = "food_log_entries",
    indices = [
        Index("logDate"),
        Index("mealSection"),
        Index("linkedSavedFoodId")
    ]
)
data class FoodLogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** ISO date string YYYY-MM-DD */
    val logDate: String,
    /** MealSection enum name */
    val mealSection: String,
    /** May be null if the food was never saved (reminder state) */
    val linkedSavedFoodId: Long? = null,
    val displayNameSnapshot: String,
    val originalNameSnapshot: String? = null,
    val servingTextSnapshot: String? = null,
    val quantity: Double,
    val unit: String,
    // Exact values (used when exactnessType == EXACT)
    val caloriesExact: Double? = null,
    val proteinExact: Double? = null,
    val carbsExact: Double? = null,
    val fatExact: Double? = null,
    // Range values (used when exactnessType == ESTIMATED)
    val caloriesMin: Double? = null,
    val caloriesMax: Double? = null,
    val proteinMin: Double? = null,
    val proteinMax: Double? = null,
    val carbsMin: Double? = null,
    val carbsMax: Double? = null,
    val fatMin: Double? = null,
    val fatMax: Double? = null,
    /** ExactnessType enum name */
    val isEstimated: Boolean = false,
    /** Shows reminder badge — food is from external source, not saved locally */
    val needsManualSaveReminder: Boolean = false,
    /** SourceType enum name snapshot */
    val sourceTypeSnapshot: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
