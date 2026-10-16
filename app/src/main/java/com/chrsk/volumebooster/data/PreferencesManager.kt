package com.chrsk.volumebooster.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent configuration for VolumeBooster.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()

    var gainDb: Float
        get() = prefs.getFloat(KEY_GAIN_DB, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_GAIN_DB, value.coerceIn(0.0f, 12.0f)).apply()

    var isLimiterEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIMITER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LIMITER_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "volumebooster_prefs"
        private const val KEY_MASTER_ENABLED = "key_master_enabled"
        private const val KEY_GAIN_DB = "key_gain_db"
        private const val KEY_LIMITER_ENABLED = "key_limiter_enabled"
    }
}
