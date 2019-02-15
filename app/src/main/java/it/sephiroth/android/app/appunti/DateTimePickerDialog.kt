package it.sephiroth.android.app.appunti

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import it.sephiroth.android.app.appunti.models.SettingsManager
import kotlinx.android.synthetic.main.mdtp_done_button.*
import org.threeten.bp.ZonedDateTime

class DateTimePickerDialog : DialogFragment() {

    private var darkTheme: Boolean = false
    private var accentColor: Int = 0
    private lateinit var currentDateTime: ZonedDateTime
    private var mListener: ((ZonedDateTime) -> (Unit))? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.apply {
            accentColor = getInt(KEY_ACCENT_COLOR, Color.YELLOW)
            currentDateTime = getSerializable(KEY_CURRENT_DATET_IME) as ZonedDateTime
        } ?: run {
            currentDateTime = ZonedDateTime.now()
            accentColor = Color.YELLOW
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.appunti_datetime_picker_dialog, container, false)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        darkTheme = SettingsManager.getInstance(context!!).darkTheme

        showDateDialog(currentDateTime)

        mdtp_cancel.setOnClickListener { dismiss() }
        mdtp_ok.setOnClickListener {
            var fragment = childFragmentManager.findFragmentByTag("dateDialog")
            fragment?.let {
                (fragment as DatePickerDialog).notifyOnDateListener()
            } ?: run {
                childFragmentManager.findFragmentByTag("timeDialog").apply {
                    (this as TimePickerDialog).notifyOnDateListener()
                }
            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        mdtp_done_background.visibility = View.INVISIBLE
    }

    private fun showTimeDialog(currentDateTime: ZonedDateTime) {

        val timeDialog = TimePickerDialog.newInstance({ _, hourOfDay, minute, second ->
            mListener?.invoke(currentDateTime.withHour(hourOfDay).withMinute(minute).withSecond(second))
            dismiss()

        }, true)

        timeDialog.isThemeDark = darkTheme
        timeDialog.version = TimePickerDialog.Version.VERSION_2
        timeDialog.accentColor = accentColor
        timeDialog.vibrate(false)

        childFragmentManager
                .beginTransaction()
                .setCustomAnimations(0, 0, 0, 0)
                .replace(R.id.container, timeDialog, "timeDialog")
                .commit()

        mdtp_done_background.visibility = View.VISIBLE
    }

    private fun showDateDialog(currentDateTime: ZonedDateTime) {

        val dateDialog = DatePickerDialog
                .newInstance({ _, year, month, day ->
                    showTimeDialog(currentDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(day))
                },
                        currentDateTime.year,
                        currentDateTime.monthValue - 1,
                        currentDateTime.dayOfMonth)

        dateDialog.isThemeDark = darkTheme
        dateDialog.version = DatePickerDialog.Version.VERSION_2
        dateDialog.accentColor = accentColor
        dateDialog.autoDismiss(false)
        dateDialog.vibrate(false)

        dateDialog.registerOnDateChangedListener {
            dateDialog.notifyOnDateListener()
        }

        childFragmentManager
                .beginTransaction()
                .setCustomAnimations(0, 0, 0, 0)
                .replace(R.id.container, dateDialog, "dateDialog")
                .commit()

    }


    fun setOnDateTimeSetListener(callback: ((ZonedDateTime) -> (Unit))?): DateTimePickerDialog {
        mListener = callback
        return this
    }

    companion object {
        const val KEY_ACCENT_COLOR = "accent.color"
        const val KEY_CURRENT_DATET_IME = "current.datetime"

        fun newInstance(currentDateTime: ZonedDateTime, accentColor: Int): DateTimePickerDialog {

            val bundle = Bundle().apply {
                putInt(KEY_ACCENT_COLOR, accentColor)
                putSerializable(KEY_CURRENT_DATET_IME, currentDateTime)
            }

            return DateTimePickerDialog().apply {
                arguments = bundle
            }

        }
    }
}