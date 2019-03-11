package it.sephiroth.android.app.appunti.ext

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
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

fun doOnScheduler(scheduler: Scheduler, func: () -> Unit): Disposable {
    return scheduler.scheduleDirect(func)
}

fun doOnScheduler(scheduler: Scheduler, disposable: Disposable? = null, func: () -> Unit): Disposable {
    disposable?.dispose()
    return scheduler.scheduleDirect(func)
}

fun doOnMainThread(func: () -> Unit) {
    AndroidSchedulers.mainThread().scheduleDirect(func)
}

fun isAPI(value: Int) = Build.VERSION.SDK_INT == value

fun isAtLeastAPI(value: Int) = Build.VERSION.SDK_INT >= value

fun currentThread() = Thread.currentThread()

fun isMainThread() = Thread.currentThread() == Looper.getMainLooper().thread

fun Date.toUserDate() = ExtensionUtils.dateformat.format(this)

inline val Activity.isInMultiWindow: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInMultiWindowMode
        } else {
            false
        }
    }

fun View.showSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
//    inputMethodManager?.showSoftInput(this, 0)
    inputMethodManager?.toggleSoftInputFromWindow(windowToken, 0, 0)
//    inputMethodManager?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

fun View.hideSoftInput() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
}

infix fun Int.hasBits(value: Int): Boolean {
    return this and value == value
}


object ExtensionUtils {
    val dateformat: DateFormat = java.text.DateFormat.getDateTimeInstance()
}