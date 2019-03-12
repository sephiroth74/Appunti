package it.sephiroth.android.app.appunti.ext

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

fun Date.toUserDate() = ExtensionUtils.dateformat.format(this)

object ExtensionUtils {
    val dateformat: DateFormat = java.text.DateFormat.getDateTimeInstance()
}