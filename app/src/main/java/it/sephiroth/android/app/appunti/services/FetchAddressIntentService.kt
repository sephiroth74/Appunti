package it.sephiroth.android.app.appunti.services

import android.app.IntentService
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.ResultReceiver
import timber.log.Timber
import java.io.IOException
import java.util.*

class FetchAddressIntentService : IntentService("fetch-address") {


    override fun onHandleIntent(intent: Intent?) {
        Timber.i("onHandleIntent($intent)")
        intent ?: return

        var receiver: android.os.ResultReceiver? = null
        var errorMessage = ""
        val geocoder = Geocoder(this, Locale.getDefault())

        Timber.v("geocoder = $geocoder")

        // Get the location passed to this service through an extra.
        val location: Location
        try {
            receiver = intent.getParcelableExtra(Constants.RECEIVER)
            location = intent.getParcelableExtra(Constants.LOCATION_DATA_EXTRA)
        } catch (t: Throwable) {
            t.printStackTrace()
            deliverResultToReceiver(receiver, Constants.FAILURE_RESULT, t.message ?: "Invalid parameters")
            return
        }

        var addresses: List<Address> = emptyList()

        Timber.v("location = $location")

        try {
            addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                // In this sample, we get just a single address.
                1
            )
        } catch (ioException: IOException) {
            Timber.e(ioException)
            errorMessage = "Service not available"
        } catch (illegalArgumentException: IllegalArgumentException) {
            // Catch invalid latitude or longitude values.
            errorMessage = "Invalid latitude or longitude"
            Timber.e(
                illegalArgumentException,
                "$errorMessage. Latitude = $location.latitude , Longitude =  $location.longitude"
            )
        }

        // Handle case where no address was found.
        Timber.v("addresses: $addresses")
        if (addresses.isEmpty()) {
            if (errorMessage.isEmpty()) {
                errorMessage = "No address found"
                Timber.e("errorMessage = $errorMessage")
            }
            deliverResultToReceiver(receiver, Constants.FAILURE_RESULT, errorMessage)
        } else {
            val address = addresses[0]
            // Fetch the address lines using getAddressLine,
            // join them, and send them to the thread.
            val addressFragments = with(address) {
                (0..maxAddressLineIndex).map { getAddressLine(it) }
            }
            deliverResultToReceiver(
                receiver,
                Constants.SUCCESS_RESULT,
                addressFragments.joinToString(separator = "\n")
            )
        }
    }

    private fun deliverResultToReceiver(
        receiver: ResultReceiver?,
        resultCode: Int,
        message: String
    ) {
        Timber.i("deliverResultToReceiver($resultCode, $message)")
        val bundle = Bundle().apply { putString(Constants.RESULT_DATA_KEY, message) }
        receiver?.send(resultCode, bundle)
    }

    object Constants {
        const val SUCCESS_RESULT = 0
        const val FAILURE_RESULT = 1
        const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationaddress"
        const val RECEIVER = "$PACKAGE_NAME.RECEIVER"
        const val RESULT_DATA_KEY = "${PACKAGE_NAME}.RESULT_DATA_KEY"
        const val LOCATION_DATA_EXTRA = "${PACKAGE_NAME}.LOCATION_DATA_EXTRA"
    }
}