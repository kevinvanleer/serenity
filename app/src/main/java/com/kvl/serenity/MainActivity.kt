package com.kvl.serenity

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.text.format.DateUtils
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.serenity.ui.theme.Serenity60
import com.kvl.serenity.ui.theme.SerenityTheme
import com.kvl.serenity.ui.theme.mooli
import com.kvl.serenity.util.isPlaying
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
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var mediaPlayer: AudioTrack
    private var sleepTimer: Fader? = null
    private var sleepTime: MutableState<Instant?> = mutableStateOf(null)

    private val isPlaying = mutableStateOf(false)
    private val enablePlayback = mutableStateOf(true)

    private lateinit var waveFile: WaveFile
    private lateinit var wakeLock: PowerManager.WakeLock

    fun pausePlayback() {
        Log.d("MediaPlayer", "Pausing playback")
        wakeLock.release()
        mediaPlayer.pause()
        isPlaying.value = false
        enablePlayback.value = true
        sleepTime.value = null
    }


    inner class Fader(val duration: Duration, val interval: Long, val delay: Instant) {
        var currentVolume = 1f
        val fader = object : CountDownTimer(duration.toMillis(), interval) {
            override fun onTick(millisUntilFinished: Long) {
                try {
                    currentVolume = millisUntilFinished.toFloat() / duration.toMillis()
                    Log.d("Fade out", "Decreasing volume: $currentVolume")
                    mediaPlayer.setVolume(currentVolume)
                } catch (_: IllegalStateException) {
                    Log.d("Fade out", "Something went wrong with media player")
                }
            }

            override fun onFinish() {
                Log.d("Fade out", "Fade out finished")
                pausePlayback()
                mediaPlayer.setVolume(1f)
            }

        }
        private lateinit var delayTimer: Timer
        fun start() {
            Log.d("Fader", "Starting fader")
            delayTimer = Timer()
            delayTimer.schedule(object : TimerTask() {
                override fun run() {
                    fader.start()
                }
            }, Date.from(delay))
        }

        fun cancel() {
            Log.d("Fader", "Canceling fader")
            fader.cancel()
            delayTimer.cancel()
            mediaPlayer.createVolumeShaper(
                VolumeShaper.Configuration.Builder()
                    .setDuration(VOLUME_RAMP_TIME)
                    .setCurve(
                        floatArrayOf(0f, 1f), floatArrayOf(currentVolume, 1f)
                    )
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .build()
            ).apply(VolumeShaper.Operation.PLAY)
            mediaPlayer.setVolume(1f)
        }
    }

    private fun createSleepTimer(sleepDelay: Int) {
        sleepTime.value = Instant.now().plus(sleepDelay.toLong(), ChronoUnit.MINUTES)
        sleepTimer = Fader(
            duration = Duration.ofMinutes(5L),
            interval = 100L,
            delay = sleepTime.value!!.minus(5, ChronoUnit.MINUTES)
        )
        Log.d("SleepTimer", "Creating sleep timer at $sleepTime")
        //if (mediaPlayer.isPlaying) {
        //scheduleSleepTimer()
        sleepTimer?.start()
        //}
    }

    private fun recordVolumeShaperError(e: java.lang.IllegalStateException) {
        Log.e("onClick", "Could not apply volume shaper", e)
        FirebaseCrashlytics.getInstance().recordException(e)
        FirebaseCrashlytics.getInstance().log("Could not apply volume shaper")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
            }
        waveFile = WaveFile(resources.openRawResource(R.raw.roaring_fork_long_wav))

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance()
        //NOT SURE THESE ACTUALLY DISABLE LOGGING TO SERVER
        firebaseAnalytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })

        mediaPlayer = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(waveFile.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(waveFile.dataSize)
            .build()

        mediaPlayer.setLoopPoints(0, mediaPlayer.bufferSizeInFrames, -1)
        mediaPlayer.write(
            waveFile.audioBuffer,
            waveFile.audioBuffer.remaining(),
            AudioTrack.WRITE_BLOCKING
        )
        val shaperConfig = VolumeShaper.Configuration.Builder()
            .setDuration(VOLUME_RAMP_TIME)
            .setCurve(
                floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)
            )
            .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
            .build()
        var shaper = mediaPlayer.createVolumeShaper(
            shaperConfig
        )

        setContent {
            SerenityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        buttonEnabled = enablePlayback.value,
                        isPlaying = isPlaying.value,
                        sleepTime = sleepTime.value,
                        onClick = {
                            when (mediaPlayer.isPlaying) {
                                true -> {
                                    firebaseAnalytics.logEvent("pause_playback", null)
                                    enablePlayback.value = false
                                    try {
                                        shaper.apply(VolumeShaper.Operation.REVERSE)
                                    } catch (e: java.lang.IllegalStateException) {
                                        recordVolumeShaperError(e)
                                        shaper = mediaPlayer.createVolumeShaper(shaperConfig)
                                        shaper.apply(VolumeShaper.Operation.REVERSE)
                                    }
                                    lifecycleScope.launch {
                                        delay(VOLUME_RAMP_TIME)
                                        sleepTimer?.cancel()
                                        pausePlayback()
                                    }
                                }

                                else -> {
                                    wakeLock.acquire(Duration.ofHours(10).toMillis())
                                    firebaseAnalytics.logEvent("start_playback", null)
                                    isPlaying.value = true
                                    mediaPlayer.play()
                                    try {
                                        shaper.apply(VolumeShaper.Operation.PLAY)
                                    } catch (e: java.lang.IllegalStateException) {
                                        recordVolumeShaperError(e)
                                        shaper = mediaPlayer.createVolumeShaper(shaperConfig)
                                        shaper.apply(VolumeShaper.Operation.PLAY)
                                    }
                                }
                            }
                        },
                        startSleepTimer = { sleepTime: Int ->
                            when (this.sleepTime.value != null) {
                                true -> {
                                    firebaseAnalytics.logEvent("cancel_sleep_timer", null)
                                    this.sleepTime.value = null
                                    sleepTimer?.cancel()
                                }

                                else -> {
                                    firebaseAnalytics.logEvent("start_sleep_timer", Bundle().apply {
                                        putInt("duration", sleepTime)
                                    })
                                    createSleepTimer(sleepTime)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
        mediaPlayer.stop()
        mediaPlayer.release()
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
            text = "serenity",
            fontFamily = mooli,
            fontSize = 10.em,
            color = when (MaterialTheme.colorScheme.primary) {
                Serenity60 -> Color.DarkGray
                else -> Color.LightGray
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Roaring Fork",
            fontFamily = mooli,
            fontSize = 6.em
        )
        Text(
            text = "The Great Smoky Mountains",
            fontFamily = mooli,
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
    data class TimerDef(val duration: Duration, val label: String)

    val timers = mapOf(
        Pair(
            "15-min", TimerDef(
                duration = Duration.ofMinutes(5),
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

    timeRemaining.value = sleepTime?.let {
        Duration.between(Instant.now(), sleepTime)
            .apply { this.minus(this.nano.toLong(), ChronoUnit.NANOS) }
    }

    fractionRemaining.value = timeRemaining.value?.toNanos()?.toDouble()
        ?.div(timers[selectedTimer.value]?.duration?.toNanos()?.toDouble() ?: 1e12)?.toFloat() ?: 0f
    if (timeRemaining.value == null) selectedTimer.value = null

    Column(Modifier.fillMaxSize()) {
        @Composable
        fun getTimerButton(key: String, def: TimerDef) =
            Button(
                modifier = Modifier.weight(1f),
                colors = when (selectedTimer.value == key) {
                    true -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    else -> ButtonDefaults.buttonColors()
                },
                onClick = {
                    selectedTimer.value = key
                    startSleepTimer(def.duration.toMinutes().toInt())
                }) { Text(def.label) }

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
                            DateUtils.formatElapsedTime(timeRemaining.value?.seconds ?: 0)
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
                        .map { getTimerButton(key = it.first, def = it.second) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 10.dp)
                ) {
                    timers.toList().slice(IntRange(3, 4))
                        .map { getTimerButton(key = it.first, def = it.second) }
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
