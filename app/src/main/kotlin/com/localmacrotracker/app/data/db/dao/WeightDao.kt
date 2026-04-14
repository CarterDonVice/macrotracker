package com.localmacrotracker.app.data.db.dao

import androidx.room.*
import com.localmacrotracker.app.data.db.entities.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entries ORDER BY logDate ASC")
    fun getAllWeights(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE logDate >= :fromDate ORDER BY logDate ASC")
    fun getWeightsSince(fromDate: String): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE logDate = :date LIMIT 1")
    suspend fun getWeightForDate(date: String): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entries WHERE logDate = :date")
    suspend fun deleteWeightForDate(date: String)
}
