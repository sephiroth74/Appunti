package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import com.dbflow5.structure.insert
import com.dbflow5.structure.save
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.doOnMainThread
import it.sephiroth.android.app.appunti.ext.isMainThread
import it.sephiroth.android.app.appunti.ext.whenNotNull
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.io.File
import java.util.ArrayList

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    val entry: LiveData<Entry> = MutableLiveData()

    var modified = false

    var entryID: Long?
        @SuppressLint("CheckResult")
        set(value) {

            value?.let { value ->
                DatabaseHelper
                    .getEntryById(value)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { result, error ->
                        error?.printStackTrace()
                        setEntry(result)
                    }
            }
        }
        get() {
            return entry.value?.entryID
        }

    fun createNewEntry() {
        Entry().apply {
            save().also {
                Timber.i("Entry saved. $this")
                setEntry(this)
            }
        }
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

    private val attachmentModelListener = object : DirectModelNotifier.ModelChangedListener<Attachment> {
        override fun onModelChanged(model: Attachment, action: ChangeAction) {
            Timber.i("onModelChanged($model, $action)")
        }

        override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
            Timber.i("onTableChanged($table, $action)")
        }
    }

    private fun setEntry(value: Entry?) {
        (entry as MutableLiveData).value = value
    }

    fun setEntryTitle(title: CharSequence?) {
        entry.whenNotNull {
            it.entryTitle = title?.toString() ?: ""
            modified = true
        }
    }

    fun setEntryText(text: CharSequence?) {
        entry.whenNotNull {
            it.entryText = text?.toString() ?: ""
            modified = true
        }
    }

    fun save(): Boolean {
        entry.whenNotNull { entry ->
            val result = entry.touch().save()
            modified = false
            return result
        } ?: run { return false }
    }

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

    fun setEntryPinned(value: Boolean): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.setEntryPinned(entry, value)
        } ?: run { return false }
    }

    fun setEntryArchived(value: Boolean): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.setEntryArchived(entry, value)
        } ?: run { return false }
    }

    fun removeReminder(): Boolean {
        entry.whenNotNull { entry ->
            return DatabaseHelper.removeReminder(entry, getApplication())
        } ?: run { return false }
    }

    fun addReminder(zone: ZonedDateTime): Boolean {
        entry.value?.let { entry ->
            val utc = zone.withZoneSameInstant(ZoneId.of("UTC"))
            return DatabaseHelper.addReminder(Entry(entry), utc.toInstant(), getApplication())
        } ?: run { return false }
    }

    fun addAttachment(uri: Uri, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        Timber.i("addAttachment($uri)")
        entry.whenNotNull { entry ->
            DatabaseHelper.addAttachmentFromUri(getApplication(), entry, uri) { success, throwable ->
                callback?.invoke(success, throwable)
            }
        } ?: run {
            callback?.invoke(false, null)
        }
    }

    fun addImage(dstFile: File, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        Timber.i("addImage($dstFile)")

        entry.whenNotNull { entry ->
            DatabaseHelper.addAttachment(
                getApplication(),
                entry,
                dstFile,
                FileSystemUtils.JPEG_MIME_TYPE
            ) { success, throwable ->
                callback?.invoke(success, throwable)
            }
        } ?: run {
            callback?.invoke(false, null)
        }
    }

    fun removeAttachment(attachment: Attachment, callback: ((Boolean, Throwable?) -> (Unit))? = null) {
        entry.value?.let { entry ->
            DatabaseHelper.deleteAttachment(
                getApplication(),
                Entry(entry),
                Attachment(attachment)
            ) { result, throwable ->
                callback?.invoke(result, throwable)
            }
        } ?: run {
            callback?.invoke(false, null)
        }
    }

    override fun onCleared() {
        DirectModelNotifier.get().unregisterForModelChanges(Entry::class.java, entryModelListener)
        DirectModelNotifier.get().unregisterForModelChanges(Attachment::class.java, attachmentModelListener)
        super.onCleared()
    }

    init {
        DirectModelNotifier.get().registerForModelChanges(Entry::class.java, entryModelListener)
        DirectModelNotifier.get().registerForModelChanges(Attachment::class.java, attachmentModelListener)
    }
}
