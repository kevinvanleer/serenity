package com.kvl.serenity.util

import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64


fun calculateMd5(filePath: String?): String? =
    try {
        val md = MessageDigest.getInstance("MD5")
        val fis = FileInputStream(filePath)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        val digest = md.digest()
        val base64Bytes: ByteArray = Base64.getEncoder().encode(digest)
        String(base64Bytes)
    } catch (e: NoSuchAlgorithmException) {
        e.printStackTrace()
        null
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
