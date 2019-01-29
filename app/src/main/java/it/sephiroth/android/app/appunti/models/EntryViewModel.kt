package it.sephiroth.android.app.appunti.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import it.sephiroth.android.app.appunti.database.AppDatabase
import it.sephiroth.android.app.appunti.database.Category
import it.sephiroth.android.app.appunti.database.EntryWithCategory
import kotlin.properties.Delegates

class EntryViewModel(application: Application) : AndroidViewModel(application) {

    val entries = MutableLiveData<LiveData<List<EntryWithCategory>>>()
    val displayAsGrid = MutableLiveData<Boolean>()

    var category: String? by Delegates.observable<String?>(null) { prop, oldValue, newValue ->
        newValue?.let {
            entries.value = getEntriesByCategory(it)
        } ?: kotlin.run {
            entries.value = getEntriesByCategory(null)
        }
    }


    fun getEntriesByCategory(name: String?): LiveData<List<EntryWithCategory>> {
        name?.let {
            return AppDatabase.getInstance(getApplication()).entryDao().allByCategory(name)
        } ?: run {
            return AppDatabase.getInstance(getApplication()).entryDao().all()
        }
    }

    val categories: LiveData<List<Category>> by lazy {
        AppDatabase.getInstance(getApplication()).categoryDao().getAll()
    }

    init {
        category = null
    }
}