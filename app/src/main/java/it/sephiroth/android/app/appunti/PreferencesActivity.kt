package it.sephiroth.android.app.appunti

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import it.sephiroth.android.app.appunti.events.RxBus
import it.sephiroth.android.app.appunti.events.ThemeChangedEvent
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.addTo
import kotlinx.android.synthetic.main.appunti_activity_preferences.*
import timber.log.Timber

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

        RxBus.listen(ThemeChangedEvent::class.java)
            .subscribe { event ->
                Timber.i("ThemeChangedEvent(${event.value})")
                AppCompatDelegate.setDefaultNightMode(
                    SettingsManager.getInstance(this).getNightMode(
                        event.value
                    )
                )
            }
            .addTo(autoDisposable)

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