package com.senseix21.muminsday.packs

/** Where to begin a file, from what is already on the phone (ADR-25: downloads resume). */
public sealed interface Start {
    public data object Fresh : Start

    /** Ask for the rest with `Range: bytes=offset-`. */
    public data class From(val offset: ByteCount) : Start

    /** Every byte is here already; only the hash check is left. */
    public data object AlreadyWhole : Start

    /** More bytes than the manifest's size: the partial file cannot be this file. Delete it and start fresh. */
    public data object Discard : Start
}

public fun startFrom(onPhone: ByteCount, expected: ByteCount): Start = when {
    onPhone == ByteCount.ZERO -> Start.Fresh
    onPhone < expected -> Start.From(onPhone)
    onPhone == expected -> Start.AlreadyWhole
    else -> Start.Discard
}

/** The value of the Range header for a resumed file (RFC 9110 section 14.1.2). */
public fun rangeHeader(from: ByteCount): String = "bytes=${from.bytes}-"

public enum class Refusal {
    /** A 3xx. Redirects are not followed: a redirect could lead to another host (ADR-25). */
    Redirect,

    /** A 206 whose Content-Range is not exactly the bytes asked for, of a file of the manifest's size. */
    WrongRange,

    /** A 416: the server has no such range; the partial file is deleted. */
    RangeNotSatisfiable,

    /** Any other status. */
    Status,
}

public sealed interface Reply {
    /** Write the body from [from]; [append] to the partial file, or over it. */
    public data class Write(val from: ByteCount, val append: Boolean) : Reply

    public data class Refused(val refusal: Refusal, val status: Int) : Reply
}

/**
 * What a response means for the file. A 200 to a ranged request is the whole file again (the server ignored the range),
 * so it overwrites. A 206 is used only if it starts at the byte asked for and describes a file of exactly the
 * manifest's size (`bytes first-last/size`, RFC 9110 section 14.4).
 */
public fun readReply(requestedFrom: ByteCount, status: Int, contentRange: String?, expected: ByteCount): Reply = when (status) {
    OK -> Reply.Write(ByteCount.ZERO, append = false)
    PARTIAL -> {
        val match = contentRange?.let { CONTENT_RANGE.matchEntire(it.trim()) }
        val first = match?.groupValues?.get(1)?.toLongOrNull()
        val last = match?.groupValues?.get(2)?.toLongOrNull()
        val size = match?.groupValues?.get(3)?.toLongOrNull()
        if (first == requestedFrom.bytes && last == expected.bytes - 1 && size == expected.bytes) {
            Reply.Write(requestedFrom, append = first > 0)
        } else {
            Reply.Refused(Refusal.WrongRange, status)
        }
    }
    RANGE_NOT_SATISFIABLE -> Reply.Refused(Refusal.RangeNotSatisfiable, status)
    in REDIRECTS -> Reply.Refused(Refusal.Redirect, status)
    else -> Reply.Refused(Refusal.Status, status)
}

private const val OK = 200
private const val PARTIAL = 206
private const val RANGE_NOT_SATISFIABLE = 416
private val REDIRECTS = 300..399
private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

public enum class DownloadFailure {
    /** The connection failed or ended early. The partial file is kept, so a tap continues it. */
    Network,

    /** The server refused or answered wrongly ([Refusal]). */
    Refused,

    /** More bytes came than the manifest's size. The file is deleted. */
    TooLarge,

    /** The file's SHA-256 is not the manifest's. The file is deleted and never used. */
    HashMismatch,
}

/** Bytes are counted across all of a pack's files; [total] is the pack's size from the manifest. */
public sealed interface DownloadState {
    public data object Absent : DownloadState

    public data class Downloading(val received: ByteCount, val total: ByteCount) : DownloadState

    /** The user stopped it. What arrived is kept; a tap continues it. */
    public data class Stopped(val received: ByteCount, val total: ByteCount) : DownloadState

    public data class Failed(val failure: DownloadFailure, val received: ByteCount, val total: ByteCount) : DownloadState

    public data object Verifying : DownloadState

    /** Every file matched its SHA-256. */
    public data class Ready(val size: ByteCount) : DownloadState
}

public sealed interface DownloadEvent {
    /** The user tapped download or continue. The only event that can start requests (ADR-25: no request without a tap). */
    public data object Tap : DownloadEvent

