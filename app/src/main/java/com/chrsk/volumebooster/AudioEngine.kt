package com.chrsk.volumebooster

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Type of audio processing engine currently delivering gain.
 */
enum class EngineType {
    NONE,
    LOUDNESS_ENHANCER,
    DYNAMICS_PROCESSING,
    HYBRID_HARDWARE_DSP
}

/**
 * Observable snapshot of the Audio Engine state.
 */
data class AudioEngineState(
    val isEnabled: Boolean = false,
    val gainDb: Float = 0.0f,
    val isLimiterEnabled: Boolean = true,
    val isAttached: Boolean = false,
    val sessionId: Int = 0,
    val engineType: EngineType = EngineType.NONE,
    val connectedDevice: String? = null,
    val activeSessions: List<String> = emptyList(),
    val isDemoPlaying: Boolean = false,
    val lastError: String? = null
)

/**
 * High-performance audio DSP engine combining LoudnessEnhancer (for universal system-wide gain)
 * and DynamicsProcessing (for peak limiter protection against clipping on low frequencies).
 */
class AudioEngine(private val defaultSessionId: Int = 0) {

    private val tag = "VolumeBooster_Engine"

    private val _state = MutableStateFlow(AudioEngineState(sessionId = defaultSessionId))
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    // Global session 0 effects
    private var globalLoudnessEnhancer: LoudnessEnhancer? = null
    private var globalDynamicsProcessing: DynamicsProcessing? = null

    // Map of active media player sessions (Spotify, YouTube Music, Deezer, etc.)
    private data class SessionHolder(
        val sessionId: Int,
        val packageName: String,
        var loudnessEnhancer: LoudnessEnhancer? = null,
        var dynamicsProcessing: DynamicsProcessing? = null
    )

    private val playerSessions = ConcurrentHashMap<Int, SessionHolder>()

    companion object {
        const val MAX_GAIN_DB = 12.0f
        const val MIN_GAIN_DB = 0.0f
        const val LIMITER_THRESHOLD_DBFS = -0.5f  // Ceiling below 0 dBFS to prevent DAC clipping
        const val LIMITER_ATTACK_MS = 1.0f        // Fast transient attack
        const val LIMITER_RELEASE_MS = 60.0f      // Smooth bass recovery
        const val LIMITER_RATIO = 20.0f           // Brickwall slope
        const val LIMITER_POST_GAIN_DB = 0.0f
        const val CHANNEL_COUNT = 2               // Stereo (L + R)
    }

    /**
     * Initializes both LoudnessEnhancer and DynamicsProcessing on Session 0.
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

        var attachedAny = false

        // 1. Always initialize LoudnessEnhancer first (universal HAL gain support on Session 0)
        try {
            val le = LoudnessEnhancer(sessionId)
            val millibels = (clampedGain * 100f).roundToInt()
            le.setTargetGain(if (isEnabled) millibels else 0)
            le.enabled = isEnabled
            globalLoudnessEnhancer = le
            attachedAny = true
            Log.i(tag, "LoudnessEnhancer initialized on session $sessionId with targetGain $millibels mB (enabled=$isEnabled)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize LoudnessEnhancer on session $sessionId: ${e.message}", e)
        }

        // 2. Initialize DynamicsProcessing companion for brickwall peak limiter
        try {
            val dp = createDynamicsProcessing(sessionId, clampedGain, isLimiterEnabled, isEnabled)
            globalDynamicsProcessing = dp
            attachedAny = true
            Log.i(tag, "DynamicsProcessing peak limiter attached on session $sessionId")
        } catch (e: Exception) {
            Log.w(tag, "DynamicsProcessing not supported on session $sessionId: ${e.message}")
        }

        _state.update {
            it.copy(
                isEnabled = isEnabled,
                gainDb = clampedGain,
                isLimiterEnabled = isLimiterEnabled,
                isAttached = attachedAny,
                sessionId = sessionId,
                engineType = if (globalDynamicsProcessing != null && globalLoudnessEnhancer != null) {
                    EngineType.HYBRID_HARDWARE_DSP
                } else if (globalLoudnessEnhancer != null) {
                    EngineType.LOUDNESS_ENHANCER
                } else {
                    EngineType.NONE
                },
                lastError = if (attachedAny) null else "Não foi possível anexar efeitos de áudio ao sistema."
            )
        }
    }

    private fun createDynamicsProcessing(
        sessionId: Int,
        gainDb: Float,
        limiterActive: Boolean,
        effectEnabled: Boolean
    ): DynamicsProcessing {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            true,   // isInputGainByChannel
            0,      // preEqBandCount
            false,  // isMbcByChannel
            0,      // mbcBandCount
            false,  // isPostEqByChannel
            0,      // postEqBandCount
            true    // isLimiterByChannel
        )

        val limiter = DynamicsProcessing.Limiter(
            true,
            limiterActive,
            0,
            LIMITER_ATTACK_MS,
            LIMITER_RELEASE_MS,
            LIMITER_RATIO,
            LIMITER_THRESHOLD_DBFS,
            LIMITER_POST_GAIN_DB
        )
        builder.setLimiterAllChannelsTo(limiter)
        builder.setInputGainAllChannelsTo(if (effectEnabled) gainDb else 0.0f)

        val config = builder.build()
        val dp = DynamicsProcessing(0, sessionId, config)
        dp.enabled = effectEnabled
        return dp
    }

    /**
     * Updates gain across Session 0 and all active media player sessions.
     */
    @Synchronized
    fun setGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val isMasterOn = _state.value.isEnabled
        val millibels = (clamped * 100f).roundToInt()

