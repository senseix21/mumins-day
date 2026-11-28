package com.senseix21.muminsday.store

import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.khatam.LayoutCheck
import com.senseix21.muminsday.khatam.QuranLayout
import com.senseix21.muminsday.khatam.buildLayout
import com.senseix21.muminsday.quran.Edition
import com.senseix21.muminsday.quran.EditionFormat
import com.senseix21.muminsday.quran.JuzParse
import com.senseix21.muminsday.quran.Lookup
import com.senseix21.muminsday.quran.Quran
import com.senseix21.muminsday.quran.QuranCheck
import com.senseix21.muminsday.quran.SurahInfo
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.TextParse
import com.senseix21.muminsday.quran.TranslationCheck
import com.senseix21.muminsday.quran.VerseRef
import com.senseix21.muminsday.quran.checkQuran
import com.senseix21.muminsday.quran.checkTranslation
import com.senseix21.muminsday.quran.indexWords
import com.senseix21.muminsday.quran.notice
import com.senseix21.muminsday.quran.parseJuzStarts
import com.senseix21.muminsday.quran.parseMetadata
import com.senseix21.muminsday.quran.parseText
import com.senseix21.muminsday.quran.quranEncTranscript
import java.io.FileNotFoundException
import java.security.MessageDigest

/** One ayah as the reader shows it: [text] exactly as in the asset (INV-QT1); [translation] null when none is chosen. */
data class ShownAyah(val ref: VerseRef, val text: String, val translation: ShownTranslation?)

