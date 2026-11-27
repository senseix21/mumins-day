package com.senseix21.muminsday.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.senseix21.muminsday.MainActivity
import com.senseix21.muminsday.R

/**
 * The only place a notification is built or a channel defined. Both channels are low importance: the Adhan is audio
 * from the playback service, not from the notification, so a notification never makes a sound of its own.
 */
object Notifications {
    private const val PLAYBACK_CHANNEL = "playback"
    private const val NOTICE_CHANNEL = "notice"

    const val PLAYBACK_NOTIFICATION_ID: Int = 1

    /** Notices are numbered from here, one per alarm id, so two prayers' notices do not replace each other. */
    private const val NOTICE_BASE_ID = 100

    private fun ensureChannels(context: Context) {
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(PLAYBACK_CHANNEL, context.getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(NOTICE_CHANNEL, context.getString(R.string.channel_notice), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** The foreground-service notification while audio plays, with a Stop action (G6). */
    fun playing(context: Context, stop: PendingIntent): Notification {
        ensureChannels(context)
        val stopAction = Notification.Action.Builder(null, context.getString(R.string.action_stop), stop).build()
        return Notification.Builder(context, PLAYBACK_CHANNEL)
            .setSmallIcon(R.drawable.ic_placeholder)
            .setContentTitle(context.getString(R.string.notification_playing))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(stopAction)
            .build()
    }

    /** A reminder that makes no sound: silent mode, a pre-reminder, a late alarm, a call in progress, a failed start. */
    fun post(context: Context, notice: Notice, requestCode: Int) {
        ensureChannels(context)
        val text = when (notice) {
            Notice.Reminder -> R.string.notification_reminder
            Notice.Missed -> R.string.notification_late
        }
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        manager.notify(
            NOTICE_BASE_ID + requestCode,
            Notification.Builder(context, NOTICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_placeholder)
                .setContentTitle(context.getString(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build(),
        )
    }

    enum class Notice { Reminder, Missed }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
