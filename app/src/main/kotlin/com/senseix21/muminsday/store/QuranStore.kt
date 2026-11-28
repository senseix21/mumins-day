package com.senseix21.muminsday.store

import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.khatam.LayoutCheck
import com.senseix21.muminsday.khatam.QuranLayout
import com.senseix21.muminsday.khatam.buildLayout
import com.senseix21.muminsday.quran.JuzParse
import com.senseix21.muminsday.quran.Line
import com.senseix21.muminsday.quran.Lookup
import com.senseix21.muminsday.quran.Quran
import com.senseix21.muminsday.quran.QuranCheck
import com.senseix21.muminsday.quran.SurahInfo
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.TEST_FIXTURE_MARKER
import com.senseix21.muminsday.quran.TextParse
import com.senseix21.muminsday.quran.Translation
import com.senseix21.muminsday.quran.TranslationCheck
import com.senseix21.muminsday.quran.VerseRef
import com.senseix21.muminsday.quran.checkQuran
import com.senseix21.muminsday.quran.checkTranslation
import com.senseix21.muminsday.quran.indexWords
import com.senseix21.muminsday.quran.notice
import com.senseix21.muminsday.quran.parseJuzStarts
import com.senseix21.muminsday.quran.parseMetadata
import com.senseix21.muminsday.quran.parseText
import java.io.FileNotFoundException
import java.security.MessageDigest

/** One ayah as the reader shows it: [text] exactly as in the asset (INV-QT1); [translation] null when none is bundled. */
data class ShownAyah(val ref: VerseRef, val text: String, val translation: String?)

sealed interface QuranOpen {
    data class Ready(val reader: QuranReader) : QuranOpen

    /** The build has no Quran assets. */
    data object Missing : QuranOpen

    /**
     * An asset's SHA-256 is not the one recorded at build time, an asset is not recorded, or the files do not describe
     * one Quran. Nothing is shown (INV-QT1). T-BUILD-6 normally stops this shipping.
     */
    data class Unverified(val reason: String) : QuranOpen
}

/**
 * The verified Quran on this phone (ADR-23). Every open hashes the assets and compares them with the hashes the build
 * recorded from `content/quran-SOURCES.md`; the search database is rebuilt whenever the assets' hashes differ from the
 * ones it was built from. Runs SQL and reads files: call it off the main thread.
 */
fun openQuran(context: Context): QuranOpen {
    val assets = context.assets
    val names = assets.list(QURAN_ASSETS).orEmpty().sorted()
    if (TEXT !in names) return QuranOpen.Missing

    val recorded = BuildConfig.QURAN_HASHES.split(',').filter { it.isNotBlank() }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val hashes = mutableMapOf<String, String>()
    for (name in names) {
        val hash = sha256(read(assets, name))
        val expected = recorded[name]
        val fakeTranslation = BuildConfig.DEBUG && name == FAKE_TRANSLATION
        if (expected == null && !fakeTranslation) return QuranOpen.Unverified("$name is not recorded")
        if (expected != null && expected != hash) return QuranOpen.Unverified("$name does not match its recorded SHA-256")
        hashes[name] = hash
    }
    val key = hashes.entries.joinToString(",") { "${it.key}=${it.value}" }

    val database = Stores.quranDatabase(context).writableDatabase
    if (contentKey(database) != key) {
        val problem = rebuild(database, assets, names, key)
        if (problem != null) return QuranOpen.Unverified(problem)
    }
    val surahs = readSurahs(database)
    // The khatam plan's layout (ADR-24): the surahs as built, the juz from the same verified metadata file.
    val juzStarts = when (val juz = parseJuzStarts(read(assets, METADATA).toString(Charsets.UTF_8))) {
        is JuzParse.Parsed -> juz.starts
        is JuzParse.Refused -> return QuranOpen.Unverified("metadata: ${juz.reason}")
    }
    return when (val layout = buildLayout(surahs, juzStarts)) {
        is LayoutCheck.Built -> QuranOpen.Ready(QuranReader(database, surahs, layout.layout))
        is LayoutCheck.Refused -> QuranOpen.Unverified("layout: ${layout.reason}")
    }
}

private fun readSurahs(database: SQLiteDatabase): List<SurahInfo> =
    database.rawQuery("SELECT number, name, ayah_count FROM surah ORDER BY number", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                SurahNumber.of(cursor.getInt(0))?.let { add(SurahInfo(it, cursor.getInt(2), cursor.getString(1))) }
            }
        }
    }

/**
 * Reads the built database. [surahs] come from the verified metadata; [layout] (ayah positions and juz, for the khatam
 * plan, ADR-24) from the same metadata.
 */
class QuranReader internal constructor(private val database: SQLiteDatabase, val surahs: List<SurahInfo>, val layout: QuranLayout) {

    /** True when a translation is bundled; false in every release until one has written permission (INV-QT1). */
    val hasTranslation: Boolean = meta(database, TRANSLATION_KIND) != null

    /** True for the FAKE debug translation only. */
    val translationIsFake: Boolean = meta(database, TRANSLATION_KIND) == FIXTURE

    fun verses(surah: SurahNumber): List<ShownAyah> =
        query("SELECT surah, ayah, text, translation FROM ayah WHERE surah = ? ORDER BY id", arrayOf(surah.value.toString()))

    /** FTS4 over the index words; the shown text is the asset's (ADR-23). At most [limit] ayahs, in Quran order. */
    fun search(words: Lookup.Words, limit: Int): List<ShownAyah> = query(
        "SELECT a.surah, a.ayah, a.text, a.translation FROM ayah_index JOIN ayah a ON a.id = ayah_index.docid " +
            "WHERE ayah_index MATCH ? ORDER BY a.id LIMIT $limit",
        arrayOf(words.match),
    )

