package com.chrsk.volumebooster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * BroadcastReceiver listening for media players (Spotify, YouTube Music, Deezer, VLC, etc.)
 * opening or closing explicit audio effect control sessions.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    private val tag = "VolumeBooster_Session"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Player"

        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId == 0) {
            return
        }

        Log.d(tag, "Audio session event: $action | Session ID: $sessionId | Package: $packageName")

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioService.openSession(context, sessionId, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioService.closeSession(context, sessionId)
            }
        }
    }
}
