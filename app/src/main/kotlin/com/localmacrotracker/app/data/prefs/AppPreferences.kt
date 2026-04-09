package com.localmacrotracker.app.data.prefs

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        private val KEY_MODEL_URI = stringPreferencesKey("model_uri")
        private val KEY_MODEL_DISPLAY_NAME = stringPreferencesKey("model_display_name")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_OFF_USER_AGENT = stringPreferencesKey("off_user_agent")
    }

    val usdaApiKey: Flow<String?> = store.data.map { it[KEY_USDA_API_KEY] }
    val modelUri: Flow<String?> = store.data.map { it[KEY_MODEL_URI] }
    val modelDisplayName: Flow<String?> = store.data.map { it[KEY_MODEL_DISPLAY_NAME] }
    val isOnboardingComplete: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    val offUserAgent: Flow<String?> = store.data.map { it[KEY_OFF_USER_AGENT] }

    suspend fun setUsdaApiKey(key: String) = store.edit { it[KEY_USDA_API_KEY] = key }
    suspend fun setModelUri(uri: Uri, displayName: String) = store.edit {
        it[KEY_MODEL_URI] = uri.toString()
        it[KEY_MODEL_DISPLAY_NAME] = displayName
    }
    suspend fun clearModel() = store.edit {
        it.remove(KEY_MODEL_URI)
        it.remove(KEY_MODEL_DISPLAY_NAME)
    }
    suspend fun setOnboardingComplete() = store.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    suspend fun setOffUserAgent(value: String) = store.edit { it[KEY_OFF_USER_AGENT] = value }
}
