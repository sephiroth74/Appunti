package it.sephiroth.android.app.appunti.events


/**
 * Appunti
 *
 * @author Alessandro Crugnola on 2019-07-29 - 10:38
 */

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import timber.log.Timber

abstract class EventBus<T> {

    private val subject = PublishSubject.create<T>().toSerialized()

    fun <E : T> send(event: E) {
        Timber.v("send: $event")
        subject.onNext(event)
    }

    fun <E : T> observe(eventClass: Class<E>): Flowable<E> {
        return observe(eventClass, AndroidSchedulers.mainThread())
    }

    @JvmOverloads
    fun observe(observeOnScheduler: Scheduler = AndroidSchedulers.mainThread()): Flowable<T> {
        return subject
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeOn(Schedulers.computation())
            .observeOn(observeOnScheduler)
    }

    fun <E : T> observe(eventClass: Class<E>, observeOnScheduler: Scheduler): Flowable<E> {
        //pass only events of specified type, filter all other
        return subject.ofType(eventClass)
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeOn(Schedulers.computation())
            .observeOn(observeOnScheduler)
    }
}