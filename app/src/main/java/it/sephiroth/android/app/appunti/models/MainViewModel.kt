package it.sephiroth.android.app.appunti.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dbflow5.query.OrderBy
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.transaction.asFlowable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val categories: LiveData<List<Category>> by lazy {
        val data = MutableLiveData<List<Category>>()
        fetchCategories { result -> data.postValue(result) }
        data
    }

    //    val entries = MutableLiveData<LiveData<List<EntryWithCategory>>>()
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

//    val categories: LiveData<List<Category>> by lazy {
//        AppDatabase.getInstance(getApplication()).categoryDao().getAll()
//    }

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