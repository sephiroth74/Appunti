package it.sephiroth.android.app.appunti.services

import android.content.Context
import android.location.Address
import com.google.android.gms.location.LocationRequest
import com.patloew.rxlocation.RxLocation
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.library.kotlin_extensions.util.Singleton
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LocationRepository private constructor(val context: Context) {
    private var rxLocation = RxLocation(context)

    fun getCurrentLocation(maxTime: Long, timeUnit: TimeUnit): Observable<Address> {
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
            .setInterval(2000)
            .setMaxWaitTime(timeUnit.toMillis(maxTime))
            .setNumUpdates(1)

        return rxLocation.location()
            .updates(locationRequest)
            .subscribeOn(Schedulers.computation())
            .flatMap { location ->
                Timber.v("location = $location")
                rxLocation.geocoding().fromLocation(location).toObservable()
            }
    }

    companion object :
        Singleton<LocationRepository, Context>(creator = { context -> LocationRepository(context) })
}