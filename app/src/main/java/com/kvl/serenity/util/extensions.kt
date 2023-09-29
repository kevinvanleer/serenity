package com.kvl.serenity.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toInt() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int
fun ByteArray.toShort() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).short
