package com.senseix21.muminsday.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.alarm.LOG_TAG
import com.senseix21.muminsday.hadithbooks.Book
import com.senseix21.muminsday.hadithbooks.BookCheck
import com.senseix21.muminsday.hadithbooks.Grade
import com.senseix21.muminsday.hadithbooks.Hadith
import com.senseix21.muminsday.hadithbooks.HadithBook
import com.senseix21.muminsday.hadithbooks.HadithNumber
import com.senseix21.muminsday.hadithbooks.Reference
import com.senseix21.muminsday.hadithbooks.Section
import com.senseix21.muminsday.hadithbooks.SectionId
import com.senseix21.muminsday.hadithbooks.checkHadithBook
import java.io.File
import java.io.FileNotFoundException

/** What opening one hadith book gave (ADR-28). Only [Ready] shows any of it. */
sealed interface HadithBookOpen {
    data class Ready(val reader: HadithBookReader) : HadithBookOpen

    /** The files are there but core refused them; the build's gate normally stops this reaching a phone. */
    data class Refused(val reasons: List<String>) : HadithBookOpen

    /** A release, or a debug build made where `content/local/hadith/` does not exist. */
    data object NotInThisBuild : HadithBookOpen
}

/** The asset folder the debug build stages `content/local/hadith/editions/` into (`stageHadithBooks`). */
private const val HADITH_ASSETS: String = "hadith-books"

/** True when this build carries the books: a debug build made where the local files exist. */
fun hadithBooksInBuild(context: Context): Boolean {
    if (!BuildConfig.DEBUG) return false
    val staged = context.assets.list(HADITH_ASSETS).orEmpty().toSet()
    return HadithBook.entries.all { it.bengaliFile in staged && it.arabicFile in staged }
}

/** One reader per book per process, so the book's screen and its sections' screens share it. */
private val opened = mutableMapOf<HadithBook, HadithBookOpen>()

/**
 * Opens [book]: the first time after an install core reads both editions from the debug assets (the check the build's
 * gate ran) and the rows are written to the cache in one transaction; after that it is a query. A release build never
 * packages the files (ADR-28, T-BUILD-7) and, should one ever carry them, shows nothing (INV-C1, INV-C6). Call off the
 * main thread.
 */
fun openHadithBook(context: Context, book: HadithBook): HadithBookOpen = synchronized(HadithBookOpen::class) {
    opened[book] ?: open(context, book).also { opened[book] = it }
}

private fun open(context: Context, book: HadithBook): HadithBookOpen {
    if (!hadithBooksInBuild(context)) return HadithBookOpen.NotInThisBuild
    val started = SystemClock.elapsedRealtime()
    val database = Stores.hadithBooksDatabase(context).writableDatabase
    val stamp = installStamp(context)
    val built = database.rawQuery("SELECT stamp FROM book WHERE key = ?", arrayOf(book.key)).use { if (it.moveToFirst()) it.getString(0) else null }
    val rebuilt = built != stamp
    if (rebuilt) {
        val texts = try {
            listOf(book.bengaliFile, book.arabicFile).map { name -> context.assets.open("$HADITH_ASSETS/$name").use { it.readBytes().toString(Charsets.UTF_8) } }
        } catch (_: FileNotFoundException) {
            return HadithBookOpen.NotInThisBuild
        }
        when (val check = checkHadithBook(book, texts[0], texts[1])) {
            is BookCheck.Refused -> return HadithBookOpen.Refused(check.reasons)
            is BookCheck.Accepted -> write(database, check.book, stamp)?.let { return HadithBookOpen.Refused(listOf(it)) }
        }
    }
    val reader = HadithBookReader(database, book)
    // A measurement for the developer's own logcat (ADR-28: how long a book takes to open); nothing leaves the phone.
    Log.i(LOG_TAG, "hadith books: ${book.key} opened in ${SystemClock.elapsedRealtime() - started} ms, cache ${if (rebuilt) "built" else "reused"}")
    return HadithBookOpen.Ready(reader)
}

/** Changes with every install, and so with every change of the assets: the APK's size and modification time. */
private fun installStamp(context: Context): String = File(context.applicationInfo.sourceDir).let { "${it.length()}:${it.lastModified()}" }

/**
 * Replaces [book]'s rows in one transaction and counts them back; the stamp is written last, so an interrupted build is
 * done again on the next open. Returns null on success, or what went wrong.
 */
