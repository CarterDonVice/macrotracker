package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One weight measurement per calendar day. Primary key is the ISO date string to enforce uniqueness. */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val logDate: String,        // "YYYY-MM-DD"
    val weightLbs: Double,
    val createdAt: Long = System.currentTimeMillis()
)
