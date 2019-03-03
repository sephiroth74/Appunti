package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dbflow5.query.and
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import timber.log.Timber


@Suppress("NAME_SHADOWING")
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("onReceive($intent, ${intent?.action})")

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let { context ->
                scheduleAlarms(context.applicationContext)
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun scheduleAlarms(context: Context) {
        DatabaseHelper.getEntries {
            where(
                Entry_Table.entryAlarmEnabled.eq(true)
                    .and(Entry_Table.entryAlarm.isNotNull())
            )
        }.subscribe { result, error ->
            error?.let {
                Timber.e(it)
            }

            result?.let { result ->
                for (entry in result) {
                    scheduleAlarm(context, entry)
                }
            }
        }
    }

    private fun scheduleAlarm(context: Context, entry: Entry) {
        if (entry.hasReminder()) {
            Timber.i("scheduleAlarm($entry)")
            if (!entry.isReminderExpired()) {
                Entry.addReminder(entry, context)
            } else {
                Timber.v("alarm is expired")
                Entry.addReminderAt(entry, context, System.currentTimeMillis() + (60 * 1000))
            }
        }
    }
}