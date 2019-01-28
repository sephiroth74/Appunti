package it.sephiroth.android.app.appunti.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import it.sephiroth.android.app.appunti.database.AppDatabase
import it.sephiroth.android.app.appunti.database.EntryWithCategory

class EntryViewModel(application: Application) : AndroidViewModel(application) {

    val entries: LiveData<List<EntryWithCategory>> by lazy {
        AppDatabase.getInstance(getApplication()).entryDao().all()
    }


}