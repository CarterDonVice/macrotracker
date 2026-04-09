package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_food_nutrients",
    foreignKeys = [ForeignKey(
        entity = SavedFoodEntity::class,
        parentColumns = ["id"],
        childColumns = ["savedFoodId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("savedFoodId")]
)
data class SavedFoodNutrientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val savedFoodId: Long,
    val nutrientCode: String,
    val nutrientDisplayName: String,
    val amount: Double,
    val unit: String
)
