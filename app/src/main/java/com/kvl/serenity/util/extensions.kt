package com.kvl.serenity.util

import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toInt() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int
fun ByteArray.toShort() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).short
val AudioTrack.isPlaying: Boolean
    get() = playState == AudioTrack.PLAYSTATE_PLAYING
