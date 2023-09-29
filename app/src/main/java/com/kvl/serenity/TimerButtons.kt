package com.kvl.serenity

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

@Composable
fun TimerButtons(
    timers: Map<String, TimerDef>,
    timeRemaining: Duration?,
    sleepTime: Instant?,
    selectedTimer: String?,
    onClickTimer: (String, Int) -> Unit
) {
    Column {
        @Composable
        fun TimerButton(
            key: String,
            def: TimerDef,
            selectedTimer: String?,
        ) =
            Button(
                modifier = Modifier.weight(1f),
                colors = when (selectedTimer == key) {
                    true -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    else -> ButtonDefaults.buttonColors()
                },
                onClick = {
                    onClickTimer(
                        key,
                        def.duration.toMinutes().toInt()
                    )
                })
            { Text(def.label) }

        when (sleepTime != null) {
            true -> Text(
                "Sleeping in ${
                    DateUtils.formatElapsedTime(timeRemaining?.seconds ?: 0)
                }"
            )

            else -> Text("Sleep timer")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .padding(top = 10.dp)
        ) {
            timers.toList().slice(IntRange(0, 2))
                .map {
                    TimerButton(
                        key = it.first,
                        def = it.second,
                        selectedTimer = selectedTimer,
                    )
                }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .padding(top = 10.dp)
        ) {
            timers.toList().slice(IntRange(3, 4))
                .map {
                    TimerButton(
                        key = it.first,
                        def = it.second,
                        selectedTimer = selectedTimer,
                    )
                }
        }
    }
}
