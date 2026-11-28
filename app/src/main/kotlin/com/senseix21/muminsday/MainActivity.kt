package com.senseix21.muminsday

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.senseix21.muminsday.alarm.ApplyResult
import com.senseix21.muminsday.alarm.PlaybackService
import com.senseix21.muminsday.alarm.TEST_ADHAN_DELAY
import com.senseix21.muminsday.alarm.currentHealth
import com.senseix21.muminsday.alarm.remindersEnabled
import com.senseix21.muminsday.alarm.replan
import com.senseix21.muminsday.alarm.scheduleTestAdhan
import com.senseix21.muminsday.clock.CalcTimesClock
import com.senseix21.muminsday.clock.ClockResult
import com.senseix21.muminsday.clock.FastingTime
import com.senseix21.muminsday.clock.Prayer
import com.senseix21.muminsday.clock.Upcoming
import com.senseix21.muminsday.clock.UpcomingFast
import com.senseix21.muminsday.clock.countdown
import com.senseix21.muminsday.clock.upcomingFast
import com.senseix21.muminsday.clock.upcomingSalah
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.hijri.hijriReadingAt
import com.senseix21.muminsday.hijri.suggestsRamadanMode
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.DistrictsLoad
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.loadDistricts
import com.senseix21.muminsday.ui.BaseActivity
import com.senseix21.muminsday.ui.DeedActivity
import com.senseix21.muminsday.ui.HadithBooksActivity
import com.senseix21.muminsday.ui.HisnulActivity
import com.senseix21.muminsday.ui.KhatamActivity
import com.senseix21.muminsday.ui.LicencesActivity
import com.senseix21.muminsday.ui.NO_TIME
import com.senseix21.muminsday.ui.OnboardingActivity
import com.senseix21.muminsday.ui.QiblaActivity
import com.senseix21.muminsday.ui.QuranActivity
import com.senseix21.muminsday.ui.Palette
import com.senseix21.muminsday.ui.ReminderHealthActivity
import com.senseix21.muminsday.ui.SettingsActivity
import com.senseix21.muminsday.ui.Texts
import com.senseix21.muminsday.ui.TodayActivity
import com.senseix21.muminsday.ui.TrackerActivity
import com.senseix21.muminsday.ui.ZakatActivity
import com.senseix21.muminsday.ui.actionButton
import com.senseix21.muminsday.ui.saveApp
import com.senseix21.muminsday.ui.body
import com.senseix21.muminsday.ui.column
import com.senseix21.muminsday.ui.dp
import com.senseix21.muminsday.ui.heading
import com.senseix21.muminsday.ui.noticeFor
import com.senseix21.muminsday.ui.panel
import com.senseix21.muminsday.ui.startFix
import com.senseix21.muminsday.ui.title
import java.time.Instant

/**
 * Home (F1, F2): today's five times, the countdown to the next, what could stop a reminder, and the buttons to test
 * and to stop the Adhan. It asks `clock` for every time, `alarm` for health and `l10n` for every word; it computes
 * and formats nothing itself.
 */
class MainActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_MILLIS)
        }
    }

    /** Shown under the Test Adhan button until the screen is drawn again. */
    private var testMessage: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Stores.settings(this).readApp().onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Stores.settings(this).readApp().onboarded) return
        // Opening the app silences a ringing Adhan (G6) and plans again, which also re-checks the exact-alarm
        // permission after the user returns from its settings screen (ADR-4). After a restore from backup this is the
        // first replan, and the health check below shows what the new phone still needs (F1 #12).
        PlaybackService.stop(this)
        replan(this, Instant.now())
        // G31: an Adhan that starts while this screen is open must be stoppable from it.
        PlaybackService.changed = { render() }
        refresh.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        PlaybackService.changed = null
        super.onPause()
    }

    private fun render() {
        val now = Instant.now()
        val settings = Stores.settings(this).read()
        val prefs = Stores.settings(this).readApp()
        val texts = Texts(this, language)
        val zone = settings.place.zone
        val clock = CalcTimesClock()
        val today = clock.dayTimes(now.atZone(zone).toLocalDate(), settings.place, settings.clock)
        // INV-T7: if Maghrib cannot be computed no time is invented; the Hijri day then changes at midnight.
        val maghrib = (today as? ClockResult.Computed)?.times?.get(Prayer.Maghrib)
        val hijri = hijriReadingAt(now, zone, maghrib, prefs.hijriAdjustment)
        val districtName = prefs.districtId?.let { id -> districtName(id.value, texts) }

        val page = column()
        page.addView(title(getString(R.string.app_name)))
        // G31: first thing on the screen, so an Adhan that starts while the app is open can always be stopped here.
        if (PlaybackService.isPlaying()) page.addView(actionButton(getString(R.string.action_stop)) { PlaybackService.stop(this) })
        if (districtName != null) page.addView(body(districtName, muted = true))
        page.addView(body(texts.date(now, zone), muted = true))
        // INV-H1: a Hijri date is never shown without the rule that produced it.
        page.addView(body(texts.hijri(hijri), muted = true))
        page.addView(body(texts.hijriRule(hijri) + " " + getString(R.string.hijri_rule_change_hint), muted = true).apply { textSize = 14f })
        // G33: the calculated month may suggest Ramadan mode, never turn it on. Only the user's tap does.
        if (suggestsRamadanMode(hijri, prefs.ramadanMode)) {
            page.addView(
                panel().apply {
                    addView(body(getString(R.string.ramadan_suggest)))
                    addView(actionButton(getString(R.string.ramadan_suggest_on)) {
                        saveApp(this@MainActivity, Stores.settings(this@MainActivity).readApp().copy(ramadanMode = true))
                        render()
                    })
                },
            )
        }

        // Health first: if a reminder cannot be promised, that is the most important thing on the screen (INV-S6).
        page.addView(healthView(now, remindersEnabled(settings), texts))

        // Ramadan mode (F2 #5, G33) is the user's switch, not a calendar: while it is on the countdown is to Sehri end or
        // Iftar, whichever is next, and those two are the largest items. Off, the home screen is the plain prayer list.
        if (prefs.ramadanMode) {
            when (val next = upcomingFast(clock, now, settings.place, settings.clock)) {
                is UpcomingFast.Found -> countdownTo(page, texts.fastingTime(next.time), now, next.at, texts)
                UpcomingFast.Unknown -> page.addView(heading(getString(R.string.home_next_fast_unknown)))
            }
        } else {
            when (val next = upcomingSalah(clock, now, settings.place, settings.clock)) {
                is Upcoming.Found -> countdownTo(page, texts.salah(next.salah), now, next.at, texts)
                Upcoming.Unknown -> page.addView(heading(getString(R.string.home_next_unknown)))
            }
        }

        fun timeText(prayer: Prayer): String = when (today) {
            is ClockResult.Computed -> texts.time(today.times[prayer], zone)
            is ClockResult.NotComputable -> NO_TIME
        }
        if (prefs.ramadanMode) {
            for (fasting in FastingTime.entries) {
                page.addView(
                    prayerRow(
                        texts.fastingTime(fasting),
                        timeText(
                            when (fasting) {
                                FastingTime.SehriEnd -> Prayer.SehriEnd
                                FastingTime.Iftar -> Prayer.Maghrib // INV-T2: Iftar is Maghrib
                            },
                        ),
                        sizeSp = LARGEST_SP,
                    ),
                )
            }
        }
        for (salah in Salah.entries) page.addView(prayerRow(texts.salah(salah), timeText(salah.prayer)))
        if (today is ClockResult.NotComputable) page.addView(body(getString(R.string.times_unavailable)))

        page.addView(actionButton(getString(R.string.deed_title), primary = false) {
            startActivity(Intent(this, DeedActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.qibla_title), primary = false) {
            startActivity(Intent(this, QiblaActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.tracker_title), primary = false) {
            startActivity(Intent(this, TrackerActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.zakat_title), primary = false) {
            startActivity(Intent(this, ZakatActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.today_title), primary = false) {
            startActivity(Intent(this, TodayActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.quran_title), primary = false) {
            startActivity(Intent(this, QuranActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.khatam_title), primary = false) {
            startActivity(Intent(this, KhatamActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.hisnul_title), primary = false) {
            startActivity(Intent(this, HisnulActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.hadith_books_title), primary = false) {
            startActivity(Intent(this, HadithBooksActivity::class.java))
        })

        page.addView(actionButton(getString(R.string.test_adhan)) { testAdhan() })
        testMessage?.let { page.addView(body(getString(it, texts.number(TEST_ADHAN_DELAY.seconds.toInt())))) }

        page.addView(actionButton(getString(R.string.health_log_title), primary = false) {
            startActivity(Intent(this, ReminderHealthActivity::class.java))
        })
        page.addView(actionButton(getString(R.string.home_settings), primary = false) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        page.addView(actionButton(getString(R.string.home_licences), primary = false) {
            startActivity(Intent(this, LicencesActivity::class.java))
        })
        show(page)
    }

    /** "Next: Iftar" and "3 hours 25 minutes left". */
    private fun countdownTo(page: LinearLayout, name: String, now: Instant, at: Instant, texts: Texts) {
        page.addView(heading(getString(R.string.home_next, name)))
        page.addView(body(getString(R.string.home_in, texts.countdown(countdown(now, at)))).apply { textSize = 22f })
    }

    private fun districtName(id: String, texts: Texts): String? {
        val district = (loadDistricts(this) as? DistrictsLoad.Loaded)?.check?.districts?.firstOrNull { it.id.value == id }
            ?: return null
        return texts.data(if (language == Language.Bengali) district.nameBn else district.nameEn)
    }

    private fun prayerRow(name: String, time: String, sizeSp: Float = 22f): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        minimumHeight = dp(48)
        setPadding(0, dp(8), 0, dp(8))
        // One focus stop for TalkBack: "Fajr, 5:10 am".
        isFocusable = true
        contentDescription = "$name, $time"
        addView(TextView(context).apply { text = name; textSize = sizeSp; setTextColor(Palette.ink) }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = time; textSize = sizeSp; setTextColor(Palette.ink) }, LinearLayout.LayoutParams(-2, -2))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun healthView(now: Instant, remindersOn: Boolean, texts: Texts): View {
        val box = column()
        if (!remindersOn) box.addView(body(getString(R.string.home_reminders_off)))
        when (val health = currentHealth(this, now)) {
            Health.Armed -> if (remindersOn) box.addView(body(getString(R.string.home_all_good)))
            is Health.Degraded -> health.reasons.forEach { reason ->
                val notice = noticeFor(reason)
                val message = notice.count?.let { resources.getQuantityString(notice.message, it, texts.number(it)) }
                    ?: getString(notice.message)
                box.addView(
                    panel().apply {
                        addView(body(message))
                        addView(actionButton(getString(notice.fixLabel)) { startFix(this@MainActivity, notice.fix) })
                    },
                )
            }
        }
        return box
    }

    private fun testAdhan() {
        testMessage = when (scheduleTestAdhan(this, Instant.now())) {
            is ApplyResult.Applied -> R.string.test_adhan_set
            ApplyResult.ExactAlarmsNotAllowed -> R.string.test_adhan_blocked
        }
        render()
    }

    private companion object {
        const val REFRESH_MILLIS = 30_000L

        /** Larger than the countdown (22 sp) and the prayer rows, so Sehri end and Iftar are the two largest items. */
        const val LARGEST_SP = 30f
    }
}
