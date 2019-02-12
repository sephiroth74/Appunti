package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import it.sephiroth.android.app.appunti.ext.getColor
import it.sephiroth.android.app.appunti.ext.isAPI
import it.sephiroth.android.app.appunti.models.SettingsManager
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZonedDateTime
import timber.log.Timber

abstract class AppuntiActivity : AppCompatActivity() {

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = SettingsManager.getInstance(this).darkTheme
        setTheme(if (darkTheme) R.style.Theme_Appunti_Dark_NoActionbar else R.style.Theme_Appunti_Light_NoActionbar)

        setContentView(getContentLayout())

        getToolbar()?.let { toolbar ->
            setSupportActionBar(toolbar)
            if (! darkTheme && isAPI(26)) {
                toolbar.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    abstract fun getToolbar(): Toolbar?

    @LayoutRes
    abstract fun getContentLayout(): Int


    fun pickDateTime(currentDateTime: ZonedDateTime, action: ((ZonedDateTime) -> (Unit))?) {

        Timber.i("pickDateTime($currentDateTime)")

        val color = ResourcesCompat.getColor(resources, R.color.colorPrimary, theme)

        val dpd = DatePickerDialog.newInstance(
                { view, year, monthOfYear, dayOfMonth ->
                    Timber.v("date selection: $dayOfMonth/$monthOfYear/$year")

                    val dialog = TimePickerDialog.newInstance({ view, hourOfDay, minute, second ->
                        Timber.v("time selection = $hourOfDay:$minute:$second")

                        val result = currentDateTime
                            .withYear(year)
                            .withMonth(monthOfYear + 1)
                            .withDayOfMonth(dayOfMonth)
                            .withHour(hourOfDay)
                            .withMinute(minute)
                            .withSecond(second)

                        action?.invoke(result)

                    }, true)

                    dialog.version = TimePickerDialog.Version.VERSION_2
                    dialog.accentColor = color
                    dialog.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
                    dialog.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
                    dialog.vibrate(false)
                    dialog.show(supportFragmentManager, "TimePickerDialog")

                },
                currentDateTime.year,
                currentDateTime.monthValue - 1,
                currentDateTime.dayOfMonth)

        dpd.version = DatePickerDialog.Version.VERSION_2
        dpd.accentColor = color
        dpd.vibrate(false)
        dpd.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
        dpd.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
        dpd.show(supportFragmentManager, "Datepickerdialog")
    }
}