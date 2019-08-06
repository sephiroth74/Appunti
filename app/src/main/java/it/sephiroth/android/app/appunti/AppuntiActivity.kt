package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import isNavBarAtBottom
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.library.kotlin_extensions.app.isInMultiWindow
import it.sephiroth.android.library.kotlin_extensions.app.isNightMode
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.AutoDisposable
import it.sephiroth.android.library.kotlin_extensions.os.isAPI
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

abstract class AppuntiActivity(
    private val wantsFullscreen: Boolean = false
) : AppCompatActivity() {

    internal var statusbarHeight: Int = 0

    internal var navigationbarHeight: Int = 0

    internal var isDarkTheme: Boolean = false

    internal var fitSystemWindows: Boolean = false

    internal var isFullScreen: Boolean = false

    protected lateinit var autoDisposable: AutoDisposable

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val darkThemeBehavior = SettingsManager.getInstance(this).darkThemeBehavior
        var mode: Int = SettingsManager.getInstance(this).getNightMode(darkThemeBehavior)
        AppCompatDelegate.setDefaultNightMode(mode)

        super.onCreate(savedInstanceState)
        autoDisposable = AutoDisposable(this)

        isFullScreen =
            wantsFullscreen && !isInMultiWindow && !resources.getBoolean(R.bool.fullscreen_style_fit_system_windows) &&
                    resources.isNavBarAtBottom

        isDarkTheme = isNightMode()

        Timber.v("SDK = ${Build.VERSION.SDK_INT}")
        if (BuildConfig.DEBUG) {
            Timber.v("wantsFullscreen = $wantsFullscreen")
            Timber.v("fullscreen = $isFullScreen")
            Timber.v("resources.isNavBarAtBottom = ${resources.isNavBarAtBottom}")
            Timber.v("isInMultiWindow = $isInMultiWindow")
            Timber.v("fullscreen_style_fit_system_windows = ${resources.getBoolean(R.bool.fullscreen_style_fit_system_windows)}")
            Timber.v("isDarkTheme: $isDarkTheme")
        }

        if (isFullScreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )

            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.statusBarColor = theme.getColor(this, android.R.attr.statusBarColor)
            window.navigationBarColor = theme.getColor(this, android.R.attr.navigationBarColor)
        } else {
            if (!isInMultiWindow) {
                window.statusBarColor = theme.getColor(this, android.R.attr.navigationBarColor)
                window.navigationBarColor = theme.getColor(this, android.R.attr.navigationBarColor)
            }
        }


        fitSystemWindows = !isFullScreen //if (isInMultiWindow) true else isFullScreen
        Timber.v("fitSystemWindows = $fitSystemWindows")

        setContentView(getContentLayout())

        getToolbar()?.let { toolbar ->
            setSupportActionBar(toolbar)
            if (!isDarkTheme && isAPI(26)) {
                toolbar.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    abstract fun getToolbar(): Toolbar?

    @LayoutRes
    abstract fun getContentLayout(): Int

    fun pickDateTime(currentDateTime: ZonedDateTime, action: ((ZonedDateTime) -> (Unit))?) {
        val darkTheme = isNightMode()
        val buttonsColor = theme.getColor(this, android.R.attr.colorForegroundInverse)
        val accentColor = theme.getColor(this, R.attr.colorAccent)

        val dateDialog = com.wdullaer.materialdatetimepicker.date.DatePickerDialog.newInstance(
            { _, year, monthOfYear, dayOfMonth ->
                Timber.v("date selection: $dayOfMonth/$monthOfYear/$year")

                val timeDialog =
                    com.wdullaer.materialdatetimepicker.time.TimePickerDialog.newInstance({ view, hourOfDay, minute, second ->
                        Timber.v("time selection = $hourOfDay:$minute:$second")

                        val result = currentDateTime
                            .withYear(year)
                            .withMonth(monthOfYear + 1)
                            .withDayOfMonth(dayOfMonth)
                            .withHour(hourOfDay)
                            .withMinute(minute)
                            .withSecond(second)

                        action?.invoke(result)

                    }, DateFormat.is24HourFormat(this))

                timeDialog.isThemeDark = darkTheme
                timeDialog.version =
                    com.wdullaer.materialdatetimepicker.time.TimePickerDialog.Version.VERSION_2
                timeDialog.accentColor = accentColor
                timeDialog.setOkColor(buttonsColor)
                timeDialog.setCancelColor(buttonsColor)
                timeDialog.vibrate(false)
                timeDialog.show(supportFragmentManager, "TimePickerDialog")

            },
            currentDateTime.year,
            currentDateTime.monthValue - 1,
            currentDateTime.dayOfMonth
        )

        dateDialog.isThemeDark = darkTheme
        dateDialog.version =
            com.wdullaer.materialdatetimepicker.date.DatePickerDialog.Version.VERSION_2
        dateDialog.accentColor = accentColor
        dateDialog.vibrate(false)
        dateDialog.setOkColor(buttonsColor)
        dateDialog.setCancelColor(buttonsColor)
        dateDialog.show(supportFragmentManager, "Datepickerdialog")
    }
}