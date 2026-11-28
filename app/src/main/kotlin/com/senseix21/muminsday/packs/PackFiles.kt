package com.senseix21.muminsday.packs

import java.io.File

/**
 * Where packs live on the phone (ADR-25): `<root>/<pack id>/<path>`, with `.part` beside a file while it downloads. A
 * file gets its final name only after its SHA-256 matched the manifest, so a final name means "checked once"; it is
 * checked again before it is used. `root` is `filesDir/packs`, which the backup rules leave out (domain `file`). Plain
 * `java.io`, so the JVM tests use it as the app does.
 */
class PackFiles(private val root: File) {

    fun fileOf(pack: Pack, file: PackFile): File = File(File(root, pack.id.value), file.path.value)

    fun partOf(pack: Pack, file: PackFile): File = File(fileOf(pack, file).path + PART_SUFFIX)

    /** What the phone holds for [pack]: every file checked, part of it (resumable), or nothing. */
    fun state(pack: Pack): DownloadState {
        val checked = pack.files.all { fileOf(pack, it).length() == it.size.bytes }
        val onPhone = pack.files.fold(ByteCount.ZERO) { sum, file ->
            val final = fileOf(pack, file)
            sum + ByteCount(if (final.isFile) final.length() else partOf(pack, file).length())
        }
        return stateOnPhone(onPhone, checked, pack.size)
    }

    /**
     * Hashes a whole partial file; on a match gives it its final name, on a mismatch deletes it (ADR-25: a pack is used
     * only if its SHA-256 matches the manifest).
     */
    fun accept(pack: Pack, file: PackFile): Acceptance {
        val part = partOf(pack, file)
        return when (checkHash(file.sha256, sha256Of(part))) {
            HashCheck.Matches -> if (part.renameTo(fileOf(pack, file))) Acceptance.Kept else Acceptance.NotStored
            is HashCheck.Mismatch -> Acceptance.HashMismatch.also { part.delete() }
        }
    }

    /** The checked files of a ready pack, hashed again now. Any mismatch deletes the whole pack and nothing is used. */
    fun verified(pack: Pack): Verified {
        val files = pack.files.associateWith { fileOf(pack, it) }
        val intact = files.all { (expected, file) -> file.isFile && checkHash(expected.sha256, sha256Of(file)) == HashCheck.Matches }
        if (!intact) delete(pack)
        return if (intact) Verified.Intact(files) else Verified.Removed
    }

    fun delete(pack: Pack) {
        File(root, pack.id.value).deleteRecursively()
    }

    /** Every byte under the packs folder, finished or partial: what Settings shows. */
    fun used(): ByteCount = ByteCount(root.walkTopDown().filter { it.isFile }.sumOf { it.length() })

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}

sealed interface Acceptance {
    data object Kept : Acceptance

    /** Deleted: the bytes are not the manifest's file. */
    data object HashMismatch : Acceptance

    /** The bytes matched but could not be renamed into place; the partial file stays for another try. */
    data object NotStored : Acceptance
}

sealed interface Verified {
    data class Intact(val files: Map<PackFile, File>) : Verified

    /** A file no longer matched its SHA-256, so the pack was deleted. */
    data object Removed : Verified
}
