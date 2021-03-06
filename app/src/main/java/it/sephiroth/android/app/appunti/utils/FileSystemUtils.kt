package it.sephiroth.android.app.appunti.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.library.kotlin_extensions.net.resolveDisplayName
import it.sephiroth.android.library.kotlin_extensions.net.resolveMimeType
import org.apache.commons.io.FilenameUtils
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileSystemUtils {

    const val PRIVATE_DIR = "private"
    const val ENTRIES_DIR = "entry"
    const val ATTACHMENTS_DIR = "attachments"

    const val JPEG_MIME_TYPE = "image/jpeg"
    const val TEXT_MIME_TYPE = "text/plain"

    /**
     * Internal base directory where database related files
     * will be stored
     */
    fun getPrivateFilesDir(context: Context): File {
        return File(context.filesDir, PRIVATE_DIR)
    }

    /**
     * Internal base directory where files related to all the [Entry][it.sephiroth.android.app.appunti.db.tables.Entry]
     * will be stored
     */
    private fun getEntriesFilesDir(context: Context): RelativePath {
        return RelativePath(FileSystemUtils.getPrivateFilesDir(context), ENTRIES_DIR)
//        return File(FileSystemUtils.getPrivateFilesDir(context), ENTRIES_DIR)
    }


    /**
     * Base directory where files related to a specific [Entry][it.sephiroth.android.app.appunti.db.tables.Entry]
     * will be stored
     *
     * @see Entry
     */
    private fun getEntryFilesDir(context: Context, entry: Entry): RelativePath {
        if (entry.isNew()) throw IllegalArgumentException("Entry must be saved first!")
        return RelativePath(getEntriesFilesDir(context), "${entry.entryID}")
//        return File(getEntriesFilesDir(context), "${entry.entryID}")
    }

    /**
     * Base directory where [it.sephiroth.android.app.appunti.db.tables.Attachment] for a specific [Entry] will be stored
     * @see Entry
     */
    fun getAttachmentFilesDir(context: Context, entry: Entry): RelativePath {
        return RelativePath(getEntryFilesDir(context, entry), ATTACHMENTS_DIR)
//        return File(getEntryFilesDir(context, entry), ATTACHMENTS_DIR)
    }

    /**
     * Given an internal stored file this method will return the corresponding
     * [Uri] to be used to expose the file to other applications
     */
    fun getFileUri(context: Context, file: RelativePath): Uri? {
        return FileProvider.getUriForFile(
            context.applicationContext,
            context.applicationContext.packageName + ".fileprovider",
            file.file
        )
    }

    @SuppressLint("SimpleDateFormat")
    fun createImageFile(context: Context, entry: Entry): RelativePath {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storagePath = getAttachmentFilesDir(context, entry)
        Timber.v("storagePath = $storagePath")

        if (!storagePath.exists()) storagePath.file.mkdirs()

        val tempFile = File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storagePath.file /* directory */
        )

        return RelativePath(storagePath, tempFile.name)
    }

    /**
     * Removes illegal characters from a file name
     */
    fun normalizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\.\\-]"), "_")
    }

    /**
     * Given a base directory and a filename it will return the next
     * available file for the directory
     */
    fun getNextFile(baseDir: RelativePath, fileName: String): RelativePath {
        Timber.i("getNextFile($fileName)")

        val index = fileName.lastIndexOf('.')

        Timber.v("index=$index, fileName.length=${fileName.length}")

        val name: String
        val extension: String

        if (index > -1) {
            name = fileName.substring(0, index)
            extension = fileName.substring(index)
        } else {
            name = fileName
            extension = ""
        }
        Timber.v("name: $name, extension: $extension")

        var curFile = RelativePath(baseDir, fileName)

        if (curFile.exists()) {
            var i = 0
            do {
                i++
                curFile = RelativePath(baseDir, "$name-$i$extension")
            } while (curFile.exists())
        }

        return curFile
    }

    fun getFileExtension(context: Context, uri: Uri): String? {
        var extension: String? = null
        uri.resolveDisplayName(context)?.let { displayName ->
            val mimeType = uri.resolveMimeType(context)
            extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension.isNullOrBlank()) {
                extension = FilenameUtils.getExtension(displayName)
            }
            if (extension.isNullOrBlank()) {
                val index = displayName.lastIndexOf(".")
                if (index > -1) {
                    extension = displayName.substring(index)
                }
            }
        }
        return extension
    }
}