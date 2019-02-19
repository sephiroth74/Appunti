package it.sephiroth.android.app.appunti.ext

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Build
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.dbflow5.isNotNullOrEmpty
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun <T> rxSingle(thread: Scheduler, func: () -> T): Single<T> {
    return Single.create<T> { emitter ->
        try {
            emitter.onSuccess(func.invoke())
        } catch (error: Throwable) {
            emitter.onError(error)
        }
    }.subscribeOn(thread)
}

fun rxTimer(
    oldTimer: Disposable?,
    time: Long,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    thread: Scheduler = Schedulers.computation(),
    observerThread: Scheduler = AndroidSchedulers.mainThread(), action: ((Long) -> Unit)
): Disposable? {

    oldTimer?.dispose()

    return Observable
        .timer(time, unit, thread)
        .observeOn(observerThread)
        .subscribe {
            action.invoke(it)
        }
}

fun ioThread(func: () -> Unit) {
    Schedulers.io().scheduleDirect {
        func.invoke()
    }
}

fun doOnScheduler(scheduler: Scheduler, func: () -> Unit) {
    scheduler.scheduleDirect(func)
}

fun doOnMainThread(func: () -> Unit) {
    AndroidSchedulers.mainThread().scheduleDirect(func)
}

fun isAPI(value: Int) = Build.VERSION.SDK_INT == value

fun isAtLeastAPI(value: Int) = Build.VERSION.SDK_INT >= value

fun currentThread() = Thread.currentThread()

fun isMainThread() = Thread.currentThread() == Looper.getMainLooper().thread

inline fun <T> T.executeIfMainThread(func: () -> Unit): T? {
    return if (isMainThread()) {
        func.invoke()
        null
    } else {
        this
    }
}

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

fun View.showSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.showSoftInput(this, 0)
}

fun View.hideSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
}

inline fun TextView.doOnTextChanged(crossinline action: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit) =
    addTextChangedListener(onTextChanged = action)

inline fun TextView.doOnBeforeTextChanged(crossinline action: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit) =
    addTextChangedListener(onBeforeTextChanged = action)


inline fun TextView.addTextChangedListener(
    crossinline onBeforeTextChanged: (s: CharSequence?, start: Int, count: Int, after: Int) -> Unit = { _, _, _, _ -> },
    crossinline onTextChanged: (s: CharSequence?, start: Int, before: Int, count: Int) -> Unit = { _, _, _, _ -> },
    crossinline onAfterTextChanged: (s: Editable?) -> Unit = { }
): TextWatcher {
    val listener = object : TextWatcher {

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
            onTextChanged(s, start, before, count)

        override fun afterTextChanged(s: Editable?) = onAfterTextChanged(s)
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = onBeforeTextChanged(s, start, count, after)


    }

    addTextChangedListener(listener)
    return listener
}


object ExtensionUtils {
    val dateformat: DateFormat = java.text.DateFormat.getDateTimeInstance()
}