        _state.update { it.copy(gainDb = clamped) }

        // Update Session 0
        try {
            globalLoudnessEnhancer?.let { le ->
                le.setTargetGain(if (isMasterOn) millibels else 0)
                le.enabled = isMasterOn
                Log.d(tag, "Global LoudnessEnhancer targetGain set to $millibels mB")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting gain on global LoudnessEnhancer: ${e.message}")
        }

        try {
            globalDynamicsProcessing?.let { dp ->
                dp.setInputGainAllChannelsTo(if (isMasterOn) clamped else 0.0f)
                dp.enabled = isMasterOn
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting gain on global DynamicsProcessing: ${e.message}")
        }

        // Update all active player sessions
        playerSessions.values.forEach { holder ->
            try {
                holder.loudnessEnhancer?.setTargetGain(if (isMasterOn) millibels else 0)
                holder.loudnessEnhancer?.enabled = isMasterOn
                holder.dynamicsProcessing?.setInputGainAllChannelsTo(if (isMasterOn) clamped else 0.0f)
                holder.dynamicsProcessing?.enabled = isMasterOn
            } catch (e: Exception) {
                Log.w(tag, "Error updating gain on session ${holder.sessionId}: ${e.message}")
            }
        }
    }

    /**
     * Toggles or configures the peak limiter stage.
     */
    @Synchronized
    fun setLimiterEnabled(enabled: Boolean) {
        _state.update { it.copy(isLimiterEnabled = enabled) }

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

        try {
            globalDynamicsProcessing?.setLimiterAllChannelsTo(limiter)
            Log.d(tag, "Global DynamicsProcessing limiter set to enabled=$enabled")
        } catch (e: Exception) {
            Log.w(tag, "Error configuring global limiter: ${e.message}")
        }

        playerSessions.values.forEach { holder ->
            try {
                holder.dynamicsProcessing?.setLimiterAllChannelsTo(limiter)
            } catch (e: Exception) {
                Log.w(tag, "Error configuring limiter on session ${holder.sessionId}: ${e.message}")
            }
        }
    }

    /**
     * Enables or disables the master audio boost effect.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        val currentGain = _state.value.gainDb
        val millibels = (currentGain * 100f).roundToInt()

        // Apply to Session 0
        try {
            globalLoudnessEnhancer?.let { le ->
                le.setTargetGain(if (enabled) millibels else 0)
                le.enabled = enabled
                Log.d(tag, "Global LoudnessEnhancer enabled=$enabled, gain=$millibels mB")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error toggling global LoudnessEnhancer: ${e.message}")
        }

        try {
            globalDynamicsProcessing?.let { dp ->
                dp.setInputGainAllChannelsTo(if (enabled) currentGain else 0.0f)
                dp.enabled = enabled
            }
        } catch (e: Exception) {
            Log.e(tag, "Error toggling global DynamicsProcessing: ${e.message}")
        }

        // Apply to all active player sessions
        playerSessions.values.forEach { holder ->
            try {
                holder.loudnessEnhancer?.setTargetGain(if (enabled) millibels else 0)
                holder.loudnessEnhancer?.enabled = enabled
                holder.dynamicsProcessing?.setInputGainAllChannelsTo(if (enabled) currentGain else 0.0f)
                holder.dynamicsProcessing?.enabled = enabled
            } catch (e: Exception) {
                Log.w(tag, "Error toggling session ${holder.sessionId}: ${e.message}")
            }
        }
    }

    /**
     * Attaches audio effects to a specific player session (e.g. Spotify, Deezer, etc.).
     */
    @Synchronized
    fun openSession(sessionId: Int, packageName: String) {
        if (sessionId == 0 || playerSessions.containsKey(sessionId)) return

        Log.i(tag, "Opening dedicated audio effect for $packageName (session $sessionId)")

        val currentGain = _state.value.gainDb
        val isMasterOn = _state.value.isEnabled
        val isLimiterOn = _state.value.isLimiterEnabled
        val millibels = (currentGain * 100f).roundToInt()

        var le: LoudnessEnhancer? = null
        try {
            le = LoudnessEnhancer(sessionId).apply {
                setTargetGain(if (isMasterOn) millibels else 0)
                this.enabled = isMasterOn
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach LoudnessEnhancer to session $sessionId: ${e.message}")
        }

        var dp: DynamicsProcessing? = null
        try {
            dp = createDynamicsProcessing(sessionId, currentGain, isLimiterOn, isMasterOn)
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach DynamicsProcessing to session $sessionId: ${e.message}")
        }

        playerSessions[sessionId] = SessionHolder(sessionId, packageName, le, dp)
        updateActiveSessionsList()
    }

    /**
     * Detaches and releases effects for a closed player session.
     */
    @Synchronized
    fun closeSession(sessionId: Int) {
        val holder = playerSessions.remove(sessionId) ?: return
        Log.i(tag, "Closing audio effect session $sessionId (${holder.packageName})")
        try {
            holder.loudnessEnhancer?.enabled = false
            holder.loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing session LE: ${e.message}")
        }

        try {
            holder.dynamicsProcessing?.enabled = false
            holder.dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing session DP: ${e.message}")
        }

        updateActiveSessionsList()
    }

    private fun updateActiveSessionsList() {
        val sessions = playerSessions.values.map { "${it.packageName} (sessão ${it.sessionId})" }
        _state.update { it.copy(activeSessions = sessions) }
    }

    fun setConnectedDevice(deviceName: String?) {
        _state.update { it.copy(connectedDevice = deviceName) }
    }

    fun setDemoPlaying(isPlaying: Boolean) {
        _state.update { it.copy(isDemoPlaying = isPlaying) }
    }

    @Synchronized
    fun release() {
        releaseInternal()
        _state.update {
            it.copy(
                isAttached = false,
                engineType = EngineType.NONE,
                activeSessions = emptyList()
            )
        }
        Log.i(tag, "AudioEngine released.")
    }

    private fun releaseInternal() {
        try {
            globalLoudnessEnhancer?.enabled = false
            globalLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing global LoudnessEnhancer: ${e.message}")
        } finally {
            globalLoudnessEnhancer = null
        }

        try {
            globalDynamicsProcessing?.enabled = false
            globalDynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing global DynamicsProcessing: ${e.message}")
        } finally {
            globalDynamicsProcessing = null
        }

        playerSessions.values.forEach { holder ->
            try {
                holder.loudnessEnhancer?.enabled = false
                holder.loudnessEnhancer?.release()
                holder.dynamicsProcessing?.enabled = false
                holder.dynamicsProcessing?.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing player session: ${e.message}")
            }
        }
        playerSessions.clear()
    }
}
