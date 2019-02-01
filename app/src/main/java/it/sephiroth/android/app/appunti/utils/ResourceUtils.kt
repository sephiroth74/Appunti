package it.sephiroth.android.app.appunti.utils

import android.content.Context
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.ext.resolveAttribute


object ResourceUtils {
    fun getCategoryColors(context: Context) = context.resources.getIntArray(context.theme.resolveAttribute(R.attr.categoryColors))
}