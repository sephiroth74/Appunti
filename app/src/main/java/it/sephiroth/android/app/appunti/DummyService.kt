package it.sephiroth.android.app.appunti

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hunter.library.debug.HunterDebugClass
import it.sephiroth.android.app.appunti.events.RxBus
import it.sephiroth.android.app.appunti.events.TaskRemovedEvent


/**
 * Appunti
 *
 * @author Alessandro Crugnola on 12.02.20 - 09:46
 */

@HunterDebugClass
class DummyService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        RxBus.send(TaskRemovedEvent())
        super.onTaskRemoved(rootIntent)
    }

}