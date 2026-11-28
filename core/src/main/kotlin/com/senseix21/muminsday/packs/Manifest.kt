package com.senseix21.muminsday.packs

import java.math.BigDecimal
import java.security.MessageDigest

/** A size in bytes, never negative. */
@JvmInline
public value class ByteCount(public val bytes: Long) : Comparable<ByteCount> {
    override fun compareTo(other: ByteCount): Int = bytes.compareTo(other.bytes)

    public operator fun plus(other: ByteCount): ByteCount = ByteCount(bytes + other.bytes)

    public companion object {
        public val ZERO: ByteCount = ByteCount(0)
    }
}

/** Megabytes (10^6 bytes) for the screen, exact; the screen rounds it up so a pack is never shown smaller than it is. */
public fun megabytes(size: ByteCount): BigDecimal = BigDecimal.valueOf(size.bytes).movePointLeft(6)

/** A SHA-256 as 64 lowercase hex digits, the form `content/quran-SOURCES.md` and the pack manifest use. */
@JvmInline
public value class Sha256 private constructor(public val hex: String) {
    public companion object {
        private val FORM = Regex("[0-9a-f]{64}")

        /** Null unless [text] is exactly 64 lowercase hex digits. */
        public fun of(text: String): Sha256? = if (FORM.matches(text)) Sha256(text) else null

        private const val HEX = "0123456789abcdef"

        // By hand rather than String.format, which would read the default locale (INV-T3).
        internal fun ofDigest(digest: ByteArray): Sha256 = Sha256(
            buildString(digest.size * 2) {
                for (byte in digest) {
                    val value = byte.toInt() and 0xff
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0f])
                }
            },
        )
    }
}

/** Hashes a file as it streams past, so a large pack is never held in memory. */
public class Sha256Builder {
    private val digest = MessageDigest.getInstance("SHA-256")

    public fun update(bytes: ByteArray, offset: Int, length: Int): Sha256Builder = apply { digest.update(bytes, offset, length) }

    public fun finish(): Sha256 = Sha256.ofDigest(digest.digest())
}

public sealed interface HashCheck {
    public data object Matches : HashCheck

    /** The file is not the one the manifest names: it is deleted and the user is told (ADR-25). */
    public data class Mismatch(val expected: Sha256, val actual: Sha256) : HashCheck
}

public fun checkHash(expected: Sha256, actual: Sha256): HashCheck =
    if (expected == actual) HashCheck.Matches else HashCheck.Mismatch(expected, actual)

/** A pack's id: lowercase letters, digits and hyphens, 1 to 40; also the name of its folder on the phone. */
@JvmInline
public value class PackId private constructor(public val value: String) {
    public companion object {
        private val FORM = Regex("[a-z0-9][a-z0-9-]{0,39}")

        public fun of(text: String): PackId? = if (FORM.matches(text)) PackId(text) else null
    }
}

/**
 * A path below the pack host and below the pack's folder on the phone: segments of letters, digits, `.`, `_` and `-`,
 * joined by `/`. No leading `/`, no `.` or `..` segment, no `\`, `:`, `?`, `#` or `%`, so it can neither leave the
 * host's root in a URL nor the pack's folder on the phone, and needs no escaping.
 */
@JvmInline
public value class PackPath private constructor(public val value: String) {
    public companion object {
        private val SEGMENT = Regex("[A-Za-z0-9._-]{1,80}")
        private const val MAX_LENGTH = 200

        public fun of(text: String): PackPath? {
            if (text.isEmpty() || text.length > MAX_LENGTH) return null
            val segments = text.split('/')
            val safe = segments.all { SEGMENT.matches(it) && it != "." && it != ".." }
            return if (safe) PackPath(text) else null
        }
    }
}

/** One file of a pack, as the manifest names it. */
public data class PackFile(val path: PackPath, val size: ByteCount, val sha256: Sha256)

public enum class PackKind { Tafsir, Audio }

/**
 * A downloadable pack (phase 16, ADR-25). [timings] is an audio pack's optional verse timings file (format in
 * `Timings.kt`); a tafsir pack has none. [files] is what is downloaded, in order.
 */
