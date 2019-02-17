package it.sephiroth.android.app.appunti.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import com.shockwave.pdfium.PdfiumCore
import com.squareup.picasso.*
import com.squareup.picasso.Picasso.LoadedFrom
import it.sephiroth.android.app.appunti.ext.getMimeTypeFromFilePart
import it.sephiroth.android.app.appunti.ext.sha1
import timber.log.Timber
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

    class PDFRequestHandler(private val context: Context) : RequestHandler() {
        private val pdfiumCore: PdfiumCore = PdfiumCore(context)
        private val cache: DiskLruImageCache = DiskLruImageCache.get(context)

        init {
            Timber.i("PDFRequestHandler()")
        }

        override fun canHandleRequest(data: Request): Boolean {
            return data.uri.getMimeTypeFromFilePart()?.startsWith(MIME_TYPE, true) == true
        }

        override fun load(request: Request, networkPolicy: Int): Result? {
            Timber.i("load(${request.uri}, $networkPolicy")

            val cacheKey =
                "${request.uri.toString().sha1().toLowerCase()}-${request.targetWidth}-${request.targetHeight}"

            if (NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
                cache.get(cacheKey)?.let { bmp ->
                    return RequestHandler.Result(bmp, LoadedFrom.DISK)
                }
            }


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

                bmp?.let {
                    if (NetworkPolicy.shouldWriteToDiskCache(networkPolicy)) {
                        cache.put(cacheKey, bmp)
                    }
                }

                return RequestHandler.Result(bmp, LoadedFrom.NETWORK)

            } finally {
                pdfiumCore.closeDocument(pdfDocument)
            }
        }

        companion object {
            private const val PAGE_NUMBER = 0
            private const val MIME_TYPE = "application/pdf"
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
                        .addRequestHandler(PDFRequestHandler(context))
                        .loggingEnabled(true)
                        .indicatorsEnabled(true)
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