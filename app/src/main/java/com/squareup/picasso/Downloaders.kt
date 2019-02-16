package com.squareup.picasso

import android.content.Context
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException


class PDFDownloader : Downloader {
    internal val client: Call.Factory
    private val cache: Cache?
    private var sharedClient = true

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     */
    constructor(context: Context) : this(Utils.createDefaultCacheDir(context)) {}

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into your application
     * cache directory.
     *
     * @param maxSize The size limit for the cache.
     */
    constructor(context: Context, maxSize: Long) : this(Utils.createDefaultCacheDir(context), maxSize) {}

    /**
     * Create new downloader that uses OkHttp. This will install an image cache into the specified
     * directory.
     *
     * @param cacheDir The directory in which the cache should be stored
     * @param maxSize The size limit for the cache.
     */
    @JvmOverloads
    constructor(cacheDir: File, maxSize: Long = Utils.calculateDiskCacheSize(cacheDir)) : this(
        OkHttpClient.Builder().cache(
            Cache(cacheDir, maxSize)
        ).build()
    ) {
        sharedClient = false
    }

    /**
     * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
     * automatically configured.
     */
    constructor(client: OkHttpClient) {
        this.client = client
        this.cache = client.cache()
    }

    /** Create a new downloader that uses the specified [Call.Factory] instance.  */
    constructor(client: Call.Factory) {
        this.client = client
        this.cache = null
    }

    @Throws(IOException::class)
    override fun load(request: okhttp3.Request): Response {
        return client.newCall(request).execute()
    }

    override fun shutdown() {
        if (!sharedClient && cache != null) {
            try {
                cache.close()
            } catch (ignored: IOException) {
            }

        }
    }
}