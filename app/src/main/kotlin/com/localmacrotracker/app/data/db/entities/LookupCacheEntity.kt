package com.localmacrotracker.app.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lookup_cache",
    indices = [
        Index(value = ["normalizedQuery", "providerName"], unique = true),
        Index("expiresAt")
    ]
)
data class LookupCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedQuery: String,
    val providerName: String,
    val rawResponseJson: String,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
