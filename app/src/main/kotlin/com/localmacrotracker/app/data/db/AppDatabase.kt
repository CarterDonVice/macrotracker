package com.localmacrotracker.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.localmacrotracker.app.data.db.dao.*
import com.localmacrotracker.app.data.db.entities.*

@Database(
    entities = [
        SavedFoodEntity::class,
        SavedFoodNutrientEntity::class,
        FoodLogEntryEntity::class,
        FoodLogEntryNutrientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        LookupCacheEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun recipeDao(): RecipeDao
    abstract fun lookupCacheDao(): LookupCacheDao

    companion object {
        const val DATABASE_NAME = "macro_tracker.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
