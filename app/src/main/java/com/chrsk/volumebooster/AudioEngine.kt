package com.chrsk.volumebooster

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Type of underlying audio effect currently active.
 */
enum class EngineType {
    NONE,
    DYNAMICS_PROCESSING,
    LOUDNESS_ENHANCER
}

/**
 * Observable snapshot of the Audio Engine status.
 */
data class AudioEngineState(
    val isEnabled: Boolean = false,
    val gainDb: Float = 0.0f,
    val isLimiterEnabled: Boolean = true,
    val isAttached: Boolean = false,
    val sessionId: Int = 0,
    val engineType: EngineType = EngineType.NONE,
    val connectedDevice: String? = null,
    val lastError: String? = null
)

/**
 * Hardware-accelerated audio processing engine using Android DynamicsProcessing API.
 * Provides clean pre-amplification up to +12.0 dB with an integrated brickwall peak limiter
 * to eliminate low-frequency clipping and distortion for Bluetooth headsets.
 */
class AudioEngine(private val defaultSessionId: Int = 0) {

    private val tag = "VolumeBooster_Engine"

    private val _state = MutableStateFlow(AudioEngineState(sessionId = defaultSessionId))
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Limiter constants tuned for transparent peak protection on low frequencies
    companion object {
        const val MAX_GAIN_DB = 12.0f
        const val MIN_GAIN_DB = 0.0f
        const val LIMITER_THRESHOLD_DBFS = -0.5f  // Ceiling just below digital full-scale
        const val LIMITER_ATTACK_MS = 1.0f        // Fast attack to prevent initial transient overshoot
        const val LIMITER_RELEASE_MS = 60.0f      // Smooth release to eliminate bass distortion/chattering
        const val LIMITER_RATIO = 20.0f           // Brickwall slope
        const val LIMITER_POST_GAIN_DB = 0.0f
        const val CHANNEL_COUNT = 2               // Stereo (Left + Right)
    }

    /**
     * Initializes the engine with specified or current parameters.
     */
    @Synchronized
    fun initialize(
        gainDb: Float = _state.value.gainDb,
        isLimiterEnabled: Boolean = _state.value.isLimiterEnabled,
        isEnabled: Boolean = _state.value.isEnabled,
        sessionId: Int = defaultSessionId
    ) {
        val clampedGain = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        releaseInternal()

        try {
            initDynamicsProcessing(sessionId, clampedGain, isLimiterEnabled, isEnabled)
            _state.update {
                it.copy(
                    isEnabled = isEnabled,
                    gainDb = clampedGain,
                    isLimiterEnabled = isLimiterEnabled,
                    isAttached = true,
                    sessionId = sessionId,
                    engineType = EngineType.DYNAMICS_PROCESSING,
                    lastError = null
                )
            }
            Log.i(tag, "DynamicsProcessing engine initialized successfully on session $sessionId")
        } catch (e: Exception) {
            Log.w(tag, "DynamicsProcessing initialization failed. Falling back to LoudnessEnhancer: ${e.message}", e)
            tryFallbackLoudnessEnhancer(sessionId, clampedGain, isEnabled, isLimiterEnabled)
        }
    }

    /**
     * Instantiates DynamicsProcessing with Input Gain and Peak Limiter stages.
     */
    private fun initDynamicsProcessing(
        sessionId: Int,
        gainDb: Float,
        limiterActive: Boolean,
        effectEnabled: Boolean
    ) {
        // Build DynamicsProcessing stereo configuration:
        // InputGain = true, Limiter = true, PreEQ/MBC/PostEQ = false (bypassed for maximum purity)
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            true,   // isInputGainByChannel
            false,  // isPreEqByChannel
            false,  // isMbcByChannel
            false,  // isPostEqByChannel
            true    // isLimiterByChannel
        )

        // Configure brickwall limiter parameters on all channels
        val limiter = DynamicsProcessing.Limiter(
            true,                       // inUse
            limiterActive,              // enabled
            0,                          // linkReadoutGroup
            LIMITER_ATTACK_MS,          // attackTime ms
            LIMITER_RELEASE_MS,         // releaseTime ms
            LIMITER_RATIO,              // ratio
            LIMITER_THRESHOLD_DBFS,     // threshold in dBFS
            LIMITER_POST_GAIN_DB        // postGain in dB
        )
        builder.setLimiterAllChannelsTo(limiter)
        builder.setInputGainAllChannelsTo(gainDb)

