package com.example.roaringforksleepsounds

import android.media.MediaPlayer
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.roaringforksleepsounds.ui.theme.RoaringForkSleepSoundsTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer.create(applicationContext, waveFile)
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

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
                    Greeting()
                    PlayPauseButton(isPlaying = mediaPlayer.isPlaying, onClick = { when(mediaPlayer.isPlaying) {true -> mediaPlayer.pause() else -> mediaPlayer.start()} })
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
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Serenity",
        modifier = modifier
    )
}

@Composable
fun PlayPauseButton( onClick: () -> Unit = {}, isPlaying: Boolean) {
    Button(onClick = onClick) {
        when(isPlaying) {
            false -> Icon(
                androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                contentDescription = "Start playing sound"
            )
            else -> Icon(
                androidx.compose.material.icons.Icons.Rounded.Pause,
                contentDescription = "Pause sound"
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
    @PreviewParameter(BooleanParameterProvider::class) isPlaying: Boolean
) {
    RoaringForkSleepSoundsTheme {
        PlayPauseButton(onClick = {}, isPlaying)
    }
}