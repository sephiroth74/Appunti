package it.sephiroth.android.app.appunti

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import timber.log.Timber
import kotlin.properties.Delegates

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(SETTINGS_NAME, 0)
    private var displayAsListChanged: ((value: Boolean) -> Unit)? = null

    fun doOnDisplayAsListChanged(action: (value: Boolean) -> Unit) {
        displayAsListChanged = action
    }

    var displayAsList: Boolean by Delegates.observable(
            prefs.getBoolean("main.display_as_list",
                    context.resources.getInteger(R.integer.list_items_columns) ==
                            context.resources.getInteger(R.integer.list_items_columns_list))) { prop, oldValue, newValue ->
        Timber.i("displayAsList -> $newValue")
        prefs.edit { putBoolean("main.display_as_list", newValue) }
        displayAsListChanged?.invoke(newValue)
    }

    companion object {
        private const val SETTINGS_NAME = "appunti"
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
                }

    }
}