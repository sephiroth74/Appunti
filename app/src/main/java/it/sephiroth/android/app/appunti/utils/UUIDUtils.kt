package it.sephiroth.android.app.appunti.utils

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*


object UUIDUtils {
    fun randomLongUUID(): Long {
        var longValue: Long
        do {
            val uid = UUID.randomUUID()
            val buffer = ByteBuffer.wrap(ByteArray(16))
            buffer.putLong(uid.leastSignificantBits)
            buffer.putLong(uid.mostSignificantBits)
            val bi = BigInteger(buffer.array())
            longValue = bi.toLong()
        } while (longValue < 0)
        return longValue
    }
}