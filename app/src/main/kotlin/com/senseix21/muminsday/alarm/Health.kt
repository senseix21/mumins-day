package com.senseix21.muminsday.alarm

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.senseix21.muminsday.health.BrandSteps
import com.senseix21.muminsday.health.HEALTH_LOOKBACK
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.health.HealthFacts
import com.senseix21.muminsday.health.assessHealth
import com.senseix21.muminsday.store.Stores
import java.time.Instant

/**
 * Reads the facts about this phone and hands them to the pure `assessHealth` (INV-S6). The home screen shows the result.
 */
fun currentHealth(context: Context, now: Instant): Health {
    val notifications = checkNotNull(context.getSystemService(NotificationManager::class.java))
    val power = checkNotNull(context.getSystemService(PowerManager::class.java))
    val facts = HealthFacts(
        exactAlarmsAllowed = AlarmScheduler(context).canScheduleExact(),
        notificationsAllowed = notifications.areNotificationsEnabled(),
        exemptFromBatteryOptimisation = power.isIgnoringBatteryOptimizations(context.packageName),
        // G27: "Confirmed" is the user's own answer from the brand-steps screen, stored for this phone only.
        brandSteps = when {
            !brandNeedsSteps(Build.MANUFACTURER) -> BrandSteps.NotNeeded
            Stores.device(context).brandStepsConfirmed() -> BrandSteps.Confirmed
            else -> BrandSteps.Needed
        },
        alarmLog = Stores.alarmLog(context).rowsSince(now.minus(HEALTH_LOOKBACK)).map { it.toHealthRecord() },
    )
    return assessHealth(now, facts)
}
