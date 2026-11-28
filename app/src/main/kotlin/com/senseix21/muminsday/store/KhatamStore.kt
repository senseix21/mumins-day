package com.senseix21.muminsday.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.senseix21.muminsday.khatam.Khatam
import com.senseix21.muminsday.khatam.KhatamRestore
import com.senseix21.muminsday.khatam.restoreKhatam
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** What is stored: a plan, or none. A row this build cannot read counts as none and is replaced by the next plan. */
sealed interface StoredKhatam {
    data class Plan(val khatam: Khatam) : StoredKhatam

    data object NoPlan : StoredKhatam
}

/**
 * The khatam plan (ADR-24): one row in the backed-up `deed-days.db`, migration 3. Read by the khatam screen and, for
 * the one daily nudge only, by the nudge's delivery. Nothing else reads it.
 */
class KhatamStore(private val database: Database) {

    fun read(): StoredKhatam = database.readableDatabase.query(
        "khatam",
        arrayOf("started_on", "target_on", "end_ayahs", "position", "marked_on", "day_start"),
        "id = 1",
        null,
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return StoredKhatam.NoPlan
        val dates = try {
            listOf(0, 1, 4).map { LocalDate.parse(cursor.getString(it)) }
        } catch (_: DateTimeParseException) {
            return StoredKhatam.NoPlan
        }
        when (val restored = restoreKhatam(dates[0], dates[1], cursor.getInt(2), cursor.getInt(3), dates[2], cursor.getInt(5))) {
            is KhatamRestore.Restored -> StoredKhatam.Plan(restored.khatam)
            KhatamRestore.Unreadable -> StoredKhatam.NoPlan
        }
    }

    fun write(khatam: Khatam) {
        val values = ContentValues().apply {
            put("id", 1)
            put("started_on", khatam.startedOn.toString())
            put("target_on", khatam.target.toString())
            put("end_ayahs", khatam.end.value)
            put("position", khatam.position.value)
            put("marked_on", khatam.markedOn.toString())
            put("day_start", khatam.dayStart.value)
        }
        database.writableDatabase.insertWithOnConflict("khatam", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** The user ended the plan. Nothing remains of it. */
    fun delete() {
        database.writableDatabase.delete("khatam", "id = 1", null)
    }
}
