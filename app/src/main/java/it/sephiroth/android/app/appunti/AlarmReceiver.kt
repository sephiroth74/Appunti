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
import timber.log.Timber


class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.i("onReceive($intent")

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Appunti:AlarmReceiver")
        wl.acquire(2000)

        val entryID = 3

        DatabaseHelper.getEntryById(entryID).subscribe { entry, error ->
            entry?.let {

                createNotificationChannel(context)

                val myIntent = Intent(context, DetailActivity::class.java).apply {
                    action = Intent.ACTION_EDIT
                    putExtra("entryID", entry.entryID)
                }

                val pendingIntent = TaskStackBuilder.create(context)
                        .addParentStack(DetailActivity::class.java)
                        .addNextIntent(myIntent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

//                val pendingIntent = PendingIntent.getActivity(context, 0, myIntent, 0)

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

                with(NotificationManagerCompat.from(context)) {
                    notify((System.currentTimeMillis() / 1000).toInt(), builder.build())
                }

            }
        }

        wl.release()
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
        const val ENTRY_ALARM_CHANNEL_ID = "entry_channel_id"
        const val DEFAULT_CHANNEL_GROUP = "appunti.default.channel.group"
    }
}