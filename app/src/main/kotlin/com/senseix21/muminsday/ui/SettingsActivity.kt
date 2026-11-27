package com.senseix21.muminsday.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.R
import com.senseix21.muminsday.alarm.currentHealth
import com.senseix21.muminsday.clock.AsrConvention
import com.senseix21.muminsday.clock.Minutes
import com.senseix21.muminsday.health.HEALTH_LOOKBACK
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.planner.PreReminder
import com.senseix21.muminsday.planner.Reminder
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.MAX_OFFSET_MINUTES
import com.senseix21.muminsday.store.SilentMode
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * Settings. Every change is saved the moment it is made and plans again through [saveSettings] or [saveApp]; there is
 * no Save button to forget (F1 #9). Plainness over cleverness: this screen needs no proof, only to be clear.
 */
class SettingsActivity : BaseActivity() {

    /** Whether the Advanced section is open. Not stored: it is a view state, and closed again next time. */
    private var advancedOpen = false

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val texts = Texts(this, language)
        val prefs = Stores.settings(this).readApp()
        val page = column()
        page.addView(title(getString(R.string.settings_title)))

        page.addView(heading(getString(R.string.settings_language)))
        page.addView(
            choices(
                listOf(getString(R.string.language_name_bn) to Language.Bengali, getString(R.string.language_name_en) to Language.English),
                prefs.language,
            ) { chosen ->
                saveApp(this, Stores.settings(this).readApp().copy(language = chosen))
                recreate()
            },
        )

        page.addView(heading(getString(R.string.settings_silent)))
        page.addView(
            choices(
                listOf(getString(R.string.silent_respect) to SilentMode.RespectSilent, getString(R.string.silent_always) to SilentMode.AlwaysRing),
                prefs.silentMode,
            ) { chosen -> saveApp(this, Stores.settings(this).readApp().copy(silentMode = chosen)) },
        )

        page.addView(heading(getString(R.string.settings_district)))
        page.addView(actionButton(getString(R.string.settings_district_change), primary = false) {
            startActivity(Intent(this, DistrictActivity::class.java))
        })

        page.addView(heading(getString(R.string.settings_prayers)))
        page.addView(heading(getString(R.string.settings_offset_title)).apply { textSize = 18f })
        page.addView(body(getString(R.string.settings_offset_help), muted = true))
        Salah.entries.forEach { page.addView(prayerSection(it, texts)) }

        val advanced = column()
        advanced.visibility = if (advancedOpen) View.VISIBLE else View.GONE
        advanced.addView(body(getString(R.string.settings_asr)))
        advanced.addView(
            choices(
                listOf(getString(R.string.asr_hanafi) to AsrConvention.Hanafi, getString(R.string.asr_shafi) to AsrConvention.Shafi),
                Stores.settings(this).read().clock.asr,
            ) { chosen ->
                val settings = Stores.settings(this).read()
                saveSettings(this, settings.copy(clock = settings.clock.copy(asr = chosen)))
            },
        )
        page.addView(
            actionButton(getString(R.string.settings_advanced), primary = false) {
                advancedOpen = !advancedOpen
                advanced.visibility = if (advancedOpen) View.VISIBLE else View.GONE
            },
        )
        page.addView(advanced)

        page.addView(heading(getString(R.string.settings_report)))
        page.addView(body(getString(R.string.report_help), muted = true))
        page.addView(actionButton(getString(R.string.settings_report)) { sendReport() })
        show(page)
    }

    private fun prayerSection(salah: Salah, texts: Texts): View {
        val box = column()
        val name = texts.salah(salah)
        box.addView(heading(name).apply { textSize = 20f })

        val settings = Stores.settings(this).read()
        val reminder = settings.reminders[salah]

        val modes = listOf(ReminderMode.Adhan, ReminderMode.Tone, ReminderMode.Silent, ReminderMode.Off)
        box.addView(labelled(getString(R.string.settings_mode), name, modes.map { texts.mode(it) }, modes, reminder.mode) { chosen ->
            val now = Stores.settings(this).read()
            val current = now.reminders[salah]
            // Off has no pre-reminder: nothing at all is planned for it (planner), so the stored value is cleared too.
            val pre = if (chosen == ReminderMode.Off) null else current.preReminder
            saveSettings(this, now.copy(reminders = now.reminders.with(salah, Reminder(chosen, pre))))
        })

        val choicesMinutes = (PRE_REMINDER_CHOICES + listOfNotNull(reminder.preReminder?.minutes)).distinct().sorted()
        val preValues: List<Int?> = listOf(null) + choicesMinutes
        val preLabels = listOf(getString(R.string.pre_none)) + choicesMinutes.map { texts.minutes(it) }
        box.addView(labelled(getString(R.string.settings_pre), name, preLabels, preValues, reminder.preReminder?.minutes) { chosen ->
            val now = Stores.settings(this).read()
            val current = now.reminders[salah]
            saveSettings(this, now.copy(reminders = now.reminders.with(salah, Reminder(current.mode, chosen?.let(PreReminder::of)))))
        })

        box.addView(offsetRow(salah, name, texts))
        return box
    }

    private fun <T> labelled(label: String, prayerName: String, labels: List<String>, values: List<T>, selected: T, onChanged: (T) -> Unit): View {
        val box = column()
        val caption = body("$label ($prayerName)", muted = true)
        val spinner = Spinner(this).apply {
            id = View.generateViewId()
            minimumHeight = dp(MIN_TARGET_DP)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
            val start = values.indexOf(selected).coerceAtLeast(0)
            setSelection(start, false)
            var current = values[start]
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (values[position] != current) {
                        current = values[position]
                        onChanged(current)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        // TalkBack reads the caption, which names the prayer, with the control.
        caption.labelFor = spinner.id
        box.addView(caption)
        box.addView(spinner)
        return box
    }

    private fun offsetRow(salah: Salah, name: String, texts: Texts): View {
        var minutes = Stores.settings(this).read().clock.offsets[salah.prayer].value
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val earlier = stepButton("−", getString(R.string.offset_earlier, name))
        val later = stepButton("+", getString(R.string.offset_later, name))
        val value = TextView(this).apply {
            textSize = 20f
            setTextColor(Palette.ink)
            gravity = android.view.Gravity.CENTER
        }

        fun refresh() {
            value.text = texts.signedMinutes(minutes)
            earlier.isEnabled = minutes > -MAX_OFFSET_MINUTES
            later.isEnabled = minutes < MAX_OFFSET_MINUTES
        }

        fun change(by: Int) {
            minutes = (minutes + by).coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES)
            val now = Stores.settings(this).read()
            saveSettings(this, now.copy(clock = now.clock.copy(offsets = now.clock.offsets.with(salah.prayer, Minutes(minutes)))))
            refresh()
        }
        earlier.setOnClickListener { change(-1) }
        later.setOnClickListener { change(1) }
        refresh()
        row.addView(earlier, LinearLayout.LayoutParams(dp(MIN_TARGET_DP + 8), dp(MIN_TARGET_DP + 8)))
        row.addView(value, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(later, LinearLayout.LayoutParams(dp(MIN_TARGET_DP + 8), dp(MIN_TARGET_DP + 8)))
        return row
    }

    /** A symbol button has no text to read, so its content description says what it does and for which prayer. */
    private fun stepButton(symbol: String, description: String): Button = Button(this).apply {
        text = symbol
        textSize = 22f
        isAllCaps = false
        contentDescription = description
        minWidth = dp(MIN_TARGET_DP)
        minHeight = dp(MIN_TARGET_DP)
        minimumWidth = dp(MIN_TARGET_DP)
        minimumHeight = dp(MIN_TARGET_DP)
        setPadding(0, 0, 0, 0)
    }

    /** G15: the support address is not known, so the draft has no recipient and the user picks one. */
    private fun sendReport() {
        val now = Instant.now()
        val log = Stores.alarmLog(this).rowsSince(now.minus(HEALTH_LOOKBACK))
        val text = reportText(
            ReportFacts(
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                health = currentHealth(this, now),
                log = log,
            ),
        )
        val draft = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT)
            .putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(draft)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.report_no_email, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        /** Offered in the pre-reminder list; a stored value outside it is added so it is never silently changed. */
        val PRE_REMINDER_CHOICES = listOf(5, 10, 15, 20, 30, 60)
        const val REPORT_SUBJECT = "Mu'mins DAY report"
    }
}
