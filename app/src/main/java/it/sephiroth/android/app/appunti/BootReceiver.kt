package it.sephiroth.android.app.appunti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("onReceive($intent, ${intent?.action})")
    }
}