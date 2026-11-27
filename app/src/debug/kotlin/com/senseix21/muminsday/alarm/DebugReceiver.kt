package com.senseix21.muminsday.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.senseix21.muminsday.planner.AlarmId
import com.senseix21.muminsday.planner.AlarmSpec
import com.senseix21.muminsday.planner.Plan
import com.senseix21.muminsday.planner.ReminderMode
import com.senseix21.muminsday.planner.Reminders
import com.senseix21.muminsday.planner.RingMode
import com.senseix21.muminsday.planner.Salah
import com.senseix21.muminsday.store.Stores
import java.time.Duration
import java.time.Instant

/**
 * Debug builds only (see src/debug/AndroidManifest.xml). Each action calls the same production function the real
 * trigger calls, so tests through here exercise the real path. Arguments are `--es` and `--ei` extras.
 *
 *   SET_REMINDERS  mode=Adhan|Tone|Silent|Off      every prayer to that mode, then replan
 *   REPLAN
 *   TEST_ADHAN     [seconds=30] [method=clock|idle]
 *   SCHEDULE_PRAYER salah=Fajr mode=Adhan|Tone|Silent seconds=20    one real planned alarm, logged, via apply()
 *   FIRE           kind=Adhan|Tone|Silent|PreReminder salah=Fajr late=SECONDS   deliver as if planned `late` s ago
 *   DECLINATION    log the platform model's declination at points across Bangladesh (F3 #6 measurement)
 *   STOP           what opening the app does to a playing Adhan
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = Instant.now()
        val name = intent.action?.substringAfterLast('.')
        Log.i(LOG_TAG, "debug: $name")
        when (name) {
            "SET_REMINDERS" -> {
                val mode = ReminderMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: return
                val store = Stores.settings(context)
                store.write(store.read().copy(reminders = Reminders.all(mode)))
                replan(context, now)
            }
            "REPLAN" -> replan(context, now)
            "TEST_ADHAN" -> {
                val method = if (intent.getStringExtra("method") == "idle") Method.ExactWhileIdle else Method.AlarmClock
                val delay = Duration.ofSeconds(intent.getIntExtra("seconds", 30).toLong())
                Log.i(LOG_TAG, "debug: test adhan ${scheduleTestAdhan(context, now, delay, method)}")
            }
            "SCHEDULE_PRAYER" -> {
                val salah = Salah.entries.firstOrNull { it.name == intent.getStringExtra("salah") } ?: return
                val mode = RingMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: return
                val at = now.plusSeconds(intent.getIntExtra("seconds", 20).toLong())
                val zone = Stores.settings(context).read().place.zone
                val spec = AlarmSpec.PrayerAlarm(AlarmId.prayer(at.atZone(zone).toLocalDate(), salah), at, salah, mode, at)
                val plan = Plan(listOf(spec), emptyList())
                val result = AlarmScheduler(context).apply(plan)
                if (result is ApplyResult.Applied) Stores.alarmLog(context).recordPlanned(plan)
                Log.i(LOG_TAG, "debug: scheduled $spec -> $result")
            }
            "FIRE" -> {
                val late = intent.getIntExtra("late", 0).toLong()
                val planned = now.minusSeconds(late)
                context.sendBroadcast(
                    Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)
                        .putExtra(AlarmScheduler.EXTRA_KIND, intent.getStringExtra("kind"))
                        .putExtra(AlarmScheduler.EXTRA_SALAH, intent.getStringExtra("salah"))
                        .putExtra(AlarmScheduler.EXTRA_PLANNED_AT, planned.toEpochMilli()),
                )
            }
            "DECLINATION" -> {
                val points = listOf(
                    "test-1" to (23.5 to 90.0), "test-2" to (24.5 to 91.0), "test-3" to (22.5 to 89.5),
                    "Dhaka" to (23.81 to 90.41), "Chattogram" to (22.36 to 91.78), "Sylhet" to (24.89 to 91.87),
                    "Rajshahi" to (24.37 to 88.60), "Khulna" to (22.85 to 89.54), "Teknaf" to (20.86 to 92.30),
                    "Panchagarh" to (26.33 to 88.55), "Kuakata" to (21.82 to 90.12),
                )
                for ((name, at) in points) {
                    val declination = com.senseix21.muminsday.sensors.declinationAt(
                        com.senseix21.muminsday.clock.Place(
                            com.senseix21.muminsday.clock.Latitude.of(at.first)!!,
                            com.senseix21.muminsday.clock.Longitude.of(at.second)!!,
                            com.senseix21.muminsday.clock.Meters.of(10.0)!!,
                            java.time.ZoneId.of("Asia/Dhaka"),
                        ),
                        now,
                    )
                    Log.i(LOG_TAG, "declination $name ${at.first},${at.second} = ${declination?.value}")
                }
            }
            "STOP" -> PlaybackService.stop(context)
        }
    }
}
