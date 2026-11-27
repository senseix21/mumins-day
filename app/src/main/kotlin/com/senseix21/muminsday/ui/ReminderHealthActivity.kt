package com.senseix21.muminsday.ui

import android.os.Bundle
import com.senseix21.muminsday.R
import com.senseix21.muminsday.health.HEALTH_LOOKBACK
import com.senseix21.muminsday.store.AlarmKind
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * INV-S8: the last seven days of reminders, each with the time it was planned for and the time it arrived, newest
 * first. This is how a user (and a bug report) can tell whether the phone kept its promise. It states what the phone's
 * alarms did and never counts anything against the user (INV-C4).
 */
class ReminderHealthActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texts = Texts(this, language)
        val now = Instant.now()
        val zone = Stores.settings(this).read().place.zone
        // Rows still in the future are plans, not history.
        val rows = Stores.alarmLog(this).rowsSince(now.minus(HEALTH_LOOKBACK)).filter { it.alarm.plannedAt <= now }.reversed()

        val page = column()
        page.addView(title(getString(R.string.health_log_title)))
        page.addView(body(getString(R.string.health_log_help), muted = true))
        if (rows.isEmpty()) page.addView(body(getString(R.string.health_log_empty)))

        var day: String? = null
        for (row in rows) {
            val date = texts.date(row.alarm.plannedAt, zone)
            if (date != day) {
                day = date
                page.addView(heading(date).apply { textSize = 18f })
            }
            val kind = when (row.alarm.kind) {
                AlarmKind.Adhan -> getString(R.string.mode_adhan)
                AlarmKind.Tone -> getString(R.string.mode_tone)
                AlarmKind.Silent -> getString(R.string.mode_silent)
                AlarmKind.PreReminder -> getString(R.string.kind_pre_reminder)
            }
            val planned = texts.time(row.alarm.plannedAt, zone)
            val times = row.deliveredAt?.let { getString(R.string.log_planned_actual, planned, texts.time(it, zone)) }
                ?: getString(R.string.log_planned_only, planned)
            val outcome = when (val status = logStatus(row, now)) {
                LogStatus.OnTime -> getString(R.string.status_on_time)
                is LogStatus.Late -> getString(R.string.status_late, texts.minutes(status.minutes))
                LogStatus.NotArrived -> getString(R.string.status_not_arrived)
                LogStatus.Pending -> getString(R.string.status_pending)
                LogStatus.Notice -> getString(R.string.status_notice)
                LogStatus.RingFailed -> getString(R.string.status_ring_failed)
            }
            page.addView(
                body("${texts.salah(row.alarm.salah)}, $kind\n$times\n$outcome").apply {
                    isFocusable = true
                    minimumHeight = dp(MIN_TARGET_DP)
                },
            )
        }
        show(page)
    }
}
