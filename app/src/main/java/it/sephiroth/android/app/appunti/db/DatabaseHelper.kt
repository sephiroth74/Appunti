package it.sephiroth.android.app.appunti.db

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.dbflow5.config.FlowManager
import com.dbflow5.query.*
import com.dbflow5.structure.delete
import com.dbflow5.structure.load
import com.dbflow5.structure.save
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.*
import it.sephiroth.android.app.appunti.db.views.EntryWithCategory
import it.sephiroth.android.app.appunti.ext.getFile
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.Optional
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnScheduler
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxCompletable
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxMaybe
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxSingle
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import it.sephiroth.android.library.kotlin_extensions.net.resolveDisplayName
import it.sephiroth.android.library.kotlin_extensions.net.resolveMimeType
import org.apache.commons.io.FileUtils
import org.threeten.bp.Instant
import timber.log.Timber
import java.io.File
import java.util.*


object DatabaseHelper {

    fun getCategories(): Single<MutableList<Category>> {
        return rxSingle(Schedulers.io()) {
            select().from(Category::class)
                .orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
        }
    }

    fun getCategoriesWithNumEntriesAsync(): Single<Triple<MutableList<EntryWithCategory>, Long, Long>> {
        return rxSingle(Schedulers.io()) {
            val archived = getEntriesArchivedCount()
            val reminder = getEntriesWithReminder()
            Timber.v("archived = $archived")
            Triple(select().from(EntryWithCategory::class).list, archived, reminder)
        }
    }

    fun getEntriesArchivedCount(): Long {
        return selectCountOf(Entry_Table.entryID)
            .from(Entry::class)
            .where(Entry_Table.entryArchived.eq(1))
            .longValue(FlowManager.getDatabase(AppDatabase::class.java))
    }

    fun getEntriesWithReminder(): Long {
        return selectCountOf(Entry_Table.entryID)
            .from(Entry::class)
            .where(Entry_Table.entryAlarmEnabled.eq(true))
            .and(Entry_Table.entryAlarm.greaterThan(Instant.now()))
            .longValue(FlowManager.getDatabase(AppDatabase::class.java))
    }

    fun getCategoryByID(id: Long): Category? {
        return select().from(Category::class).where(Category_Table.categoryID.eq(id)).result
    }

    fun setEntryPinned(entry: Entry, value: Boolean): Boolean {
        Timber.i("setEntryPinned($entry, $value)")
        entry.entryPinned = if (value) 1 else 0
        return entry.touch().save()
    }

    fun setEntryArchived(entry: Entry, value: Boolean): Boolean {
        Timber.i("setEntryArchived($entry, $value)")
        entry.entryArchived = if (value) 1 else 0
        if (value) entry.entryDeleted = 0
        return entry.touch().save()
    }

    fun deleteEntry(context: Context, entry: Entry): Boolean {
        Timber.i("deleteEntry($entry)")
        entry.entryArchived = 0
        entry.entryAlarmEnabled = false
        entry.entryAlarm = null
        Entry.removeReminder(entry, context)
        return entry.delete()
    }

    fun setEntryCategory(entry: Entry, category: Category?): Boolean {
        Timber.i("setEntryCategory($entry, $category)")
        entry.category = category
        return entry.touch().save()
    }

    fun removeReminder(entry: Entry, context: Context): Boolean {
        Timber.i("removeReminder($entry)")
        entry.entryAlarm = null
        entry.entryAlarmEnabled = false
        entry.touch()
        val result = entry.save()

        if (result) {
            Entry.removeReminder(entry, context)
        }

        return result
    }

    fun addReminder(entry: Entry, date: Instant, context: Context): Boolean {
        Timber.i("addReminder($entry)")
        // first remove the current alarm
        Entry.removeReminder(entry, context)

        entry.entryAlarm = date
        entry.entryAlarmEnabled = true
        entry.touch()
        val result = entry.save()

        if (result) {
            Entry.addReminder(entry, context)
        }

        return result
    }

    fun setEntriesPinned(values: List<Entry>, pin: Boolean): Completable {
        val pinnedValue = if (pin) 1 else 0

        return rxCompletable(Schedulers.io()) {
            update(Entry::class)
                .set(
                    Entry_Table.entryPinned.eq(pinnedValue),
                    Entry_Table.entryModifiedDate.eq(Instant.now())
                )
                .where(Entry_Table.entryID.`in`(values.map { it.entryID }))
                .execute(FlowManager.getDatabase(AppDatabase::class.java))
        }
    }

