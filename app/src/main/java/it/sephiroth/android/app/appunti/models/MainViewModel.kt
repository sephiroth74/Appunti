package it.sephiroth.android.app.appunti.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.query.*
import com.dbflow5.reactivestreams.transaction.asFlowable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import timber.log.Timber
import kotlin.properties.Delegates

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val categories: LiveData<List<Category>> by lazy {
        val data = MutableLiveData<List<Category>>()
        fetchCategories { result -> data.postValue(result) }
        data
    }

    val entries: LiveData<List<Entry>> = MutableLiveData<List<Entry>>()

    val category: LiveData<Category?> = MutableLiveData()

    var currentCategoryID: Int by Delegates.observable(0) { prop, oldValue, newValue ->
        Timber.i("currentCategoryID = $newValue")

        if (newValue > 0) {
            val cat = categories.value?.firstOrNull { it.categoryID == newValue }
            (category as MutableLiveData).postValue(cat)
        }

        fetchEntries(newValue) { result -> (entries as MutableLiveData).postValue(result) }
    }

    val displayAsList: LiveData<Boolean> = MutableLiveData<Boolean>()
    val settingsManager = SettingsManager.getInstance(application)

//
//    var category: String? by Delegates.observable<String?>(null) { prop, oldValue, newValue ->
//        newValue?.let {
//            entries.value = getEntriesByCategory(it)
//        } ?: kotlin.run {
//            entries.value = getEntriesByCategory(null)
//        }
//    }

    private fun fetchCategories(action: (List<Category>) -> Unit) {
        select().from(Category::class)
                .orderBy(OrderBy(Category_Table.categoryID.nameAlias, true))
                .asFlowable { _, query ->
                    action.invoke(query.list.toList())
                }.subscribeOn(Schedulers.io()).subscribe()
    }

    private fun fetchEntries(categoryID: Int, action: (List<Entry>) -> Unit) {
        Timber.i("fetchEntries(categoryID=$categoryID)")
        select().from(Entry::class)
                .run {
                    if (categoryID > 0) {
                        return@run where(Entry_Table.category_categoryID.eq(categoryID)) as Transformable<Entry>
                    } else {
                        return@run this as Transformable<Entry>
                    }

                }.run {
                    orderByAll(listOf(
                            OrderBy(Entry_Table.entryPinned.nameAlias, true),
                            OrderBy(Entry_Table.entryPriority.nameAlias, false),
                            OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)
                    ))
                            .asFlowable { _, query ->
                                action.invoke(query.list.toList())
                            }.subscribeOn(Schedulers.io()).subscribe()
                }
    }


    fun setDisplayAsList(value: Boolean) {
        settingsManager.displayAsList = value
    }
//
//    fun getEntriesByCategory(name: String?): Flowable<MutableList<Entry>> {
//        return SQLite.select()
//                .from(Entry::class.java)
//                .orderBy(OrderBy.fromNameAlias(Entry_Table
//                        .entryModifiedDate
//                        .nameAlias)
//                        .descending())
//                .rx().observeOnTableChanges().map { it.queryList() }
//    }

    init {
//        category = null
        (displayAsList as MutableLiveData).value = settingsManager.displayAsList
        settingsManager.doOnDisplayAsListChanged { value: Boolean -> displayAsList.value = value }
    }


}