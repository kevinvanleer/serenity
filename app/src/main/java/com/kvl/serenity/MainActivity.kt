package com.kvl.serenity

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.WindowMetricsCalculator
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kvl.serenity.ui.theme.SerenityTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

const val VOLUME_RAMP_TIME = 2000L

val Context.dataStore by preferencesDataStore(name = "state")
val CURRENT_SOUND_SELECTION_KEY = intPreferencesKey("current_sound_selection")
val START_PLAYBACK_COUNT = intPreferencesKey("start_playback_count")
val SOUND_DEF = stringPreferencesKey("sound_def")

class MainActivity : ComponentActivity() {
    private val downloadSoundWorkTag = "com.kvl.cyclotrack:download-sound"
    private val workManager = WorkManager.getInstance(this)
    private lateinit var soundsDir: File
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var waveTrack: AudioTrack
    private lateinit var shaper: VolumeShaper
    private var sleepTimer: Fader? = null
    private var sleepTime: MutableState<Instant?> = mutableStateOf(null)

    private val isPlaying = mutableStateOf(false)
    private val enablePlayback = mutableStateOf(true)
    private val bluetoothConnectStatePermissionGranted = mutableStateOf(true)

    private lateinit var wakeLock: PowerManager.WakeLock

    private val selectedSoundIndex = mutableStateOf(0)
    private val sounds = mutableStateOf((emptyList<SoundDef>()))
    private val downloadProgress = mutableStateOf((emptyMap<String, Float>()))
    private val widthWindowSizeClass = mutableStateOf(WindowWidthSizeClass.COMPACT)
    private val heightWindowSizeClass = mutableStateOf(WindowHeightSizeClass.MEDIUM)

