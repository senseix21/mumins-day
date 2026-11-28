package com.senseix21.muminsday.packs

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The bundled manifest (ADR-25): `assets/packs/manifest.txt`, comments only in a release. Parsed once, here. */
fun loadPackManifest(context: Context): ManifestParse =
    parseManifest(context.assets.open(MANIFEST_ASSET).use { it.readBytes() }.toString(Charsets.UTF_8))

/** The packs folder, under `filesDir`, which the backup rules leave out. */
fun packFiles(context: Context): PackFiles = PackFiles(File(context.applicationContext.filesDir, PACKS_FOLDER))

/**
 * The downloads in this process (ADR-25): each pack's state and a stop flag, one worker thread per running download.
 * [start] is called only from the downloads screen's buttons, so no request is ever made without a tap. The screen
 * listens through [listener], on the main thread.
 */
object PackDownloads {
    private val states = ConcurrentHashMap<PackId, DownloadState>()
    private val stops = ConcurrentHashMap<PackId, AtomicBoolean>()
    private val deleted = ConcurrentHashMap.newKeySet<PackId>()
    private val main = Handler(Looper.getMainLooper())

    /** Told on the main thread whenever a state changes. One screen at a time sets it. */
    var listener: (() -> Unit)? = null

    fun state(files: PackFiles, pack: Pack): DownloadState = states[pack.id] ?: files.state(pack)

    /** A user's tap on download or continue. Does nothing while the pack is already downloading or ready. */
    fun start(context: Context, host: PackSource.Host, pack: Pack) {
        val files = packFiles(context)
        val before = state(files, pack)
        val after = step(before, DownloadEvent.Tap, pack.size)
        if (mayRequest(before) || !mayRequest(after)) return
        val stop = AtomicBoolean(false)
        stops[pack.id] = stop
        set(pack, after)
        deleted.remove(pack.id)
        Thread {
            downloadPack(files, pack, { packUrl(host, it) }, stop::get, { event -> set(pack, step(states[pack.id] ?: after, event, pack.size)) })
            // A delete during the download: the last read may have written after the folder went, so remove it again.
            if (deleted.remove(pack.id)) files.delete(pack)
            stops.remove(pack.id, stop)
        }.start()
    }

    /** Stops after the current read; what arrived is kept for a resume. */
    fun cancel(pack: Pack) {
        stops[pack.id]?.set(true)
    }

    fun delete(context: Context, pack: Pack) {
        stops[pack.id]?.let { stop ->
            deleted.add(pack.id)
            stop.set(true)
        }
        packFiles(context).delete(pack)
        set(pack, step(DownloadState.Absent, DownloadEvent.Delete, pack.size))
    }

    private fun set(pack: Pack, state: DownloadState) {
        states[pack.id] = state
        main.post { listener?.invoke() }
    }
}

private const val MANIFEST_ASSET = "packs/manifest.txt"
private const val PACKS_FOLDER = "packs"
