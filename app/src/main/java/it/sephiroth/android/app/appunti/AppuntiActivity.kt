package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import it.sephiroth.android.app.appunti.ext.isAPI
import it.sephiroth.android.app.appunti.models.SettingsManager

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
}