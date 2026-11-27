package com.senseix21.muminsday.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.senseix21.muminsday.R
import com.senseix21.muminsday.deed.Deed
import com.senseix21.muminsday.deed.DeedContent
import com.senseix21.muminsday.deed.SourceBook
import com.senseix21.muminsday.deed.SwapResult
import com.senseix21.muminsday.deed.cardFor
import com.senseix21.muminsday.deed.markDone
import com.senseix21.muminsday.deed.swap
import com.senseix21.muminsday.deed.weekDots
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.store.DeedsLoad
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.loadDeeds
import java.time.Instant
import java.time.LocalDate

/**
 * The day card (F4): one deed, where it comes from, one tap to mark it done, one swap, and seven dots. It decides
 * nothing: `deed/` chooses the card, `store/` keeps it, `l10n` writes digits. The local date is computed here from the
 * clock and the user's zone and handed to core, which never reads a clock (INV-T3).
 *
 * There is no count anywhere on this screen and no word about days missed (INV-C4).
 */
class DeedActivity : BaseActivity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val page = column()
        page.addView(title(getString(R.string.deed_title)))
        when (val loaded = loadDeeds(this)) {
            is DeedsLoad.Loaded -> showCard(page, loaded.content)
            // No card, and nothing about why beyond a plain sentence: a reviewer reads the build output, not the user.
            is DeedsLoad.Rejected -> page.addView(body(getString(R.string.deed_rejected)))
            DeedsLoad.Unavailable -> page.addView(body(getString(R.string.deed_unavailable)))
        }
        show(page)
    }

    private fun showCard(page: LinearLayout, content: DeedContent) {
        val now = Instant.now()
        val date = now.atZone(Stores.settings(this).read().place.zone).toLocalDate()
        val days = Stores.deedDays(this)

        // G5: the card is recorded the first time it is shown, so a content update cannot change today's card later.
        // Saved again only when cardFor changed it (a retired deed is replaced at once, F4 failure mode 5).
        val stored = days.get(date)
        val card = cardFor(date, content, stored)
        if (card != stored) days.save(card)

        val deed = content.find(card.deedId)
        if (deed == null) {
            page.addView(body(getString(R.string.deed_unavailable)))
            return
        }
        val texts = Texts(this, language)
        page.addView(
            column().apply {
                // Plain outline, not the pale warning panel: nothing here asks the user to act or worry.
                setPadding(dp(16), dp(12), dp(16), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Palette.paper)
                    setStroke(dp(2), Palette.line)
                }
                addView(deedText(deed, texts))
            },
        )
        page.addView(body(sourceLine(deed, texts), muted = true))

        val actions = actionsFor(card)
        if (!actions.canMarkDone) {
            // Quiet: a plain sentence. No colour change, no icon, no count.
            page.addView(body(getString(R.string.deed_done), muted = true))
        }
        if (actions.canMarkDone) {
            page.addView(actionButton(getString(R.string.deed_mark_done)) {
                days.save(markDone(card, Instant.now()))
                render()
            })
        }
        if (actions.canShowAnother) {
            page.addView(actionButton(getString(R.string.deed_another), primary = false) {
                when (val result = swap(card, content)) {
                    is SwapResult.Swapped -> days.save(result.card)
                    is SwapResult.NotAvailable -> Unit
                }
                render()
            })
        }

        page.addView(heading(getString(R.string.deed_week)))
        val done = days.doneDates(date.minusDays(6), date)
        page.addView(weekRow(date, weekDots(date, done), texts))
    }

    /** The record's own words, in the chosen language; only the digits in them follow the language (INV-L2). */
    private fun deedText(deed: Deed, texts: Texts) = body(
        texts.data(if (language == Language.Bengali) deed.textBn else deed.textEn),
    ).apply { textSize = 22f }

    private fun sourceLine(deed: Deed, texts: Texts): String {
        val book = getString(
            when (deed.source.book) {
                SourceBook.Bukhari -> R.string.deed_book_bukhari
                SourceBook.Muslim -> R.string.deed_book_muslim
            },
        )
        return getString(R.string.deed_source, book, texts.data(deed.source.number), texts.data(deed.source.edition))
    }

    /** Seven dots, oldest first, today last. Filled means done. Each is named for TalkBack by its weekday, never a number. */
    private fun weekRow(today: LocalDate, dots: List<Boolean>, texts: Texts): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
        dots.forEachIndexed { index, filled ->
            val day = today.minusDays((dots.size - 1 - index).toLong())
            val weekday = texts.weekday(day)
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (filled) {
                            setColor(Palette.accent)
                        } else {
                            setColor(Palette.paper)
                            setStroke(dp(2), Palette.accent)
                        }
                    }
                    contentDescription = if (filled) getString(R.string.deed_week_done, weekday) else weekday
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) },
            )
        }
    }
}