    fun pausePlayback() {
        Log.d("MediaPlayer", "Pausing playback")
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        if (::waveTrack.isInitialized) waveTrack.pause()
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
                    if (::waveTrack.isInitialized) waveTrack.setVolume(currentVolume)
                } catch (_: IllegalStateException) {
                    Log.d("Fade out", "Something went wrong with media player")
                }
            }

            override fun onFinish() {
                Log.d("Fade out", "Fade out finished")
                pausePlayback()
                if (::waveTrack.isInitialized) waveTrack.setVolume(1f)
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
            if (::waveTrack.isInitialized) {
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

    private fun startPlayback() {
        updatePermissionGrants()
        if (!wakeLock.isHeld) wakeLock.acquire(Duration.ofHours(10).toMillis())
        firebaseAnalytics.logEvent("start_playback", null)
        isPlaying.value = true
        when (::waveTrack.isInitialized) {
            false -> onSetSelectedSound(selectedSoundIndex.value)
            else -> {
                waveTrack.play()
                shaper.apply(VolumeShaper.Operation.PLAY)
            }
        }
    }

    private fun onStartPlayback() {
        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data.first().let { prefs ->
                prefs[START_PLAYBACK_COUNT]
            }.let {
                Log.d("MainActivity", "Playback count: $it")
                val playbackCount = (it ?: 0) + 1
                when {
                    playbackCount % 10 == 0 -> {
                        val manager = ReviewManagerFactory.create(this@MainActivity)
                        val request = manager.requestReviewFlow()
                        request.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // We got the ReviewInfo object
                                val reviewInfo = task.result
                                val flow = manager.launchReviewFlow(this@MainActivity, reviewInfo)
                                flow.addOnCompleteListener { _ ->
                                    // The flow has finished. The API does not indicate whether the user
                                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                                    // matter the result, we continue our app flow.
                                    startPlayback()
                                }
                            } else {
                                startPlayback()
                                // There was some problem, log or handle the error code.
                                @ReviewErrorCode val reviewErrorCode =
                                    (task.exception as ReviewException).errorCode
                                FirebaseCrashlytics.getInstance()
                                    .recordException(task.exception as ReviewException)
                                Log.e(
                                    "MainActivity",
                                    "Review error: $reviewErrorCode",
                                    task.exception as ReviewException
                                )
                            }
                        }
                    }

                    else -> startPlayback()
                }
                dataStore.edit { settings ->
                    settings[START_PLAYBACK_COUNT] = playbackCount
                }
            }
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

        if (sounds.value.isNotEmpty() && downloadProgress.value.isNotEmpty() &&
            downloadProgress.value[sounds.value[selected].filename] == 1f
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("PREFS", "Setting selected sound to $selected")
                getWaveTrack(sounds.value[selectedSoundIndex.value].filename)
                dataStore.edit { settings ->
                    settings[CURRENT_SOUND_SELECTION_KEY] = selectedSoundIndex.value
                }
            }.invokeOnCompletion { if (wasPlaying) onStartPlayback() }
        }
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

    private fun computeWindowSizeClasses() {
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
        val width = metrics.bounds.width()
        val height = metrics.bounds.height()
        val density = resources.displayMetrics.density
        val windowSizeClass = WindowSizeClass.compute(width / density, height / density)
        // COMPACT, MEDIUM, or EXPANDED
        widthWindowSizeClass.value = windowSizeClass.windowWidthSizeClass
        // COMPACT, MEDIUM, or EXPANDED
        heightWindowSizeClass.value = windowSizeClass.windowHeightSizeClass
    }

    private fun initializeUi() {
        Log.d("MainActivity", "initializeUi")
        computeWindowSizeClasses()
        setContent {
            SerenityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(
                        widthWindowSizeClass = widthWindowSizeClass.value,
                        heightWindowSizeClass = heightWindowSizeClass.value,
                        downloadProgress = downloadProgress.value,
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!bluetoothConnectStatePermissionGranted.value) NearbyDevicesPermissionDialog(
                        onAllow = {
                            requestPermissions(arrayOf(BLUETOOTH_CONNECT), 0)
                            bluetoothConnectStatePermissionGranted.value = true
                        },
                    )
                }
            }
        }
    }

    private fun updatePermissionGrants() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (ContextCompat.checkSelfPermission(
                this,
                BLUETOOTH_CONNECT
            )) {
                PackageManager.PERMISSION_DENIED -> bluetoothConnectStatePermissionGranted.value =
                    false

                PackageManager.PERMISSION_GRANTED -> bluetoothConnectStatePermissionGranted.value =
                    true
            }
        } else {
            bluetoothConnectStatePermissionGranted.value = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("MainActivity", "saving instance state")
        outState.putBoolean("is_playing", isPlaying.value)
        outState.putString("download_progress_json", Gson().toJson(downloadProgress.value))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePermissionGrants()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            "com.kvl.serenity.pause_playback" -> pausePlayback()
                        }
                    }
                },
                IntentFilter("com.kvl.serenity.pause_playback"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            "com.kvl.serenity.pause_playback" -> pausePlayback()
                        }
                    }
                },
                IntentFilter("com.kvl.serenity.pause_playback")
            )
        }

        initializeUi()
        soundsDir = File(
            when (Environment.isExternalStorageEmulated()) {
                true -> applicationContext.filesDir
                else -> applicationContext.getExternalFilesDir(null)
            }, "sounds"
        )
        if (!soundsDir.exists()) {
            Log.d("MainActivity", "Creating sounds directory")
            soundsDir.mkdirs()
        }
        Log.d("MainActivity", "soundsDir = ${soundsDir.toPath()}")

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data.first().let { prefs ->
                prefs[CURRENT_SOUND_SELECTION_KEY]
            }.let {
                Log.d("MainActivity", "Current sound selection: $it")
                selectedSoundIndex.value = it ?: 0
            }
        }


        savedInstanceState?.getBoolean("is_playing")?.let {
            Log.d("MainActivity", "Restoring saved play state")
            isPlaying.value = it
        }
        savedInstanceState?.getString("download_progress_json")?.let {
            val mapType = object : TypeToken<Map<String, Float>>() {}.type
            Log.d("MainActivity", it)
            downloadProgress.value = Gson().fromJson(
                it, mapType
            )
            Log.d("MainActivity", downloadProgress.value.toString())
        }

        lifecycleScope.launch {
            dataStore.data.first().let { prefs ->
                prefs[SOUND_DEF]
            }.let { pref ->
                if (pref != null) {
                    Log.d("MainActivity", "Getting sound def from pref")
                    sounds.value = Gson().fromJson(
                        pref, Array<SoundDef>::class.java
                    ).toList()

                    workManager.getWorkInfosByTagLiveData(downloadSoundWorkTag)
                        .let { downloadOutput ->
                            downloadOutput.observe(this@MainActivity) { workInfos ->
                                workInfos?.filterNot { it.state.isFinished }
                                    ?.forEach { workInfo ->
                                        try {
                                            getDownloadStatus(
                                                sounds.value.first {
                                                    it.filename == workInfo.tags.first { tag ->
                                                        tag.startsWith(
                                                            "filename:"
                                                        )
                                                    }.drop("filename:".length)
                                                },
                                                workInfo
                                            )
                                        } catch (e: NoSuchElementException) {
                                            Log.e(
                                                "MainActivity",
                                                "Could not observe running download worker",
                                                e
                                            )
                                            FirebaseCrashlytics.getInstance().recordException(e)
                                        }
                                    }
                            }
                        }
                }
            }
        }

        downloadSounds()

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Serenity::PlaybackWakeLock")
            }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
