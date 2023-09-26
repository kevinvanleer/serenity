package com.kvl.serenity

import android.media.MediaPlayer
import android.media.VolumeShaper
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.lifecycleScope
import com.kvl.serenity.ui.theme.SerenityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Timer
import java.util.TimerTask

const val VOLUME_RAMP_TIME = 2000L

class MainActivity : ComponentActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var nextMediaPlayer: MediaPlayer
    private lateinit var sleepTimer: Timer
    private var sleepTime: MutableState<Instant?> = mutableStateOf(null)

    private val isPlaying = mutableStateOf(false)
    private val enablePlayback = mutableStateOf(true)

    private val waveFile: Int = R.raw.roaring_fork_long_wav

    private fun createNextMediaPlayer() {
        nextMediaPlayer = MediaPlayer.create(applicationContext, waveFile)
        nextMediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

        mediaPlayer.setNextMediaPlayer(nextMediaPlayer)
        mediaPlayer.setOnCompletionListener {
            Log.d("MediaPlayer", "Playback complete")
            mediaPlayer.stop()
            mediaPlayer.reset()
            mediaPlayer.release()
            mediaPlayer = nextMediaPlayer
            createNextMediaPlayer()
        }
    }

    private fun createSleepTimer(sleepDelay: Int) {
        sleepTimer = Timer()
        sleepTime.value = Instant.now().plus(sleepDelay.toLong(), ChronoUnit.MINUTES)
        Log.d("SleepTimer", "Creating sleep timer at $sleepTime")
        //if (mediaPlayer.isPlaying) {
        scheduleSleepTimer()
        //}
    }

    private fun scheduleSleepTimer() {
        sleepTimer.schedule(object : TimerTask() {
            override fun run() {
                Log.d("SleepTimer", "Executing sleep timer")
                pausePlayback()
            }
        }, Date.from(sleepTime.value))
    }

    private fun pausePlayback() {
        mediaPlayer.pause()
        isPlaying.value = false
        enablePlayback.value = true
        sleepTime.value = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer.create(applicationContext, waveFile)
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        val shaper = mediaPlayer.createVolumeShaper(
            VolumeShaper.Configuration.Builder()
                .setDuration(VOLUME_RAMP_TIME)
                .setCurve(
                    floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)
                )
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .build()
        )
        /*mediaPlayer.setOnPreparedListener {
            it.start()
        }*/
        createNextMediaPlayer()

        setContent {
            SerenityTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        buttonEnabled = enablePlayback.value,
                        isPlaying = isPlaying.value,
                        /*timeRemaining = sleepTime.value?.let {
                            it.minus(
                                Instant.now().toEpochMilli(),
                                ChronoUnit.MILLIS
                            )
                        }?.toEpochMilli()?.div(1000)?.toInt(),*/
                        sleepTime = sleepTime.value,
                        onClick = {
                            isPlaying.value = !mediaPlayer.isPlaying
                            when (mediaPlayer.isPlaying) {
                                true -> {
                                    enablePlayback.value = false
                                    shaper.apply(VolumeShaper.Operation.REVERSE)
                                    lifecycleScope.launch {
                                        delay(VOLUME_RAMP_TIME)
                                        pausePlayback()
                                    }
                                }

                                else -> {
                                    mediaPlayer.start()
                                    shaper.apply(VolumeShaper.Operation.PLAY)
                                }
                            }
                        },
                        startSleepTimer = { sleepTime: Int ->
                            when (this.sleepTime.value != null) {
                                true -> {
                                    this.sleepTime.value = null
                                    sleepTimer.cancel()
                                }

                                else -> createSleepTimer(sleepTime)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        nextMediaPlayer.stop()
        mediaPlayer.release()
        nextMediaPlayer.release()
    }
}

@Composable
fun Greeting() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Serenity",
            fontSize = 10.em
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Roaring Fork",
            fontSize = 6.em
        )
        Text(
            text = "The Great Smoky Mountains",
            fontSize = 4.em
        )
    }
}

@Composable
fun PlayPauseButton(onClick: () -> Unit = {}, isPlaying: Boolean, enabled: Boolean = true) {
    Button(
        enabled = enabled, onClick = onClick, modifier = Modifier
            .aspectRatio(1f, false)
    ) {
        when (isPlaying) {
            false -> Icon(
                androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                contentDescription = "Start playing sound",
                modifier = Modifier
                    .width(Dp(64f))
                    .height(Dp(64f))
            )

            else -> Icon(
                androidx.compose.material.icons.Icons.Rounded.Pause,
                contentDescription = "Pause sound",
                modifier = Modifier
                    .width(Dp(64f))
                    .height(Dp(64f))
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SerenityTheme {
        Greeting()
    }
}

class BooleanParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}

@Preview(showBackground = true)
@Composable
fun PlayPauseButtonPreview(
    @PreviewParameter(BooleanParameterProvider::class) isPlaying: Boolean, enabled: Boolean = true
) {
    SerenityTheme {
        PlayPauseButton(onClick = {}, isPlaying, enabled = enabled)
    }
}

@Composable
fun App(
    onClick: () -> Unit,
    startSleepTimer: (time: Int) -> Unit,
    sleepTime: Instant? = null,
    isPlaying: Boolean,
    buttonEnabled: Boolean
) {
    val timers = mapOf(
        Pair("15-min", Duration.ofMinutes(15)),
        Pair("30-min", Duration.ofMinutes(30)),
        Pair("45-min", Duration.ofMinutes(45)),
        Pair("1-hour", Duration.ofHours(1)),
        Pair("2-hour", Duration.ofHours(2)),
    )
    val selectedTimer: MutableState<String?> = remember { mutableStateOf(null) }
    val timeRemaining: MutableState<Duration?> = remember { mutableStateOf(null) }
    val fractionRemaining = remember { mutableStateOf(0f) }

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
            Greeting()
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
            Column {
                when (sleepTime != null) {
                    true -> Text(
                        "Sleeping in ${
                            timeRemaining.value?.seconds?.coerceAtLeast(0)?.let {
                                val minutesPart = it / 60
                                val secondsPart = it - (minutesPart * 60)
                                "${minutesPart}:${secondsPart}"
                            }
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
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = when (selectedTimer.value == "15-min") {
                            true -> ButtonDefaults.filledTonalButtonColors()
                            else -> ButtonDefaults.buttonColors()
                        },
                        onClick = {
                            selectedTimer.value = "15-min"
                            startSleepTimer(15)
                        }) { Text("15 min") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedTimer.value = "30-min"
                            startSleepTimer(30)
                        }) { Text("30 min") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedTimer.value = "45-min"
                            startSleepTimer(45)
                        }) { Text("45 min") }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedTimer.value = "1-hour"
                            startSleepTimer(60)
                        }) { Text("1 hour") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedTimer.value = "2-hour"
                            startSleepTimer(120)
                        }) { Text("2 hours") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    SerenityTheme {
        App({}, {}, isPlaying = false, sleepTime = null, buttonEnabled = true)
    }
}
