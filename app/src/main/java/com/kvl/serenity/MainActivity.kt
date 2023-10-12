package com.kvl.serenity

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import com.kvl.serenity.ui.theme.SerenityTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Timer
import java.util.TimerTask

const val VOLUME_RAMP_TIME = 2000L

val Context.dataStore by preferencesDataStore(name = "state")
val CURRENT_SOUND_SELECTION_KEY = intPreferencesKey("current_sound_selection")
val SOUND_DEF = stringPreferencesKey("sound_def")

class MainActivity : ComponentActivity() {
    private lateinit var soundsDir: File
    private lateinit var firebaseStorage: StorageReference
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var waveTrack: AudioTrack
    private lateinit var shaper: VolumeShaper
    private var sleepTimer: Fader? = null
    private var sleepTime: MutableState<Instant?> = mutableStateOf(null)

    private val isPlaying = mutableStateOf(false)
    private val enablePlayback = mutableStateOf(true)

    private lateinit var wakeLock: PowerManager.WakeLock

    private val selectedSoundIndex = mutableStateOf(0)
    private val sounds = mutableStateOf((emptyList<SoundDef>()))
    private val downloading = mutableStateOf(false)

    fun pausePlayback() {
        Log.d("MediaPlayer", "Pausing playback")
        if (wakeLock.isHeld) wakeLock.release()
        waveTrack.pause()
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
                    waveTrack.setVolume(currentVolume)
                } catch (_: IllegalStateException) {
                    Log.d("Fade out", "Something went wrong with media player")
                }
            }

            override fun onFinish() {
                Log.d("Fade out", "Fade out finished")
                pausePlayback()
                waveTrack.setVolume(1f)
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
            waveTrack.createVolumeShaper(
                VolumeShaper.Configuration.Builder()
                    .setDuration(VOLUME_RAMP_TIME)
                    .setCurve(
                        floatArrayOf(0f, 1f), floatArrayOf(currentVolume, 1f)
                    )
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .build()
            ).apply(VolumeShaper.Operation.PLAY)
            waveTrack.setVolume(1f)
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

    private fun onStartPlayback() {
        if (!wakeLock.isHeld) wakeLock.acquire(Duration.ofHours(10).toMillis())
        firebaseAnalytics.logEvent("start_playback", null)
        isPlaying.value = true
        if (!::waveTrack.isInitialized) onSetSelectedSound(selectedSoundIndex.value) else {
            waveTrack.play()
            shaper.apply(VolumeShaper.Operation.PLAY)
        }
    }

    private fun onPausePlayback() {
        firebaseAnalytics.logEvent("pause_playback", null)
        enablePlayback.value = false
        shaper.apply(VolumeShaper.Operation.REVERSE)
        lifecycleScope.launch {
            delay(VOLUME_RAMP_TIME)
            sleepTimer?.cancel()
            pausePlayback()
        }
    }

    private fun onCancelSleepTimer() {
        firebaseAnalytics.logEvent("cancel_sleep_timer", null)
        this.sleepTime.value = null
        sleepTimer?.cancel()
        sleepTimer = null
    }

    private fun onStartSleepTimer(sleepTime: Int) {
        onCancelSleepTimer()
        firebaseAnalytics.logEvent("start_sleep_timer", Bundle().apply {
            putInt("duration", sleepTime)
        })
        createSleepTimer(sleepTime)
    }

    private fun onSetSelectedSound(selected: Int) {
        selectedSoundIndex.value = selected
        val wasPlaying = isPlaying.value
        isPlaying.value = false

        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("PREFS", "Setting selected sound to $selected")
            getWaveTrack(sounds.value[selectedSoundIndex.value].filename)
            dataStore.edit { settings ->
                settings[CURRENT_SOUND_SELECTION_KEY] = selectedSoundIndex.value
            }
        }.invokeOnCompletion { if (wasPlaying) onStartPlayback() }
    }

    private fun getWaveTrack(filePathString: String) {
        Log.d("SND", "Loading $filePathString")

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Serenity::PlaybackWakeLock")
            }
        val waveFile = WaveFile(
            File(
                soundsDir,
                filePathString
            ).inputStream()
        )

        if (::waveTrack.isInitialized) waveTrack.release()

        waveTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(
                        when (waveFile.formatCode) {
                            3 -> AudioFormat.ENCODING_PCM_FLOAT
                            1 -> when (waveFile.sampleSize) {
                                16 -> AudioFormat.ENCODING_PCM_16BIT
                                8 -> AudioFormat.ENCODING_PCM_8BIT
                                else -> AudioFormat.ENCODING_DEFAULT
                            }

                            else -> AudioFormat.ENCODING_DEFAULT
                        }
                    )
                    .setSampleRate(waveFile.sampleRate)
                    .setChannelMask(
                        when (waveFile.channelCount) {
                            2 -> AudioFormat.CHANNEL_OUT_STEREO
                            1 -> AudioFormat.CHANNEL_OUT_MONO
                            else -> AudioFormat.CHANNEL_OUT_DEFAULT
                        }
                    )
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(waveFile.dataSize)
            .build()

        waveTrack.setLoopPoints(0, waveTrack.bufferSizeInFrames, -1)
        waveTrack.write(
            waveFile.audioBuffer,
            waveFile.audioBuffer.remaining(),
            AudioTrack.WRITE_BLOCKING
        )

        shaper = waveTrack.createVolumeShaper(
            VolumeShaper.Configuration.Builder()
                .setDuration(VOLUME_RAMP_TIME)
                .setCurve(
                    floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)
                )
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .build()
        )

    }

    private fun downloadFromManifest() {
        val tasks: List<FileDownloadTask> = sounds.value.map { it.filename }.mapNotNull { file ->
            Log.d("FIREBASE_STORAGE", file)
            File(
                soundsDir,
                file
            ).exists().takeIf { e -> !e }?.let {
                downloading.value = true
                Log.d("FIREBASE_STORAGE", "Downloading file")
                //item.metadata.result.md5Hash
                firebaseStorage.child(file)
                    .getFile(
                        File(
                            soundsDir,
                            file
                        )
                    )
            }
        }

        Tasks.whenAll(tasks).addOnCompleteListener {
            downloading.value = false
        }
    }

    private fun initializeUi() {
        Log.d("onCreate", "initializeUi")
        setContent {
            SerenityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        downloading = downloading.value,
                        sounds = sounds.value,
                        selectedSoundIndex = selectedSoundIndex.value,
                        onSetSelectedSound = { idx: Int -> onSetSelectedSound(idx) },
                        buttonEnabled = enablePlayback.value,
                        isPlaying = isPlaying.value,
                        sleepTime = sleepTime.value,
                        onClick = {
                            when (isPlaying.value) {
                                true -> onPausePlayback()
                                else -> onStartPlayback()
                            }
                        },
                        startSleepTimer = { sleepTime: Int? ->
                            when (sleepTime == null) {
                                true -> onCancelSleepTimer()
                                else -> onStartSleepTimer(sleepTime)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeUi()

        soundsDir = File(
            when (Environment.isExternalStorageEmulated()) {
                true -> applicationContext.filesDir
                else -> applicationContext.getExternalFilesDir(null)
            }, "sounds"
        )
        if (!soundsDir.exists()) {
            Log.d("onCreate", "Creating sounds directory")
            soundsDir.mkdirs()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data.map { prefs ->
                prefs[CURRENT_SOUND_SELECTION_KEY]
            }.collect {
                Log.d("PREFS", "Current sound selection: $it")
                selectedSoundIndex.value = it ?: 0
            }
        }

        lifecycleScope.launch {
            dataStore.data.map { prefs ->
                prefs[SOUND_DEF]
            }.collect { pref ->
                if (pref != null) {
                    Log.d("onCreate", "Getting sound def from pref")
                    sounds.value = Gson().fromJson(
                        pref, Array<SoundDef>::class.java
                    ).toList()
                    sounds.value.all { sound ->
                        File(soundsDir, sound.filename).exists()
                    }.takeIf { it }.let {
                        Log.d("onCreate", "Took shortcut to initialize sounds")
                        //initializeUi()
                        downloading.value = false
                    }
                } else {
                    Log.d("onCreate", "No sound def pref")
                    downloading.value = true
                }
            }
        }

        firebaseStorage = FirebaseStorage.getInstance().let {
            when (BuildConfig.DEBUG) {
                true -> it.getReference("dev")
                else -> it.reference
            }
        }

        lifecycleScope.launch {
            firebaseStorage.child("sound-def.json").getBytes(1000)
                .addOnSuccessListener {
                    val jsonString = it.decodeToString()

                    lifecycleScope.launch(Dispatchers.IO) {
                        dataStore.edit { settings ->
                            settings[SOUND_DEF] = jsonString
                        }
                    }
                    sounds.value = Gson().fromJson(
                        jsonString, Array<SoundDef>::class.java
                    ).toList()

                    downloadFromManifest()
                }.addOnFailureListener {
                    Log.e(
                        "onCreate",
                        "Failed to get sound definitions: ${(it as StorageException).errorCode}:${it.httpResultCode}",
                        it
                    )


                }
        }

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Serenity::PlaybackWakeLock")
            }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance()
        //NOT SURE THESE ACTUALLY DISABLE LOGGING TO SERVER
        firebaseAnalytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })

    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
        if (wakeLock.isHeld) wakeLock.release()
        waveTrack.stop()
        waveTrack.release()
    }
}
