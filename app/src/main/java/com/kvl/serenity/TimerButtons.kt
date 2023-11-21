package com.kvl.serenity

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    orientation: String,
    enabled: Boolean,
    timers: Map<String, TimerDef>,
    timeRemaining: Duration?,
    sleepTime: Instant?,
    selectedTimer: String?,
    onClickTimer: (String?, Int?) -> Unit
) {
    @Composable
    fun TimerButton(
        key: String,
        def: TimerDef,
        selectedTimer: String?,
        modifier: Modifier,
    ) =
        Button(
            enabled = enabled,
            modifier = modifier,
            colors = when (selectedTimer == key) {
                true -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                else -> ButtonDefaults.buttonColors()
            },
            onClick = {
                when (selectedTimer) {
                    key -> onClickTimer(null, null)
                    else -> onClickTimer(
                        key,
                        def.duration.toMinutes().toInt()
                    )
                }
            })
        {
            Text(
                when (selectedTimer == key) {
                    true -> "Cancel"
                    else -> def.label
                }
            )
        }

    @Composable
    fun TimerButtonsVertical(
    ) = Column {
        when (sleepTime != null) {
            true -> Text(
                "Sleeping in ${
                    DateUtils.formatElapsedTime(timeRemaining?.seconds ?: 0)
                }"
            )

            else -> Text("Sleep timer")
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .padding(top = 10.dp)
        ) {
            timers.toList()
                .map {
                    TimerButton(
                        key = it.first,
                        def = it.second,
                        selectedTimer = selectedTimer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
        }
    }

    @Composable
    fun TimerButtonsHorizontal(
    ) = Column {
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
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
                    )
                }
        }
    }

    when (orientation) {
        "VERTICAL" -> TimerButtonsVertical()
        else -> TimerButtonsHorizontal()
    }
}
