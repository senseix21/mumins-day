package com.senseix21.muminsday.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.senseix21.muminsday.health.AlarmRecord
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Delivery
import com.senseix21.muminsday.planner.Plan
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import java.time.Duration
import java.time.Instant

/** How long rows are kept. The health screen reads 7 days; the rest is slack, not history to browse (INV-S8). */
val LOG_RETENTION: Duration = Duration.ofDays(30)

/** Rows planned before this go. Pure: [now] is an argument. */
fun logCutoff(now: Instant): Instant = now.minus(LOG_RETENTION)

/** What kind of alarm a row is. Stored by name; never rename a case without a migration. */
enum class AlarmKind { Adhan, Tone, Silent, PreReminder }

/**
 * What became of an alarm. Missed is not stored: it is a [Planned] row whose time has passed, which is how
 * `assessHealth` reads it. A new case here needs a reader for old rows to stay valid (unknown names are skipped).
 */
enum class Outcome {
    Planned,
    Rang,
    SilentNotification,

    /** Delivered, but the Adhan could not be started (the platform refused). A notification was posted instead. */
    RingFailed,
    ;

    companion object {
        fun of(delivery: Delivery): Outcome = when (delivery) {
            Delivery.Ring -> Rang
            Delivery.SilentNotification -> SilentNotification
        }
    }
}

/** The identity of a logged alarm: the same alarm at the same instant is the same row (INV-S4). */
data class LoggedAlarm(val salah: Salah, val kind: AlarmKind, val plannedAt: Instant) {
    companion object {
        /** Null for the replan alarm, which is not a reminder and is never logged. */
        fun of(spec: AlarmSpec): LoggedAlarm? = when (spec) {
            is AlarmSpec.PrayerAlarm -> LoggedAlarm(
                spec.salah,
                when (spec.mode) {
                    RingMode.Adhan -> AlarmKind.Adhan
                    RingMode.Tone -> AlarmKind.Tone
                    RingMode.Silent -> AlarmKind.Silent
                },
                spec.at,
            )
            is AlarmSpec.PreReminderAlarm -> LoggedAlarm(spec.salah, AlarmKind.PreReminder, spec.at)
            is AlarmSpec.Replan -> null
        }
    }
}

data class AlarmLogRow(
    val id: Long,
    val alarm: LoggedAlarm,
    val deliveredAt: Instant?,
    val outcome: Outcome,
) {
    /** What `assessHealth` consumes (INV-S6, INV-S8). */
    fun toHealthRecord(): AlarmRecord = AlarmRecord(plannedAt = alarm.plannedAt, deliveredAt = deliveredAt)
}

/** The alarm log (INV-S8). Every time comes in as an argument; nothing here reads the clock. */
class AlarmLog(private val database: Database) {

    /** Adds the plan's reminders. Ones already logged are left alone, so every replan can call this. */
    fun recordPlanned(plan: Plan) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            for (alarm in plan.alarms.mapNotNull(LoggedAlarm::of)) {
                val values = ContentValues().apply {
                    put("prayer", alarm.salah.name)
                    put("kind", alarm.kind.name)
                    put("planned_utc", alarm.plannedAt.toEpochMilli())
                    put("outcome", Outcome.Planned.name)
                }
                db.insertWithOnConflict("alarm_log", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * The alarm has arrived. If its planned row is missing (trimmed, or planned by an older build) one is inserted
     * first, so a delivery is never lost from the log (INV-S8).
     */
    fun recordDelivered(alarm: LoggedAlarm, deliveredAt: Instant, outcome: Outcome) {
        val planned = ContentValues().apply {
            put("prayer", alarm.salah.name)
            put("kind", alarm.kind.name)
            put("planned_utc", alarm.plannedAt.toEpochMilli())
            put("outcome", Outcome.Planned.name)
        }
        database.writableDatabase.insertWithOnConflict("alarm_log", null, planned, SQLiteDatabase.CONFLICT_IGNORE)
        val values = ContentValues().apply {
            put("delivered_utc", deliveredAt.toEpochMilli())
            put("outcome", outcome.name)
        }
        database.writableDatabase.update(
            "alarm_log",
            values,
            "prayer = ? AND kind = ? AND planned_utc = ?",
            arrayOf(alarm.salah.name, alarm.kind.name, alarm.plannedAt.toEpochMilli().toString()),
        )
    }

    /**
     * Forget alarms that were planned but are not yet due. Called on replan, before the new plan is recorded: a
     * reminder the user has since switched off would otherwise stay planned, pass its time, and read as missed.
     * Rows whose time has passed are kept, delivered or not, because that is the evidence.
     */
    fun deleteNotYetDue(now: Instant) {
        database.writableDatabase.delete(
            "alarm_log",
            "outcome = ? AND planned_utc > ?",
            arrayOf(Outcome.Planned.name, now.toEpochMilli().toString()),
        )
    }

    fun trim(now: Instant) {
        database.writableDatabase.delete("alarm_log", "planned_utc < ?", arrayOf(logCutoff(now).toEpochMilli().toString()))
    }

    /** Rows planned at or after [since], oldest first. A row with a name this build does not know is skipped. */
    fun rowsSince(since: Instant): List<AlarmLogRow> {
        val rows = mutableListOf<AlarmLogRow>()
        database.readableDatabase.query(
            "alarm_log",
            arrayOf("id", "prayer", "kind", "planned_utc", "delivered_utc", "outcome"),
            "planned_utc >= ?",
            arrayOf(since.toEpochMilli().toString()),
            null,
            null,
            "planned_utc ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val salah = Salah.entries.firstOrNull { it.name == cursor.getString(1) } ?: continue
                val kind = AlarmKind.entries.firstOrNull { it.name == cursor.getString(2) } ?: continue
                val outcome = Outcome.entries.firstOrNull { it.name == cursor.getString(5) } ?: continue
                rows += AlarmLogRow(
                    id = cursor.getLong(0),
                    alarm = LoggedAlarm(salah, kind, Instant.ofEpochMilli(cursor.getLong(3))),
                    deliveredAt = if (cursor.isNull(4)) null else Instant.ofEpochMilli(cursor.getLong(4)),
                    outcome = outcome,
                )
            }
        }
        return rows
    }
}
