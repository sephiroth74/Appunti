package it.sephiroth.android.app.appunti.models

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.BuildCompat
import androidx.preference.PreferenceManager
import com.hunter.library.debug.HunterDebug
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.events.RxBus
import it.sephiroth.android.app.appunti.events.ThemeChangedEvent
import timber.log.Timber
import java.time.Instant
import java.util.*
import kotlin.properties.Delegates

class SettingsManager(val context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var displayAsListChanged: ((value: Boolean) -> Unit)? = null

    fun setOnDisplayAsListChanged(action: (value: Boolean) -> Unit) {
        displayAsListChanged = action
    }


    var openLinksOnClick: Boolean?
        get() {
            if (prefs.contains(PREFS_KEY_OPEN_LINKS_ON_CLICK)) {
                return prefs.getBoolean(PREFS_KEY_OPEN_LINKS_ON_CLICK, true)
            }
            return null
        }
        set(value) {
            prefs.edit {
                value?.let {
                    putBoolean(PREFS_KEY_OPEN_LINKS_ON_CLICK, it)
                } ?: run {
                    remove(PREFS_KEY_OPEN_LINKS_ON_CLICK)
                }
            }
        }

    val isFirstLaunch: Boolean by lazy {
        if (prefs.contains(PREFS_KEY_FIRST_LAUNCH)) {
            false
        } else {
            prefs.edit { putBoolean(PREFS_KEY_FIRST_LAUNCH, true) }
            true
        }
    }

    var darkThemeBehavior: DarkThemeBehavior = Companion.DarkThemeBehavior.Automatic
        get() {
            Timber.v("get themeBehavior")
            if (prefs.contains(PREFS_KEY_THEME_BEHAVIOR)) {
                val value =
                    prefs.getInt(PREFS_KEY_THEME_BEHAVIOR, DarkThemeBehavior.Automatic.ordinal)
                if (value >= 0 && value < DarkThemeBehavior.values().size) {
                    return DarkThemeBehavior.values()[value]
                }
            }
            return Companion.DarkThemeBehavior.Automatic
        }
        set(value) {
            Timber.i("set themeBehavior = $value")
            field = value
            prefs.edit { putInt(PREFS_KEY_THEME_BEHAVIOR, value.ordinal) }
            RxBus.send(ThemeChangedEvent(value))
        }

    fun getNightMode() = getNightMode(darkThemeBehavior)

    @HunterDebug(debugResult = true)
    fun getNightMode(value: DarkThemeBehavior): Int {
        return when (value) {
            DarkThemeBehavior.Automatic -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    if (hour < 7 || hour > 22) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                    }
                }
            }
            DarkThemeBehavior.Night -> AppCompatDelegate.MODE_NIGHT_YES
            DarkThemeBehavior.Day -> AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    internal var displayAsList: Boolean by Delegates.observable(
        prefs.getBoolean(
            "main.display_as_list",
            context.resources.getInteger(R.integer.list_items_columns) ==
                    context.resources.getInteger(R.integer.list_items_columns_list)
        )
    ) { _, _, newValue ->
        Timber.i("displayAsList -> $newValue")
        prefs.edit { putBoolean("main.display_as_list", newValue) }
        displayAsListChanged?.invoke(newValue)
    }

    companion object {

        @Deprecated("use PREFS_KEY_DARK_THEME_BEHAVIOR")
        const val PREFS_KEY_DARK_THEME = "main.isDarkTheme"

        const val PREFS_KEY_THEME_BEHAVIOR = "main.themeBehavior"
        const val PREFS_KEY_FIRST_LAUNCH = "app.isFirstLaunch"
        const val PREFS_KEY_OPEN_LINKS_ON_CLICK = "app.openLinksOnClick"

        enum class DarkThemeBehavior {
            Automatic,
            Day,
            Night
        }


        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: SettingsManager(context).also { INSTANCE = it }
            }

    }
}