//NOT SURE THESE ACTUALLY DISABLE LOGGING TO SERVER
        firebaseAnalytics.setAnalyticsCollectionEnabled(shouldCollectAnalytics())
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(shouldCollectAnalytics())
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })

    }

    private fun shouldCollectAnalytics(): Boolean =
        Settings.System.getString(this.contentResolver, "firebase.test.lab").let {
            ("true" == it) || !BuildConfig.DEBUG
        }

    private fun downloadSounds() {
        Log.d("MainActivity", "Downloading sounds")
        val downloadSoundManifestWorkTag = "com.kvl.cyclotrack:download-sound-manifest"
        OneTimeWorkRequestBuilder<SoundManifestDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                Data.Builder()
                    .putString("soundsDir", soundsDir.toString())
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build().let { request ->
                workManager.beginUniqueWork(
                    downloadSoundManifestWorkTag,
                    ExistingWorkPolicy.KEEP,
                    request
                ).enqueue()
                workManager.getWorkInfoByIdLiveData(request.id)
                    .let { downloadOutput ->
                        downloadOutput.observe(this) { workInfo ->
                            if (workInfo != null) {
                                processManifest(workInfo)
                                downloadFiles(workManager)
                            }
                        }
                    }
            }
    }

    private fun processManifest(workInfo: WorkInfo) {
        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
            workInfo.outputData.getString("soundManifest")
                ?.let { jsonString ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        dataStore.edit { settings ->
                            settings[SOUND_DEF] = jsonString
                        }
                    }
                    sounds.value = Gson().fromJson(
                        jsonString, Array<SoundDef>::class.java
                    ).toList()

                    downloadProgress.value.toMutableMap().let { dp ->
                        Log.d("DOWNLOAD", "dp:${dp}")
                        sounds.value.forEach { sound ->
                            if (!dp.containsKey(sound.filename)) {
                                dp[sound.filename] = 0f
                            }
                        }
                        downloadProgress.value = dp
                    }
                }
        }
    }

    private fun downloadFiles(workManager: WorkManager) {
        sounds.value.map { sound ->
            OneTimeWorkRequestBuilder<SoundDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .addTag(downloadSoundWorkTag)
                .addTag("filename:${sound.filename}")
                .setInputData(
                    Data.Builder()
                        .putString(
                            "soundsDir",
                            soundsDir.toString()
                        )
                        .putString("sound", sound.filename)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build().let { request ->
                    workManager.beginUniqueWork(
                        "$downloadSoundWorkTag-${sound.filename}",
                        ExistingWorkPolicy.KEEP,
                        request
                    ).enqueue()
                    workManager.getWorkInfoByIdLiveData(request.id)
                        .let { output ->
                            output.observe(this) { workInfo ->
                                if (workInfo != null) getDownloadStatus(sound, workInfo)
                            }
                        }
                }
        }
    }

    private fun getDownloadStatus(sound: SoundDef, workInfo: WorkInfo) {
        when (val state =
            workInfo.state) {
            WorkInfo.State.BLOCKED,
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING -> {
                workInfo.progress.getFloat(
                    sound.filename,
                    -1f
                ).takeIf { it != -1f }
                    ?.let { progress ->
                        downloadProgress.value.toMutableMap()
                            .let { dp ->
                                dp[sound.filename] =
                                    progress
                                downloadProgress.value =
                                    dp
                                Log.v(
                                    "getDownloadStatus",
                                    "${sound.filename}: $progress"
                                )
                            }
                    }
            }

            WorkInfo.State.FAILED -> {
                FirebaseCrashlytics.getInstance().log("Failed to download ${sound.filename}")
            }

            WorkInfo.State.SUCCEEDED -> {
                Log.d("getDownloadStatus", "SUCCESS: ${sound.filename}")
                downloadProgress.value.toMutableMap()
                    .let { dp ->
                        dp[sound.filename] =
                            1f
                        downloadProgress.value =
                            dp
                    }
            }

            else -> {
                Log.d(
                    "getDownloadStatus",
                    "state is: ${state.name}"
                )
                downloadProgress.value.toMutableMap()
                    .let { dp ->
                        dp[sound.filename] =
                            0f
                        downloadProgress.value =
                            dp
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
        if (wakeLock.isHeld) wakeLock.release()
        if (::waveTrack.isInitialized) {
            waveTrack.stop()
            waveTrack.release()
        }
    }
}
