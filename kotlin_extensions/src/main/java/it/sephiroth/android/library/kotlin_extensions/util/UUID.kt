package it.sephiroth.android.library.kotlin_extensions.util

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*

fun UUID.toLong(): Long {
    var longValue: Long
    do {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(leastSignificantBits)
        buffer.putLong(mostSignificantBits)
        val bi = BigInteger(buffer.array())
        longValue = bi.toLong()
    } while (longValue < 0)
    return longValue
}