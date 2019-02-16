package okhttp3

import okhttp3.Cache
import java.io.Closeable
import java.io.File
import java.io.Flushable

class PublicCache(directory: File, maxSize: Long) : Closeable, Flushable {
    private val cache: Cache = Cache(directory, maxSize)

    fun get(request: Request) = cache.get(request)
    fun put(response: Response) = cache.put(response)

    override fun close() {
        cache.close()
    }

    override fun flush() {
        cache.flush()
    }


}