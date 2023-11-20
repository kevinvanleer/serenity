package com.kvl.serenity.util

import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64


fun calculateMd5(filePath: String?): String? = filePath?.let { calculateMd5(File(it).toPath()) }

fun calculateMd5(filePath: Path): String? = try {
    MessageDigest.getInstance("MD5").let { md ->
        FileChannel.open(filePath, StandardOpenOption.READ).let { fileChannel ->
            md.update(fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()))
            val base64Bytes: ByteArray = Base64.getEncoder().encode(md.digest())
            String(base64Bytes)
        }
    }
} catch (e: NoSuchAlgorithmException) {
    Log.d("MD5", "NoSuchAlgorithm", e)
    null
} catch (e: IOException) {
    Log.d("MD5", "IOException", e)
    null
} catch (e: FileNotFoundException) {
    Log.d("MD5", "File not found", e)
    null
}
