package com.senseix21.muminsday.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.senseix21.muminsday.alarm.exactAlarmGrantIntent

/**
 * Opens the screen that fixes one health reason. Only standard Android settings screens are used. No brand-specific
 * deep link is claimed (G32: none is known), and the battery fix opens the list of battery-optimised apps, which needs
 * no permission, not the one-tap request, which would need `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (INV-P3).
 */
fun startFix(activity: Activity, fix: Fix) {
    val intent = when (fix) {
        Fix.AllowExactAlarms -> exactAlarmGrantIntent(activity) ?: appDetails(activity)
        Fix.AllowNotifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        Fix.BatteryOptimisation -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        Fix.BrandSteps -> Intent(activity, BrandStepsActivity::class.java)
        Fix.ReminderHealth -> Intent(activity, ReminderHealthActivity::class.java)
    }
    try {
        activity.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A phone without that settings screen: this app's own details page is always there.
        activity.startActivity(appDetails(activity))
    }
}

fun appDetails(activity: Activity): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
