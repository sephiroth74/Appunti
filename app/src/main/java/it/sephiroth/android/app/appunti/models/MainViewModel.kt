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

    var currentCategory by Delegates.observable<Category?>(null) { prop, oldValue, newValue ->
        fetchEntries(newValue) { result -> (entries as MutableLiveData).postValue(result) }
        (category as MutableLiveData).value = newValue
    }

    val displayAsList: LiveData<Boolean> = MutableLiveData<Boolean>()
    val settingsManager = SettingsManager.getInstance(application)


    fun findCategoryByName(name: String): Category? {
        return categories.value?.firstOrNull { it.categoryTitle.equals(name) }
    }


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

    private fun fetchEntries(category: Category?, action: (List<Entry>) -> Unit) {
        Timber.i("fetchEntries(categoryID=${category?.categoryTitle})")
        select().from(Entry::class)
                .run {
                    category?.let {
                        return@run where(Entry_Table.category_categoryID.eq(it.categoryID)) as Transformable<Entry>
                    } ?: run {
                        this as Transformable<Entry>
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
        currentCategory = null
        (displayAsList as MutableLiveData).value = settingsManager.displayAsList
        settingsManager.doOnDisplayAsListChanged { value: Boolean -> displayAsList.value = value }
    }


}