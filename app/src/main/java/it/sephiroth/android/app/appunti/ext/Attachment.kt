package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import java.io.File

fun Attachment.getFile(context: Context): File {
    return File(DatabaseHelper.getFilesDir(context), attachmentPath)
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
        setDataAndType(finalUri, attachmentMime)
        putExtra(android.content.Intent.EXTRA_SUBJECT, attachmentTitle)
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