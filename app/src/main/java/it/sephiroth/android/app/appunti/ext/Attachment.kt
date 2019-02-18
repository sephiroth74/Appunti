package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.shockwave.pdfium.PdfiumCore
import com.squareup.picasso.Callback
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.PicassoUtils
import timber.log.Timber
import java.io.File


fun Attachment.getFile(context: Context): File {
    return File(FileSystemUtils.getPrivateFilesDir(context), attachmentPath)
}

fun Attachment.getFileUri(context: Context): Uri? {
    return FileProvider.getUriForFile(
        context.applicationContext,
        context.applicationContext.packageName + ".fileprovider",
        getFile(context)
    )
}

fun Attachment.createShareIntent(context: Context): Intent {
    val finalUri = getFileUri(context)
    return Intent(Intent.ACTION_SEND).apply {
        putExtra(android.content.Intent.EXTRA_SUBJECT, attachmentTitle)
        putExtra(android.content.Intent.EXTRA_STREAM, finalUri)
        setDataAndType(finalUri, attachmentMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun Attachment.createViewIntent(context: Context): Intent {
    val finalUri = getFileUri(context)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(finalUri, attachmentMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun Attachment.loadThumbnail(context: Context, view: ImageView) {
    Timber.i("loadThumbnail($attachmentPath, $attachmentMime)")

    if (isImage() || isVideo() || isPdf()) {
        PicassoUtils
            .get(context)
            .load(getFile(context))
            .resizeDimen(
                R.dimen.appunti_detail_attachment_thumbnail_size,
                R.dimen.appunti_detail_attachment_thumbnail_size
            )
            .into(view, object : Callback {
                override fun onError(e: Exception?) {
                    e?.printStackTrace()
                    view.setImageResource(R.drawable.sharp_attach_file_24_rotated)
                }

                override fun onSuccess() {
                    Timber.v("success=${(view.drawable as BitmapDrawable).bounds}")
                }
            })
    } else if (isPdf()) {
        view.setImageResource(R.drawable.sharp_attach_file_24_rotated)
    } else if (isText()) {
        view.setImageResource(R.drawable.sharp_attach_file_24_rotated)
    } else {
        view.setImageResource(R.drawable.sharp_attach_file_24_rotated)
    }
}