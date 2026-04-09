package com.localmacrotracker.app.data.db.dao

import androidx.room.*
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.db.entities.FoodLogEntryNutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {

    @Query("SELECT * FROM food_log_entries WHERE logDate = :date ORDER BY createdAt ASC")
    fun getEntriesForDate(date: String): Flow<List<FoodLogEntryEntity>>

    @Query("""
        SELECT * FROM food_log_entries
        WHERE logDate = :date AND mealSection = :mealSection
        ORDER BY createdAt ASC
    """)
    suspend fun getEntriesForDateAndSection(date: String, mealSection: String): List<FoodLogEntryEntity>

    @Query("SELECT * FROM food_log_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): FoodLogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FoodLogEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<FoodLogEntryEntity>): List<Long>

    @Update
    suspend fun updateEntry(entry: FoodLogEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: FoodLogEntryEntity)

    @Query("DELETE FROM food_log_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    /** Update the linkedSavedFoodId after user saves an unsaved reminder entry. */
    @Query("""
        UPDATE food_log_entries
        SET linkedSavedFoodId = :savedFoodId,
            needsManualSaveReminder = 0,
            updatedAt = :now
        WHERE id = :entryId
    """)
    suspend fun linkEntryToSavedFood(entryId: Long, savedFoodId: Long, now: Long = System.currentTimeMillis())

    // Nutrients
    @Query("SELECT * FROM food_log_entry_nutrients WHERE foodLogEntryId = :entryId")
    suspend fun getNutrientsForEntry(entryId: Long): List<FoodLogEntryNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutrients(nutrients: List<FoodLogEntryNutrientEntity>)

    @Query("DELETE FROM food_log_entry_nutrients WHERE foodLogEntryId = :entryId")
    suspend fun deleteNutrientsForEntry(entryId: Long)
}
