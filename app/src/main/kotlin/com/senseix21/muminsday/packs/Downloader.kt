package com.senseix21.muminsday.packs

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/** How one file's download ended. */
sealed interface Fetched {
    /** Every byte of the file is in the partial file. Its hash is not checked yet. */
    data object Whole : Fetched

    /** The user stopped it; what arrived is kept for a resume. */
    data object Cancelled : Fetched

    data class Failed(val failure: DownloadFailure) : Fetched
}

/**
 * Downloads one pack file into [part], resuming from what is already there (ADR-25). The only code in the app that
 * opens a connection (`NetworkSurfaceTest`). It uses the platform's `HttpsURLConnection` and no library (INV-P4), and
 * only `java.*` and `javax.*`, so the JVM tests run it against a local HTTPS server.
 *
 * What it sends: the request line, `Host`, the platform's own connection headers, a fixed `User-Agent` that names
 * nothing ([PACK_USER_AGENT]), `Accept-Encoding: identity`, and `Range` when resuming. No cookie, no identifier, no
 * query string. It never follows a redirect, refuses any URL that is not https, writes no more bytes than the manifest's
 * size, and checks [cancelled] between reads. [onWritten] is told how many bytes of this file are on the phone. [sockets] is for the tests' self-signed certificate only.
 */
fun fetchFile(
    url: String,
    part: File,
    expected: PackFile,
    cancelled: () -> Boolean,
    onWritten: (ByteCount) -> Unit,
    sockets: SSLSocketFactory? = null,
): Fetched {
    val address = try {
        URL(url)
    } catch (_: MalformedURLException) {
        return Fetched.Failed(DownloadFailure.Refused)
    }
    if (address.protocol != "https") return Fetched.Failed(DownloadFailure.Refused)
    val offset = when (val start = startFrom(ByteCount(part.length()), expected.size)) {
        Start.AlreadyWhole -> return Fetched.Whole
        Start.Discard -> ByteCount.ZERO.also { part.delete() }
        Start.Fresh -> ByteCount.ZERO
        is Start.From -> start.offset
    }
    part.parentFile?.mkdirs()
    val connection = try {
        address.openConnection() as HttpsURLConnection
    } catch (_: IOException) {
        return Fetched.Failed(DownloadFailure.Network)
    }
    try {
        sockets?.let { connection.sslSocketFactory = it }
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", PACK_USER_AGENT)
        // Android's HttpURLConnection asks for gzip and unzips by itself unless told otherwise, which would make byte
        // offsets and Content-Length describe a different body (developer.android.com/reference/java/net/HttpURLConnection,
        // "Performance").
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > ByteCount.ZERO) connection.setRequestProperty("Range", rangeHeader(offset))
        val write = when (val reply = readReply(offset, connection.responseCode, connection.getHeaderField("Content-Range"), expected.size)) {
            is Reply.Write -> reply
            is Reply.Refused -> {
                if (reply.refusal == Refusal.RangeNotSatisfiable) part.delete()
                return Fetched.Failed(DownloadFailure.Refused)
            }
        }
        var written = write.from.bytes
        connection.inputStream.use { input ->
            FileOutputStream(part, write.append).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    if (cancelled()) return Fetched.Cancelled
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (written + read > expected.size.bytes) {
                        output.close()
                        part.delete()
                        return Fetched.Failed(DownloadFailure.TooLarge)
                    }
                    output.write(buffer, 0, read)
                    written += read
                    onWritten(ByteCount(written))
                }
            }
        }
        return if (written == expected.size.bytes) Fetched.Whole else Fetched.Failed(DownloadFailure.Network)
    } catch (_: IOException) {
        return Fetched.Failed(DownloadFailure.Network)
    } finally {
        connection.disconnect()
    }
}

/** The SHA-256 of a file, streamed. */
fun sha256Of(file: File): Sha256 {
    val builder = Sha256Builder()
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            builder.update(buffer, 0, read)
        }
    }
    return builder.finish()
}

/** (target) Long enough for a slow mobile connection to answer; a stalled one fails and can be resumed. */
private const val TIMEOUT_MILLIS = 30_000
private const val BUFFER_BYTES = 64 * 1024
