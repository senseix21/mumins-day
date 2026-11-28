package com.senseix21.muminsday.store

/** One step of a database's history. [version] is the `user_version` the database has after [statements] ran. */
class Migration(val version: Int, val statements: List<String>)

/**
 * The alarm log's history, in order. Plain strings and no Android type, so a JVM test can check the list (numbering,
 * no gaps) even though only a phone can run the SQL (ADR-5). The log has a database file of its own (G34): backup rules
 * cannot split a file by table, so the log is left out of backup while the deed days stay in.
 *
 * Never edit a released entry: a phone that already ran it will not run it again. Add the next number instead.
 */
val alarmLogMigrations: List<Migration> = listOf(
    Migration(
        version = 1,
        statements = listOf(
            // One row per planned alarm. The unique key makes "record the plan" safe to repeat on every replan
            // (INV-S4): the same alarm at the same instant is the same row. Times are epoch milliseconds.
            """
            CREATE TABLE alarm_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prayer TEXT NOT NULL,
                kind TEXT NOT NULL,
                planned_utc INTEGER NOT NULL,
                delivered_utc INTEGER,
                outcome TEXT NOT NULL,
                UNIQUE (prayer, kind, planned_utc)
            )
            """.trimIndent(),
            // Trimming and the health window both filter on planned_utc.
            "CREATE INDEX alarm_log_planned_utc ON alarm_log (planned_utc)",
        ),
    ),
)

/**
 * The backed-up user data's history (G5, G34): the deed days, then the Salah log (ADR-18). This file is backed up, so it
 * holds nothing about one phone's alarms. Numbered on its own: version 1 here has nothing to do with version 1 of the
 * alarm log.
 */
val deedMigrations: List<Migration> = listOf(
    Migration(
        version = 1,
        statements = listOf(
            // One row per local day a card was shown (G5). local_date is ISO yyyy-MM-dd; done_at is epoch milliseconds.
            """
            CREATE TABLE deed_day (
                local_date TEXT PRIMARY KEY,
                deed_id TEXT NOT NULL,
                content_version INTEGER NOT NULL,
                swapped INTEGER NOT NULL,
                done_at INTEGER
            )
            """.trimIndent(),
        ),
    ),
    Migration(
        version = 2,
        statements = listOf(
            // The Salah tracker (INV-C5, ADR-18): one row per prayer the user marked, on the local date it was prayed for.
            // Only marked prayers have rows, so the table cannot hold anything about prayers not marked. prayed_at is
            // epoch milliseconds, kept for the user's own record and read by no screen.
            """
            CREATE TABLE salah_log (
                local_date TEXT NOT NULL,
                salah TEXT NOT NULL,
                prayed_at INTEGER NOT NULL,
                PRIMARY KEY (local_date, salah)
            )
            """.trimIndent(),
        ),
    ),
)

/** The version a fresh database with this [history] is created at. */
fun latestVersion(history: List<Migration>): Int = history.last().version

/**
 * The SQL that takes a database from [from] to [to]. A new install is `from = 0`. Callers pass `from <= to`;
 * SQLiteOpenHelper handles a downgrade before it gets here.
 */
fun statementsBetween(from: Int, to: Int, history: List<Migration>): List<String> {
    require(from in 0..to) { "cannot migrate from $from to $to" }
    return history.filter { it.version in (from + 1)..to }.flatMap { it.statements }
}
