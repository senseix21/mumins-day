package com.senseix21.muminsday.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.senseix21.muminsday.R
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.tracker.TrackerDay
import com.senseix21.muminsday.tracker.trackerWindow
import java.time.Instant

/**
 * The Salah tracker (INV-C5): today and the seven days before it, five dots a day, one tap to mark a prayer and one to
 * take the mark back. It shows only what the user marked. There is no number on this screen, no total, no streak and no
 * word about prayers not marked, and it sends no notification about itself. `tracker/` in core decides the window; the
 * date is the place's, computed here from the clock and handed to core (INV-T3).
 */
class TrackerActivity : BaseActivity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val zone = Stores.settings(this).read().place.zone
        val today = Instant.now().atZone(zone).toLocalDate()
        val log = Stores.salahLog(this)
        val days = trackerWindow(today, log.marks(today.minusDays(7), today))
        val texts = Texts(this, language)

        val page = column()
        page.addView(title(getString(R.string.tracker_title)))
        page.addView(body(getString(R.string.tracker_help), muted = true))
        for (day in days) {
            val label = if (day.date == today) getString(R.string.tracker_today) else texts.weekday(day.date)
            page.addView(heading(label))
            page.addView(dayRow(day, label, texts) { salah ->
                if (day.isPrayed(salah)) log.unmark(day.date, salah) else log.mark(day.date, salah, Instant.now())
                render()
            })
        }
        show(page)
    }

    /** Five columns, each a dot of at least 48 dp to touch and the prayer's name under it. Filled means marked. */
    private fun dayRow(day: TrackerDay, dayLabel: String, texts: Texts, onTap: (Salah) -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (salah in Salah.entries) {
            val name = texts.salah(salah)
            val prayed = day.isPrayed(salah)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumHeight = dp(MIN_TARGET_DP)
                    isClickable = true
                    isFocusable = true
                    contentDescription = getString(if (prayed) R.string.tracker_prayer_marked else R.string.tracker_prayer_open, name, dayLabel)
                    setOnClickListener { onTap(salah) }
                    addView(
                        View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                if (prayed) {
                                    setColor(Palette.accent)
                                } else {
                                    setColor(Palette.paper)
                                    setStroke(dp(2), Palette.accent)
                                }
                            }
                        },
                        LinearLayout.LayoutParams(dp(32), dp(32)).apply { topMargin = dp(8) },
                    )
                    addView(
                        TextView(context).apply {
                            text = name
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTextColor(Palette.muted)
                        },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            .apply { topMargin = dp(4); bottomMargin = dp(8) },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }
}
