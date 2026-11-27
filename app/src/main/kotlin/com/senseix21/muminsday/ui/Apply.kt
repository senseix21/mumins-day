package com.senseix21.muminsday.ui

import android.content.Context
import com.senseix21.muminsday.alarm.replan
import com.senseix21.muminsday.planner.PlannerSettings
import com.senseix21.muminsday.store.AppPrefs
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * Every change a screen makes goes through one of these two, so every change plans again (INV-S3, F1 #9): the single
 * `replan`, never a second path to the alarm queue.
 */
fun saveSettings(context: Context, settings: PlannerSettings) {
    Stores.settings(context).write(settings)
    replan(context, Instant.now())
}

fun saveApp(context: Context, prefs: AppPrefs) {
    Stores.settings(context).writeApp(prefs)
    replan(context, Instant.now())
}
