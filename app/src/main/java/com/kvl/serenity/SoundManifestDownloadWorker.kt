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
import com.kvl.serenity.util.useDebugBlobs
import java.io.ByteArrayOutputStream

class SoundManifestDownloadWorker(private val appContext: Context, workParams: WorkerParameters) :
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

    override suspend fun doWork(): Result {
        try {
            val blob = gcpStorage.get(
                BlobId.of(
                    "serenity-sounds", when (BuildConfig.DEBUG && useDebugBlobs) {
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
            return Result.success(Data.Builder().putString("soundManifest", jsonString).build())
        } catch (e: StorageException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("GCP", "Failed to download sound manifest", e)
            return when (e.code) {
                429, 502, 503, 504 -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}
