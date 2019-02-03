package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.ext.isLightTheme
import timber.log.Timber
import kotlin.properties.Delegates

class SettingsManager(val context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var displayAsListChanged: ((value: Boolean) -> Unit)? = null
    private var themeChangedListener: ((value: Boolean) -> Unit)? = null

    fun setOnDisplayAsListChanged(action: (value: Boolean) -> Unit) {
        displayAsListChanged = action
    }

    fun setOnThemeChanged(action: (value: Boolean) -> Unit) {
        themeChangedListener = action
    }

    var darkTheme: Boolean = true
        get() {
            if (prefs.contains(PREFS_KEY_DARK_THEME)) return prefs.getBoolean(PREFS_KEY_DARK_THEME, false)
            return !context.isLightTheme()
        }
        set(value) {
            field = value
            prefs.edit { putBoolean(PREFS_KEY_DARK_THEME, value) }
            themeChangedListener?.invoke(value)
        }

    internal var displayAsList: Boolean by Delegates.observable(
            prefs.getBoolean("main.display_as_list",
                    context.resources.getInteger(R.integer.list_items_columns) ==
                            context.resources.getInteger(R.integer.list_items_columns_list))) { prop, oldValue, newValue ->
        Timber.i("displayAsList -> $newValue")
        prefs.edit { putBoolean("main.display_as_list", newValue) }
        displayAsListChanged?.invoke(newValue)
    }

    companion object {

        const val PREFS_KEY_DARK_THEME = "main.isDarkTheme"


        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager =
                INSTANCE ?: synchronized(this) {
                    INSTANCE
                            ?: SettingsManager(context).also { INSTANCE = it }
                }

    }
}