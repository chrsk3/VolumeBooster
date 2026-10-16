package com.chrsk.volumebooster

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

/**
 * BroadcastReceiver responsible for detecting Bluetooth A2DP audio device
 * connections/disconnections and notifying the AudioService.
 */
class BluetoothReceiver : BroadcastReceiver() {

    private val tag = "VolumeBooster_BT"

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(tag, "Broadcast received: $action")

        val serviceIntent = Intent(context, AudioService::class.java)

        when (action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceName = try {
                    device?.name ?: "Fone Bluetooth"
                } catch (e: SecurityException) {
                    "Fone Bluetooth (A2DP)"
                }

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(tag, "Bluetooth A2DP connected: $deviceName")
                        serviceIntent.action = AudioService.ACTION_DEVICE_CONNECTED
                        serviceIntent.putExtra(AudioService.EXTRA_DEVICE_NAME, deviceName)
                        startServiceCompat(context, serviceIntent)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(tag, "Bluetooth A2DP disconnected: $deviceName")
                        serviceIntent.action = AudioService.ACTION_DEVICE_DISCONNECTED
                        startServiceCompat(context, serviceIntent)
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    device?.name ?: "Dispositivo Bluetooth"
                } catch (e: SecurityException) {
                    "Dispositivo Bluetooth"
                }
                serviceIntent.action = AudioService.ACTION_DEVICE_CONNECTED
                serviceIntent.putExtra(AudioService.EXTRA_DEVICE_NAME, deviceName)
                startServiceCompat(context, serviceIntent)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                serviceIntent.action = AudioService.ACTION_DEVICE_DISCONNECTED
                startServiceCompat(context, serviceIntent)
            }

            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Audio route transitioned to loudspeaker or disconnected
                Log.d(tag, "Audio becoming noisy: headphones unplugged or disconnected.")
                serviceIntent.action = AudioService.ACTION_DEVICE_DISCONNECTED
                startServiceCompat(context, serviceIntent)
            }
        }
    }

    private fun startServiceCompat(context: Context, intent: Intent) {
        try {
            if (AudioService.isRunning) {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to deliver intent to AudioService: ${e.message}")
        }
    }
}
