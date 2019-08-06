package it.sephiroth.android.app.appunti.utils

import android.content.Context
import it.sephiroth.android.app.appunti.R


object ResourceUtils {
    fun getCategoryColors(context: Context): IntArray {
        return context.resources.getIntArray(R.array.category_colors)
    }
}