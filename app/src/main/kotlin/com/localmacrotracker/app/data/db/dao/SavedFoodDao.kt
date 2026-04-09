package com.localmacrotracker.app.data.db.dao

import androidx.room.*
import com.localmacrotracker.app.data.db.entities.SavedFoodEntity
import com.localmacrotracker.app.data.db.entities.SavedFoodNutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFoodDao {

    @Query("SELECT * FROM saved_foods ORDER BY updatedAt DESC")
    fun getAllFoods(): Flow<List<SavedFoodEntity>>

    @Query("SELECT * FROM saved_foods WHERE category = :category ORDER BY updatedAt DESC")
    fun getFoodsByCategory(category: String): Flow<List<SavedFoodEntity>>

    @Query("""
        SELECT * FROM saved_foods
        WHERE searchIndexText LIKE '%' || :query || '%'
           OR displayName LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN displayName LIKE :query || '%' THEN 0 ELSE 1 END,
            updatedAt DESC
        LIMIT 50
    """)
    fun searchFoods(query: String): Flow<List<SavedFoodEntity>>

    @Query("""
        SELECT * FROM saved_foods
        WHERE (searchIndexText LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%')
          AND category = :category
        ORDER BY
            CASE WHEN displayName LIKE :query || '%' THEN 0 ELSE 1 END,
            updatedAt DESC
        LIMIT 50
    """)
    fun searchFoodsByCategory(query: String, category: String): Flow<List<SavedFoodEntity>>

    @Query("SELECT * FROM saved_foods WHERE id = :id")
    suspend fun getFoodById(id: Long): SavedFoodEntity?

    @Query("SELECT * FROM saved_foods WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodByBarcode(barcode: String): SavedFoodEntity?

    @Query("""
        SELECT * FROM saved_foods
        WHERE displayName LIKE '%' || :name || '%'
        LIMIT 10
    """)
    suspend fun findByNameSimilar(name: String): List<SavedFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: SavedFoodEntity): Long

    @Update
    suspend fun updateFood(food: SavedFoodEntity)

    @Delete
    suspend fun deleteFood(food: SavedFoodEntity)

    @Query("DELETE FROM saved_foods WHERE id = :id")
    suspend fun deleteFoodById(id: Long)

    // Nutrients
    @Query("SELECT * FROM saved_food_nutrients WHERE savedFoodId = :savedFoodId")
    suspend fun getNutrientsForFood(savedFoodId: Long): List<SavedFoodNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutrients(nutrients: List<SavedFoodNutrientEntity>)

    @Query("DELETE FROM saved_food_nutrients WHERE savedFoodId = :savedFoodId")
    suspend fun deleteNutrientsForFood(savedFoodId: Long)
}