public data class Pack(val id: PackId, val kind: PackKind, val name: String, val main: PackFile, val timings: PackFile?) {
    public val files: List<PackFile> get() = listOfNotNull(main, timings)

    public val size: ByteCount get() = files.fold(ByteCount.ZERO) { sum, file -> sum + file.size }
}

public enum class ManifestProblemKind { FieldCount, BadId, DuplicateId, UnknownKind, BlankName, BadSize, BadHash, BadPath, DuplicatePath, TimingsOnTafsir }

/** [line] is 1-based, as an editor shows it. */
public data class ManifestProblem(val line: Int, val kind: ManifestProblemKind)

public sealed interface ManifestParse {
    /** Empty in a release until a pack with permission and a host exist (ADR-25). */
    public data class Accepted(val packs: List<Pack>) : ManifestParse

    public data class Refused(val problems: List<ManifestProblem>) : ManifestParse
}

/**
 * Reads the bundled pack manifest (parse at the edge, ADR-25). One pack per line, `|`-separated, `#` lines and blank
 * lines ignored:
 *
 * `id|tafsir|name|size|sha256|path` or `id|audio|name|size|sha256|path[|timings_size|timings_sha256|timings_path]`.
 *
 * Sizes are whole bytes above zero; hashes are SHA-256 in lowercase hex; paths are [PackPath]s, unique across the file.
 * Every problem is collected; one problem refuses the whole file, so a half-valid manifest offers nothing.
 */
public fun parseManifest(text: String): ManifestParse {
    val packs = mutableListOf<Pack>()
    val problems = mutableListOf<ManifestProblem>()
    val ids = mutableSetOf<PackId>()
    val paths = mutableSetOf<PackPath>()
    text.split('\n').forEachIndexed { index, raw ->
        val line = raw.removeSuffix("\r")
        if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
        val number = index + 1
        fun problem(kind: ManifestProblemKind) {
            problems += ManifestProblem(number, kind)
        }
        val fields = line.split('|')
        if (fields.size != MAIN_FIELDS && fields.size != MAIN_FIELDS + TIMING_FIELDS) return@forEachIndexed problem(ManifestProblemKind.FieldCount)
        val id = PackId.of(fields[0])
        if (id == null) problem(ManifestProblemKind.BadId) else if (!ids.add(id)) problem(ManifestProblemKind.DuplicateId)
        val kind = when (fields[1]) {
            "tafsir" -> PackKind.Tafsir
            "audio" -> PackKind.Audio
            else -> null.also { problem(ManifestProblemKind.UnknownKind) }
        }
        val name = fields[2].trim().takeIf { it.isNotEmpty() }
        if (name == null) problem(ManifestProblemKind.BlankName)
        fun file(at: Int): PackFile? {
            val size = fields[at].takeIf { it.all { c -> c in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }
            if (size == null) problem(ManifestProblemKind.BadSize)
            val hash = Sha256.of(fields[at + 1])
            if (hash == null) problem(ManifestProblemKind.BadHash)
            val path = PackPath.of(fields[at + 2])
            if (path == null) problem(ManifestProblemKind.BadPath) else if (!paths.add(path)) problem(ManifestProblemKind.DuplicatePath)
            return if (size != null && hash != null && path != null) PackFile(path, ByteCount(size), hash) else null
        }
        val main = file(MAIN_FILE_AT)
        val timings = if (fields.size > MAIN_FIELDS) file(MAIN_FIELDS) else null
        if (kind == PackKind.Tafsir && fields.size > MAIN_FIELDS) problem(ManifestProblemKind.TimingsOnTafsir)
        if (id != null && kind != null && name != null && main != null) packs += Pack(id, kind, name, main, timings)
    }
    return if (problems.isEmpty()) ManifestParse.Accepted(packs) else ManifestParse.Refused(problems)
}

private const val MAIN_FIELDS = 6
private const val TIMING_FIELDS = 3
private const val MAIN_FILE_AT = 3
