package com.senseix21.muminsday.ui

import android.content.Intent
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.packs.ByteCount
import com.senseix21.muminsday.packs.DownloadFailure
import com.senseix21.muminsday.packs.DownloadState
import com.senseix21.muminsday.packs.ManifestParse
import com.senseix21.muminsday.packs.PACK_HOST
import com.senseix21.muminsday.packs.Pack
import com.senseix21.muminsday.packs.PackDownloads
import com.senseix21.muminsday.packs.PackKind
import com.senseix21.muminsday.packs.PackSource
import com.senseix21.muminsday.packs.loadPackManifest
import com.senseix21.muminsday.packs.megabytes
import com.senseix21.muminsday.packs.packFiles
import com.senseix21.muminsday.packs.packSource
import java.math.RoundingMode

/**
 * Downloadable packs (phase 16, ADR-25). With no host in the app, which is every build today, it says packs are not
 * available yet and does nothing else: no manifest is offered and no request can start. With a host, each pack in the
 * bundled manifest has its state and buttons; a download starts only from a tap here.
 */
class PacksActivity : BaseActivity() {

    override fun onResume() {
        super.onResume()
        PackDownloads.listener = { render() }
        render()
    }

    override fun onPause() {
        super.onPause()
        PackDownloads.listener = null
    }

    private fun render() {
        val page = column()
        page.addView(title(getString(R.string.packs_title)))
        when (val source = packSource(PACK_HOST)) {
            PackSource.NotAvailable, is PackSource.Invalid -> page.addView(body(getString(R.string.packs_not_available)))
            is PackSource.Host -> offer(page, source)
        }
        show(page)
    }

    private fun offer(page: LinearLayout, host: PackSource.Host) {
        page.addView(body(getString(R.string.packs_privacy), muted = true))
        val packs = when (val manifest = loadPackManifest(this)) {
            is ManifestParse.Accepted -> manifest.packs
            is ManifestParse.Refused -> return page.addView(body(getString(R.string.packs_manifest_refused)))
        }
        if (packs.isEmpty()) page.addView(body(getString(R.string.packs_none)))
        val texts = Texts(this, language)
        val files = packFiles(this)
        for (pack in packs) {
            page.addView(heading(pack.name))
            val kind = getString(
                when (pack.kind) {
                    PackKind.Tafsir -> R.string.packs_kind_tafsir
                    PackKind.Audio -> R.string.packs_kind_audio
                },
            )
            page.addView(body(kind + " · " + getString(R.string.packs_size, mb(texts, pack.size)), muted = true))
            val state = PackDownloads.state(files, pack)
            when (state) {
                DownloadState.Absent -> page.addView(actionButton(getString(R.string.packs_download)) { PackDownloads.start(this, host, pack) })
                is DownloadState.Downloading -> {
                    page.addView(body(getString(R.string.packs_downloading, mb(texts, state.received), mb(texts, state.total))))
                    page.addView(actionButton(getString(R.string.packs_cancel), primary = false) { PackDownloads.cancel(pack) })
                }
                is DownloadState.Stopped -> {
                    page.addView(body(getString(R.string.packs_stopped, mb(texts, state.received), mb(texts, state.total))))
                    page.addView(actionButton(getString(R.string.packs_resume)) { PackDownloads.start(this, host, pack) })
                }
                is DownloadState.Failed -> {
                    page.addView(body(getString(failureText(state.failure))))
                    page.addView(actionButton(getString(R.string.packs_resume)) { PackDownloads.start(this, host, pack) })
                }
                DownloadState.Verifying -> page.addView(body(getString(R.string.packs_verifying)))
                is DownloadState.Ready -> {
                    page.addView(body(getString(R.string.packs_ready)))
                    if (pack.kind == PackKind.Audio) page.addView(actionButton(getString(R.string.packs_listen)) { listen(pack) })
                }
            }
            if (state != DownloadState.Absent) {
                page.addView(actionButton(getString(R.string.packs_delete), primary = false) { PackDownloads.delete(this, pack) })
            }
        }
    }

    private fun listen(pack: Pack) {
        startActivity(Intent(this, RecitationActivity::class.java).putExtra(RecitationActivity.EXTRA_PACK, pack.id.value))
    }

    private fun failureText(failure: DownloadFailure): Int = when (failure) {
        DownloadFailure.Network -> R.string.packs_failed_network
        DownloadFailure.Refused -> R.string.packs_failed_refused
        DownloadFailure.TooLarge -> R.string.packs_failed_large
        DownloadFailure.HashMismatch -> R.string.packs_failed_hash
    }
}

/** Megabytes with one decimal, rounded up so nothing looks smaller than it is, in the chosen language's digits. */
internal fun mb(texts: Texts, size: ByteCount): String = texts.decimal(megabytes(size), 1, RoundingMode.UP)
