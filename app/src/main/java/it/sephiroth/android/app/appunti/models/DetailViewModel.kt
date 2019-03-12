package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import com.dbflow5.structure.save
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.ext.convertToList
import it.sephiroth.android.app.appunti.ext.doOnMainThread
import it.sephiroth.android.app.appunti.ext.whenNotNull
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import it.sephiroth.android.library.kotlin_extensions.lang.isMainThread
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    val entry: LiveData<Entry> = MutableLiveData()

    var modified = false

    var isNew = false

    var entryID: Long?
        @SuppressLint("CheckResult")
        set(value) {

            value?.let { value ->
                DatabaseHelper
                    .getEntryById(value)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result, error ->
                        error?.printStackTrace()
                        isNew = false
                        setEntry(result)
                    }
            }
        }
        get() {
            return entry.value?.entryID
        }

    fun createNewEntry(entry: Entry, isNewEntry: Boolean): Long? {
        if (entry.save()) {
            isNew = isNewEntry
            setEntry(entry)
            return entry.entryID
        }
        return null
    }

    private val entryModelListener = object : DirectModelNotifier.ModelChangedListener<Entry> {
        override fun onModelChanged(model: Entry, action: ChangeAction) {
            Timber.i("[${currentThread()}] onModelChanged($model, $action)")
            Timber.v("model.entryID=${model.entryID} == $entryID")

            if (model.entryID == entryID) {
                if (action == ChangeAction.CHANGE || action == ChangeAction.UPDATE) {
                    Timber.v("this entry = ${entry.value}")
                    Timber.v("new entry = $model")

                    if (isMainThread()) {
                        setEntry(model)
                    } else {
                        doOnMainThread { setEntry(model) }
                    }
                }
            } else if (action == ChangeAction.INSERT && null == entryID) {
                // void
            }
        }

        override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
            Timber.i("onTableChanged($table, $action)")
        }
    }

    private fun setEntry(value: Entry?) {
        (entry as MutableLiveData).value = value
    }

    /**
     * Update the entry title (without saving)
     */
    fun setEntryTitle(title: CharSequence?) {
        entry.whenNotNull {
            it.entryTitle = title?.toString() ?: ""
            modified = true
        }
    }

    /**
     * Update the entry text (without saving)
     */
    fun setEntryText(text: CharSequence?) {
        entry.whenNotNull {
            it.entryText = text?.toString() ?: ""
            modified = true
        }
    }

    /**
     * Save the current entry
     */
    fun save(): Boolean {
        Timber.i("save")
        entry.whenNotNull { entry ->
            val result = entry.touch().save()
            modified = false
            return result
        } ?: run { return false }
    }

    /**
     * Change the Entry category
     */
    fun setEntryCategory(categoryID: Long): Boolean {
        entry.whenNotNull { entry ->
            if (categoryID > -1) {
                DatabaseHelper.getCategoryByID(categoryID)?.let { category ->
                    return DatabaseHelper.setEntryCategory(entry, category)
                }
            }
        }
        return false
    }

    /**
     * Convert the [Entry] into a [Entry.EntryType.LIST] entry
     */
    fun convertEntryToList(): Boolean {
        entry.whenNotNull { entry ->
            if (entry.convertToList()) {
                return save()
            }
        }
        return false
    }

    /**
     * Convert the [Entry] into a [Entry.EntryType.TEXT] entry
     */
    fun convertEntryToText(text: String?): Boolean {
        entry.whenNotNull { entry ->
            if (entry.entryType == Entry.EntryType.LIST) {
                entry.entryText = text ?: ""
                entry.entryType = Entry.EntryType.TEXT
                return save()
            }
        }
        return false
    }

    /**
     * Toggle the [Entry] pin status
     */
    fun setEntryPinned(value: Boolean): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.setEntryPinned(entry, value)
        } ?: run { return false }
    }

    /**
     * Toggle the [Entry archived status
     */
    fun setEntryArchived(value: Boolean): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.setEntryArchived(entry, value)
        } ?: run { return false }
    }

    /**
     * Toggle the [Entry] deleted status
     */
    fun setEntryDeleted(value: Boolean): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.setEntryDeleted(getApplication(), entry, value)
        } ?: run { return false }
    }

    /**
     * Remove the pending reminder from the [Entry]
     */
    fun removeReminder(): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.removeReminder(entry, getApplication())
        } ?: run { return false }
    }

    /**
     * Add a new reminder to the current [Entry]
     */
    fun addReminder(zone: ZonedDateTime): Boolean {
        entry.whenNotNull { entry ->
            val utc = zone.withZoneSameInstant(ZoneId.of("UTC"))
            return DatabaseHelper.addReminder(entry, utc.toInstant(), getApplication())
        } ?: run { return false }
    }

    /**
     * Add a new [Attachment] to the current [Entry]
     */
    fun addAttachment(uri: Uri, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        Timber.i("addAttachment($uri)")
        entry.whenNotNull { entry ->
            DatabaseHelper.addAttachmentFromUri(getApplication(), entry, uri) { success, throwable ->
                entry.invalidateAttachments()
                callback?.invoke(success, throwable)
            }
        } ?: run {
            callback?.invoke(false, null)
        }
    }

    /**
     * Add a new Image [Attachment] to the current [Entry]
     */
    fun addAttachment(dstFile: RelativePath, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        Timber.i("addAttachment($dstFile)")

        entry.whenNotNull { entry ->
            DatabaseHelper.addAttachment(
                getApplication(),
                entry,
                dstFile,
                FileSystemUtils.JPEG_MIME_TYPE
            ) { success, throwable ->
                entry.invalidateAttachments()
                callback?.invoke(success, throwable)
            }
        } ?: run {
            callback?.invoke(false, null)
        }
    }

    /**
     * Remove an [Attachment] from the current [Entry]
     */
    fun removeAttachment(attachment: Attachment, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        entry.whenNotNull { entry ->
            DatabaseHelper.deleteAttachment(
                getApplication(),
                entry,
                Attachment(attachment)
            ) { result, throwable ->
                entry.invalidateAttachments()
                callback?.invoke(result, throwable)
            }

        } ?: run {
            callback?.invoke(false, null)
        }
    }

    fun hideRemoteUrl(remoteUrl: RemoteUrl, callback: ((Boolean, Throwable?) -> Unit)? = null) {
        entry.whenNotNull { entry ->
            DatabaseHelper.hideRemoteUrl(getApplication(), entry, remoteUrl) { result, throwable ->
                entry.invalidateRemoteUrls()
                callback?.invoke(result, throwable)
            }
        }
    }

    override fun onCleared() {
        Timber.i("onCleared")
        super.onCleared()
    }
}
