package com.senseix21.muminsday.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.tracker.PrayerMark
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The prayers the user marked (ADR-18). A row exists only for a marked prayer, so nothing here can be turned into a
 * count of prayers not marked (INV-C5). Read by the tracker screen and nothing else.
 */
class SalahLog(private val database: Database) {

    /** Idempotent: a second mark of the same prayer on the same date keeps the first instant. */
    fun mark(date: LocalDate, salah: Salah, at: Instant) {
        val values = ContentValues().apply {
            put("local_date", date.toString())
            put("salah", salah.name)
            put("prayed_at", at.toEpochMilli())
        }
        database.writableDatabase.insertWithOnConflict("salah_log", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** The user took a mark back. Nothing remains of it. */
    fun unmark(date: LocalDate, salah: Salah) {
        database.writableDatabase.delete("salah_log", "local_date = ? AND salah = ?", arrayOf(date.toString(), salah.name))
    }

    /** The marks on the dates [from]..[to], both included. ISO dates sort as text, which makes the range query right. */
    fun marks(from: LocalDate, to: LocalDate): Set<PrayerMark> {
        val found = mutableSetOf<PrayerMark>()
        database.readableDatabase.query(
            "salah_log",
            arrayOf("local_date", "salah"),
            "local_date >= ? AND local_date <= ?",
            arrayOf(from.toString(), to.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // A row this build cannot read is skipped rather than shown wrong.
                val date = try {
                    LocalDate.parse(cursor.getString(0))
                } catch (_: DateTimeParseException) {
                    continue
                }
                val salah = Salah.entries.firstOrNull { it.name == cursor.getString(1) } ?: continue
                found += PrayerMark(date, salah)
            }
        }
        return found
    }
}
