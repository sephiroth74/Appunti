package it.sephiroth.android.app.appunti

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lapism.searchview.Search
import it.sephiroth.android.app.appunti.database.AppDatabase
import it.sephiroth.android.app.appunti.database.Entry
import it.sephiroth.android.app.appunti.ext.ioThread
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_activity_content.*

class MainActivity : AppCompatActivity() {

    private var twoPane: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        if (item_detail_container != null) {
            twoPane = true
        }

        val fragment = ItemListFragment()
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.item_list_container, fragment)
                .commit()


        fab.setOnClickListener {
            ioThread {
                AppDatabase.getInstance(this).entryDao().add(Entry("Title1", 1))
            }
        }

//        searchView.setOnMicClickListener {}
//        searchView.setOnQueryTextListener(object : Search.OnQueryTextListener {
//            override fun onQueryTextSubmit(query: CharSequence?): Boolean {
//                return false
//            }
//
//            override fun onQueryTextChange(newText: CharSequence?) {
//            }
//
//        })
    }
}
