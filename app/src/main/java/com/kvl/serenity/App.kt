package com.kvl.serenity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kvl.serenity.ui.theme.SerenityTheme
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(
    sounds: List<SoundDef>,
    selectedSoundIndex: Int,
    onSetSelectedSound: (Int) -> Unit,
    onClick: () -> Unit,
    startSleepTimer: (Int?) -> Unit,
    sleepTime: Instant? = null,
    isPlaying: Boolean,
    buttonEnabled: Boolean
) {
    val timers = mapOf(
        Pair(
            "15-min", TimerDef(
                duration = Duration.ofMinutes(15),
                label = "15 min"
            )
        ),
        Pair(
            "30-min", TimerDef(
                duration = Duration.ofMinutes(30),
                label = "30 min"
            )
        ),
        Pair(
            "45-min", TimerDef(
                duration = Duration.ofMinutes(45),
                label = "45 min"
            )
        ),
        Pair(
            "1-hour", TimerDef(
                duration = Duration.ofHours(1),
                label = "1 hour"
            )
        ),
        Pair(
            "2-hour", TimerDef(
                duration = Duration.ofHours(2),
                label = "2 hours"
            )
        ),
    )

    val selectedTimer: MutableState<String?> = remember { mutableStateOf(null) }
    val timeRemaining: MutableState<Duration?> = remember { mutableStateOf(null) }
    val fractionRemaining = remember { mutableStateOf(0f) }
    val pagerState = rememberPagerState(selectedSoundIndex)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onSetSelectedSound(page)
        }
    }

    timeRemaining.value = sleepTime?.let {
        Duration.between(Instant.now(), sleepTime)
            .apply { this.minus(this.nano.toLong(), ChronoUnit.NANOS) }
    }

    fractionRemaining.value = timeRemaining.value?.toNanos()?.toDouble()
        ?.div(timers[selectedTimer.value]?.duration?.toNanos()?.toDouble() ?: 1e12)?.toFloat() ?: 0f
    if (timeRemaining.value == null) selectedTimer.value = null

    Column(Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Greeting()
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalPager(
                    pageCount = sounds.size,
                    state = pagerState
                ) { pageIdx ->
                    SoundInfo(
                        name = sounds[pageIdx].name,
                        location = sounds[pageIdx].location
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Dots(sounds.size, selectedSoundIndex)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CircularProgressIndicator(
                progress = fractionRemaining.value,
                modifier = Modifier
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                strokeWidth = 6.dp
            )
            Box(Modifier.padding(9.dp)) {
                PlayPauseButton(enabled = buttonEnabled, onClick = onClick, isPlaying = isPlaying)
            }
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TimerButtons(
                timers = timers,
                timeRemaining = timeRemaining.value,
                sleepTime = sleepTime,
                selectedTimer = selectedTimer.value
            ) { key, duration ->
                selectedTimer.value = key
                startSleepTimer(duration)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    SerenityTheme {
        App(emptyList(), 0, {}, {}, {}, isPlaying = false, sleepTime = null, buttonEnabled = true)
    }
}