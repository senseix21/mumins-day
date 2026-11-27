package com.senseix21.muminsday.store

import android.content.ContentValues
import com.senseix21.muminsday.deed.DayCard
import com.senseix21.muminsday.deed.DeedId
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * One row per local day a card was shown (G5, F4#4). Keyed by date, so moving the phone's clock back finds the row that
 * is already there. Holds no count of any kind (INV-C4).
 */
class DeedDays(private val database: Database) {

    fun get(date: LocalDate): DayCard? = query("local_date = ?", arrayOf(date.toString())).firstOrNull()

    /** Replaces the day's row. */
    fun save(card: DayCard) {
        val values = ContentValues().apply {
            put("local_date", card.date.toString())
            put("deed_id", card.deedId.value)
            put("content_version", card.contentVersion)
            put("swapped", if (card.swapped) 1 else 0)
            val doneAt = card.doneAt
            if (doneAt == null) putNull("done_at") else put("done_at", doneAt.toEpochMilli())
        }
        database.writableDatabase.replaceOrThrow("deed_day", null, values)
    }

    /** The days in [from]..[to] (both included) marked done, for the week dots. */
    fun doneDates(from: LocalDate, to: LocalDate): Set<LocalDate> =
        query("local_date >= ? AND local_date <= ? AND done_at IS NOT NULL", arrayOf(from.toString(), to.toString()))
            .map { it.date }.toSet()

    // ISO dates sort as text, which is what makes the range query above right.
    private fun query(selection: String, args: Array<String>): List<DayCard> {
        val cards = mutableListOf<DayCard>()
        database.readableDatabase.query(
            "deed_day",
            arrayOf("local_date", "deed_id", "content_version", "swapped", "done_at"),
            selection,
            args,
            null,
            null,
            "local_date ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // A row this build cannot read is skipped rather than shown wrong.
                val date = try {
                    LocalDate.parse(cursor.getString(0))
                } catch (_: DateTimeParseException) {
                    continue
                }
                val deedId = DeedId.of(cursor.getString(1)) ?: continue
                cards += DayCard(
                    date = date,
                    deedId = deedId,
                    contentVersion = cursor.getInt(2),
                    swapped = cursor.getInt(3) != 0,
                    doneAt = if (cursor.isNull(4)) null else Instant.ofEpochMilli(cursor.getLong(4)),
                )
            }
        }
        return cards
    }
}
