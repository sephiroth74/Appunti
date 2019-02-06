package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.os.Handler
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.config.FlowManager
import com.dbflow5.query.*
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.runtime.OnTableChangedListener
import com.dbflow5.structure.ChangeAction
import com.dbflow5.structure.insert
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.executeIfMainThread
import it.sephiroth.android.app.appunti.ext.ioThread
import it.sephiroth.android.app.appunti.ext.rxSingle
import timber.log.Timber
import kotlin.properties.Delegates

class MainViewModel(application: Application) : AndroidViewModel(application),
        DirectModelNotifier.OnModelStateChangedListener<BaseRXModel>, OnTableChangedListener {

    val handler: Handler = Handler()

    val updateEntriesRunnable = Runnable {
        updateEntries(currentCategory)
    }

    val updateCategoriesRunnable = Runnable {
        updateCategories()
    }

    val categories: LiveData<List<Category>> by lazy {
        val data = MutableLiveData<List<Category>>()
        fetchCategories().subscribe { result, error ->
            data.postValue(result)
        }
        data
    }

    val entries: LiveData<MutableList<Entry>> = MutableLiveData<MutableList<Entry>>()

    val category: LiveData<Category?> = MutableLiveData()

    var currentCategory by Delegates.observable<Category?>(null) { _, _, newValue ->
        Timber.i("[${currentThread()}] currentCategory = $newValue")

        executeIfMainThread {
            (category as MutableLiveData).value = newValue
        } ?: run {
            (category as MutableLiveData).postValue(newValue)
        }

        updateEntries(newValue)
    }

    val displayAsList: LiveData<Boolean> = MutableLiveData<Boolean>()
    val settingsManager = SettingsManager.getInstance(application)

    fun batchPinEntries(values: List<Entry>, pin: Boolean) {
        Timber.i("batchPinEntries($pin)")

        if (values.isEmpty()) return

        val pinnedValue = if (pin) 1 else 0

        rxSingle(Schedulers.io()) {
            update(Entry::class)
                    .set(Entry_Table.entryPinned.eq(pinnedValue))
                    .where(Entry_Table.entryID.`in`(values.map { it.entryID }))
                    .execute(FlowManager.getDatabase(AppDatabase::class.java))
        }.subscribe()
    }

    private var trash = mutableListOf<Entry>()

    fun restoreFromTrash() {
        Timber.i("restoreFromTrash(${trash.size}")
        if (trash.isNotEmpty()) {

            FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
                for (entry in trash) {
                    entry.insert()
                }
            }.success { transaction, unit ->
                Timber.i("transaction succes!!")
            }.error { transaction, throwable ->
                Timber.e("transaction error!")
            }.build().execute()
        }
    }

    fun emptyTrash() {
        Timber.i("emptyTrash")
        trash.clear()
    }

    @SuppressLint("CheckResult")
    fun batchDeleteEntries(values: List<Entry>, action: ((Boolean) -> Unit)?) {
        Timber.i("batchDeleteEntries(${values.size})")

        trash.clear()

        if (values.isEmpty()) return

        rxSingle(Schedulers.io()) {
            delete()
                    .from(Entry::class)
                    .where(Entry_Table.entryID.`in`(values.map { it.entryID }))
                    .execute(FlowManager.getDatabase(AppDatabase::class.java))

        }.subscribe({
            trash.addAll(values)
            action?.invoke(true)
        }, { error ->
            Timber.e(error)
            action?.invoke(false)
        })
    }

    fun updateEntries() {
        updateEntries(currentCategory)
    }

    fun setDisplayAsList(value: Boolean) {
        settingsManager.displayAsList = value
    }

    @SuppressLint("CheckResult")
    private fun updateEntries(newValue: Category?) {
        fetchEntries(newValue).observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            Timber.v("[${currentThread()}] entries returned = ${result.size}")
            (entries as MutableLiveData).value = result

            error?.let {
                Timber.e("error = $error")
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun updateCategories() {
        fetchCategories().observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            (categories as MutableLiveData).value = result
            currentCategory?.let { category ->
                val categoryResult = result.firstOrNull { it.categoryID == category.categoryID }
                currentCategory = categoryResult
            } ?: run {
                currentCategory = null
            }
        }
    }

    private fun fetchCategories(): Single<MutableList<Category>> {
        return rxSingle(Schedulers.io()) {
            select().from(Category::class).orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
        }
    }

    @SuppressLint("CheckResult")
    private fun fetchEntries(category: Category?): Single<MutableList<Entry>> {
        Timber.i("fetchEntries(category=${category})")

        return rxSingle(Schedulers.io()) {
            select().from(Entry::class)
                    .run {
                        category?.let {
                            return@run where(Entry_Table.category_categoryID.eq(it.categoryID)) as Transformable<Entry>
                        } ?: run {
                            this as Transformable<Entry>
                        }

                    }.run {
                        orderByAll(listOf(
                                OrderBy(Entry_Table.entryPinned.nameAlias, false),
                                OrderBy(Entry_Table.entryPriority.nameAlias, false),
                                OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)
                        )).list
                    }
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
        currentCategory = null
        (displayAsList as MutableLiveData).value = settingsManager.displayAsList
        settingsManager.setOnDisplayAsListChanged { value: Boolean -> displayAsList.value = value }

        DirectModelNotifier.get().registerForModelStateChanges(Category::class.java, this)
        DirectModelNotifier.get().registerForModelStateChanges(Entry::class.java, this)
        DirectModelNotifier.get().registerForTableChanges(Entry::class.java, this)
    }

}