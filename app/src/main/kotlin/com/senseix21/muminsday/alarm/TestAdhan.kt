package com.senseix21.muminsday.alarm

import android.content.Context
import java.time.Duration
import java.time.Instant

/** F1 check 2: Test Adhan is a real alarm this far ahead, so the user can lock the screen and hear it. */
val TEST_ADHAN_DELAY: Duration = Duration.ofSeconds(30)

/** Sets a real alarm through the same receiver and delivery path as a prayer. */
fun scheduleTestAdhan(context: Context, now: Instant, delay: Duration = TEST_ADHAN_DELAY, method: Method = Method.AlarmClock): ApplyResult =
    AlarmScheduler(context).scheduleTest(now.plus(delay), method)
