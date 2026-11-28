package com.senseix21.muminsday.l10n

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The words are obviously fake and carry no digits, so any digit in the output came from the formatter (INV-L2). */
class TimeTextTest {

    private val bengali = Words(
        language = Language.Bengali,
        dayParts = DayPartWords("DAWN", "MORNING", "NOON", "AFTERNOON", "EVENING", "NIGHT"),
        am = "AM",
        pm = "PM",
        months = (1..12).map { "MONTH${"ABCDEFGHIJKL"[it - 1]}" },
        weekdays = (1..7).map { "DAY${"ABCDEFG"[it - 1]}" },
        hijriMonths = (1..12).map { "HIJRI${"ABCDEFGHIJKL"[it - 1]}" },
    )
    private val english = bengali.copy(language = Language.English)
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun at(text: String, zone: ZoneId = dhaka): Instant = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun twelve_hour_bengali_uses_day_part_words_and_bengali_digits() {
        assertEquals("DAWN ৪:৪৫", timeText(at("2026-11-27T04:45"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("MORNING ৬:০৫", timeText(at("2026-11-27T06:05"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("NOON ১২:০০", timeText(at("2026-11-27T12:00"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("AFTERNOON ৩:৪০", timeText(at("2026-11-27T15:40"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("EVENING ৫:১০", timeText(at("2026-11-27T17:10"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("NIGHT ৮:৩০", timeText(at("2026-11-27T20:30"), dhaka, ClockStyle.Hour12, bengali))
        assertEquals("NIGHT ১২:০৫", timeText(at("2026-11-27T00:05"), dhaka, ClockStyle.Hour12, bengali))
    }

    @Test
    fun twenty_four_hour_and_english() {
        assertEquals("০৫:০৯", timeText(at("2026-11-27T05:09"), dhaka, ClockStyle.Hour24, bengali))
        assertEquals("১৭:৩০", timeText(at("2026-11-27T17:30"), dhaka, ClockStyle.Hour24, bengali))
        assertEquals("05:09", timeText(at("2026-11-27T05:09"), dhaka, ClockStyle.Hour24, english))
        assertEquals("5:09 AM", timeText(at("2026-11-27T05:09"), dhaka, ClockStyle.Hour12, english))
        assertEquals("12:00 PM", timeText(at("2026-11-27T12:00"), dhaka, ClockStyle.Hour12, english))
        assertEquals("12:30 AM", timeText(at("2026-11-27T00:30"), dhaka, ClockStyle.Hour12, english))
    }

    @Test
    fun seconds_are_dropped_not_rounded() {
        assertEquals("০৫:৩৯", timeText(at("2026-11-27T05:39:59"), dhaka, ClockStyle.Hour24, bengali))
    }

    @Test
    fun the_zone_is_an_argument() {
        val instant = at("2026-11-27T12:00", ZoneOffset.UTC)
        assertEquals("১২:০০", timeText(instant, ZoneOffset.UTC, ClockStyle.Hour24, bengali))
        assertEquals("১৮:০০", timeText(instant, dhaka, ClockStyle.Hour24, bengali))
    }

    @Test
    fun date_text() {
        // 2026-11-27 is a Friday.
        assertEquals("DAYE, ২৭ MONTHK ২০২৬", dateText(at("2026-11-27T10:00"), dhaka, bengali))
        assertEquals("DAYE, 27 MONTHK 2026", dateText(at("2026-11-27T10:00"), dhaka, english))
        // The date is the one in the zone: 20:00 UTC on the 26th is already the 27th in Dhaka.
        assertEquals("DAYE, ২৭ MONTHK ২০২৬", dateText(at("2026-11-26T20:00", ZoneOffset.UTC), dhaka, bengali))
        assertEquals("DAYD, ২৬ MONTHK ২০২৬", dateText(at("2026-11-26T20:00", ZoneOffset.UTC), ZoneOffset.UTC, bengali))
    }

    @Test
    fun number_text() {
        assertEquals("১২০৯", numberText(1209, Language.Bengali))
        assertEquals("-৩০", numberText(-30, Language.Bengali))
        assertEquals("1209", numberText(1209, Language.English))
    }

    @Test
    fun every_part_of_the_day_is_reached_and_the_hours_cover_the_day() {
        assertEquals(DayPart.entries.toSet(), (0..23).map(::dayPartOf).toSet())
    }

    private val zones = listOf("Asia/Dhaka", "UTC", "America/New_York", "Pacific/Kiritimati", "Asia/Kolkata", "Australia/Lord_Howe")
        .map(ZoneId::of)

    @Test
    fun T_UNIT_9_property_no_latin_digit_leaves_a_bengali_formatter() {
        val random = Random(21)
        repeat(5000) {
            val instant = Instant.ofEpochSecond(random.nextLong(-2_000_000_000L, 4_000_000_000L))
            val zone = zones.random(random)
            val outputs = listOf(
                timeText(instant, zone, ClockStyle.Hour12, bengali),
                timeText(instant, zone, ClockStyle.Hour24, bengali),
                dateText(instant, zone, bengali),
                numberText(random.nextInt(), Language.Bengali),
            )
            for (output in outputs) assertFalse(output.any { it in '0'..'9' }, "Latin digit in '$output'")
        }
    }

    @Test
    fun property_times_round_trip() {
        val random = Random(22)
        val reverse = bengali.dayParts.let { listOf(it.dawn, it.morning, it.noon, it.afternoon, it.evening, it.night) }
        repeat(5000) {
            val instant = Instant.ofEpochSecond(random.nextLong(0, 4_000_000_000L))
            val zone = zones.random(random)
            val local = instant.atZone(zone)

            val twentyFour = timeText(instant, zone, ClockStyle.Hour24, bengali).map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
            val (hour24, minute24) = twentyFour.split(":").map(String::toInt)
            assertEquals(local.hour to local.minute, hour24 to minute24)

            val twelve = timeText(instant, zone, ClockStyle.Hour12, bengali)
            val (word, clock) = twelve.split(" ")
            val (hour12, minute12) = clock.map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("").split(":").map(String::toInt)
            assertEquals(local.minute, minute12)
            assertTrue(word in reverse && hour12 in 1..12, twelve)
            // The 12-hour reading names the same hour modulo 12 as the 24-hour one.
            assertEquals(local.hour % 12, hour12 % 12, twelve)
            // And the day part word is the one for that hour, so 12-hour times cannot be read as the wrong half-day.
            assertEquals(bengali.dayParts.of(dayPartOf(local.hour)), word)

            val englishText = timeText(instant, zone, ClockStyle.Hour12, english)
            val (englishClock, marker) = englishText.split(" ")
            val (englishHour, englishMinute) = englishClock.split(":").map(String::toInt)
            assertEquals(local.hour, englishHour % 12 + if (marker == "PM") 12 else 0)
            assertEquals(local.minute, englishMinute)
        }
    }
}
