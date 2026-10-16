package com.chrsk.volumebooster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrsk.volumebooster.AudioEngineState
import com.chrsk.volumebooster.AudioService
import com.chrsk.volumebooster.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

class VolumeBoosterViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferencesManager(application)
    private val context = application.applicationContext

    // Local state fallback in case service is starting
    private val _localEnabled = MutableStateFlow(preferences.isMasterEnabled)
    private val _localGain = MutableStateFlow(preferences.gainDb)
    private val _localLimiter = MutableStateFlow(preferences.isLimiterEnabled)

    // Merged UI state combining service status with persistent fallback
    val uiState: StateFlow<AudioEngineState> = combine(
        AudioService.serviceState,
        _localEnabled,
        _localGain,
        _localLimiter
    ) { serviceState, localEnabled, localGain, localLimiter ->
        if (AudioService.isRunning) {
            serviceState
        } else {
            AudioEngineState(
                isEnabled = localEnabled,
                gainDb = localGain,
                isLimiterEnabled = localLimiter,
                isAttached = false,
                sessionId = 0
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioEngineState(
            isEnabled = preferences.isMasterEnabled,
            gainDb = preferences.gainDb,
            isLimiterEnabled = preferences.isLimiterEnabled,
            sessionId = 0
        )
    )

    init {
        // Ensure service is triggered to guarantee background persistence
        AudioService.start(context)
    }

    fun toggleMaster() {
        val next = !uiState.value.isEnabled
        _localEnabled.value = next
        preferences.isMasterEnabled = next
        AudioService.setEnabled(context, next)
    }

    fun onGainChange(rawGain: Float) {
        // Quantize gain to 0.1 dB increments
        val quantized = (rawGain * 10f).roundToInt() / 10f
        val clamped = quantized.coerceIn(0.0f, 12.0f)
        _localGain.value = clamped
        preferences.gainDb = clamped
        AudioService.setGain(context, clamped)
    }

    fun toggleLimiter() {
        val next = !uiState.value.isLimiterEnabled
        _localLimiter.value = next
        preferences.isLimiterEnabled = next
        AudioService.setLimiter(context, next)
    }

    fun setPreset(presetGain: Float) {
        onGainChange(presetGain)
    }
}
