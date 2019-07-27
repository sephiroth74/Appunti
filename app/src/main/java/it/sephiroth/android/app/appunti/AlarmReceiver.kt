package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.getSummary
import it.sephiroth.android.app.appunti.utils.IntentUtils
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber


@Suppress("NAME_SHADOWING")
class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.i("onReceive($intent")

        intent?.let { intent ->
            when (intent.action) {
                ACTION_ENTRY_VIEW_REMINDER -> onEntryReminderRecived(
                    context.applicationContext,
                    intent
                )
                ACTION_ENTRY_REMOVE_REMINDER -> onEntryRemoveReminderReceived(
                    context.applicationContext,
                    intent
                )
                ACTION_ENTRY_POSTPONE_REMINDER -> onEntryPostponeReminderReceived(
                    context.applicationContext,
                    intent
                )
            }
        }
    }

    private fun onEntryPostponeReminderReceived(context: Context, intent: Intent) {
        Timber.i("onEntryPostpontReminderReceived($intent")
        intent.data?.let { data ->
            data.lastPathSegment?.toLong()?.let { entryID ->

                getNotificationManager(context).cancel(NOTIFICATION_ALARM_BASE_ID + entryID.toInt())

                DatabaseHelper
                    .getEntryById(entryID)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { entry, error ->
                        entry?.let { entry ->
                            val instant = Instant.now().plus(POSTPONE_MINUTES, ChronoUnit.MINUTES)
                            if (DatabaseHelper.addReminder(entry, instant, context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.alarm_postponed, POSTPONE_MINUTES),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
            }
        }
    }

    private fun onEntryRemoveReminderReceived(context: Context, intent: Intent) {
        Timber.i("onEntryRemoveReminderReceived($intent)")
        intent.data?.let { data ->
            val entryID = data.lastPathSegment?.toLong()
            entryID?.let { entryID ->

                getNotificationManager(context).cancel(NOTIFICATION_ALARM_BASE_ID + entryID.toInt())

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock =
                    powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
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
            val entryID = data.lastPathSegment?.toLong()
            entryID?.let { entryID ->
                Timber.i("entryID=$entryID")

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock =
                    powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                wakeLock.acquire(1000)

                DatabaseHelper.getEntryById(entryID).subscribe { entry, error ->
                    if (null != error) {
                        Timber.e(error)
                    } else if (entry != null) {
                        createNotificationChannel(context)

                        val contentIntent =
                            IntentUtils.createViewEntryIntent(context, entry.entryID, true)

                        val pendingIntent = TaskStackBuilder.create(context)
                            .addParentStack(DetailActivity::class.java)
                            .addNextIntent(contentIntent)
                            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

                        Timber.v("entry = $entry")
                        Timber.v("entryType = ${entry.entryType}")

                        val builder = NotificationCompat
                            .Builder(context, ENTRY_ALARM_CHANNEL_ID)
                            .setLargeIcon(
                                BitmapFactory.decodeResource(
                                    context.resources,
                                    R.mipmap.ic_launcher_round
                                )
                            )
                            .setSmallIcon(R.drawable.sharp_alarm_24)
                            .setContentTitle(entry.entryTitle)
                            .setContentText(
                                entry.getSummary(
                                    context,
                                    context.resources.getDimension(R.dimen.text_size_body_1_material),
                                    100,
                                    1
                                )
                            )
                            .setColor(entry.getColor(context))
                            .setTicker(entry.entryTitle)
                            .setContentIntent(pendingIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setSound(
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                                AudioManager.STREAM_ALARM
                            )
                            .setStyle(
                                NotificationCompat.BigTextStyle()
                                    .bigText(
                                        entry.getSummary(
                                            context,
                                            context.resources.getDimension(R.dimen.text_size_body_1_material),
                                            200,
                                            5
                                        )
                                    )
                            )
                            .setAutoCancel(true)
                            .addAction(
                                NotificationCompat.Action(
                                    0,
                                    context.getString(R.string.snooze),
                                    Entry.getDeleteReminderPendingIntent(entry, context)
                                )
                            )
                            .addAction(
                                NotificationCompat.Action(
                                    0,
                                    context.getString(R.string.postpone),
                                    Entry.getPostponeReminderPendingIntent(entry, context)
                                )
                            )
                            .setDeleteIntent(Entry.getDeleteReminderPendingIntent(entry, context))

                        with(NotificationManagerCompat.from(context)) {
                            notify(NOTIFICATION_ALARM_BASE_ID + entryID.toInt(), builder.build())
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

    private fun getNotificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        // notification
        const val ENTRY_ALARM_CHANNEL_ID = "entry_channel_id"
        const val DEFAULT_CHANNEL_GROUP = "appunti.default.channel.group"

        // intent action
        const val ACTION_ENTRY_VIEW_REMINDER = "appunti.entry.view.reminder"
        const val ACTION_ENTRY_REMOVE_REMINDER = "appunti.entry.remove.reminder"
        const val ACTION_ENTRY_POSTPONE_REMINDER = "appunti.entry.postpone.reminder"

        private const val WAKE_LOCK_TAG = "Appunti:AlarmReceiver"

        // alarm postpone in minutes
        private const val POSTPONE_MINUTES = 15L

        private const val NOTIFICATION_ALARM_BASE_ID = 100
    }
}