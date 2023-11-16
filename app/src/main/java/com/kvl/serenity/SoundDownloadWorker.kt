package com.kvl.serenity

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.kvl.serenity.util.calculateMd5
import java.io.ByteArrayOutputStream
import java.io.File

class SoundDownloadWorker(private val appContext: Context, workParams: WorkerParameters) :
    CoroutineWorker(appContext, workParams) {
    private val gcpStorage = StorageOptions.newBuilder()
        .setCredentials(
            ServiceAccountCredentials.fromPkcs8(
                appContext.getString(R.string.serenity_service_account_client_id),
                appContext.getString(R.string.serenity_service_account_client_email),
                appContext.getString(R.string.serenity_service_account_private_key),
                appContext.getString(R.string.serenity_service_account_private_key_id),
                listOf("https://www.googleapis.com/auth/devstorage.read_only")
            )
        )
        .setProjectId(appContext.getString(R.string.serenity_service_account_project_id))
        .build().service

    private suspend fun downloadFromManifest(sounds: List<SoundDef>) {
        val soundsDir = inputData.getString("soundsDir")?.let { File(it) }
        sounds.map { it.filename }.forEach { filename ->
            Log.d("GCP", filename)
            File(
                soundsDir,
                filename
            ).let { file ->
                try {
                    val blob = gcpStorage.get(
                        BlobId.of(
                            "serenity-sounds", when (BuildConfig.DEBUG) {
                                true -> "dev/$filename"
                                else -> filename
                            }
                        )
                    )
                    when (file.exists() && blob.md5 == calculateMd5(file.path)) {
                        true -> Log.d("MD5", "MD5s match")
                        false -> {
                            Log.d("MD5", "MD5s conflict")
                            Log.d("MD5", blob.md5)
                            Log.d("MD5", calculateMd5(file.path).toString())
                        }
                    }
                    if (!file.exists() || blob.md5 != calculateMd5(file.path)) {
                        //setProgress(Data.Builder().putFloat(filename, 0f).build())
                        Log.d("GCP", "Downloading file")
                        blob.downloadTo(file.toPath())
                        Log.d("GCP", "Download complete")
                    }
                    //setProgress(Data.Builder().putFloat(filename, 1f).build())
                } catch (e: StorageException) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    Log.e("GCP", "Failed to download $filename", e)
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        try {
            val blob = gcpStorage.get(
                BlobId.of(
                    "serenity-sounds", when (BuildConfig.DEBUG) {
                        true -> "dev/sound-def.json"
                        else -> "sound-def.json"
                    }
                )
            )
            val os = ByteArrayOutputStream()
            blob.downloadTo(os)
            Log.d("GCP", "Downloaded manifest")
            val jsonString = os.toByteArray().decodeToString()
            appContext.dataStore.edit { settings ->
                settings[SOUND_DEF] = jsonString
            }
            val soundsManifest = Gson().fromJson(
                jsonString, Array<SoundDef>::class.java
            ).toList()

            downloadFromManifest(soundsManifest)
            return Result.success(Data.Builder().putString("soundManifest", jsonString).build())
        } catch (e: StorageException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("GCP", "Failed to download sound manifest", e)
            return Result.failure()
        }
    }
}
