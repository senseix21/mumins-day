package com.senseix21.muminsday.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.senseix21.muminsday.MainActivity
import com.senseix21.muminsday.R
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.ui.DeedActivity
import java.time.Instant

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

    /** Above every notice id (100 plus a notice code of at most 60). */
    private const val NUDGE_NOTIFICATION_ID = 200

    /** Request codes of the "Prayed" actions, clear of the notice and nudge ids. */
    private const val PRAYED_REQUEST_BASE = 2000

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

    /**
     * A reminder that makes no sound: silent mode, a pre-reminder, a late alarm, a call in progress, a failed start.
     * [prayed] adds the tracker's one "Prayed" action (INV-C5): a tap the user chooses to make, never a prompt.
     */
    fun post(context: Context, notice: Notice, requestCode: Int, prayed: PrayedTarget? = null) {
        ensureChannels(context)
        val text = when (notice) {
            Notice.Reminder -> R.string.notification_reminder
            Notice.Missed -> R.string.notification_late
        }
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val builder = Notification.Builder(context, NOTICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_placeholder)
            .setContentTitle(context.getString(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        if (prayed != null) {
            val notificationId = NOTICE_BASE_ID + requestCode
            val intent = Intent(context, PrayedReceiver::class.java)
                .setAction(ACTION_PRAYED)
                .putExtra(EXTRA_PRAYED_SALAH, prayed.salah.name)
                .putExtra(EXTRA_PRAYED_AT, prayed.prayerAt.toEpochMilli())
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            // One request code per notice, so two prayers' actions never share a PendingIntent.
            val operation = PendingIntent.getBroadcast(
                context,
                PRAYED_REQUEST_BASE + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.tracker_prayed_action), operation).build())
        }
        manager.notify(NOTICE_BASE_ID + requestCode, builder.build())
    }

    /** Takes a notice down once the user has answered it. */
    fun dismiss(context: Context, notificationId: Int) {
        checkNotNull(context.getSystemService(NotificationManager::class.java)).cancel(notificationId)
    }

    /**
     * The daily deed nudge (F4): one quiet notification whose tap opens the deed card. A fixed id, so even a second
     * call could only replace it, never stack. It carries no count and no word about days missed (INV-C4).
     */
    fun postNudge(context: Context) {
        ensureChannels(context)
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        manager.notify(
            NUDGE_NOTIFICATION_ID,
            Notification.Builder(context, NOTICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_placeholder)
                .setContentTitle(context.getString(R.string.notification_nudge))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openDeed(context))
                .build(),
        )
    }

    enum class Notice { Reminder, Missed }

    /** The prayer a notification is about, for its "Prayed" action. [prayerAt] decides which date the mark belongs to. */
    class PrayedTarget(val salah: Salah, val prayerAt: Instant)

    private fun openDeed(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, DeedActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
