package com.senseix21.muminsday.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.khatam.AyahRange
import com.senseix21.muminsday.khatam.Khatam
import com.senseix21.muminsday.khatam.KhatamDay
import com.senseix21.muminsday.khatam.QuranLayout
import com.senseix21.muminsday.khatam.khatamToday
import com.senseix21.muminsday.khatam.markRead
import com.senseix21.muminsday.khatam.startKhatam
import com.senseix21.muminsday.khatam.undoToday
import com.senseix21.muminsday.store.QuranOpen
import com.senseix21.muminsday.store.StoredKhatam
import com.senseix21.muminsday.store.Stores
import java.time.Instant
import java.time.LocalDate

/**
 * The khatam plan (phase 15, ADR-24): a finish date the user picks, and today's portion as surah:ayah ranges, worked out
 * afresh each day by `khatam/` in core. It never says the user is behind, never counts a day without reading, and a
 * finish before the date gets the same plain words as one on it (INV-C4). Every string it shows starts with `khatam_`
 * and is scanned by `KhatamStringsTest`. "Open" goes to the phase 14 reader at the portion's first unread ayah.
 */
class KhatamActivity : BaseActivity() {

    private var layout: QuranLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.khatam_title)))
        page.addView(body(getString(R.string.quran_preparing), muted = true))
        show(page)
        whenQuranOpens(this) { opened ->
            when (opened) {
                is QuranOpen.Ready -> {
                    layout = opened.reader.layout
                    render()
                }
                QuranOpen.Missing -> showMessage(getString(R.string.quran_missing))
                is QuranOpen.Unverified -> showMessage(getString(R.string.quran_unverified))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (layout != null) render()
    }

    private fun showMessage(text: String) {
        val page = column()
        page.addView(title(getString(R.string.khatam_title)))
        page.addView(panel().apply { addView(body(text)) })
        show(page)
    }

    /** The place's local date: the plan's days are the place's days (INV-T3: core is handed the date). */
    private fun today(): LocalDate = Instant.now().atZone(Stores.settings(this).read().place.zone).toLocalDate()

    private fun render() {
        val layout = layout ?: return
        val texts = Texts(this, language)
        val today = today()
        val page = column()
        page.addView(title(getString(R.string.khatam_title)))
        page.addView(body(getString(R.string.khatam_help), muted = true))
        when (val stored = Stores.khatam(this).read()) {
            StoredKhatam.NoPlan -> {
                page.addView(body(getString(R.string.khatam_none)))
                page.addView(actionButton(getString(R.string.khatam_start)) { pickDate(today, today.plusDays(DEFAULT_PLAN_DAYS)) { save(startKhatam(today, it, layout.end)) } })
            }
            is StoredKhatam.Plan -> showPlan(page, stored.khatam, layout, today, texts)
        }
        show(page)
    }

    private fun showPlan(page: LinearLayout, khatam: Khatam, layout: QuranLayout, today: LocalDate, texts: Texts) {
        page.addView(body(getString(R.string.khatam_target, texts.localDate(khatam.target))))
        when (val day = khatamToday(khatam, today)) {
            is KhatamDay.ToRead -> {
                page.addView(heading(getString(R.string.khatam_today)))
                addRange(page, day.portion, layout, texts)
                page.addView(actionButton(getString(R.string.khatam_open)) { openSurah(this, layout.refAt(day.resumeAt)) })
                page.addView(actionButton(getString(R.string.khatam_mark_read), primary = false) { save(markRead(khatam, today, day.portion.end)) })
            }
            is KhatamDay.Read -> {
                page.addView(panel().apply { addView(body(getString(R.string.khatam_today_read))) })
                page.addView(body(getString(R.string.khatam_more_help), muted = true))
                addRange(page, day.more, layout, texts)
                page.addView(actionButton(getString(R.string.khatam_open_more), primary = false) { openSurah(this, layout.refAt(day.more.first)) })
                page.addView(actionButton(getString(R.string.khatam_mark_more), primary = false) { save(markRead(khatam, today, day.more.end)) })
                page.addView(actionButton(getString(R.string.khatam_undo), primary = false) { save(undoToday(khatam, today)) })
            }
            KhatamDay.Complete -> {
                page.addView(panel().apply { addView(body(getString(R.string.khatam_complete))) })
                page.addView(actionButton(getString(R.string.khatam_start_again)) {
                    pickDate(today, today.plusDays(DEFAULT_PLAN_DAYS)) { save(startKhatam(today, it, layout.end)) }
                })
            }
            is KhatamDay.NewDateNeeded -> {
                page.addView(body(getString(R.string.khatam_new_date)))
                page.addView(actionButton(getString(R.string.khatam_open_where), primary = false) { openSurah(this, layout.refAt(day.resumeAt)) })
            }
        }
        if (khatamToday(khatam, today) != KhatamDay.Complete) {
            page.addView(actionButton(getString(R.string.khatam_change_date), primary = false) {
                pickDate(today, maxOf(today, khatam.target)) { save(khatam.withTarget(it)) }
            })
        }
        page.addView(actionButton(getString(R.string.khatam_end_plan), primary = false) { confirmRemove() })
    }

    /** "From surah 2, ayah 142, to surah 2, ayah 252" and the juz the range starts in, in the chosen language's digits. */
    private fun addRange(page: LinearLayout, range: AyahRange, layout: QuranLayout, texts: Texts) {
        val from = layout.refAt(range.first)
        val to = layout.lastOf(range)
        page.addView(
            body(
                getString(
                    R.string.khatam_range,
                    texts.number(from.surah.value),
                    texts.number(from.ayah),
                    texts.number(to.surah.value),
                    texts.number(to.ayah),
                ),
            ),
        )
        page.addView(body(getString(R.string.khatam_juz, texts.number(layout.juzOf(range.first).value)), muted = true))
    }

    /** A date from [earliest] on; the picker cannot choose an earlier one. Nothing is saved if the user cancels. */
    private fun pickDate(earliest: LocalDate, initial: LocalDate, then: (LocalDate) -> Unit) {
        val dialog = DatePickerDialog(this, { _, year, month, day ->
            val picked = LocalDate.of(year, month + 1, day)
            // The picker's minimum is in the phone's zone; the place's date decides.
            then(maxOf(earliest, picked))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        dialog.datePicker.minDate = System.currentTimeMillis() - PICKER_SLACK_MILLIS
        dialog.show()
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.khatam_end_confirm))
            .setPositiveButton(getString(R.string.khatam_end_yes)) { _, _ ->
                Stores.khatam(this).delete()
                render()
            }
            .setNegativeButton(getString(R.string.khatam_end_no), null)
            .show()
    }

    private fun save(khatam: Khatam) {
        Stores.khatam(this).write(khatam)
        render()
    }

    private companion object {
        /** Where the picker opens for a new plan: a month (target). Nothing is saved until the user picks. */
        const val DEFAULT_PLAN_DAYS = 29L

        /** The picker works in the phone's zone and the plan in the place's: a day of slack, and [pickDate] clamps to the place's date. */
        const val PICKER_SLACK_MILLIS = 24L * 60 * 60 * 1000
    }
}
