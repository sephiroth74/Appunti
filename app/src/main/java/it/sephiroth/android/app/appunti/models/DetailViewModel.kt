package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
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

    override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
        Timber.i("onTableChanged($table, $action)")
    }

    override fun onCleared() {
        DirectModelNotifier.get().unregisterForModelChanges(Entry::class.java, this)
        super.onCleared()
    }

    private fun setEntry(value: Entry) {
        (entry as MutableLiveData).value = value
    }

    fun togglePin(): Boolean {
        entry.value?.let {
            val copy = Entry(it)
            return DatabaseHelper.setEntryPinned(copy, copy.entryPinned == 0)
        } ?: run { return false }
    }

    fun toggleArchived(): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryArchived(it, it.entryArchived == 0)
        } ?: run { return false }
    }

    fun toggleDeleted(): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryDeleted(it, it.entryDeleted == 0)
        } ?: run { return false }
    }

    fun setEntryCategory(category: Category?): Boolean {
        entry.value?.let {
            return DatabaseHelper.setEntryCategory(Entry(it), category)
        } ?: run { return false }
    }

    init {
        DirectModelNotifier.get().registerForModelChanges(Entry::class.java, this)
    }
}
