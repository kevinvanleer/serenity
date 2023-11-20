package com.kvl.serenity

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.cloud.storage.contrib.nio.CloudStorageConfiguration
import com.google.cloud.storage.contrib.nio.CloudStorageFileSystem
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.serenity.util.calculateMd5
import com.kvl.serenity.util.debugUseFullBlobs
import com.kvl.serenity.util.getGcpStorage
import com.kvl.serenity.util.getServiceAccountCredentials
import com.kvl.serenity.util.getSoundBlob
import com.kvl.serenity.util.getSoundPath
import com.kvl.serenity.util.soundsBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class SoundDownloadWorker(private val appContext: Context, workParams: WorkerParameters) :
    CoroutineWorker(appContext, workParams) {

    private val logTag = this::class.simpleName
    override suspend fun doWork(): Result =
        inputData.getString("soundsDir")?.let { soundsDir ->
            inputData.getString("sound")?.let { filename ->
                Log.d(logTag, "doWork $filename: $id")
                File(
                    File(soundsDir),
                    filename
                ).let { file ->
                    try {
                        val blob = getGcpStorage(appContext).get(
                            getSoundBlob(filename)
                        )
                        when (file.exists() && blob.md5 == calculateMd5(file.path)) {
                            true -> {
                                Log.d(logTag, "$filename MD5s match")
                                Result.success()
                            }

                            false -> {
                                Log.d(logTag, "$filename MD5s conflict")
                                downloadFile(blob.md5, file.toPath(), filename)
                            }
                        }
                    } catch (e: StorageException) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                        Log.e(logTag, "Failed to download $filename", e)
                        return when (e.code) {
                            429, 502, 503, 504 -> Result.retry()
                            else -> Result.failure()
                        }
                    }
                }
            } ?: Result.failure()
        } ?: Result.failure()

    private suspend fun downloadFile(
        blobMd5: String,
        filePath: Path,
        filename: String
    ): Result =
        try {
            withContext(Dispatchers.IO) {
                FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            }
                .use { to ->
                    Log.d(logTag, "Downloading $filename")

                    CloudStorageFileSystem.forBucket(
                        soundsBucket,
                        CloudStorageConfiguration.DEFAULT,
                        StorageOptions.newBuilder()
                            .setCredentials(
                                getServiceAccountCredentials(appContext)
                            )
                            .build()
                    ).let { fs ->
                        Files.newByteChannel(
                            fs.getPath("/${getSoundPath(filename)}"),
                            StandardOpenOption.READ
                        ).use { from ->
                            val totalBytes = from.size()
                            val chunkSize = when (BuildConfig.DEBUG && debugUseFullBlobs) {
                                false -> (totalBytes / 100 / 1024 * 1024).coerceAtMost(1024 * 1024)
                                true -> 1024L
                            }
                            var startByte = when (to.size() >= totalBytes) {
                                true -> 0L
                                else -> to.size()
                            }
                            setProgress(
                                Data.Builder()
                                    .putFloat(filename, startByte.toFloat() / totalBytes)
                                    .build()
                            )

                            from.position(startByte)

                            Log.v(logTag, "$filename chunk size: $chunkSize")
                            Log.v(logTag, "$filename total download bytes: $totalBytes")
                            Log.v(logTag, "$filename initial temp file size: ${to.size()}")
                            Log.v(logTag, "$filename seeking to $startByte")

                            while (to.size() < totalBytes) {
                                val bytesRead = to.transferFrom(
                                    from,
                                    startByte,
                                    chunkSize
                                )
                                startByte += bytesRead
                                (startByte.toFloat() / totalBytes).let { progress ->
                                    setProgress(
                                        Data.Builder()
                                            .putFloat(filename, progress)
                                            .build()
                                    )
                                    Log.v(logTag, "$filename progress: $progress")
                                }

                                Log.v(logTag, "$filename downloaded bytes: $startByte")
                                Log.v(logTag, "$filename channel size: ${to.size()}")
                                Log.v(logTag, "$filename total bytes: $totalBytes")
                            }
                        }
                    }
                }
            when (blobMd5 != calculateMd5(filePath)) {
                true -> {
                    Log.d(logTag, "$filename: MD5 check failed, retrying...")
                    FirebaseCrashlytics.getInstance()
                        .log("$filename: MD5 check failed. Will retry.")
                    Result.retry()
                }

                else -> {
                    Log.d(logTag, "$filename: download complete")
                    Result.success()
                }
            }
        } catch (e: StorageException) {
            Log.e(logTag, "Error downloading $filename", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            when (e.code) {
                429, 502, 503, 504 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: IOException) {
            Log.e(logTag, "Error downloading $filename", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            Result.retry()
        }
}
