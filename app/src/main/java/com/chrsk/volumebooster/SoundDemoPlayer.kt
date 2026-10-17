package com.chrsk.volumebooster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Built-in audio tone generator to test preamp gain and peak limiter in real-time.
 * Automatically reports its own audioSessionId so the AudioEngine attaches directly.
 */
class SoundDemoPlayer(
    private val onSessionStarted: (Int) -> Unit,
    private val onSessionStopped: (Int) -> Unit,
    private val onPlaybackStateChanged: (Boolean) -> Unit
) {

    private val tag = "VolumeBooster_Demo"
    private var audioTrack: AudioTrack? = null
    private var currentSessionId: Int = 0
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun toggleDemo() {
        if (isPlaying) {
            stopDemo()
        } else {
            startDemo()
        }
    }

    fun startDemo() {
        stopDemo()
        onPlaybackStateChanged(true)

        playbackJob = scope.launch {
            try {
                val sampleRate = 44100
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                val sessionId = track.audioSessionId
                currentSessionId = sessionId
                Log.i(tag, "AudioTrack initialized with audioSessionId: $sessionId")

                // Immediately link this track's audio session to AudioEngine
                onSessionStarted(sessionId)

                track.play()

                // Generate rich bass & chords pattern (80 Hz sub-bass, 220 Hz low mid, 440 Hz tone)
                val bufferSize = sampleRate / 2 // 0.5s chunks
                val shortBuffer = ShortArray(bufferSize * 2) // Stereo (L+R)
                var phaseBass = 0.0
                var phaseMid = 0.0
                var phaseHigh = 0.0
                val bassFreq = 80.0
                val midFreq = 220.0
                val highFreq = 440.0

                while (isActive) {
                    for (i in 0 until bufferSize) {
                        val sampleBass = sin(phaseBass) * 0.35
                        val sampleMid = sin(phaseMid) * 0.20
                        val sampleHigh = sin(phaseHigh) * 0.15
                        val combined = ((sampleBass + sampleMid + sampleHigh) * 32767.0)
                            .toInt()
                            .coerceIn(-32768, 32767)
                            .toShort()

                        shortBuffer[i * 2] = combined     // Left
                        shortBuffer[i * 2 + 1] = combined // Right

                        phaseBass += (2.0 * PI * bassFreq) / sampleRate
                        phaseMid += (2.0 * PI * midFreq) / sampleRate
                        phaseHigh += (2.0 * PI * highFreq) / sampleRate
                        if (phaseBass > 2.0 * PI) phaseBass -= 2.0 * PI
                        if (phaseMid > 2.0 * PI) phaseMid -= 2.0 * PI
                        if (phaseHigh > 2.0 * PI) phaseHigh -= 2.0 * PI
                    }
                    track.write(shortBuffer, 0, shortBuffer.size)
                }
            } catch (e: Exception) {
                Log.e(tag, "Demo playback error: ${e.message}", e)
            } finally {
                onPlaybackStateChanged(false)
            }
        }
    }

    fun stopDemo() {
        playbackJob?.cancel()
        playbackJob = null
        if (currentSessionId != 0) {
            onSessionStopped(currentSessionId)
            currentSessionId = 0
        }
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping audioTrack: ${e.message}")
        } finally {
            audioTrack = null
            onPlaybackStateChanged(false)
        }
    }
}
