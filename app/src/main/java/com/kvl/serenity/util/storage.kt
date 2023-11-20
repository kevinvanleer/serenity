package com.kvl.serenity.util

import android.content.Context
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.kvl.serenity.BuildConfig
import com.kvl.serenity.R
import java.io.File

const val soundsBucket = "serenity-sounds"
const val debugUseFullBlobs = false
fun getServiceAccountCredentials(appContext: Context): ServiceAccountCredentials =
    ServiceAccountCredentials.fromPkcs8(
        appContext.getString(R.string.serenity_service_account_client_id),
        appContext.getString(R.string.serenity_service_account_client_email),
        appContext.getString(R.string.serenity_service_account_private_key),
        appContext.getString(R.string.serenity_service_account_private_key_id),
        listOf("https://www.googleapis.com/auth/devstorage.read_only")
    )

fun getGcpStorage(appContext: Context): Storage = StorageOptions.newBuilder()
    .setCredentials(
        getServiceAccountCredentials(appContext)
    )
    .setProjectId(appContext.getString(R.string.serenity_service_account_project_id))
    .build().service

fun getSoundPath(filename: String) = when (BuildConfig.DEBUG && debugUseFullBlobs) {
    true -> "dev/$filename"
    else -> filename
}

fun getSoundBlob(filename: String): BlobId = BlobId.of(
    soundsBucket, getSoundPath(filename)
)

fun isBlobDownloaded(file: File, blob: Blob) = file.exists() && blob.md5 == calculateMd5(file.path)
//fun isBlobDownloaded(soundsDir: File, blob: Blob) = File(soundsDir, blob.name).let { file ->
//    file.exists() && blob.md5 == calculateMd5(file.path)
//}
