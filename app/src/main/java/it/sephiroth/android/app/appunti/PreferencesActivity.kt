package it.sephiroth.android.app.appunti

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import it.sephiroth.android.app.appunti.ext.applyNoActionBarTheme

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyNoActionBarTheme(null) {
            setContentView(R.layout.appunti_activity_preferences)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preferencesContainer, PreferencesFragment())
                .commit()
    }
}