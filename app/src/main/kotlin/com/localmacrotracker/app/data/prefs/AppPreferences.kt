package com.localmacrotracker.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localmacrotracker.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.dataStore

    companion object {
        private val KEY_USDA_API_KEY = stringPreferencesKey("usda_api_key")
        private val KEY_CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_OFF_USER_AGENT = stringPreferencesKey("off_user_agent")
        private val KEY_GOAL_CALORIES = intPreferencesKey("goal_calories")
        private val KEY_GOAL_PROTEIN = intPreferencesKey("goal_protein")
        private val KEY_GOAL_CARBS = intPreferencesKey("goal_carbs")
        private val KEY_GOAL_FAT = intPreferencesKey("goal_fat")

        val DEFAULT_CLAUDE_API_KEY: String get() = BuildConfig.DEFAULT_CLAUDE_API_KEY
    }

    val usdaApiKey: Flow<String?> = store.data.map { it[KEY_USDA_API_KEY] }
    val claudeApiKey: Flow<String> = store.data.map { it[KEY_CLAUDE_API_KEY] ?: DEFAULT_CLAUDE_API_KEY }
    val isOnboardingComplete: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    val offUserAgent: Flow<String?> = store.data.map { it[KEY_OFF_USER_AGENT] }

    suspend fun setUsdaApiKey(key: String) = store.edit { it[KEY_USDA_API_KEY] = key }
    suspend fun setClaudeApiKey(key: String) = store.edit { it[KEY_CLAUDE_API_KEY] = key }
    suspend fun setOnboardingComplete() = store.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    suspend fun setOffUserAgent(value: String) = store.edit { it[KEY_OFF_USER_AGENT] = value }

    val goalCalories: Flow<Int> = store.data.map { it[KEY_GOAL_CALORIES] ?: 0 }
    val goalProtein: Flow<Int> = store.data.map { it[KEY_GOAL_PROTEIN] ?: 0 }
    val goalCarbs: Flow<Int> = store.data.map { it[KEY_GOAL_CARBS] ?: 0 }
    val goalFat: Flow<Int> = store.data.map { it[KEY_GOAL_FAT] ?: 0 }

    suspend fun setGoals(calories: Int, protein: Int, carbs: Int, fat: Int) = store.edit {
        it[KEY_GOAL_CALORIES] = calories
        it[KEY_GOAL_PROTEIN] = protein
        it[KEY_GOAL_CARBS] = carbs
        it[KEY_GOAL_FAT] = fat
    }
}
