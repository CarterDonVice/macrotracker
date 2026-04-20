package com.localmacrotracker.app.di

import android.content.Context
import com.localmacrotracker.app.data.db.AppDatabase
import com.localmacrotracker.app.data.db.dao.*
import com.localmacrotracker.app.data.network.OcrLabelParser
import com.localmacrotracker.app.domain.OcrLabelParserImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides fun provideSavedFoodDao(db: AppDatabase): SavedFoodDao = db.savedFoodDao()
    @Provides fun provideFoodLogDao(db: AppDatabase): FoodLogDao = db.foodLogDao()
    @Provides fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideLookupCacheDao(db: AppDatabase): LookupCacheDao = db.lookupCacheDao()
    @Provides fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindOcrLabelParser(impl: OcrLabelParserImpl): OcrLabelParser
}
