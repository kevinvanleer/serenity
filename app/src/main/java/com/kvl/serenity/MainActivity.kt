package com.kvl.serenity

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.serenity.ui.theme.SerenityTheme
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

    private fun onStartPlayback(shaper: VolumeShaper) {
        wakeLock.acquire(Duration.ofHours(10).toMillis())
        firebaseAnalytics.logEvent("start_playback", null)
        isPlaying.value = true
        mediaPlayer.play()
        shaper.apply(VolumeShaper.Operation.PLAY)
    }

    private fun onPausePlayback(shaper: VolumeShaper) {
        firebaseAnalytics.logEvent("pause_playback", null)
        enablePlayback.value = false
        shaper.apply(VolumeShaper.Operation.REVERSE)
        lifecycleScope.launch {
            delay(VOLUME_RAMP_TIME)
            sleepTimer?.cancel()
            pausePlayback()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Serenity::PlaybackWakeLock")
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

        val shaper = mediaPlayer.createVolumeShaper(
            VolumeShaper.Configuration.Builder()
                .setDuration(VOLUME_RAMP_TIME)
                .setCurve(
                    floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)
                )
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .build()
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
                                    onPausePlayback(shaper)
                                }

                                else -> {
                                    onStartPlayback(shaper)
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
        wakeLock.release()
        mediaPlayer.stop()
        mediaPlayer.release()
    }
}