        val config = builder.build()
        val dp = DynamicsProcessing(0, sessionId, config)
        dp.enabled = effectEnabled
        dynamicsProcessing = dp
    }

    /**
     * Fallback mechanism if vendor HAL does not support DynamicsProcessing on session 0.
     */
    private fun tryFallbackLoudnessEnhancer(
        sessionId: Int,
        gainDb: Float,
        effectEnabled: Boolean,
        limiterActive: Boolean
    ) {
        try {
            val le = LoudnessEnhancer(sessionId)
            val millibels = (gainDb * 100f).toInt()
            le.setTargetGain(millibels)
            le.enabled = effectEnabled
            loudnessEnhancer = le

            _state.update {
                it.copy(
                    isEnabled = effectEnabled,
                    gainDb = gainDb,
                    isLimiterEnabled = limiterActive,
                    isAttached = true,
                    sessionId = sessionId,
                    engineType = EngineType.LOUDNESS_ENHANCER,
                    lastError = "DynamicsProcessing indisponível no dispositivo; usando LoudnessEnhancer como fallback."
                )
            }
            Log.i(tag, "LoudnessEnhancer fallback initialized on session $sessionId with gain $millibels mB")
        } catch (e: Exception) {
            Log.e(tag, "Audio effect fallback also failed: ${e.message}", e)
            _state.update {
                it.copy(
                    isAttached = false,
                    engineType = EngineType.NONE,
                    lastError = "Falha ao anexar efeito à sessão de áudio: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Updates preamp linear gain in dB (0.0 dB to +12.0 dB).
     */
    @Synchronized
    fun setGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        _state.update { it.copy(gainDb = clamped) }

        try {
            dynamicsProcessing?.let { dp ->
                dp.setInputGainAllChannelsTo(clamped)
                Log.d(tag, "Input gain updated to $clamped dB")
                return
            }

            loudnessEnhancer?.let { le ->
                val mB = (clamped * 100f).toInt()
                le.setTargetGain(mB)
                Log.d(tag, "LoudnessEnhancer target gain set to $mB mB")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting gain: ${e.message}", e)
        }
    }

    /**
     * Toggles or configures the peak limiter stage.
     */
    @Synchronized
    fun setLimiterEnabled(enabled: Boolean) {
        _state.update { it.copy(isLimiterEnabled = enabled) }

        try {
            dynamicsProcessing?.let { dp ->
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    enabled,
                    0,
                    LIMITER_ATTACK_MS,
                    LIMITER_RELEASE_MS,
                    LIMITER_RATIO,
                    LIMITER_THRESHOLD_DBFS,
                    LIMITER_POST_GAIN_DB
                )
                dp.setLimiterAllChannelsTo(limiter)
                Log.d(tag, "Peak Limiter stage set to enabled=$enabled")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error configuring peak limiter: ${e.message}", e)
        }
    }

    /**
     * Enables or disables the master audio effect.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }

        try {
            dynamicsProcessing?.let { dp ->
                dp.enabled = enabled
                Log.d(tag, "DynamicsProcessing master enabled=$enabled")
                return
            }

            loudnessEnhancer?.let { le ->
                le.enabled = enabled
                Log.d(tag, "LoudnessEnhancer master enabled=$enabled")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting master enabled state: ${e.message}", e)
        }
    }

    /**
     * Updates connected device display name in state.
     */
    fun setConnectedDevice(deviceName: String?) {
        _state.update { it.copy(connectedDevice = deviceName) }
    }

    /**
     * Releases audio effect hardware handles and memory.
     */
    @Synchronized
    fun release() {
        releaseInternal()
        _state.update {
            it.copy(
                isAttached = false,
                engineType = EngineType.NONE
            )
        }
        Log.i(tag, "AudioEngine released.")
    }

    private fun releaseInternal() {
        try {
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing DynamicsProcessing: ${e.message}")
        } finally {
            dynamicsProcessing = null
        }

        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }
}
