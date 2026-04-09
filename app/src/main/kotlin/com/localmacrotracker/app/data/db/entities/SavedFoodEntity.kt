package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_foods",
    indices = [
        Index("barcode", unique = true),
        Index("displayName"),
        Index("searchIndexText"),
        Index("category")
    ]
)
data class SavedFoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val originalName: String? = null,
    /** FoodCategory enum name */
    val category: String,
    val barcode: String? = null,
    /** Space-joined tokens for full-text search approximation. */
    val searchIndexText: String,
    val servingText: String? = null,
    val servingWeightGrams: Double? = null,
    val servingVolumeMl: Double? = null,
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    /** ExactnessType enum name */
    val exactnessType: String = "EXACT",
    /** SourceType enum name */
    val sourceType: String? = null,
    val sourceUrl: String? = null,
    val sourceLastCheckedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
