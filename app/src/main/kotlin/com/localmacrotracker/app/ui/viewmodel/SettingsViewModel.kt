package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    val isOnboardingComplete: StateFlow<Boolean> = prefs.isOnboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val goalCalories: StateFlow<Int> = prefs.goalCalories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val goalProtein: StateFlow<Int> = prefs.goalProtein
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val goalCarbs: StateFlow<Int> = prefs.goalCarbs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val goalFat: StateFlow<Int> = prefs.goalFat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val usdaKey: StateFlow<String> = prefs.usdaApiKey
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingComplete() }
    }

    fun setGoals(calories: Int, protein: Int, carbs: Int, fat: Int) {
        viewModelScope.launch { prefs.setGoals(calories, protein, carbs, fat) }
    }

    fun saveUsdaKey(key: String) {
        viewModelScope.launch { prefs.setUsdaApiKey(key) }
    }
}
