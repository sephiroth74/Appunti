package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
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


    fun pickDateTime(currentDateTime: ZonedDateTime, action: (() -> (Unit))?) {

        val calendar = Calendar.getInstance()
        calendar.set(currentDateTime.year, currentDateTime.monthValue, currentDateTime.dayOfMonth, currentDateTime.hour, currentDateTime.minute, currentDateTime.second)

        val dpd = DatePickerDialog.newInstance(
                { view, year, monthOfYear, dayOfMonth ->
                    Timber.v("date selection: $dayOfMonth/$monthOfYear/$year")

                    val dialog = TimePickerDialog.newInstance({ view, hourOfDay, minute,
                                                                second ->
                        Timber.v("time selection = $hourOfDay:$minute:$second")

                        action?.invoke()

                    }, true)

                    dialog.version = TimePickerDialog.Version.VERSION_2
                    dialog.accentColor = ResourcesCompat.getColor(resources, R.color.colorPrimary, theme)
                    dialog.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
                    dialog.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
                    dialog.vibrate(false)
                    dialog.show(supportFragmentManager, "TimePickerDialog")

                },
                currentDateTime.year,
                currentDateTime.monthValue,
                currentDateTime.dayOfMonth)

        dpd.version = DatePickerDialog.Version.VERSION_2
        dpd.accentColor = ResourcesCompat.getColor(resources, R.color.colorPrimary, theme)
        dpd.minDate = calendar
        dpd.vibrate(false)
        dpd.setOkColor(theme.getColor(this, android.R.attr.textColorSecondary))
        dpd.setCancelColor(theme.getColor(this, android.R.attr.textColorSecondary))
        dpd.show(supportFragmentManager, "Datepickerdialog")
    }
}