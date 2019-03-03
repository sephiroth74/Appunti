package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import getColor
import isNavBarAtBottom
import it.sephiroth.android.app.appunti.ext.isAPI
import it.sephiroth.android.app.appunti.ext.isInMultiWindow
import it.sephiroth.android.app.appunti.models.SettingsManager
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

abstract class AppuntiActivity(
    private val wantsFullscreen: Boolean = false,
    private val lightTheme: Int = R.style.Theme_Appunti_Light_NoActionbar,
    private val darkTheme: Int = R.style.Theme_Appunti_Dark_NoActionbar
) : AppCompatActivity() {

    internal var statusbarHeight: Int = 0

    internal var navigationbarHeight: Int = 0

    internal var isDarkTheme: Boolean = false

    internal var fitSystemWindows: Boolean = false

    internal var isFullScreen: Boolean = false

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isFullScreen =
            wantsFullscreen && !isInMultiWindow && !resources.getBoolean(R.bool.fullscreen_style_fit_system_windows) && resources.isNavBarAtBottom

        isDarkTheme = SettingsManager.getInstance(this).darkTheme
        setTheme(if (isDarkTheme) darkTheme else lightTheme)

        Timber.v("SDK = ${Build.VERSION.SDK_INT}")
        Timber.v("fullscreen = $isFullScreen")

        if (isFullScreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }

        window.statusBarColor = theme.getColor(this, android.R.attr.statusBarColor)
        window.navigationBarColor = theme.getColor(this, android.R.attr.navigationBarColor)

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
//
//    class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {
//
//        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//
//            val c: Calendar = (arguments?.getSerializable("calendar") as Calendar?) ?: Calendar.getInstance()
//            val year = c.get(Calendar.YEAR)
//            val month = c.get(Calendar.MONTH)
//            val day = c.get(Calendar.DAY_OF_MONTH)
//            return DatePickerDialog(activity, this, year, month, day)
//        }
//
//        var callback: DatePickerDialog.OnDateSetListener? = null
//
//        override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
//            callback?.onDateSet(view, year, month, day)
//        }
//
//        companion object {
//            fun newInstance(year: Int, month: Int, dayOfMonth: Int): DatePickerFragment {
//                val c = Calendar.getInstance()
//                c.set(Calendar.YEAR, year)
//                c.set(Calendar.MONTH, month)
//                c.set(Calendar.DAY_OF_MONTH, dayOfMonth)
//
//                return DatePickerFragment().apply {
//                    arguments = Bundle().apply {
//                        putSerializable("calendar", c)
//                    }
//                }
//            }
//        }
//    }
//
//    class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {
//
//        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//            val c: Calendar = (arguments?.getSerializable("calendar") as Calendar?) ?: Calendar.getInstance()
//            val hour = c.get(Calendar.HOUR_OF_DAY)
//            val minute = c.get(Calendar.MINUTE)
//            return TimePickerDialog(activity, this, hour, minute, DateFormat.is24HourFormat(activity))
//        }
//
//        var callback: TimePickerDialog.OnTimeSetListener? = null
//
//        override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
//            callback?.onTimeSet(view, hourOfDay, minute)
//        }
//
//        companion object {
//            fun newInstance(hours: Int, minutes: Int): TimePickerFragment {
//                val c = Calendar.getInstance()
//                c.set(Calendar.HOUR_OF_DAY, hours)
//                c.set(Calendar.MINUTE, minutes)
//
//                return TimePickerFragment().apply {
//                    arguments = Bundle().apply {
//                        putSerializable("calendar", c)
//                    }
//                }
//            }
//        }
//    }

    fun pickDateTime(currentDateTime: ZonedDateTime, action: ((ZonedDateTime) -> (Unit))?) {
//
//        Timber.i("pickDateTime($currentDateTime)")
//
//        val dialog =
//            DatePickerFragment.newInstance(
//                currentDateTime.year,
//                currentDateTime.monthValue - 1,
//                currentDateTime.dayOfMonth
//            )
//
//        dialog.callback = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
//            val timeDialog = TimePickerFragment.newInstance(currentDateTime.hour, currentDateTime.minute)
//            timeDialog.callback = TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
//                val result = currentDateTime
//                    .withYear(year)
//                    .withMonth(month + 1)
//                    .withDayOfMonth(dayOfMonth)
//                    .withHour(hourOfDay)
//                    .withMinute(minute)
//
//                action?.invoke(result)
//            }
//            timeDialog.show(supportFragmentManager, "timeDialog")
//        }
//
//        dialog.show(supportFragmentManager, "dateDialog")


        val darkTheme = SettingsManager.getInstance(this).darkTheme
        val buttonsColor = theme.getColor(this, android.R.attr.colorForegroundInverse)
        val accentColor = theme.getColor(this, R.attr.colorAccent)

        val dateDialog = com.wdullaer.materialdatetimepicker.date.DatePickerDialog.newInstance(
            { view, year, monthOfYear, dayOfMonth ->
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
                timeDialog.version = com.wdullaer.materialdatetimepicker.time.TimePickerDialog.Version.VERSION_2
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
        dateDialog.version = com.wdullaer.materialdatetimepicker.date.DatePickerDialog.Version.VERSION_2
        dateDialog.accentColor = accentColor
        dateDialog.vibrate(false)
        dateDialog.setOkColor(buttonsColor)
        dateDialog.setCancelColor(buttonsColor)
        dateDialog.show(supportFragmentManager, "Datepickerdialog")
    }
}