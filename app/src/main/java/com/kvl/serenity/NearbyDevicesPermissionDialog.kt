package com.kvl.serenity

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun NearbyDevicesPermissionDialog(onAllow: () -> Unit, onCancel: () -> Unit = {}) {
    AlertDialog(
        title = { Text("Nearby devices") },
        text = { Text("Serenity needs the Nearby Devices permission to detect when your bluetooth headset has been disconnected. Serenity will automatically stop playback when it detects your headset has disconnected.") },
        onDismissRequest = onCancel,
        //dismissButton = { TextButton(onClick = onCancel) { Text("NOT NOW") } },
        confirmButton = { TextButton(onClick = onAllow) { Text("OK") } })
}