    private fun query(sql: String, arguments: Array<String>): List<ShownAyah> = database.rawQuery(sql, arguments).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val surah = SurahNumber.of(cursor.getInt(0)) ?: continue
                add(ShownAyah(VerseRef(surah, cursor.getInt(1)), cursor.getString(2), if (cursor.isNull(3)) null else cursor.getString(3)))
            }
        }
    }
}

/** The Tanzil copyright block, read from the shipped text as it is, for the Licences screen. */
fun quranNotice(context: Context): String? = try {
    read(context.assets, TEXT).toString(Charsets.UTF_8).split('\n').filter { it.startsWith("#") }.joinToString("\n")
} catch (_: FileNotFoundException) {
    null
}

/**
 * Parses and checks the verified assets with core, then writes every table in one transaction and reads the ayahs back:
 * if any row differs from the asset's text by one character, the transaction is rolled back and nothing is shown.
 * Returns null on success, or what went wrong.
 */
private fun rebuild(database: SQLiteDatabase, assets: AssetManager, names: List<String>, key: String): String? {
    fun text(name: String) = read(assets, name).toString(Charsets.UTF_8)
    val parsedText = parseText(text(TEXT))
    val quran: Quran = when (val check = checkQuran(parsedText, parseText(text(SEARCH_TEXT)), parseMetadata(text(METADATA)))) {
        is QuranCheck.Verified -> check.quran
        is QuranCheck.Refused -> return "the Quran files do not agree: ${check.reasons.take(3)}"
    }
    val translationName = names.firstOrNull { it.startsWith("translation-") }
    val translation: Translation? = translationName?.let { name ->
        val body = text(name)
        when (val checked = checkTranslation(quran, parseText(body), body.lineSequence().first())) {
            is TranslationCheck.Refused -> return checked.reason
            is TranslationCheck.Accepted ->
                if (checked.translation.testFixture && !BuildConfig.DEBUG) return "$name is a test fixture" else checked.translation
        }
    }
    val copyright = notice((parsedText as? TextParse.Parsed)?.lines.orEmpty())

    database.beginTransaction()
    try {
        for (table in listOf("meta", "surah", "ayah", "ayah_index")) database.execSQL("DELETE FROM $table")
        for (surah in quran.surahs) {
            database.execSQL("INSERT INTO surah (number, name, ayah_count) VALUES (?, ?, ?)", arrayOf<Any>(surah.number.value, surah.name, surah.ayahCount))
        }
        val ayah = database.compileStatement("INSERT INTO ayah (id, surah, ayah, text, translation) VALUES (?, ?, ?, ?, ?)")
        val index = database.compileStatement("INSERT INTO ayah_index (docid, arabic, arabic_simple, translation) VALUES (?, ?, ?, ?)")
        quran.verses.forEachIndexed { at, verse ->
            val id = (at + 1).toLong()
            val translated: Line.Verse? = translation?.verses?.get(at)
            ayah.clearBindings()
            ayah.bindLong(1, id)
            ayah.bindLong(2, verse.ref.surah.value.toLong())
            ayah.bindLong(3, verse.ref.ayah.toLong())
            ayah.bindString(4, verse.text)
            if (translated == null) ayah.bindNull(5) else ayah.bindString(5, translated.text)
            ayah.executeInsert()
            index.clearBindings()
            index.bindLong(1, id)
            index.bindString(2, indexWords(verse.text))
            index.bindString(3, indexWords(quran.searchVerses[at].text))
            index.bindString(4, translated?.let { indexWords(it.text) } ?: "")
            index.executeInsert()
        }
        // INV-QT1 on the phone: every stored ayah reads back exactly as the asset has it, in order.
        val stored = database.rawQuery("SELECT text FROM ayah ORDER BY id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (stored != quran.verses.map { it.text }) return "the stored text differs from the asset"
        putMeta(database, CONTENT_KEY, key)
        putMeta(database, NOTICE, copyright)
        translation?.let { putMeta(database, TRANSLATION_KIND, if (it.testFixture) FIXTURE else "bundled") }
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
    return null
}

private fun contentKey(database: SQLiteDatabase): String? = meta(database, CONTENT_KEY)

private fun meta(database: SQLiteDatabase, key: String): String? =
    database.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

private fun putMeta(database: SQLiteDatabase, key: String, value: String) =
    database.execSQL("INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)", arrayOf(key, value))

private fun read(assets: AssetManager, name: String): ByteArray = assets.open("$QURAN_ASSETS/$name").use { it.readBytes() }

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** The asset folder the build stages `content/quran/` into (`app/build.gradle.kts`, `stageQuran`). */
const val QURAN_ASSETS: String = "quran"
const val QURAN_FONT: String = "$QURAN_ASSETS/AmiriQuran.ttf"
const val QURAN_FONT_LICENCE: String = "$QURAN_ASSETS/Amiri-OFL.txt"
private const val TEXT = "quran-uthmani.txt"
private const val SEARCH_TEXT = "quran-simple-clean.txt"
private const val METADATA = "quran-data.xml"

/** The FAKE translation a debug build generates; it has no recorded hash and starts with [TEST_FIXTURE_MARKER]. */
const val FAKE_TRANSLATION: String = "translation-test.txt"

private const val CONTENT_KEY = "content_key"
private const val NOTICE = "notice"
private const val TRANSLATION_KIND = "translation"
private const val FIXTURE = "test_fixture"
