package com.senseix21.muminsday.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One phone's alarm history. Never backed up: it describes this phone's alarms and no other's (G34, INV-S8). */
const val ALARM_LOG_FILE: String = "alarm-log.db"

/** The days a card was shown and done. Backed up, so a new phone keeps them (F4#4, G34). */
const val DEED_DAYS_FILE: String = "deed-days.db"

/** The one file that held both tables before G34. Nothing reads it any more; [Stores] deletes it. */
const val LEGACY_DATABASE_FILE: String = "mumins-day.db"

/**
 * A SQLite file with its own numbered history (ADR-5). All its SQL is in [history]; this only runs it.
 *
 * Write-ahead logging is off: with a rollback journal the main file is complete between transactions, and backup copies
 * only the main file. A log in a side file could be left out of a backup, so a restored file would be missing rows.
 */
class Database(context: Context, file: String, private val history: List<Migration>) :
    SQLiteOpenHelper(context, file, null, latestVersion(history)) {

    init {
        setWriteAheadLoggingEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) = run(db, statementsBetween(0, latestVersion(history), history))

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        run(db, statementsBetween(oldVersion, newVersion, history))

    // The helper wraps both callbacks in one transaction, so a failed step leaves the old schema in place.
    private fun run(db: SQLiteDatabase, statements: List<String>) = statements.forEach(db::execSQL)
}
