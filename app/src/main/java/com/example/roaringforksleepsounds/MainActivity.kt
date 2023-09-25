package com.example.roaringforksleepsounds

import android.media.MediaPlayer
import android.media.VolumeShaper
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import com.example.roaringforksleepsounds.ui.theme.RoaringForkSleepSoundsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val VOLUME_RAMP_TIME = 2000L

class MainActivity : ComponentActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var nextMediaPlayer: MediaPlayer

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

    private fun createSleepTimer(sleepTime: Int) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer.create(applicationContext, waveFile)
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        val shaper = mediaPlayer.createVolumeShaper(VolumeShaper.Configuration.Builder()
            .setDuration(VOLUME_RAMP_TIME)
            .setCurve(
                //generateSequence(0f) { (it + 0.1f).takeUnless { new -> new > 1f } }.toMutableList().apply{add(1f)}.toFloatArray(),
                //generateSequence(0f) { (it + 0.1f).takeUnless { new -> new > 1f } }.toMutableList().apply{add(1f)}.toFloatArray(),
            floatArrayOf(0f,1f), floatArrayOf(0f,1f)
            )
            .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
            .build())
        val isPlaying = mutableStateOf(false)
        val buttonEnabled = mutableStateOf(true)
        /*mediaPlayer.setOnPreparedListener {
            it.start()
        }*/
        createNextMediaPlayer()

        setContent {
            RoaringForkSleepSoundsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        buttonEnabled = buttonEnabled.value, isPlaying = isPlaying.value, timeRemaining = null, onClick = {
                            isPlaying.value = !mediaPlayer.isPlaying
                            when (mediaPlayer.isPlaying) {
                                true -> {
                                    buttonEnabled.value = false
                                    shaper.apply(VolumeShaper.Operation.REVERSE)
                                    lifecycleScope.launch {
                                        delay(VOLUME_RAMP_TIME)
                                        mediaPlayer.pause()
                                        buttonEnabled.value = true
                                    }
                                }
                                else -> {
                                    mediaPlayer.start()
                                    shaper.apply(VolumeShaper.Operation.PLAY)
                                }
                            }
                        },
                        startSleepTimer = {sleepTime: Int ->
                            createSleepTimer(sleepTime)
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
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Serenity",
            fontSize = 8.em
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Roaring Fork",
                fontSize = 6.em
        )
        Text(text = "The Great Smoky Mountains",
            fontSize = 4.em
        )
    }
}

@Composable
fun PlayPauseButton( onClick: () -> Unit = {}, isPlaying: Boolean, enabled: Boolean = true) {
    Button(enabled = enabled, onClick = onClick, modifier = Modifier
        .aspectRatio(1f, false)) {
        when(isPlaying) {
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
    RoaringForkSleepSoundsTheme {
        Greeting()
    }
}

class BooleanParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true,false)
}

@Preview(showBackground = true)
@Composable
fun PlayPauseButtonPreview(
    @PreviewParameter(BooleanParameterProvider::class) isPlaying: Boolean, enabled: Boolean = true
) {
    RoaringForkSleepSoundsTheme {
        PlayPauseButton(onClick = {}, isPlaying, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(onClick: () -> Unit, startSleepTimer: (time: Int) -> Unit, timeRemaining: Int? = null, isPlaying: Boolean, buttonEnabled: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth()) {
            Greeting()
        }
        Box(contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().weight(1f)) {
            PlayPauseButton(enabled = buttonEnabled, onClick = onClick, isPlaying = isPlaying)
        }
        Box(contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            Column {
                Text("Sleep timer")
                when(timeRemaining != null) {
                    true -> Text("Show time remaining")
                    else -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Button(onClick = {startSleepTimer(15)}) { Text("15 minutes") }
                        Button(onClick = {startSleepTimer(30)}) { Text("30 minutes") }
                        Button(onClick = {startSleepTimer(45)}) { Text("45 minutes") }
                        Button(onClick = {startSleepTimer(60)}) { Text("1 hour") }
                        Button(onClick = {startSleepTimer(120)}) { Text("2 hours") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    RoaringForkSleepSoundsTheme {
        App({}, {}, isPlaying = false, timeRemaining = null, buttonEnabled = true)
    }
}