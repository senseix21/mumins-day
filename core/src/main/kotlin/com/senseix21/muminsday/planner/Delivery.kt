package com.senseix21.muminsday.planner

import java.time.Duration
import java.time.Instant

/**
 * How late an alarm may arrive and still ring (INV-S7). An Adhan well after its time misleads more than it helps, so
 * past this the user gets a silent notification instead. A target until phones measure it.
 */
public val LATE_LIMIT: Duration = Duration.ofMinutes(10)

/** What the shell does with an alarm the system has just delivered. */
public enum class Delivery { Ring, SilentNotification }

/**
 * INV-S7. [deliveredAt] is the clock reading when the alarm arrived; the shell passes it in. Exactly [LATE_LIMIT] late
 * still rings. An alarm that arrives early rings too: the system does not deliver before the set time, so "early"
 * means the clock moved, and a clock change triggers a replan of its own.
 */
public fun decideDelivery(plannedAt: Instant, deliveredAt: Instant): Delivery =
    if (Duration.between(plannedAt, deliveredAt) <= LATE_LIMIT) Delivery.Ring else Delivery.SilentNotification
