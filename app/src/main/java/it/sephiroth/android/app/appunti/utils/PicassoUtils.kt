package it.sephiroth.android.app.appunti.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import com.shockwave.pdfium.PdfiumCore
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import it.sephiroth.android.app.appunti.ext.getMimeTypeFromFilePart
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.cache.DiskLruCache
import okhttp3.internal.io.FileSystem
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


object PicassoUtils {

    class VideoRequestHandler : RequestHandler() {

        companion object {
            const val SCHEME_VIDEO = "video"
        }

        override fun canHandleRequest(data: Request): Boolean {
            Timber.i("canHandleRequest(uri=${data.uri}, scheme=${data.uri.scheme})")
            val scheme = data.uri.scheme
            return SCHEME_VIDEO == scheme || data.uri.getMimeTypeFromFilePart()?.startsWith("video/", true) == true
        }

        @Throws(IOException::class)
        override fun load(data: Request, policy: Int): RequestHandler.Result {
            Timber.i("load(${data.uri}, $policy")
            val bm = ThumbnailUtils.createVideoThumbnail(data.uri.path, MediaStore.Images.Thumbnails.MINI_KIND)
            return RequestHandler.Result(bm, LoadedFrom.NETWORK)
        }
    }

    class PDFRequestHandler(
        private val context: Context,
        private val pdfiumCore: PdfiumCore,
        private val cache: DiskLruCache
    ) : RequestHandler() {

        companion object {
            const val PAGE_NUMBER = 0
        }

        override fun canHandleRequest(data: Request): Boolean {
            return data.uri.getMimeTypeFromFilePart()?.startsWith("application/pdf", true) == true
        }

        override fun load(request: Request, networkPolicy: Int): Result? {
            Timber.i("load(${request.uri}, $networkPolicy")

//            val loadedFrom = if (response.cacheResponse() == null) LoadedFrom.NETWORK else LoadedFrom.DISK
            val loadedFrom = LoadedFrom.NETWORK
//
//            if (loadedFrom == LoadedFrom.DISK && body?.contentLength() == 0L) {
//                body.close()
//                throw IOException("Received response with 0 content-length header.")
//            }

//            val pdfDocument = pdfiumCore.newDocument(response.body()?.bytes())
            val fd = context.contentResolver.openFileDescriptor(request.uri, "r")
            val pdfDocument = pdfiumCore.newDocument(fd)

            try {
                pdfiumCore.openPage(pdfDocument, PAGE_NUMBER)

                var width = pdfiumCore.getPageWidthPoint(pdfDocument, PAGE_NUMBER)
                var height = pdfiumCore.getPageHeightPoint(pdfDocument, PAGE_NUMBER)
                Timber.v("pdf size=$width,$height")

                if (request.targetWidth > 0 && request.targetHeight > 0) {
                    width = request.targetWidth
                    height = request.targetHeight
                }

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                pdfiumCore.renderPageBitmap(pdfDocument, bmp, PAGE_NUMBER, 0, 0, width, height)

                Timber.v("bitmap size: ${bmp.width} x ${bmp.height}")

                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()

                val okRequest = okhttp3.Request.Builder().url(request.uri.toString())
                    .put(RequestBody.create(MediaType.parse("application/pdf"), byteArray)).build()
                val response = Response.Builder().request(okRequest).build()
                cache.put(response)

                return RequestHandler.Result(bmp, loadedFrom)
            } finally {
                pdfiumCore.closeDocument(pdfDocument)
            }
        }

    }


    fun get(context: Context): Picasso {
        val i = picassoInstance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = picassoInstance
            if (i2 != null) {
                i2
            } else {
                val downloader = OkHttp3Downloader(context, Integer.MAX_VALUE.toLong())
                val created =
                    Picasso.Builder(context.applicationContext)
                        .downloader(downloader)
                        .addRequestHandler(VideoRequestHandler())
                        .addRequestHandler(
                            PDFRequestHandler(
                                context,
                                PdfiumCore(context),
                                DiskLruCache.create(
                                    FileSystem.SYSTEM,
                                    File(context.cacheDir, "picasso-cache"),
                                    1,
                                    100,
                                    Int.MAX_VALUE.toLong()
                                )
                            )
                        )
                        .loggingEnabled(true)
                        .indicatorsEnabled(true)
//                        .memoryCache(LruCache(context))
                        .build()

                Picasso.setSingletonInstance(created)
                picassoInstance = created
                Picasso.get()
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var picassoInstance: Picasso? = null
}