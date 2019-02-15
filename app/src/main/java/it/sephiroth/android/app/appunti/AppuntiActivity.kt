package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import it.sephiroth.android.app.appunti.ext.isAPI
import it.sephiroth.android.app.appunti.models.SettingsManager
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.util.*

abstract class AppuntiActivity : AppCompatActivity() {

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = SettingsManager.getInstance(this).darkTheme
        setTheme(if (darkTheme) R.style.Theme_Appunti_Dark_NoActionbar else R.style.Theme_Appunti_Light_NoActionbar)

        setContentView(getContentLayout())

        getToolbar()?.let { toolbar ->
            setSupportActionBar(toolbar)
            if (!darkTheme && isAPI(26)) {
                toolbar.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    abstract fun getToolbar(): Toolbar?

    @LayoutRes
    abstract fun getContentLayout(): Int

    class DatePickerFragment : DialogFragment(), DatePickerDialog.OnDateSetListener {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

            val c: Calendar = (arguments?.getSerializable("calendar") as Calendar?) ?: Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            return DatePickerDialog(activity, this, year, month, day)
        }

        var callback: DatePickerDialog.OnDateSetListener? = null

        override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
            callback?.onDateSet(view, year, month, day)
        }

        companion object {
            fun newInstance(year: Int, month: Int, dayOfMonth: Int): DatePickerFragment {
                val c = Calendar.getInstance()
                c.set(Calendar.YEAR, year)
                c.set(Calendar.MONTH, month)
                c.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                return DatePickerFragment().apply {
                    arguments = Bundle().apply {
                        putSerializable("calendar", c)
                    }
                }
            }
        }
    }

    class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val c: Calendar = (arguments?.getSerializable("calendar") as Calendar?) ?: Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val minute = c.get(Calendar.MINUTE)
            return TimePickerDialog(activity, this, hour, minute, DateFormat.is24HourFormat(activity))
        }

        var callback: TimePickerDialog.OnTimeSetListener? = null

        override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
            callback?.onTimeSet(view, hourOfDay, minute)
        }

        companion object {
            fun newInstance(hours: Int, minutes: Int): TimePickerFragment {
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, hours)
                c.set(Calendar.MINUTE, minutes)

                return TimePickerFragment().apply {
                    arguments = Bundle().apply {
                        putSerializable("calendar", c)
                    }
                }
            }
        }
    }

    fun pickDateTime(currentDateTime: ZonedDateTime, action: ((ZonedDateTime) -> (Unit))?) {

        Timber.i("pickDateTime($currentDateTime)")

        val dialog =
            DatePickerFragment.newInstance(
                currentDateTime.year,
                currentDateTime.monthValue - 1,
                currentDateTime.dayOfMonth
            )

        dialog.callback = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
            val timeDialog = TimePickerFragment.newInstance(currentDateTime.hour, currentDateTime.minute)
            timeDialog.callback = TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
                val result = currentDateTime
                    .withYear(year)
                    .withMonth(month + 1)
                    .withDayOfMonth(dayOfMonth)
                    .withHour(hourOfDay)
                    .withMinute(minute)

                action?.invoke(result)
            }
            timeDialog.show(supportFragmentManager, "timeDialog")
        }

        dialog.show(supportFragmentManager, "dateDialog")


//
//
//        val dateDialog = DatePickerDialog.newInstance(
//                { view, year, monthOfYear, dayOfMonth ->
//                    Timber.v("date selection: $dayOfMonth/$monthOfYear/$year")
//
//                    val timeDialog = TimePickerDialog.newInstance({ view, hourOfDay, minute, second ->
//                        Timber.v("time selection = $hourOfDay:$minute:$second")
//
//                        val result = currentDateTime
//                            .withYear(year)
//                            .withMonth(monthOfYear + 1)
//                            .withDayOfMonth(dayOfMonth)
//                            .withHour(hourOfDay)
//                            .withMinute(minute)
//                            .withSecond(second)
//
//                        action?.invoke(result)
//
//                    }, true)
//
//                    timeDialog.isThemeDark = SettingsManager.getInstance(this).darkTheme
//                    timeDialog.version = TimePickerDialog.Version.VERSION_2
//                    timeDialog.accentColor = color
//                    timeDialog.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
//                    timeDialog.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
//                    timeDialog.vibrate(false)
//                    timeDialog.show(supportFragmentManager, "TimePickerDialog")
//
//                },
//                currentDateTime.year,
//                currentDateTime.monthValue - 1,
//                currentDateTime.dayOfMonth)
//
//        dateDialog.isThemeDark = SettingsManager.getInstance(this).darkTheme
//        dateDialog.version = DatePickerDialog.Version.VERSION_2
//        dateDialog.accentColor = color
//        dateDialog.vibrate(false)
//        dateDialog.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
//        dateDialog.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
//        dateDialog.show(supportFragmentManager, "Datepickerdialog")
    }
}