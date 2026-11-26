package com.senseix21.muminsday.planner

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryTest {

    private val planned = Instant.parse("2026-11-26T11:12:00Z")

    private fun lateBy(duration: Duration) = decideDelivery(planned, planned.plus(duration))

    @Test
    fun INV_S7_on_time_and_up_to_the_limit_rings() {
        assertEquals(Delivery.Ring, lateBy(Duration.ZERO))
        assertEquals(Delivery.Ring, lateBy(Duration.ofMinutes(5)))
        assertEquals(Delivery.Ring, lateBy(LATE_LIMIT))
    }

    @Test
    fun INV_S7_past_the_limit_is_a_silent_notification() {
        assertEquals(Delivery.SilentNotification, lateBy(LATE_LIMIT.plusNanos(1)))
        assertEquals(Delivery.SilentNotification, lateBy(Duration.ofMinutes(15)))
        assertEquals(Delivery.SilentNotification, lateBy(Duration.ofDays(3)))
    }

    @Test
    fun INV_S7_an_early_arrival_rings() {
        assertEquals(Delivery.Ring, lateBy(Duration.ofSeconds(-30)))
    }

    @Test
    fun INV_S7_the_limit_is_ten_minutes() {
        assertEquals(Duration.ofMinutes(10), LATE_LIMIT)
    }
}
