package com.senseix21.muminsday.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.packs.AyahAt
import com.senseix21.muminsday.packs.AyahTiming
import com.senseix21.muminsday.packs.ManifestParse
import com.senseix21.muminsday.packs.Pack
import com.senseix21.muminsday.packs.PackFile
import com.senseix21.muminsday.packs.PackKind
import com.senseix21.muminsday.packs.TimingsParse
import com.senseix21.muminsday.packs.Verified
import com.senseix21.muminsday.packs.ayahAt
import com.senseix21.muminsday.packs.loadPackManifest
import com.senseix21.muminsday.packs.packFiles
import com.senseix21.muminsday.packs.parseTimings
import java.io.File
import java.io.IOException
import java.time.Duration

/**
 * Plays a downloaded audio pack with the platform `MediaPlayer` (ADR-17 item 4, ADR-25), and follows the ayah only if
 * the pack has a timings file. Every file is hashed again before it is played; a mismatch deletes the pack. It plays
 * while this screen is in front and stops when the user leaves it: background playback would need a foreground service,
 * which is not part of this phase.
 */
class RecitationActivity : BaseActivity() {

    private var player: MediaPlayer? = null
    private var timings: List<AyahTiming> = emptyList()
    private val ticks = Handler(Looper.getMainLooper())
    private lateinit var now: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.recitation_title)))
        page.addView(body(getString(R.string.packs_verifying), muted = true))
        show(page)
        val id = intent.getStringExtra(EXTRA_PACK).orEmpty()
        val context = applicationContext
        // Hashing a large file takes time: off the main thread.
        Thread {
            val pack = when (val manifest = loadPackManifest(context)) {
                is ManifestParse.Accepted -> manifest.packs.firstOrNull { it.id.value == id && it.kind == PackKind.Audio }
                is ManifestParse.Refused -> null
            }
            val opening = when {
                pack == null -> Opening.NotFound
                else -> when (val verified = packFiles(context).verified(pack)) {
                    is Verified.Intact -> Opening.Ready(pack, verified.files)
                    Verified.Removed -> Opening.Removed
                }
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) render(opening) }
        }.start()
    }

    /** Leaving the screen ends the recitation (no background playback in this phase). */
    override fun onStop() {
        super.onStop()
        ticks.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        finish()
    }

    private fun render(opening: Opening) {
        val page = column()
        page.addView(title(getString(R.string.recitation_title)))
        val (pack, files) = when (opening) {
            is Opening.Ready -> opening.pack to opening.files
            Opening.Removed -> return message(page, R.string.recitation_unverified)
            Opening.NotFound -> return message(page, R.string.recitation_cannot_play)
        }
        page.addView(heading(pack.name))
        timings = pack.timings?.let { file -> files[file] }?.let { readTimings(it) }.orEmpty()
        if (timings.isEmpty()) page.addView(body(getString(R.string.recitation_no_timings), muted = true))
        val audio = files.getValue(pack.main)
        val media = MediaPlayer()
        try {
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(audio.path)
            media.prepare()
        } catch (_: IOException) {
            media.release()
            return message(page, R.string.recitation_cannot_play)
        } catch (_: IllegalStateException) {
            media.release()
            return message(page, R.string.recitation_cannot_play)
        }
        player = media
        now = body("")
        page.addView(now)
        val toggle: Button = actionButton(getString(R.string.recitation_play)) {}
        toggle.setOnClickListener {
            if (media.isPlaying) media.pause() else media.start()
            toggle.text = getString(if (media.isPlaying) R.string.recitation_pause else R.string.recitation_play)
            tick()
        }
        page.addView(toggle)
        show(page)
    }

    /** A timings file that does not parse is ignored: the audio still plays, the ayah is just not followed. */
    private fun readTimings(file: File): List<AyahTiming> = when (val parsed = parseTimings(file.readText(Charsets.UTF_8))) {
        is TimingsParse.Parsed -> parsed.timings
        is TimingsParse.Refused -> emptyList()
    }

    private fun tick() {
        val media = player ?: return
        val texts = Texts(this, language)
        now.text = when (val at = ayahAt(timings, Duration.ofMillis(media.currentPosition.toLong()))) {
            is AyahAt.At -> getString(R.string.recitation_ayah, texts.number(at.ref.surah.value), texts.number(at.ref.ayah))
            AyahAt.Between -> ""
        }
        ticks.removeCallbacksAndMessages(null)
        if (media.isPlaying) ticks.postDelayed({ tick() }, TICK_MILLIS)
    }

    private fun message(page: android.widget.LinearLayout, text: Int) {
        page.addView(panel().apply { addView(body(getString(text))) })
        show(page)
    }

    private sealed interface Opening {
        data class Ready(val pack: Pack, val files: Map<PackFile, File>) : Opening

        /** A file no longer matched its SHA-256; the pack was deleted. */
        data object Removed : Opening

        data object NotFound : Opening
    }

    companion object {
        const val EXTRA_PACK: String = "pack"

        /** How often the current ayah is looked up while playing (target). */
        private const val TICK_MILLIS = 250L
    }
}
