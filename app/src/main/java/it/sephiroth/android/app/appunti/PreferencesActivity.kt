package it.sephiroth.android.app.appunti

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import kotlinx.android.synthetic.main.appunti_activity_preferences.*

class PreferencesActivity : AppuntiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preferencesContainer, PreferencesFragment())
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getToolbar(): Toolbar? = toolbar
    override fun getContentLayout(): Int = R.layout.appunti_activity_preferences
}