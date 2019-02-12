package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.AlarmReceiver
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

class DetailViewModel(application: Application) : AndroidViewModel(application), DirectModelNotifier.ModelChangedListener<Entry> {

    val entry: LiveData<Entry> = MutableLiveData()

    var entryID: Int
        @SuppressLint("CheckResult")
        set(value) {
            DatabaseHelper
                .getEntryById(value)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { result, error ->
                    result?.let {
                        setEntry(it)
                    } ?: run {
                        setEntry(Entry())
                    }
                }
        }
        get() {
            return entry.value?.entryID ?: 0
        }

    override fun onModelChanged(model: Entry, action: ChangeAction) {
        Timber.i("onModelChanged($model, $action)")

        if (model.entryID == entryID) {
            if (action == ChangeAction.CHANGE) {
                Timber.v("this entry = ${entry.value}")
                Timber.v("new entry = $model")

                setEntry(model)
            }
        }
    }

    override fun onTableChanged(table: Class<*>?, action: ChangeAction) {}

    private fun setEntry(value: Entry) {
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
        DirectModelNotifier.get().unregisterForModelChanges(Entry::class.java, this)
        super.onCleared()
    }

    init {
        DirectModelNotifier.get().registerForModelChanges(Entry::class.java, this)
    }
}
