package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.squareup.picasso.Callback
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.PicassoUtils
import timber.log.Timber
import java.io.File


fun Attachment.getFile(context: Context): File {
    return File(FileSystemUtils.getPrivateFilesDir(context), attachmentPath)
}

fun Attachment.getPath(context: Context): RelativePath {
    return RelativePath(FileSystemUtils.getPrivateFilesDir(context), attachmentPath)
}

fun Attachment.getFileUri(context: Context): Uri? {
    val file = getFile(context)
    Timber.v("file: $file")
    return FileProvider.getUriForFile(
        context.applicationContext,
        context.applicationContext.packageName + ".fileprovider",
        file
    )
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