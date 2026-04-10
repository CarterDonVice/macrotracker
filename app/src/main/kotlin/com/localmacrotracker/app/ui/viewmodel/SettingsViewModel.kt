package com.localmacrotracker.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.prefs.AppPreferences
import com.localmacrotracker.app.llm.LocalInferenceEngine
import com.localmacrotracker.app.llm.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val inferenceEngine: LocalInferenceEngine
) : ViewModel() {

    val isOnboardingComplete: StateFlow<Boolean> = prefs.isOnboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val usdaKey: StateFlow<String> = prefs.usdaApiKey
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val modelUri: StateFlow<String?> = prefs.modelUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modelDisplayName: StateFlow<String?> = prefs.modelDisplayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _modelStatus = MutableStateFlow(inferenceEngine.status)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingComplete()
        }
    }

    fun saveUsdaKey(key: String) {
        viewModelScope.launch {
            prefs.setUsdaApiKey(key)
        }
    }

    fun selectModel(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _modelStatus.value = ModelStatus.LOADING
            try {
                val success = inferenceEngine.loadModel(uri)
                if (success) {
                    prefs.setModelUri(uri, displayName)
                    _modelStatus.value = ModelStatus.READY
                } else {
                    _modelStatus.value = ModelStatus.LOAD_FAILED
                }
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.LOAD_FAILED
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun clearModel() {
        viewModelScope.launch {
            inferenceEngine.unloadModel()
            prefs.clearModel()
            _modelStatus.value = ModelStatus.NOT_CONFIGURED
        }
    }
}
