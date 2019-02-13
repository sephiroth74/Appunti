package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import timber.log.Timber


@Suppress("NAME_SHADOWING")
class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.i("onReceive($intent")

        intent?.let { intent ->
            when (intent.action) {
                ACTION_ENTRY_VIEW_REMINDER -> onEntryReminderRecived(context.applicationContext, intent)
                ACTION_ENTRY_REMOVE_REMINDER -> onEntryRemoveReminderReceived(context.applicationContext, intent)
            }
        }
    }

    private fun onEntryRemoveReminderReceived(context: Context, intent: Intent) {
        intent.data?.let { data ->
            val entryID = data.lastPathSegment?.toInt()
            entryID?.let { entryID ->

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                wakeLock.acquire(1000)

                Timber.i("entryID=$entryID")
                DatabaseHelper.getEntryById(entryID).subscribe { entry, error ->
                    error?.let {
                        Timber.e(error)
                    }

                    entry?.let { entry ->
                        DatabaseHelper.removeReminder(entry, context)
                        wakeLock.release()
                    }
                }
            }
        }
    }

    private fun onEntryReminderRecived(context: Context, intent: Intent) {
        intent.data?.let { data ->
            val entryID = data.lastPathSegment?.toInt()
            entryID?.let { entryID ->
                Timber.i("entryID=$entryID")

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                wakeLock.acquire(1000)

                DatabaseHelper.getEntryById(entryID).subscribe { entry, error ->
                    if (null != error) {
                        Timber.e(error)
                    } else if (entry != null) {
                        createNotificationChannel(context)

                        val contentIntent = Intent(context, DetailActivity::class.java).apply {
                            action = Intent.ACTION_EDIT
                            putExtra(DetailActivity.KEY_ENTRY_ID, entry.entryID)
                            putExtra(DetailActivity.KEY_REMOVE_ALARM, true)
                        }

                        val pendingIntent = TaskStackBuilder.create(context)
                                .addParentStack(DetailActivity::class.java)
                                .addNextIntent(contentIntent)
                                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

                        val builder = NotificationCompat
                                .Builder(context, ENTRY_ALARM_CHANNEL_ID)
                                .setSmallIcon(R.drawable.sharp_favorite_24)
                                .setContentTitle(entry.entryTitle ?: "")
                                .setContentText(entry.entryText ?: "")
                                .setColor(entry.getColor(context))
                                .setTicker(entry.entryTitle ?: "")
                                .setContentIntent(pendingIntent)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setStyle(NotificationCompat.BigTextStyle()
                                        .bigText(entry.entryText ?: ""))
                                .setAutoCancel(true)
                                .setDeleteIntent(Entry.getDeleteReminderPendingIntent(entry, context))

                        with(NotificationManagerCompat.from(context)) {
                            notify((System.currentTimeMillis() / 1000).toInt(), builder.build())
                        }

                        wakeLock.release()
                    }
                }

            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ENTRY_ALARM_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                enableVibration(true)
                enableLights(true)
                lightColor = Color.YELLOW
            }

            val notificationManager: NotificationManager = context
                    .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val group = NotificationChannelGroup(DEFAULT_CHANNEL_GROUP, "Default")
            notificationManager.createNotificationChannelGroup(group)

            channel.group = DEFAULT_CHANNEL_GROUP

            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        // notification
        const val ENTRY_ALARM_CHANNEL_ID = "entry_channel_id"
        const val DEFAULT_CHANNEL_GROUP = "appunti.default.channel.group"

        // intent action
        const val ACTION_ENTRY_VIEW_REMINDER = "appunti.entry.view.reminder"
        const val ACTION_ENTRY_REMOVE_REMINDER = "appunti.entry.remove.reminder"

        private const val WAKE_LOCK_TAG = "Appunti:AlarmReceiver"
    }
}