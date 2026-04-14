package com.localmacrotracker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        WeightEntryEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun recipeDao(): RecipeDao
    abstract fun lookupCacheDao(): LookupCacheDao
    abstract fun weightDao(): WeightDao

    companion object {
        const val DATABASE_NAME = "macro_tracker.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `weight_entries` (
                        `logDate` TEXT NOT NULL,
                        `weightLbs` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`logDate`)
                    )"""
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
