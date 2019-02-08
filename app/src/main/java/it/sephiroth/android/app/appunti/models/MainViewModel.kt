package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.query.SQLOperator
import com.dbflow5.query.or
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
import it.sephiroth.android.app.appunti.ext.executeIfMainThread
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
        DatabaseHelper.getCategories().subscribe { result, error ->
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

    fun updateEntries() {
        updateEntries(currentCategory)
    }

    fun setDisplayAsList(value: Boolean) {
        settingsManager.displayAsList = value
    }

    @SuppressLint("CheckResult")
    private fun updateEntries(newValue: Category?) {
        DatabaseHelper
            .getEntriesByCategory(newValue)
            .observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
                Timber.v("[${currentThread()}] entries returned = ${result.size}")
                (entries as MutableLiveData).value = result

                error?.let {
                    Timber.e("error = $error")
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun updateCategories() {
        DatabaseHelper.getCategories().observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            (categories as MutableLiveData).value = result
            currentCategory?.let { category ->
                val categoryResult = result.firstOrNull { it.categoryID == category.categoryID }
                currentCategory = categoryResult
            } ?: run {
                currentCategory = null
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