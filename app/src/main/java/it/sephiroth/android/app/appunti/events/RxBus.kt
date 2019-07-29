package it.sephiroth.android.app.appunti.events


/**
 * Appunti
 *
 * @author Alessandro Crugnola on 2019-07-29 - 10:39
 */

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers


/**
 * the purpose of this class is to handle
 * generic events
 *
 *
 * @author Alessandro Crugnola on 2019-06-04 - 13:28
 */

object RxBus : EventBus<RxEvent>() {
    fun <T : RxEvent> listen(eventType: Class<T>): Flowable<T> =
        listen(eventType, AndroidSchedulers.mainThread())

    @Suppress("UNUSED_PARAMETER")
    fun <T : RxEvent> listen(
        eventType: Class<T>,
        scheduler: Scheduler = AndroidSchedulers.mainThread()
    ): Flowable<T> = observe(eventType)
}

open class RxEvent
