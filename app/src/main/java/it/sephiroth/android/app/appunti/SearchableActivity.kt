package it.sephiroth.android.app.appunti

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

class SearchableActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        Timber.i("onCreate")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.searchable_activity)

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                Timber.i("QUERY: $query")
            }
        }
    }
}