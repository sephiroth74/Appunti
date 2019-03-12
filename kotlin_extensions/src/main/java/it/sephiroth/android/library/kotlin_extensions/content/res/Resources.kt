package it.sephiroth.android.library.kotlin_extensions.content.res

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

inline val Resources.isPortrait: Boolean get() = this.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

// Theme

fun Resources.Theme.resolveAttribute(@AttrRes id: Int, resolveRefs: Boolean = false): Int {
    val typedValue = TypedValue()
    resolveAttribute(id, typedValue, resolveRefs)
    return typedValue.data
}

fun Resources.Theme.getTypedValue(@AttrRes id: Int, resolveRefs: Boolean = false): TypedValue {
    val typedValue = TypedValue()
    resolveAttribute(id, typedValue, resolveRefs)
    return typedValue
}

fun Resources.Theme.getDimensionPixelSize(context: Context, @AttrRes id: Int): Int {
    return resources.getDimensionPixelSize(context.theme.resolveAttribute(id))
}

fun Resources.Theme.getInteger(context: Context, @AttrRes id: Int): Int {
    return context.resources.getInteger(context.theme.resolveAttribute(id))
}

fun Resources.Theme.getColorStateList(context: Context, @AttrRes id: Int): ColorStateList? {
    return ContextCompat.getColorStateList(context, context.theme.resolveAttribute(id))
}

fun Resources.Theme.getColor(context: Context, @AttrRes id: Int): Int {
    return ContextCompat.getColor(context, context.theme.resolveAttribute(id))
}