private fun write(database: SQLiteDatabase, book: Book, stamp: String): String? {
    val key = book.book.key
    database.beginTransaction()
    try {
        for (table in listOf("book", "section", "hadith", "grade")) {
            database.execSQL("DELETE FROM $table WHERE ${if (table == "book") "key" else "book"} = ?", arrayOf(key))
        }
        val section = database.compileStatement("INSERT INTO section (book, id, position, name, hadith_count) VALUES (?, ?, ?, ?, ?)")
        book.sections.forEachIndexed { at, it ->
            section.clearBindings()
            section.bindString(1, key)
            section.bindLong(2, it.id.value.toLong())
            section.bindLong(3, at.toLong())
            section.bindString(4, it.name)
            section.bindLong(5, it.hadithCount.toLong())
            section.executeInsert()
        }
        val row = database.compileStatement(
            "INSERT INTO hadith (book, position, number, arabic_number, section, arabic, bengali, reference_book, reference_hadith) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val grade = database.compileStatement("INSERT INTO grade (book, position, place, name, grade) VALUES (?, ?, ?, ?, ?)")
        book.hadiths.forEachIndexed { at, hadith ->
            row.clearBindings()
            row.bindString(1, key)
            row.bindLong(2, at.toLong())
            row.bindString(3, hadith.number.written)
            hadith.arabicNumber?.let { row.bindString(4, it) } ?: row.bindNull(4)
            row.bindLong(5, hadith.section.value.toLong())
            hadith.arabic?.let { row.bindString(6, it) } ?: row.bindNull(6)
            hadith.bengali?.let { row.bindString(7, it) } ?: row.bindNull(7)
            row.bindLong(8, hadith.reference.book.toLong())
            row.bindLong(9, hadith.reference.hadith.toLong())
            row.executeInsert()
            hadith.grades.forEachIndexed { place, it ->
                grade.clearBindings()
                grade.bindString(1, key)
                grade.bindLong(2, at.toLong())
                grade.bindLong(3, place.toLong())
                grade.bindString(4, it.name)
                grade.bindString(5, it.grade)
                grade.executeInsert()
            }
        }
        val stored = database.rawQuery("SELECT COUNT(*) FROM hadith WHERE book = ?", arrayOf(key)).use { it.moveToFirst(); it.getInt(0) }
        if (stored != book.hadiths.size) return "the cache holds $stored hadiths of ${book.hadiths.size}"
        database.execSQL("INSERT INTO book (key, name, stamp) VALUES (?, ?, ?)", arrayOf(key, book.name, stamp))
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
    return null
}

/** One book's rows in the cache. Every read is a query; call off the main thread. */
class HadithBookReader internal constructor(private val database: SQLiteDatabase, val book: HadithBook) {

    /** The source's own name for the book (English), as the editions give it. */
    val name: String = database.rawQuery("SELECT name FROM book WHERE key = ?", arrayOf(book.key)).use { if (it.moveToFirst()) it.getString(0) else "" }

    /** The sections that hold a hadith, in the source's order. */
    val sections: List<Section> = database.rawQuery("SELECT id, name, hadith_count FROM section WHERE book = ? ORDER BY position", arrayOf(book.key)).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Section(SectionId(cursor.getInt(0)), cursor.getString(1), cursor.getInt(2))) }
    }

    /** The section's hadiths in the source's order, each with its grades in the source's order. */
    fun hadiths(section: SectionId): List<Hadith> {
        val grades = database.rawQuery(
            "SELECT g.position, g.name, g.grade FROM grade g JOIN hadith h ON h.book = g.book AND h.position = g.position " +
                "WHERE g.book = ? AND h.section = ? ORDER BY g.position, g.place",
            arrayOf(book.key, section.value.toString()),
        ).use { cursor ->
            val byPosition = mutableMapOf<Int, MutableList<Grade>>()
            while (cursor.moveToNext()) byPosition.getOrPut(cursor.getInt(0)) { mutableListOf() } += Grade(cursor.getString(1), cursor.getString(2))
            byPosition
        }
        return database.rawQuery(
            "SELECT position, number, arabic_number, arabic, bengali, reference_book, reference_hadith FROM hadith WHERE book = ? AND section = ? ORDER BY position",
            arrayOf(book.key, section.value.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Hadith(
                            number = HadithNumber(cursor.getString(1)),
                            arabicNumber = cursor.stringOrNull(2),
                            section = section,
                            arabic = cursor.stringOrNull(3),
                            bengali = cursor.stringOrNull(4),
                            grades = grades[cursor.getInt(0)].orEmpty(),
                            reference = Reference(cursor.getInt(5), cursor.getInt(6)),
                        ),
                    )
                }
            }
        }
    }

    /** The section holding the hadith the source numbers [number], or null when the book has no such number. */
    fun sectionOf(number: HadithNumber): SectionId? =
        database.rawQuery("SELECT section FROM hadith WHERE book = ? AND number = ?", arrayOf(book.key, number.written)).use {
            if (it.moveToFirst()) SectionId(it.getInt(0)) else null
        }
}

private fun android.database.Cursor.stringOrNull(column: Int): String? = if (isNull(column)) null else getString(column)
