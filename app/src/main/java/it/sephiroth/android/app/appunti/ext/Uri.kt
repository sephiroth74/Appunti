package it.sephiroth.android.app.appunti.ext

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import timber.log.Timber
import java.io.File


fun Uri.getMimeType(context: Context): String? {
    val mimeType: String?
    mimeType = if (scheme == ContentResolver.SCHEME_CONTENT) {
        val cr = context.contentResolver
        cr.getType(this)
    } else {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())
    }
    return mimeType
}

/**
 * Tries to return the mime type from the file path
 */
fun Uri.getMimeTypeFromFilePart(): String? {
    val fileExtension = MimeTypeMap.getFileExtensionFromUrl(toString())
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())
}

fun Uri.getDisplayName(context: Context): String? {
    val uriString = toString()
    if (uriString.startsWith("content://", true)) {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(this, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                Timber.v("columnIndex = $columnIndex")
                if (columnIndex > -1) {
                    return cursor.getString(columnIndex)
                }
            }
        } finally {
            cursor?.close()
        }
    } else if (uriString.startsWith("file://", true)) {
        return File(uriString).name
    }
    return lastPathSegment
}