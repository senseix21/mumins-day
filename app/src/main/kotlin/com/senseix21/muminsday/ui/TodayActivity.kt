package com.senseix21.muminsday.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.content.Body
import com.senseix21.muminsday.content.ContentKind
import com.senseix21.muminsday.content.Reviewed
import com.senseix21.muminsday.content.forDay
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.l10n.rightToLeft
import com.senseix21.muminsday.store.KindLoad
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.loadKind
import java.time.Instant

/**
 * Today's dua and today's hadith (phase 13), one section per kind. The records are the reviewer's, shown as written;
 * `content/` picks the day's record by the deed's rule (INV-C2), and the date is the place's, computed here from the
 * clock and handed to core (INV-T3). Arabic is laid out right to left inside an isolate (INV-L3). There is no count, no
 * mark and no notification for this screen (ADR-22: the deed nudge stays the only content notification).
 */
class TodayActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column()
        page.addView(title(getString(R.string.today_title)))
        val date = Instant.now().atZone(Stores.settings(this).read().place.zone).toLocalDate()
        val texts = Texts(this, language)
        for (kind in ContentKind.entries) {
            page.addView(heading(getString(headingOf(kind))))
            when (val loaded = loadKind(this, kind)) {
                is KindLoad.Loaded -> {
                    if (loaded.content.testFixture) page.addView(panel().apply { addView(body(getString(R.string.today_fixture_banner))) })
                    showRecord(page, loaded.content.forDay(date), texts)
                }
                is KindLoad.Rejected -> page.addView(body(getString(R.string.today_rejected)))
                KindLoad.Unavailable -> page.addView(body(getString(R.string.today_unavailable)))
            }
        }
        show(page)
    }

    private fun showRecord(page: LinearLayout, record: Reviewed, texts: Texts) {
        page.addView(arabic(record.body.arabic))
        when (val words = record.body) {
            is Body.DuaBody -> {
                page.addView(body(texts.data(words.transliteration), muted = true))
                page.addView(body(texts.data(if (language == Language.Bengali) words.bengali else words.english)).apply { textSize = 20f })
                page.addView(body(getString(R.string.today_occasion, texts.data(words.occasion)), muted = true))
            }
            is Body.HadithBody ->
                page.addView(body(texts.data(if (language == Language.Bengali) words.bengali else words.english)).apply { textSize = 20f })
        }
        page.addView(
            body(
                getString(R.string.today_source, texts.data(record.citation.book), texts.data(record.citation.number), texts.data(record.citation.edition)),
                muted = true,
            ),
        )
        page.addView(body(getString(R.string.today_grading, texts.data(record.review.grading)), muted = true))
    }

    /** The Arabic as the reviewer gave it: no digit conversion, right to left, in its own isolate (INV-L3). */
    private fun arabic(text: String): TextView = body(rightToLeft(text)).apply {
        textSize = 26f
        textDirection = View.TEXT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun headingOf(kind: ContentKind): Int = when (kind) {
        ContentKind.Dua -> R.string.today_dua
        ContentKind.Hadith -> R.string.today_hadith
    }
}
