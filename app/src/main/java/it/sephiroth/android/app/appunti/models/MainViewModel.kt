package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.query.From
import com.dbflow5.query.Transformable
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.runtime.OnTableChangedListener
import com.dbflow5.structure.ChangeAction
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.ext.currentThread
import timber.log.Timber

class MainViewModel(application: Application) : AndroidViewModel(application),
    DirectModelNotifier.OnModelStateChangedListener<BaseRXModel>, OnTableChangedListener {

    class Group(private val callback: (() -> (Unit))? = null) {
        //        private var mCategory: Category? = null
        private var mCategoryID: Long? = null
        private var mArchived: Boolean = false
        private var mDeleted: Boolean = false

        private fun dispatchValue() {
            callback?.invoke()
        }

        fun setCategoryID(id: Long?) {
            mCategoryID = id
            mArchived = false
            mDeleted = false
            dispatchValue()
        }

        fun setIsArchived(value: Boolean) {
            if (value != mArchived) {
                mArchived = value
                mDeleted = if (value) false else mDeleted
                dispatchValue()
            }
        }

        fun setDeleted(value: Boolean) {
            if (value != mDeleted) {
                mDeleted = value
                mArchived = if (value) false else mArchived
                dispatchValue()
            }
        }

        fun isDeleted() = mDeleted

        fun isArchived() = mArchived

        fun getCategoryID(): Long? {
            if (!mDeleted && !mArchived) return mCategoryID
            return null
        }

        override fun toString(): String {
            return "Group(deleted=$mDeleted, archived=$mArchived, category=$mCategoryID)"
        }

        fun buildQuery(from: From<Entry>): Transformable<Entry> {
            Timber.i("buildQuery($this)")

            // prima deleted and archived e poi il resto
            return if (mDeleted) {
                from.where(Entry_Table.entryDeleted.eq(1))
            } else if (mArchived) {
                from.where(Entry_Table.entryArchived.eq(1))
                    .and(Entry_Table.entryDeleted.eq(0))
            } else {
                return from
                    .where(Entry_Table.entryArchived.eq(0))
                    .and(Entry_Table.entryDeleted.eq(0)).also { where ->

                        mCategoryID?.let { categoryID ->
                            where.and(Entry_Table.category_categoryID.eq(categoryID))
                        }
                    }
            }
        }
    }

    private val handler: Handler = Handler()

    private val updateEntriesRunnable = Runnable {
        updateEntries()
    }

    private val updateCategoriesRunnable = Runnable { updateCategories() }

    val group: Group = Group {
        (categoryChanged as MutableLiveData).value = true
        updateEntries()
    }

    val categories: LiveData<List<Category>> by lazy {
        val data = MutableLiveData<List<Category>>()
        DatabaseHelper.getCategories().subscribe { result, error ->
            data.postValue(result)
        }
        data
    }

    val entries: LiveData<MutableList<Entry>> = MutableLiveData<MutableList<Entry>>()

    val categoryChanged: LiveData<Boolean> = MutableLiveData()

    @SuppressLint("CheckResult")
    private fun updateEntries() {
        Timber.i("updateEntries")
        DatabaseHelper
            .getEntries { group.buildQuery(this) }
            .observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
                Timber.v("[${currentThread()}] entries returned = ${result?.size}")
                (entries as MutableLiveData).value = result

                error?.let {
                    Timber.e("error = $error")
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun updateCategories() {
        Timber.i("updateCategories")
        DatabaseHelper.getCategories().observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            (categories as MutableLiveData).value = result
        }
    }

    override fun onCleared() {
        super.onCleared()
        DirectModelNotifier.get().unregisterForModelStateChanges(Category::class.java, this)
        DirectModelNotifier.get().unregisterForModelStateChanges(Entry::class.java, this)
        DirectModelNotifier.get().unregisterForTableChanges(Entry::class.java, this)
    }

    @SuppressLint("CheckResult")
    override fun onModelChanged(model: BaseRXModel, action: ChangeAction) {
        Timber.i("onModelChanged($model, $action)")

        if (model is Category) {
            handler.removeCallbacks(updateCategoriesRunnable)
            handler.post(updateCategoriesRunnable)

            if (action in arrayOf(ChangeAction.UPDATE, ChangeAction.DELETE)) {
                handler.removeCallbacks(updateEntriesRunnable)
                handler.post(updateEntriesRunnable)
            }

        } else if (model is Entry) {
            handler.removeCallbacks(updateEntriesRunnable)
            handler.post(updateEntriesRunnable)
        }
    }

    override fun onTableChanged(table: Class<*>?, action: ChangeAction) {
        Timber.i("onTableChange: $action, $table")
        when (table) {
            Entry::class.java ->
                when (action) {
                    ChangeAction.UPDATE, ChangeAction.DELETE -> {
                        handler.removeCallbacks(updateEntriesRunnable)
                        handler.post(updateEntriesRunnable)
                    }
                    ChangeAction.INSERT, ChangeAction.CHANGE -> {

                    }
                }
        }
    }

    init {
        DirectModelNotifier.get().registerForModelStateChanges(Category::class.java, this)
        DirectModelNotifier.get().registerForModelStateChanges(Entry::class.java, this)
        DirectModelNotifier.get().registerForTableChanges(Entry::class.java, this)
    }

}