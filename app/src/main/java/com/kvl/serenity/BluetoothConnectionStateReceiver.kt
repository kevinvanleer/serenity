package com.kvl.serenity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothConnectionStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BT", "Received broadcast")
        val intentAction = intent.action ?: return
        when (intentAction) {
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                Log.d("BT", "Received Bluetooth Headset Connection State Changed")
                when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_DISCONNECTING,
                    BluetoothAdapter.STATE_DISCONNECTED,
                    BluetoothProfile.STATE_DISCONNECTING,
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BT", "Disconnected")
                        context.sendBroadcast(Intent().apply {
                            action = "com.kvl.serenity.pause_playback"
                        })
                    }

                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BT", "Connected")
                    }
                }
            }
        }
    }
}
