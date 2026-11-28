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
 * The backed-up user data's history (G5, G34): the deed days, then the Salah log (ADR-18), then the khatam plan
 * (ADR-24), so the backup rules did not change for either. This file is backed up, so it
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
    Migration(
        version = 3,
        statements = listOf(
            // The khatam plan (phase 15, ADR-24): at most one row, where the user has read to, counted in ayahs from 1:1.
            // It holds no row per day, so nothing here can count a day without reading (INV-C4). end_ayahs is the text's
            // own ayah count when the plan was made; marked_on and day_start keep today's portion fixed for the day.
            // Dates are ISO yyyy-MM-dd local dates of the place.
            """
            CREATE TABLE khatam (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                started_on TEXT NOT NULL,
                target_on TEXT NOT NULL,
                end_ayahs INTEGER NOT NULL,
                position INTEGER NOT NULL,
                marked_on TEXT NOT NULL,
                day_start INTEGER NOT NULL
            )
            """.trimIndent(),
        ),
    ),
)

/**
 * The Quran search database's history (ADR-23). It is built on the phone from the verified assets and holds nothing of
 * the user's, so it is never backed up (not listed in the backup rules) and can be deleted and rebuilt at any time.
 * FTS4, not FTS5: API 26's SQLite is built with FTS3 and FTS4 only (KNOWN, AOSP `external/sqlite/dist/Android.mk` at
 * android-8.0.0_r1: `-DSQLITE_ENABLE_FTS3`, `-DSQLITE_ENABLE_FTS4`, no FTS5). The `simple` tokenizer: every character
 * above 127 is part of a word, and the app has already reduced each text to space-separated words (`quran.indexWords`),
 * so the tokenizer only splits on spaces. `unicode61` is not used: whether API 26 has it was not established.
 */
val quranMigrations: List<Migration> = listOf(
    Migration(
        version = 1,
        statements = listOf(
            // content_key: the SHA-256 of every asset the rows were built from. A different key means rebuild.
            // notice: the Tanzil copyright block, which its terms ask to reproduce in every derived file.
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "CREATE TABLE surah (number INTEGER PRIMARY KEY, name TEXT NOT NULL, ayah_count INTEGER NOT NULL)",
            // text is the Uthmani ayah exactly as in the asset (INV-QT1); translation is null when none is bundled.
            """
            CREATE TABLE ayah (
                id INTEGER PRIMARY KEY,
                surah INTEGER NOT NULL,
                ayah INTEGER NOT NULL,
                text TEXT NOT NULL,
                translation TEXT,
                UNIQUE (surah, ayah)
            )
            """.trimIndent(),
            // docid = ayah.id. Index words only, never shown.
            "CREATE VIRTUAL TABLE ayah_index USING fts4(arabic, arabic_simple, translation, tokenize=simple)",
        ),
    ),
    // ADR-26: two bundled translations, the user's choice shown and searched. The database holds nothing of the user's,
    // so this step drops the version 1 tables and clears `meta`; the next open rebuilds every row from the assets.
    // (`ALTER TABLE ... DROP COLUMN` needs SQLite 3.35; API 26 has 3.18.)
    Migration(
        version = 2,
        statements = listOf(
            "DROP TABLE ayah_index",
            "DROP TABLE ayah",
            "DELETE FROM meta",
            "DELETE FROM surah",
            """
            CREATE TABLE ayah (
                id INTEGER PRIMARY KEY,
                surah INTEGER NOT NULL,
                ayah INTEGER NOT NULL,
                text TEXT NOT NULL,
                UNIQUE (surah, ayah)
            )
            """.trimIndent(),
            // docid = ayah.id. Index words of the Uthmani and Simple Clean texts only, never shown.
            "CREATE VIRTUAL TABLE arabic_index USING fts4(arabic, arabic_simple, tokenize=simple)",
            // One row per edition and ayah: text and footnotes exactly as the source has them (INV-QT1), "" for empty
            // footnotes. edition is core's Edition.key.
            """
            CREATE TABLE translation (
                id INTEGER PRIMARY KEY,
                edition TEXT NOT NULL,
                ayah_id INTEGER NOT NULL REFERENCES ayah (id),
                text TEXT NOT NULL,
                footnotes TEXT NOT NULL,
                UNIQUE (edition, ayah_id)
            )
            """.trimIndent(),
            // docid = translation.id. Index words of the translation's text (not its footnotes), never shown.
            "CREATE VIRTUAL TABLE translation_index USING fts4(text, tokenize=simple)",
        ),
    ),
)

/**
 * The hadith books' cache (ADR-28): one book's rows are written the first time it is opened after an install, from the
 * debug assets core has accepted, so a book of 7,589 hadiths opens by query instead of parsing 24 MB of JSON again. It
 * holds nothing of the user's and is never backed up; deleting it costs one rebuild per book.
 */
val hadithBookMigrations: List<Migration> = listOf(
    Migration(
        version = 1,
        statements = listOf(
            // stamp: the installed APK's size and time; a new install (and so new assets) rebuilds the book.
            "CREATE TABLE book (key TEXT PRIMARY KEY, name TEXT NOT NULL, stamp TEXT NOT NULL)",
            """
            CREATE TABLE section (
                book TEXT NOT NULL,
                id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                name TEXT NOT NULL,
                hadith_count INTEGER NOT NULL,
                PRIMARY KEY (book, id)
            )
            """.trimIndent(),
            // number and arabic_number as the source writes them; arabic and bengali exactly as the source has them,
            // null where that edition has no record with this number.
            """
            CREATE TABLE hadith (
                book TEXT NOT NULL,
                position INTEGER NOT NULL,
                number TEXT NOT NULL,
                arabic_number TEXT,
                section INTEGER NOT NULL,
                arabic TEXT,
                bengali TEXT,
                reference_book INTEGER NOT NULL,
                reference_hadith INTEGER NOT NULL,
                PRIMARY KEY (book, position),
                UNIQUE (book, number)
            )
            """.trimIndent(),
            "CREATE INDEX hadith_by_section ON hadith (book, section, position)",
            // Each grade in the source's order (place), with the grader's name and the grade's words unchanged.
            """
            CREATE TABLE grade (
                book TEXT NOT NULL,
                position INTEGER NOT NULL,
                place INTEGER NOT NULL,
                name TEXT NOT NULL,
                grade TEXT NOT NULL,
                PRIMARY KEY (book, position, place)
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
