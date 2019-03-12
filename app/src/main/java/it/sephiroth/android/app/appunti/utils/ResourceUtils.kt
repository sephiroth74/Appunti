package it.sephiroth.android.app.appunti.utils

import android.content.Context
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.library.kotlin_extensions.content.res.resolveAttribute


object ResourceUtils {
    private var categoryColors: IntArray? = null

    fun getCategoryColors(context: Context): IntArray {
        if (null == categoryColors) {
            val resourceID = context.theme.resolveAttribute(R.attr.categoryColors)
            if (resourceID != 0) {
                categoryColors = context.resources.getIntArray(resourceID)
            } else {
                return context.resources.getIntArray(R.array.category_colors_light)
            }
        }
        return categoryColors!!
    }
}