package com.senseix21.muminsday.packs

import javax.net.ssl.SSLSocketFactory

/**
 * One tapped download of a whole pack, file after file, each checked against its SHA-256 as soon as it is whole
 * (ADR-25). Every step is reported as a [DownloadEvent] for `step` in core. Blocking: call it on a worker thread, and
 * only from a user's tap. [urlOf] is `packUrl` with the one host in the app; the tests point it at a local server.
 */
fun downloadPack(
    files: PackFiles,
    pack: Pack,
    urlOf: (PackPath) -> String,
    cancelled: () -> Boolean,
    report: (DownloadEvent) -> Unit,
    sockets: SSLSocketFactory? = null,
) {
    var done = ByteCount.ZERO
    for (file in pack.files) {
        if (files.fileOf(pack, file).length() == file.size.bytes) {
            done += file.size
            continue
        }
        val before = done
        val fetched = fetchFile(urlOf(file.path), files.partOf(pack, file), file, cancelled, { written -> report(DownloadEvent.Progress(before + written)) }, sockets)
        when (fetched) {
            Fetched.Whole -> when (files.accept(pack, file)) {
                Acceptance.Kept -> done += file.size
                Acceptance.HashMismatch -> return report(DownloadEvent.HashMismatched)
                Acceptance.NotStored -> return report(DownloadEvent.Lost(DownloadFailure.Network))
            }
            Fetched.Cancelled -> return report(DownloadEvent.Cancel)
            is Fetched.Failed -> return report(DownloadEvent.Lost(fetched.failure))
        }
    }
    report(DownloadEvent.Progress(done))
    report(DownloadEvent.BodiesEnded)
    report(DownloadEvent.HashesMatched)
}
