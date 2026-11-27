package com.senseix21.muminsday.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.app.NotificationManager
import android.os.Bundle
import android.widget.LinearLayout
import com.senseix21.muminsday.MainActivity
import com.senseix21.muminsday.R
import com.senseix21.muminsday.alarm.AlarmScheduler
import com.senseix21.muminsday.alarm.brandNeedsSteps
import com.senseix21.muminsday.alarm.exactAlarmGrantIntent
import com.senseix21.muminsday.l10n.Language
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.SilentMode
import com.senseix21.muminsday.store.Stores

/**
 * First run, shown once (F1, F2). Five steps in one Activity: language, district, reminders, silent mode, permissions.
 * Each step saves its answer as soon as it is given, so a restart (or the language switch, which recreates the screen)
 * loses nothing; the step number alone is kept across it. Location is not asked for: the district is picked by hand.
 */
class OnboardingActivity : BaseActivity() {

    private enum class Step { Language, District, Reminders, Silent, Allow }

    private var step = Step.Language

    /** The notification dialog was shown once; after a refusal Android shows no dialog again, so the button opens settings. */
    private var notificationsAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getInt(KEY_STEP)?.let { step = Step.entries[it] }
        notificationsAsked = savedInstanceState?.getBoolean(KEY_ASKED) ?: false
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step.ordinal)
        outState.putBoolean(KEY_ASKED, notificationsAsked)
    }

    private fun go(next: Step) {
        step = next
        render()
    }

    private fun render() {
        val texts = Texts(this, language)
        val page = column()
        when (step) {
            Step.Language -> {
                page.addView(title(getString(R.string.onb_language_title)))
                // Each language is named in itself, so a user who cannot read the current one can still choose.
                page.addView(actionButton(getString(R.string.language_name_bn)) { chooseLanguage(Language.Bengali) })
                page.addView(actionButton(getString(R.string.language_name_en)) { chooseLanguage(Language.English) })
            }
            Step.District -> {
                page.addView(title(getString(R.string.onb_district_title)))
                page.addView(body(getString(R.string.onb_district_help), muted = true))
                page.addView(
                    districtPicker(language, texts) { district ->
                        chooseDistrict(this, district)
                        go(Step.Reminders)
                    },
                )
            }
            Step.Reminders -> {
                page.addView(title(getString(R.string.onb_reminders_title)))
                page.addView(body(getString(R.string.onb_reminders_help), muted = true))
                val modes = listOf(ReminderMode.Adhan, ReminderMode.Tone, ReminderMode.Silent, ReminderMode.Off)
                var chosen = Stores.settings(this).read().reminders.let { current ->
                    // Off is not a starting point: someone who has never chosen sees Adhan selected.
                    modes.firstOrNull { mode -> mode != ReminderMode.Off && Salah.entries.all { current[it].mode == mode } }
                        ?: ReminderMode.Adhan
                }
                page.addView(choices(modes.map { texts.mode(it) to it }, chosen) { chosen = it })
                page.addView(
                    actionButton(getString(R.string.action_continue)) {
                        val settings = Stores.settings(this).read()
                        saveSettings(this, settings.copy(reminders = Reminders.all(chosen)))
                        go(Step.Silent)
                    },
                )
            }
            Step.Silent -> {
                page.addView(title(getString(R.string.onb_silent_title)))
                val options = listOf(
                    getString(R.string.silent_respect) to SilentMode.RespectSilent,
                    getString(R.string.silent_always) to SilentMode.AlwaysRing,
                )
                var chosen = Stores.settings(this).readApp().silentMode
                page.addView(choices(options, chosen) { chosen = it })
                page.addView(
                    actionButton(getString(R.string.action_continue)) {
                        val store = Stores.settings(this)
                        saveApp(this, store.readApp().copy(silentMode = chosen))
                        go(Step.Allow)
                    },
                )
            }
            Step.Allow -> allowStep(page)
        }
        show(page)
    }

    private fun chooseLanguage(chosen: Language) {
        val store = Stores.settings(this)
        saveApp(this, store.readApp().copy(language = chosen))
        step = Step.District
        // New language, new resources: draw the screen again from the top.
        recreate()
    }

    private fun allowStep(page: LinearLayout) {
        page.addView(title(getString(R.string.onb_allow_title)))
        var asked = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            asked = true
            page.addView(body(getString(R.string.perm_notifications_why)))
            if (notificationsAllowed()) {
                page.addView(body(getString(R.string.perm_granted), muted = true))
            } else {
                page.addView(
                    actionButton(getString(R.string.perm_notifications_button)) {
                        if (notificationsAsked) {
                            startFix(this, Fix.AllowNotifications)
                        } else {
                            notificationsAsked = true
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }

        // Android 12 and 12L only: the user grants exact alarms (ADR-4, T-INST-6). Elsewhere there is nothing to ask.
        val exactIntent = exactAlarmGrantIntent(this)
        if (exactIntent != null) {
            asked = true
            page.addView(body(getString(R.string.perm_exact_why)))
            if (AlarmScheduler(this).canScheduleExact()) {
                page.addView(body(getString(R.string.perm_granted), muted = true))
            } else {
                page.addView(actionButton(getString(R.string.perm_exact_button)) { startFix(this, Fix.AllowExactAlarms) })
            }
        }
        if (!asked) page.addView(body(getString(R.string.perm_none_needed)))

        if (brandNeedsSteps(Build.MANUFACTURER)) {
            page.addView(heading(getString(R.string.onb_brand_title)))
            page.addView(body(getString(R.string.health_brand)))
            page.addView(actionButton(getString(R.string.fix_brand), primary = false) { startFix(this, Fix.BrandSteps) })
        }

        // F1 #18: a force stop cannot be fixed by any app, so it is said once, in one sentence.
        page.addView(body(getString(R.string.onb_force_stop), muted = true))
        page.addView(actionButton(getString(R.string.action_finish)) { complete() })
    }

    private fun complete() {
        val store = Stores.settings(this)
        saveApp(this, store.readApp().copy(onboarded = true))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun notificationsAllowed(): Boolean = checkNotNull(getSystemService(NotificationManager::class.java)).areNotificationsEnabled()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) render()
    }

    private companion object {
        const val KEY_STEP = "step"
        const val KEY_ASKED = "asked"
        const val REQUEST_NOTIFICATIONS = 1
    }
}
