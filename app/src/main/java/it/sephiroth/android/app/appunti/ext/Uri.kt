package it.sephiroth.android.app.appunti.ext

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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

fun Uri.getDisplayName(context: Context): String? {
    val uriString = toString()
    if (uriString.startsWith("content://", true)) {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(this, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        } finally {
            cursor?.close()
        }
    } else if (uriString.startsWith("file://", true)) {
        return File(uriString).name
    }
    return lastPathSegment
}