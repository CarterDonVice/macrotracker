package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_log_entry_nutrients",
    foreignKeys = [ForeignKey(
        entity = FoodLogEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["foodLogEntryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("foodLogEntryId")]
)
data class FoodLogEntryNutrientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodLogEntryId: Long,
    val nutrientCode: String,
    val nutrientDisplayName: String,
    val amount: Double,
    val unit: String
)