    /**
     * How many of the pack's bytes are on the phone now, counted afresh by the shell (finished files plus the file in
     * progress), so a server that ignores a range and starts again cannot make the count drift.
     */
    public data class Progress(val received: ByteCount) : DownloadEvent

    /** Every file's body has ended. */
    public data object BodiesEnded : DownloadEvent

    public data object HashesMatched : DownloadEvent

    public data object HashMismatched : DownloadEvent

    public data object Cancel : DownloadEvent

    public data class Lost(val failure: DownloadFailure) : DownloadEvent

    public data object Delete : DownloadEvent
}

/** What the phone holds for a pack when the app starts: every file checked, part of it, or nothing. */
public fun stateOnPhone(onPhone: ByteCount, allFilesChecked: Boolean, total: ByteCount): DownloadState = when {
    allFilesChecked -> DownloadState.Ready(total)
    onPhone == ByteCount.ZERO -> DownloadState.Absent
    onPhone < total -> DownloadState.Stopped(onPhone, total)
    else -> DownloadState.Stopped(ByteCount.ZERO, total)
}

/** True only while a download the user tapped for is running: the shell makes requests in this state and no other. */
public fun mayRequest(state: DownloadState): Boolean = state is DownloadState.Downloading

/**
 * The life of one pack's download (ADR-25). Pure and total: an event that makes no sense in a state leaves it as it is,
 * so a late byte count after a cancel, or a second tap while downloading, changes nothing.
 */
public fun step(state: DownloadState, event: DownloadEvent, total: ByteCount): DownloadState = when (event) {
    DownloadEvent.Delete -> DownloadState.Absent
    DownloadEvent.Tap -> when (state) {
        DownloadState.Absent -> DownloadState.Downloading(ByteCount.ZERO, total)
        is DownloadState.Stopped -> DownloadState.Downloading(state.received, total)
        is DownloadState.Failed -> DownloadState.Downloading(state.received, total)
        is DownloadState.Downloading, DownloadState.Verifying, is DownloadState.Ready -> state
    }
    is DownloadEvent.Progress -> when (state) {
        is DownloadState.Downloading ->
            if (event.received > total) {
                DownloadState.Failed(DownloadFailure.TooLarge, ByteCount.ZERO, total)
            } else {
                DownloadState.Downloading(event.received, total)
            }
        DownloadState.Absent, is DownloadState.Stopped, is DownloadState.Failed, DownloadState.Verifying, is DownloadState.Ready -> state
    }
    DownloadEvent.BodiesEnded -> when (state) {
        is DownloadState.Downloading ->
            if (state.received == total) DownloadState.Verifying else DownloadState.Failed(DownloadFailure.Network, state.received, total)
        DownloadState.Absent, is DownloadState.Stopped, is DownloadState.Failed, DownloadState.Verifying, is DownloadState.Ready -> state
    }
    DownloadEvent.HashesMatched -> when (state) {
        DownloadState.Verifying -> DownloadState.Ready(total)
        DownloadState.Absent, is DownloadState.Downloading, is DownloadState.Stopped, is DownloadState.Failed, is DownloadState.Ready -> state
    }
    // A file is checked as soon as it is whole, so a mismatch can arrive while later files are still downloading.
    DownloadEvent.HashMismatched -> when (state) {
        DownloadState.Verifying, is DownloadState.Downloading -> DownloadState.Failed(DownloadFailure.HashMismatch, ByteCount.ZERO, total)
        DownloadState.Absent, is DownloadState.Stopped, is DownloadState.Failed, is DownloadState.Ready -> state
    }
    DownloadEvent.Cancel -> when (state) {
        is DownloadState.Downloading -> DownloadState.Stopped(state.received, total)
        DownloadState.Absent, is DownloadState.Stopped, is DownloadState.Failed, DownloadState.Verifying, is DownloadState.Ready -> state
    }
    is DownloadEvent.Lost -> when (state) {
        is DownloadState.Downloading -> {
            val kept = when (event.failure) {
                DownloadFailure.Network, DownloadFailure.Refused -> state.received
                DownloadFailure.TooLarge, DownloadFailure.HashMismatch -> ByteCount.ZERO
            }
            DownloadState.Failed(event.failure, kept, total)
        }
        DownloadState.Absent, is DownloadState.Stopped, is DownloadState.Failed, DownloadState.Verifying, is DownloadState.Ready -> state
    }
}
