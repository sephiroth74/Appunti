package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.query.OrderBy
import com.dbflow5.query.Transformable
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.ext.mainThread
import it.sephiroth.android.app.appunti.ext.rxSingle
import timber.log.Timber
import kotlin.properties.Delegates

class MainViewModel(application: Application) : AndroidViewModel(application), DirectModelNotifier.OnModelStateChangedListener<BaseRXModel> {
    val categories: LiveData<List<Category>> by lazy {
        val data = MutableLiveData<List<Category>>()
        fetchCategories { result -> data.postValue(result) }
        data
    }

    val entries: LiveData<MutableList<Entry>> = MutableLiveData<MutableList<Entry>>()

    val category: LiveData<Category?> = MutableLiveData()

    var currentCategory by Delegates.observable<Category?>(null) { _, _, newValue ->
        Timber.i("currentCategory = $newValue")
        (category as MutableLiveData).value = newValue
        fetchEntries(newValue).observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            Timber.v("entries returned = ${result.size}")
            (entries as MutableLiveData).value = result

            error?.let {
                Timber.e("error = $error")
            }
        }
    }

    val displayAsList: LiveData<Boolean> = MutableLiveData<Boolean>()
    val settingsManager = SettingsManager.getInstance(application)


    private fun fetchCategories(action: (List<Category>) -> Unit) {
        val list = select().from(Category::class).orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
        action.invoke(list)
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

    fun setDisplayAsList(value: Boolean) {
        settingsManager.displayAsList = value
    }


    override fun onCleared() {
        super.onCleared()
        DirectModelNotifier.get().unregisterForModelStateChanges(Category::class.java, this)
        DirectModelNotifier.get().unregisterForModelStateChanges(Entry::class.java, this)
    }

    override fun onModelChanged(model: BaseRXModel, action: ChangeAction) {
        Timber.i("onModelChanged($model, $action)")

        if (model is Category) {
            fetchCategories { result ->
                mainThread {
                    (categories as MutableLiveData).value = result
                    currentCategory?.let { category ->
                        val result = result.firstOrNull { it.categoryID == category.categoryID }
                        if (result == null) {
                            currentCategory = null
                        } else {
                            currentCategory = result
                        }
                    } ?: run {
                        currentCategory = null
                    }
                }
            }
        } else {
            currentCategory = currentCategory
        }
    }

    init {
        currentCategory = null
        (displayAsList as MutableLiveData).value = settingsManager.displayAsList
        settingsManager.setOnDisplayAsListChanged { value: Boolean -> displayAsList.value = value }

        DirectModelNotifier.get().registerForModelStateChanges(Category::class.java, this)
        DirectModelNotifier.get().registerForModelStateChanges(Entry::class.java, this)
    }

}