package it.sephiroth.android.app.appunti.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StatFs
import okhttp3.internal.cache.DiskLruCache
import okhttp3.internal.io.FileSystem
import okio.Buffer
import timber.log.Timber
import java.io.File

@Suppress("NAME_SHADOWING")
class DiskLruImageCache(private val cache: DiskLruCache) {

    init {
        Timber.i("DiskLruImageCache(path=${cache.directory.absolutePath}, maxSize=${cache.maxSize})")
    }

    fun get(key: String): Bitmap? {
        Timber.i("get($key)")
        cache.get(key)?.use { cacheResult ->
            cacheResult.getSource(CACHE_IMAGE_KEY_INDEX)?.use { source ->
                Buffer().use { buffer ->
                    do {
                        val read = source.read(buffer, DEFAULT_BUFFER_SIZE)
                    } while (read > -1)
                    return BitmapFactory.decodeStream(buffer.inputStream())
                }
            }
        }
        return null
    }

    fun put(key: String, bmp: Bitmap?): Boolean {
        if (bmp == null) return false
        Timber.i("put($key)")

        val editor = cache.edit(key)

        editor?.let { editor ->
            editor.newSink(CACHE_IMAGE_KEY_INDEX).apply {
                Buffer().use { buffer ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, buffer.outputStream())
                    this.write(buffer, buffer.size())
                    this.flush()
                }
            }

            Timber.v("committing editor")
            editor.commit()
            return true
        } ?: kotlin.run {
            return false
        }
    }


    companion object {
        private const val IMAGE_CACHE = "image-cache"
        private const val CACHE_APP_VERSION = 1
        private const val DEFAULT_BUFFER_SIZE = (1024 * 4).toLong()
        private const val CACHE_IMAGE_KEY_INDEX = 0
        private const val MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB

        private var INSTANCE: DiskLruImageCache? = null

        fun get(context: Context): DiskLruImageCache {
            val i = INSTANCE
            if (i != null) {
                return i
            }

            return synchronized(this) {
                val i2 = INSTANCE
                if (i2 != null) {
                    i2
                } else {
                    val created = DiskLruImageCache(
                        DiskLruCache.create(
                            FileSystem.SYSTEM,
                            File(context.cacheDir, IMAGE_CACHE),
                            CACHE_APP_VERSION,
                            1,
                            calculateDiskCacheSize(context.cacheDir)
                        )
                    )

                    INSTANCE = created
                    created
                }
            }
        }


        fun calculateDiskCacheSize(dir: File): Long {
            var size = MIN_DISK_CACHE_SIZE.toLong()

            try {
                val statFs = StatFs(dir.absolutePath)
                val blockCount = statFs.blockCountLong
                val blockSize = statFs.blockSizeLong
                val available = blockCount * blockSize
                // Target 2% of the total space.
                size = available / 50
            } catch (ignored: IllegalArgumentException) {
            }

            // Bound inside min/max size for disk cache.
            return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE.toLong()), MIN_DISK_CACHE_SIZE.toLong())
        }
    }
}