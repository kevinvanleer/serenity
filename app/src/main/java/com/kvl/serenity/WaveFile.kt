package com.kvl.serenity

import android.util.Log
import com.kvl.serenity.util.toInt
import com.kvl.serenity.util.toShort
import java.io.InputStream
import java.nio.ByteBuffer

class WaveFile(private val inputStream: InputStream) {
    val fileSize: Int
    val dataSize: Int
    val sampleRate: Int
    val channelCount: Int
    val sampleSize: Int
    val formatCode: Int
    val audioBuffer: ByteBuffer

    init {
        val fourByteArray = ByteArray(4)
        val twoByteArray = ByteArray(2)

        inputStream.read(fourByteArray)
        Log.d("WAVE", fourByteArray.decodeToString())
        if (fourByteArray.decodeToString() != "RIFF") throw RuntimeException("INVALID RIFF HEADER: ${fourByteArray.decodeToString()}")
        inputStream.read(fourByteArray)
        fileSize = fourByteArray.toInt()
        Log.d("WAVE", "$fileSize")
        val wave = ByteArray(4)
        inputStream.read(wave)
        Log.d("WAVE", wave.decodeToString())
        if (wave.decodeToString() != "WAVE") throw RuntimeException("INVALID WAVE HEADER: ${wave.decodeToString()}")

        val fmt = ByteArray(4)
        inputStream.read(fmt)
        Log.d("WAVE", fmt.decodeToString())
        if (fmt.decodeToString() != "fmt ") throw RuntimeException("INVALID FMT HEADER: ${fmt.decodeToString()}")

        val chunkSize = ByteArray(4)
        inputStream.read(chunkSize)
        Log.d("WAVE", "${chunkSize.toInt()}")

        inputStream.read(twoByteArray)
        formatCode = twoByteArray.toShort().toInt()
        Log.d("WAVE", "${formatCode}")

        inputStream.read(twoByteArray)
        channelCount = twoByteArray.toShort().toInt()
        Log.d("WAVE", "$channelCount")

        inputStream.read(fourByteArray)
        sampleRate = fourByteArray.toInt()
        Log.d("WAVE", "$sampleRate")

        val bytesPerSecond = ByteArray(4)
        inputStream.read(bytesPerSecond)
        Log.d("WAVE", "${bytesPerSecond.toInt()}")

        val blockAlign = ByteArray(2)
        inputStream.read(blockAlign)
        Log.d("WAVE", "${blockAlign.toShort()}")

        inputStream.read(twoByteArray)
        sampleSize = twoByteArray.toShort().toInt()
        Log.d("WAVE", "${sampleSize.toShort()}")


        inputStream.read(fourByteArray)
        Log.d("WAVE", fourByteArray.decodeToString())
        if (fourByteArray.decodeToString() == "fact") {
            //throw RuntimeException("INVALID FACT CHUNK HEADER: ${factChunk.decodeToString()}")

            val factChunkSize = ByteArray(4)
            inputStream.read(factChunkSize)
            Log.d("WAVE", "${factChunkSize.toInt()}")

            val factChunkSampleLength = ByteArray(4)
            inputStream.read(factChunkSampleLength)
            Log.d("WAVE", "${factChunkSampleLength.toInt()}")

            val peakChunk = ByteArray(4)
            inputStream.read(peakChunk)
            Log.d("WAVE", peakChunk.decodeToString())
            if (peakChunk.decodeToString() != "PEAK") throw RuntimeException("INVALID PEAK CHUNK HEADER: ${peakChunk.decodeToString()}")

            val peakChunkSize = ByteArray(4)
            inputStream.read(peakChunkSize)
            Log.d("WAVE", "${peakChunkSize.toInt()}")

            val peakChunkVersion = ByteArray(4)
            inputStream.read(peakChunkVersion)
            Log.d("WAVE", "${peakChunkVersion.toInt()}")

            val peakChunkTimestamp = ByteArray(4)
            inputStream.read(peakChunkTimestamp)
            Log.d("WAVE", "${peakChunkTimestamp.toInt()}")

            val peakData = ByteArray(peakChunkSize.toInt() - 8)
            inputStream.read(peakData)
            Log.d("WAVE", "$peakData")

            inputStream.read(fourByteArray)
        }

        Log.d("WAVE", fourByteArray.decodeToString())
        if (fourByteArray.decodeToString() != "data") throw RuntimeException("INVALID DATA CHUNK HEADER: ${fourByteArray.decodeToString()}")

        inputStream.read(fourByteArray)
        dataSize = fourByteArray.toInt()
        Log.d("WAVE", "$dataSize")

        audioBuffer = ByteArray(dataSize).let {
            inputStream.read(it)
            ByteBuffer.wrap(it)
        }

        if (audioBuffer.remaining() != dataSize) throw RuntimeException("DATA SIZE DOES NOT MATCH BUFFER SIZE: ${audioBuffer.remaining()} != $dataSize")

        inputStream.close()
    }
}
