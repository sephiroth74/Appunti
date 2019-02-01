package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.R
import java.text.DateFormat
import java.util.*

fun Any.ioThread(func: () -> Unit) {
    Schedulers.io().scheduleDirect {
        func.invoke()
    }
}

fun Any.mainThread(func: () -> Unit) {
    AndroidSchedulers.mainThread().scheduleDirect { func.invoke() }
}

fun Any.isAPI(value: Int) = Build.VERSION.SDK_INT == value

fun Any.isAtLeastAPI(value: Int) = Build.VERSION.SDK_INT >= value

fun Any.currentThread() = Thread.currentThread()

fun Date.toUserDate() = ExtensionUtils.dateformat.format(this)

fun Resources.Theme.getColorStateList(context: Context, @AttrRes id: Int): ColorStateList? {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, false)
    return ContextCompat.getColorStateList(context, typedValue.data)
}

fun Resources.Theme.getColor(context: Context, @AttrRes id: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, false)
    return ContextCompat.getColor(context, typedValue.data)
}


fun Context.isLightTheme(): Boolean {
    val typedValue = TypedValue()
    val identifier = resources.getIdentifier("isLightTheme", "attr", this.packageName)
    theme.resolveAttribute(identifier, typedValue, false)
    return typedValue.data != 0
}

fun Resources.Theme.resolveAttribute(@AttrRes id: Int): Int {
    val typedValue = TypedValue()
    resolveAttribute(id, typedValue, false)
    return typedValue.data
}

object ExtensionUtils {
    val dateformat: DateFormat = java.text.DateFormat.getDateTimeInstance()
}