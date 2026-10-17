package com.chrsk.volumebooster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.chrsk.volumebooster.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * Lightweight Foreground Service ensuring the audio dynamics processing engine
 * persists seamlessly across background playback in Spotify, YouTube, Poweramp, etc.
 */
class AudioService : Service() {

    private val tag = "VolumeBooster_Service"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioEngine: AudioEngine
    private lateinit var notificationManager: NotificationManager
    private var demoPlayer: SoundDemoPlayer? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    companion object {
        const val CHANNEL_ID = "volumebooster_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.chrsk.volumebooster.action.START"
        const val ACTION_STOP = "com.chrsk.volumebooster.action.STOP"
        const val ACTION_SET_GAIN = "com.chrsk.volumebooster.action.SET_GAIN"
        const val ACTION_SET_LIMITER = "com.chrsk.volumebooster.action.SET_LIMITER"
        const val ACTION_SET_ENABLED = "com.chrsk.volumebooster.action.SET_ENABLED"
        const val ACTION_TOGGLE_POWER = "com.chrsk.volumebooster.action.TOGGLE_POWER"
        const val ACTION_TOGGLE_LIMITER = "com.chrsk.volumebooster.action.TOGGLE_LIMITER"
        const val ACTION_DEVICE_CONNECTED = "com.chrsk.volumebooster.action.DEVICE_CONNECTED"
        const val ACTION_DEVICE_DISCONNECTED = "com.chrsk.volumebooster.action.DEVICE_DISCONNECTED"
        const val ACTION_OPEN_SESSION = "com.chrsk.volumebooster.action.OPEN_SESSION"
        const val ACTION_CLOSE_SESSION = "com.chrsk.volumebooster.action.CLOSE_SESSION"
        const val ACTION_TOGGLE_DEMO = "com.chrsk.volumebooster.action.TOGGLE_DEMO"

        const val EXTRA_GAIN_DB = "extra_gain_db"
        const val EXTRA_LIMITER_ENABLED = "extra_limiter_enabled"
        const val EXTRA_MASTER_ENABLED = "extra_master_enabled"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        var isRunning: Boolean = false
            private set

        private val _serviceState = MutableStateFlow(AudioEngineState())
        val serviceState: StateFlow<AudioEngineState> = _serviceState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setGain(context: Context, gainDb: Float) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_SET_GAIN
                putExtra(EXTRA_GAIN_DB, gainDb)
            }
            context.startService(intent)
        }

        fun setLimiter(context: Context, enabled: Boolean) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_SET_LIMITER
                putExtra(EXTRA_LIMITER_ENABLED, enabled)
            }
            context.startService(intent)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_SET_ENABLED
                putExtra(EXTRA_MASTER_ENABLED, enabled)
            }
            context.startService(intent)
        }

        fun openSession(context: Context, sessionId: Int, packageName: String) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_OPEN_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            if (isRunning) {
                context.startService(intent)
            }
        }

        fun closeSession(context: Context, sessionId: Int) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_CLOSE_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (isRunning) {
                context.startService(intent)
            }
        }

        fun toggleDemo(context: Context) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_TOGGLE_DEMO
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        preferencesManager = PreferencesManager(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        audioEngine = AudioEngine(defaultSessionId = 0)

        demoPlayer = SoundDemoPlayer(
            onSessionStarted = { sessionId ->
                audioEngine.openSession(sessionId, "Demo Áudio Interno")
            },
            onSessionStopped = { sessionId ->
                audioEngine.closeSession(sessionId)
            },
            onPlaybackStateChanged = { playing ->
                audioEngine.setDemoPlaying(playing)
            }
        )

        createNotificationChannel()

        // Sync AudioEngine State with Companion Flow
        audioEngine.state.onEach { state ->
            _serviceState.value = state
            if (isRunning) {
                updateNotification(state)
            }
        }.launchIn(serviceScope)

        // Initial setup from persisted preferences
        val savedEnabled = preferencesManager.isMasterEnabled
        val savedGain = preferencesManager.gainDb
        val savedLimiter = preferencesManager.isLimiterEnabled

        audioEngine.initialize(
            gainDb = savedGain,
            isLimiterEnabled = savedLimiter,
            isEnabled = savedEnabled,
            sessionId = 0
        )

        // Start listening to system audio playback events
        registerPlaybackCallback()

        val notification = buildNotification(audioEngine.state.value)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        Log.i(tag, "AudioService created and started in foreground.")
    }

    private fun registerPlaybackCallback() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                    super.onPlaybackConfigChanged(configs)
                    for (config in configs) {
                        val sessionId = extractSessionId(config)
                        if (sessionId > 0 && sessionId != AudioEffect.ERROR_BAD_VALUE) {
                            audioEngine.openSession(sessionId, "Stream de Áudio ($sessionId)")
                        }
                    }
                }
            }
            audioManager.registerAudioPlaybackCallback(playbackCallback!!, Handler(Looper.getMainLooper()))
            Log.i(tag, "AudioPlaybackCallback registered for continuous session interception.")
        } catch (e: Exception) {
            Log.w(tag, "Failed to register AudioPlaybackCallback: ${e.message}")
        }
    }

    private fun unregisterPlaybackCallback() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
            playbackCallback = null
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering AudioPlaybackCallback: ${e.message}")
        }
    }

    private fun extractSessionId(config: AudioPlaybackConfiguration): Int {
        val methodNames = arrayOf("getSessionId", "getClientAudioSessionId")
        for (name in methodNames) {
            try {
                val method = AudioPlaybackConfiguration::class.java.getDeclaredMethod(name)
                method.isAccessible = true
                val res = method.invoke(config)
                if (res is Int && res > 0) {
                    return res
                }
            } catch (_: Exception) {}
        }
        return -1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_START -> {
                Log.d(tag, "Action START received.")
            }

            ACTION_STOP -> {
                Log.d(tag, "Action STOP received. Stopping foreground service.")
                stopForegroundService()
                return START_NOT_STICKY
            }

            ACTION_SET_GAIN -> {
                val gain = intent.getFloatExtra(EXTRA_GAIN_DB, preferencesManager.gainDb)
                preferencesManager.gainDb = gain
                audioEngine.setGain(gain)
            }

            ACTION_SET_LIMITER -> {
                val limiter = intent.getBooleanExtra(EXTRA_LIMITER_ENABLED, preferencesManager.isLimiterEnabled)
                preferencesManager.isLimiterEnabled = limiter
                audioEngine.setLimiterEnabled(limiter)
            }

            ACTION_SET_ENABLED -> {
                val enabled = intent.getBooleanExtra(EXTRA_MASTER_ENABLED, preferencesManager.isMasterEnabled)
                preferencesManager.isMasterEnabled = enabled
                audioEngine.setEnabled(enabled)
            }

            ACTION_TOGGLE_POWER -> {
                val newEnabled = !preferencesManager.isMasterEnabled
                preferencesManager.isMasterEnabled = newEnabled
                audioEngine.setEnabled(newEnabled)
            }

            ACTION_TOGGLE_LIMITER -> {
                val newLimiter = !preferencesManager.isLimiterEnabled
                preferencesManager.isLimiterEnabled = newLimiter
                audioEngine.setLimiterEnabled(newLimiter)
            }

            ACTION_DEVICE_CONNECTED -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Bluetooth"
                audioEngine.setConnectedDevice(deviceName)
            }

            ACTION_DEVICE_DISCONNECTED -> {
                audioEngine.setConnectedDevice(null)
            }

            ACTION_OPEN_SESSION -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, 0)
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Player"
                if (sessionId != 0) {
                    audioEngine.openSession(sessionId, pkg)
                }
            }

            ACTION_CLOSE_SESSION -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, 0)
                if (sessionId != 0) {
                    audioEngine.closeSession(sessionId)
                }
            }

            ACTION_TOGGLE_DEMO -> {
                demoPlayer?.toggleDemo()
            }
        }

        return START_STICKY
    }

    private fun stopForegroundService() {
        isRunning = false
        demoPlayer?.stopDemo()
        unregisterPlaybackCallback()
        audioEngine.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: AudioEngineState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePowerIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioService::class.java).apply { action = ACTION_TOGGLE_POWER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLimiterIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioService::class.java).apply { action = ACTION_TOGGLE_LIMITER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (state.isEnabled) {
            String.format(
                Locale.US,
                "Ganho: +%.1f dB | Limiter: %s",
                state.gainDb,
                if (state.isLimiterEnabled) "Ativo" else "Bypass"
            )
        } else {
            "Efeito em Espera (Desativado)"
        }

        val deviceSubtext = state.connectedDevice?.let { "Conectado: $it" } ?: "Saída Global (Sessão 0)"

        val powerActionText = if (state.isEnabled) "Desativar" else "Ativar"
        val limiterActionText = if (state.isLimiterEnabled) "Limiter Off" else "Limiter On"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (state.isEnabled) "VolumeBooster Ativo" else "VolumeBooster Pausado")
            .setContentText(statusText)
            .setSubText(deviceSubtext)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification, powerActionText, togglePowerIntent)
            .addAction(R.drawable.ic_notification, limiterActionText, toggleLimiterIntent)
            .build()
    }

    private fun updateNotification(state: AudioEngineState) {
        val notification = buildNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        demoPlayer?.stopDemo()
        unregisterPlaybackCallback()
        serviceScope.cancel()
        audioEngine.release()
        Log.i(tag, "AudioService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