/** The chosen translation of one ayah, both strings exactly as the source has them (INV-QT1); [footnotes] "" when empty. */
data class ShownTranslation(val text: String, val footnotes: String)

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
    val names = assetFiles(assets, "").sorted()
    if (TEXT !in names) return QuranOpen.Missing

    val recorded = BuildConfig.QURAN_HASHES.split(',').filter { it.isNotBlank() }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val hashes = mutableMapOf<String, String>()
    for (name in names) {
        val hash = sha256(read(assets, name))
        val expected = recorded[name] ?: return QuranOpen.Unverified("$name is not recorded")
        if (expected != hash) return QuranOpen.Unverified("$name does not match its recorded SHA-256")
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

/**
 * Every file under `assets/quran/[folder]`, by its path below `assets/quran/`. `AssetManager.list` gives a folder's
 * entries and an empty array for a file (ASSUMED from its use here; the Quran's files are never empty folders).
 */
private fun assetFiles(assets: AssetManager, folder: String): List<String> =
    assets.list(if (folder.isEmpty()) QURAN_ASSETS else "$QURAN_ASSETS/$folder").orEmpty().flatMap { name ->
        val path = if (folder.isEmpty()) name else "$folder/$name"
        if (assets.list("$QURAN_ASSETS/$path").isNullOrEmpty()) listOf(path) else assetFiles(assets, path)
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

    /** The translations this build bundles (ADR-26), in [Edition] order. */
    val editions: List<Edition> = database.rawQuery("SELECT DISTINCT edition FROM translation", null).use { cursor ->
        buildList { while (cursor.moveToNext()) Edition.ofKey(cursor.getString(0))?.let(::add) }
    }.sorted()

    /** The ayahs of [surah], with [edition]'s translation when one is given and bundled. */
    fun verses(surah: SurahNumber, edition: Edition?): List<ShownAyah> =
        query("WHERE a.surah = ?", listOf(surah.value.toString()), edition, limit = null)

    /**
     * FTS4, one query per word (ADR-26): an ayah is found when each word is in its Arabic index or in [edition]'s
     * translation index. The shown text is the asset's (ADR-23). At most [limit] ayahs, in Quran order.
     */
    fun search(words: Lookup.Words, edition: Edition?, limit: Int): List<ShownAyah> {
        val arguments = mutableListOf<String>()
        val conditions = words.terms.map { term ->
            arguments += term
            val arabic = "SELECT docid FROM arabic_index WHERE arabic_index MATCH ?"
            if (edition == null) {
                "a.id IN ($arabic)"
            } else {
                arguments += term
                arguments += edition.key
                "a.id IN ($arabic UNION SELECT t.ayah_id FROM translation_index JOIN translation t ON t.id = translation_index.docid " +
                    "WHERE translation_index MATCH ? AND t.edition = ?)"
            }
        }
        return query("WHERE " + conditions.joinToString(" AND "), arguments, edition, limit)
    }

    private fun query(where: String, arguments: List<String>, edition: Edition?, limit: Int?): List<ShownAyah> {
        // The edition is bound first: it belongs to the join, which comes before the WHERE clause.
        val sql = "SELECT a.surah, a.ayah, a.text, t.text, t.footnotes FROM ayah a " +
            "LEFT JOIN translation t ON t.ayah_id = a.id AND t.edition = ? $where ORDER BY a.id" + (limit?.let { " LIMIT $it" } ?: "")
        return database.rawQuery(sql, (listOf(edition?.key ?: "") + arguments).toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val surah = SurahNumber.of(cursor.getInt(0)) ?: continue
                    val translation = if (cursor.isNull(3)) null else ShownTranslation(cursor.getString(3), cursor.getString(4))
                    add(ShownAyah(VerseRef(surah, cursor.getInt(1)), cursor.getString(2), translation))
                }
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
 * [edition]'s transcript information as its shipped file has it (QuranEnc condition 4), for the Licences screen; null
 * when its source carries none. Reads only the file's first kilobytes: the block is at the top.
 */
fun translationTranscript(context: Context, edition: Edition): String? = when (edition.format) {
    EditionFormat.SuraResponses -> null
    EditionFormat.WholeXml -> try {
        val folder = "$QURAN_ASSETS/$TRANSLATIONS${edition.key}"
        context.assets.list(folder).orEmpty().singleOrNull()?.let { name ->
            context.assets.open("$folder/$name").use { stream ->
                // InputStream.readNBytes needs API 33; minSdk is 26.
                val head = ByteArray(TRANSCRIPT_HEAD)
                var filled = 0
                while (filled < head.size) {
                    val read = stream.read(head, filled, head.size - filled)
                    if (read < 0) break
                    filled += read
                }
                quranEncTranscript(String(head, 0, filled, Charsets.UTF_8))
            }
        }
    } catch (_: FileNotFoundException) {
        null
    }
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
    val copyright = notice((parsedText as? TextParse.Parsed)?.lines.orEmpty())

    database.beginTransaction()
    try {
        for (table in listOf("meta", "surah", "ayah", "arabic_index", "translation", "translation_index")) database.execSQL("DELETE FROM $table")
        for (surah in quran.surahs) {
            database.execSQL("INSERT INTO surah (number, name, ayah_count) VALUES (?, ?, ?)", arrayOf<Any>(surah.number.value, surah.name, surah.ayahCount))
        }
        val ayah = database.compileStatement("INSERT INTO ayah (id, surah, ayah, text) VALUES (?, ?, ?, ?)")
        val index = database.compileStatement("INSERT INTO arabic_index (docid, arabic, arabic_simple) VALUES (?, ?, ?)")
        quran.verses.forEachIndexed { at, verse ->
            val id = (at + 1).toLong()
            ayah.clearBindings()
            ayah.bindLong(1, id)
            ayah.bindLong(2, verse.ref.surah.value.toLong())
            ayah.bindLong(3, verse.ref.ayah.toLong())
            ayah.bindString(4, verse.text)
            ayah.executeInsert()
            index.clearBindings()
            index.bindLong(1, id)
            index.bindString(2, indexWords(verse.text))
            index.bindString(3, indexWords(quran.searchVerses[at].text))
            index.executeInsert()
        }
        // INV-QT1 on the phone: every stored ayah reads back exactly as the asset has it, in order.
        val stored = database.rawQuery("SELECT text FROM ayah ORDER BY id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (stored != quran.verses.map { it.text }) return "the stored text differs from the asset"

        // One edition at a time, so only one translation's text is in memory (Zakaria's files are 30 MB of escapes).
        var nextId = 1L
        for (edition in Edition.entries) {
            val folder = "$TRANSLATIONS${edition.key}/"
            val files = names.filter { it.startsWith(folder) }.associate { it.removePrefix(folder) to text(it) }
            if (files.isEmpty()) continue
            val translation = when (val checked = checkTranslation(quran, edition, files)) {
                is TranslationCheck.Refused -> return checked.reason
                is TranslationCheck.Accepted -> checked.translation
            }
            val row = database.compileStatement("INSERT INTO translation (id, edition, ayah_id, text, footnotes) VALUES (?, ?, ?, ?, ?)")
            val words = database.compileStatement("INSERT INTO translation_index (docid, text) VALUES (?, ?)")
            translation.verses.forEachIndexed { at, translated ->
                val id = nextId++
                row.clearBindings()
                row.bindLong(1, id)
                row.bindString(2, edition.key)
                row.bindLong(3, (at + 1).toLong())
                row.bindString(4, translated.text)
                row.bindString(5, translated.footnotes)
                row.executeInsert()
                words.clearBindings()
                words.bindLong(1, id)
                words.bindString(2, indexWords(translated.text))
                words.executeInsert()
            }
            // The same read-back for the translation: text and footnotes exactly as the source has them.
            val back = database.rawQuery("SELECT text, footnotes FROM translation WHERE edition = ? ORDER BY ayah_id", arrayOf(edition.key)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
            if (back != translation.verses.map { it.text to it.footnotes }) return "the stored ${edition.key} translation differs from the asset"
        }
        putMeta(database, CONTENT_KEY, key)
        putMeta(database, NOTICE, copyright)
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

/** Where the translations are staged, below `assets/quran/` (`content/quran/translations/<key>/`, ADR-26). */
private const val TRANSLATIONS = "translations/"

/** Rowwad's `<meta>` block ends within its first 700 bytes (KNOWN, the shipped file); 4 KB leaves room. */
private const val TRANSCRIPT_HEAD = 4096

private const val CONTENT_KEY = "content_key"
private const val NOTICE = "notice"