    fun deleteEntries(context: Context, values: List<Entry>): Completable {
        return rxCompletable(Schedulers.io()) {

            values.forEach {
                Entry.removeReminder(it, context)
            }

            com.dbflow5.query.delete()
                .from(Entry::class)
                .where(Entry_Table.entryID.`in`(values.map { it.entryID }))
                .execute(FlowManager.getDatabase(AppDatabase::class.java))
        }
    }

    fun setEntriesArchived(values: List<Entry>, archive: Boolean): Completable {
        Timber.i("setEntriesArchived($archive, $values")
        return rxCompletable(Schedulers.io()) {
            update(Entry::class)
                .set(
                    Entry_Table.entryDeleted.eq(0),
                    Entry_Table.entryArchived.eq(if (archive) 1 else 0),
                    Entry_Table.entryModifiedDate.eq(Instant.now())
                )
                .where(Entry_Table.entryID.`in`(values.map { it.entryID }))
                .execute(FlowManager.getDatabase(AppDatabase::class.java))

        }
    }

    /**
     * Deleted the attachment from the Database and remove the stored file from the filesystem
     */
    fun deleteAttachment(
        context: Context,
        entry: Entry,
        attachment: Attachment,
        callback: ((Boolean, Throwable?) -> (Unit))? = null
    ) {
        Timber.i("deleteAttachment($attachment")

        if (attachment.attachmentEntryID != entry.entryID) {
            callback?.invoke(
                false,
                IllegalArgumentException("This attachment doesn't belong to the provided entry!")
            )
            return
        }

        FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
            Timber.i("[${currentThread()}] onRemoveAttachment($attachment)")
            val file = attachment.getFile(context)
            Timber.v("deleting ${file.absolutePath}")

            val result: Boolean = attachment.delete()
            if (result) {
                FileUtils.deleteQuietly(file)
            }

            result
        }.success { _, result ->
            callback?.invoke(result, null)
        }.error { _, throwable ->
            callback?.invoke(false, throwable)
        }
            .build()
            .execute()
    }

    fun hideRemoteUrl(
        context: Context,
        entry: Entry,
        remoteUrl: RemoteUrl,
        callback: ((Boolean, Throwable?) -> (Unit))? = null
    ) {
        Timber.i("hideRemoteUrl($remoteUrl")

        if (remoteUrl.remoteUrlEntryID != entry.entryID) {
            callback?.invoke(
                false,
                IllegalArgumentException("This attachment doesn't belong to the provided entry!")
            )
            return
        }

        FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
            remoteUrl.remoteUrlVisible = false
            remoteUrl.save()
        }.success { _, result ->
            callback?.invoke(result, null)
        }.error { _, throwable ->
            callback?.invoke(false, throwable)
        }
            .build()
            .execute()
    }

    fun addAttachmentFromUri(
        context: Context,
        entry: Entry,
        uri: Uri,
        dstName: String?,
        callback: ((Boolean, Throwable?) -> (Unit))? = null
    ) {
        Timber.i("addAttachmentFromUri($entry, $uri, $dstName)")

        var displayName: String = uri.resolveDisplayName(context) ?: UUID.randomUUID().toString()
        val mimeType = uri.resolveMimeType(context)

        Timber.v("displayName: $displayName, mimeType: $mimeType")

        val filesDir = FileSystemUtils.getPrivateFilesDir(context)
        val attachmentsDir = FileSystemUtils.getAttachmentFilesDir(context, entry)
        val dstFile = FileSystemUtils.getNextFile(attachmentsDir, FileSystemUtils.normalizeFileName(displayName))

        if (!dstName.isNullOrBlank()) {
            val extension = FileSystemUtils.getFileExtension(context, uri)
            if (!extension.isNullOrBlank()) {
                displayName = "$dstName.$extension"
            }
        }

        val relativePath = dstFile.path

        Timber.v("filesDir=${filesDir.absolutePath}")
        Timber.v("attachmentsDir=${attachmentsDir.absolutePath}")
        Timber.v("dstFile=$dstFile")
        Timber.v("relative=$relativePath")

        doOnScheduler(Schedulers.io()) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                FileUtils.copyInputStreamToFile(stream, dstFile.file)

                FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
                    val attachment = Attachment()
                    attachment.attachmentEntryID = entry.entryID
                    attachment.attachmentPath = relativePath
                    attachment.attachmentTitle = displayName
                    attachment.attachmentMime = mimeType
                    attachment.attachmentOriginalPath = uri.toString()
                    attachment.save()
                    entry.load()?.touch()?.save()
                }
                    .success { _, result -> callback?.invoke(true, null) }
                    .error { _, throwable -> callback?.invoke(false, throwable) }
                    .build()
                    .execute()
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(false, e)
            }
        }
    }

    fun addAttachment(
        context: Context,
        entry: Entry,
        dstFile: RelativePath,
        dstFileMimeType: String?,
        callback: ((Boolean, Throwable?) -> (Unit))? = null
    ) {
        Timber.i("addAttachment($entry, ${dstFile.absolutePath})")

        val displayName: String = dstFile.name
        val mimeType =
            dstFileMimeType
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(dstFile.extension.toLowerCase())

        Timber.v("displayName: $displayName, mimeType: $mimeType")

        val dstUri = FileSystemUtils.getFileUri(context, dstFile)
        val filesDir = FileSystemUtils.getPrivateFilesDir(context)
        val attachmentsDir = FileSystemUtils.getAttachmentFilesDir(context, entry)
        val relativePath = filesDir.toURI().relativize(dstFile.toURI())

        Timber.v("relative path is absolute: ${relativePath.isAbsolute}")

        if (relativePath.isAbsolute) throw java.lang.IllegalArgumentException("Not an internal file")

        Timber.v("filesDir=${filesDir.absolutePath}")
        Timber.v("attachmentsDir=${attachmentsDir.absolutePath}")
        Timber.v("dstFile=$dstFile")
        Timber.v("relative=$relativePath")

        doOnScheduler(Schedulers.io()) {
            try {
                FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
                    val attachment = Attachment()
                    attachment.attachmentEntryID = entry.entryID
                    attachment.attachmentPath = relativePath.toString()
                    attachment.attachmentTitle = displayName
                    attachment.attachmentMime = mimeType
                    attachment.attachmentOriginalPath = dstUri.toString()
                    attachment.save()
                    entry.load()?.touch()?.save()
                }
                    .success { _, _ -> callback?.invoke(true, null) }
                    .error { _, throwable -> callback?.invoke(false, throwable) }
                    .build()
                    .execute()
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(false, e)
            }
        }
    }

    fun getEntriesByCategory(category: Category?): Single<MutableList<Entry>> {
        return rxSingle(Schedulers.io()) {
            select().from(Entry::class)
                .run {
                    category?.let {
                        where(Entry_Table.category_categoryID.eq(it.categoryID))
                            .and(Entry_Table.entryArchived.eq(0))
                            .and(Entry_Table.entryDeleted.eq(0)) as Transformable<Entry>
                    } ?: run {
                        where(Entry_Table.entryArchived.eq(0)).and(Entry_Table.entryDeleted.eq(0)) as Transformable<Entry>
                    }

                }.run {
                    orderByAll(
                        listOf(
                            OrderBy(Entry_Table.entryPinned.nameAlias, false),
                            OrderBy(Entry_Table.entryPriority.nameAlias, false),
                            OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)
                        )
                    ).list
                }
        }
    }

    @Suppress("RemoveExplicitTypeArguments")
    fun getEntryById(id: Long): Single<Optional<Entry>> {
        return rxSingle(Schedulers.io()) {
            val entry = select().from(Entry::class).where(Entry_Table.entryID.eq(id)).result
            entry?.let { Optional.of(it) } ?: run { Optional.empty<Entry>() }
        }
    }

    fun getEntries(func: From<Entry>.() -> Transformable<Entry>): Single<MutableList<Entry>> {
        return rxSingle(Schedulers.io()) {
            select().from(Entry::class)
                .run {
                    this.func()
                }.run {
                    orderByAll(
                        listOf(
                            OrderBy(Entry_Table.entryArchived.nameAlias, false),
                            OrderBy(Entry_Table.entryDeleted.nameAlias, false),
                            OrderBy(Entry_Table.entryPinned.nameAlias, false),
//                            OrderBy(Entry_Table.entryPriority.nameAlias, false),
                            OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)
                        )
                    ).list
                }
        }
    }

    fun copyDatabase(context: Context, dstDir: File) {
        Timber.i("copyDatabase: %s", dstDir.absolutePath)
        val db = FlowManager.getDatabase(AppDatabase::class.java)
        val src = db.databaseFileName
        Timber.v("db path: %s", context.getDatabasePath(db.databaseFileName))
        val srcFile = context.getDatabasePath(db.databaseFileName)
        val dstFile = File(dstDir, db.databaseFileName)
        Timber.v("dstFile: %s", dstFile.absolutePath)
        return FileUtils.copyFile(srcFile, dstFile)
    }

    fun loadAttachment(id: Long): Maybe<Attachment> {
        return rxMaybe(Schedulers.io()) {
            (select from Attachment::class where (Attachment_Table.attachmentID.eq(id))).result
        }

    }
}