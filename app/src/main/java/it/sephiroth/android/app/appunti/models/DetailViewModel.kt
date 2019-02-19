package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import com.dbflow5.structure.insert
import com.dbflow5.structure.save
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.doOnMainThread
import it.sephiroth.android.app.appunti.ext.isMainThread
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    val entry: LiveData<Entry> = MutableLiveData()

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
//                if (isMainThread()) {
//                    setEntry(model)
//                } else {
//                    doOnMainThread { setEntry(model) }
//                }
            }
        }

        override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
            Timber.i("onTableChanged($table, $action)")
        }
    }

    val attachmentModelListener = object : DirectModelNotifier.ModelChangedListener<Attachment> {
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

    fun togglePin(): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryPinned(Entry(it), it.entryPinned == 0)
        } ?: run { return false }
    }

    fun toggleArchived(): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryArchived(Entry(it), it.entryArchived == 0)
        } ?: run { return false }
    }

    fun toggleDeleted(): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryDeleted(Entry(it), it.entryDeleted == 0)
        } ?: run { return false }
    }

    fun removeReminder(): Boolean {
        entry.value?.let {
            return DatabaseHelper.removeReminder(Entry(it), getApplication())
        } ?: run { return false }
    }

    fun addReminder(zone: ZonedDateTime): Boolean {
        entry.value?.let { entry ->
            val utc = zone.withZoneSameInstant(ZoneId.of("UTC"))
            return DatabaseHelper.addReminder(Entry(entry), utc.toInstant(), getApplication())
        } ?: run { return false }
    }

    fun setEntryCategory(category: Category?): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryCategory(Entry(it), category)
        } ?: run { return false }
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
