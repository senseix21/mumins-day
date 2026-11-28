package com.senseix21.muminsday.khatam

import com.senseix21.muminsday.quran.JuzParse
import com.senseix21.muminsday.quran.MetadataParse
import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef
import com.senseix21.muminsday.quran.parseJuzStarts
import com.senseix21.muminsday.quran.parseMetadata
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 15 (ADR-24) over the real Tanzil metadata in `content/quran/quran-data.xml`: the plan counts ayahs, and every
 * number here (6236, 30 juz, 2:142) is read from that file, not typed from memory, except where a test says so.
 */
class KhatamTest {

    private val metadata = File("../content/quran/quran-data.xml").readText(Charsets.UTF_8)
    private val surahs = (parseMetadata(metadata) as MetadataParse.Parsed).surahs
    private val juzStarts = (parseJuzStarts(metadata) as JuzParse.Parsed).starts
    private val layout = (buildLayout(surahs, juzStarts) as LayoutCheck.Built).layout
    private val end = layout.end
    private val day = LocalDate.of(2026, 11, 28)

    private fun ref(surah: Int, ayah: Int) = VerseRef(checkNotNull(SurahNumber.of(surah)), ayah)

    @Test
    fun `the layout is the metadata's 114 surahs, 6236 ayahs and 30 juz`() {
        assertEquals(6236, end.value)
        assertEquals(30, juzStarts.size)
        assertEquals(ref(1, 1), juzStarts.first())
        // The second juz's start as the file gives it (<juz index="2" sura="2" aya="142" />).
        assertEquals(ref(2, 142), juzStarts[1])
        assertEquals(JuzNumber(1), layout.juzOf(AyahIndex.START))
        assertEquals(JuzNumber(2), layout.juzOf(checkNotNull(layout.indexOf(ref(2, 142)))))
        assertEquals(JuzNumber(1), layout.juzOf(checkNotNull(layout.indexOf(ref(2, 141)))))
        assertEquals(JuzNumber(30), layout.juzOf(AyahIndex(end.value - 1)))
    }

    @Test
    fun `every index and surah-ayah round trip, in order, and nothing outside the text has an index`() {
        var previous: VerseRef? = null
        for (i in 0 until end.value) {
            val at = layout.refAt(AyahIndex(i))
            assertEquals(AyahIndex(i), layout.indexOf(at))
            previous?.let { assertTrue(at.surah > it.surah || at.ayah == it.ayah + 1, "$it then $at") }
            previous = at
        }
        assertEquals(ref(114, 6), previous)
        assertEquals(null, layout.indexOf(ref(1, 8)))
        assertEquals(null, layout.indexOf(ref(2, 0)))
    }

    @Test
    fun `a juz list out of order or of the wrong length is refused`() {
        assertIs<LayoutCheck.Refused>(buildLayout(surahs, juzStarts.drop(1)))
        assertIs<LayoutCheck.Refused>(buildLayout(surahs, juzStarts.reversed()))
        assertIs<LayoutCheck.Refused>(buildLayout(surahs, listOf(ref(1, 9)) + juzStarts.drop(1)))
        assertIs<JuzParse.Refused>(parseJuzStarts(metadata.replace("<juz index=\"2\"", "<juz index=\"3\"")))
        assertIs<JuzParse.Refused>(parseJuzStarts("<juz index=\"1\" sura=\"1\" aya=\"1\" />"))
    }

    /** Reads exactly the portion each day and records it. */
    private fun followPlan(start: LocalDate, target: LocalDate): Pair<List<Pair<LocalDate, AyahRange>>, Khatam> {
        var khatam = startKhatam(start, target, end)
        val portions = mutableListOf<Pair<LocalDate, AyahRange>>()
        var date = start
        while (date <= target) {
            when (val today = khatamToday(khatam, date)) {
                is KhatamDay.ToRead -> {
                    portions += date to today.portion
                    khatam = markRead(khatam, date, today.portion.end)
                }
                KhatamDay.Complete -> break
                is KhatamDay.Read, is KhatamDay.NewDateNeeded -> error("$date: unexpected $today")
            }
            date = date.plusDays(1)
        }
        return portions to khatam
    }

    // The property the plan rests on: following it covers 1:1 to 114:6 once, with no gap and no overlap, by the target.
    @Test
    fun `portions over a plan sum exactly to 6236 ayahs with no gap or overlap, for any length of plan`() {
        val random = Random(15)
        val lengths = listOf(0L, 1L, 2L, 29L, 30L, 364L, 6235L, 6236L, 7000L) + List(150) { random.nextLong(0, 3000) }
        for (length in lengths) {
            val start = day.plusDays(random.nextLong(-2000, 2000))
            val target = start.plusDays(length)
            val (portions, khatam) = followPlan(start, target)
            assertEquals(AyahIndex.START, portions.first().second.first, "length $length")
            portions.zipWithNext().forEach { (a, b) -> assertEquals(a.second.end, b.second.first, "length $length: gap or overlap") }
            assertEquals(end, portions.last().second.end, "length $length")
            assertEquals(6236, portions.sumOf { it.second.ayahs }, "length $length")
            assertTrue(portions.all { it.second.ayahs >= 1 }, "length $length: an empty portion")
            assertTrue(portions.last().first <= target, "length $length: finished after the target")
            assertEquals(KhatamDay.Complete, khatamToday(khatam, target))
            // Portions differ by at most one ayah while the plan is spread over more than one ayah a day.
            val sizes = portions.map { it.second.ayahs }
            assertTrue(sizes.max() - sizes.min() <= 1 || length == 0L, "length $length: sizes $sizes")
        }
    }

    @Test
    fun `a plan whose target is today hands out everything that is left`() {
        val today = khatamToday(startKhatam(day, day, end), day)
        assertEquals(KhatamDay.ToRead(AyahRange(AyahIndex.START, end), AyahIndex.START), today)
        val halfway = markRead(startKhatam(day.minusDays(3), day, end), day.minusDays(1), AyahIndex(3000))
        assertEquals(AyahRange(AyahIndex(3000), end), (khatamToday(halfway, day) as KhatamDay.ToRead).portion)
    }

    @Test
    fun `a target in the past is a value that asks for a new date, and never a portion or a throw`() {
        val khatam = markRead(startKhatam(day.minusDays(40), day.minusDays(10), end), day.minusDays(20), AyahIndex(1000))
        assertEquals(KhatamDay.NewDateNeeded(AyahIndex(1000)), khatamToday(khatam, day))
        assertEquals(false, portionWaits(khatam, day))
        // A new date gives a portion again, from where the user had read.
        val moved = khatam.withTarget(day.plusDays(9))
        val portion = (khatamToday(moved, day) as KhatamDay.ToRead).portion
        assertEquals(AyahIndex(1000), portion.first)
        assertEquals(524, portion.ayahs) // (6236 - 1000) / 10 days, rounded up
    }

    @Test
    fun `finishing early is complete, and stays complete on every later date, the target's and after`() {
        var khatam = startKhatam(day, day.plusDays(60), end)
        val lastDay = day.plusDays(4)
        var date = day
        // Five days of today's portion and one stretch more; on the fifth, read on, stretch after stretch, to the end.
        while (khatamToday(khatam, date) != KhatamDay.Complete) {
            when (val today = khatamToday(khatam, date)) {
                is KhatamDay.ToRead -> khatam = markRead(khatam, date, today.portion.end)
                is KhatamDay.Read -> {
                    khatam = markRead(khatam, date, today.more.end)
                    if (date < lastDay) date = date.plusDays(1)
                }
                KhatamDay.Complete, is KhatamDay.NewDateNeeded -> error("unexpected $today")
            }
        }
        assertEquals(lastDay, date)
        assertTrue(date < day.plusDays(60), "finished on $date")
        for (later in listOf(date, day.plusDays(60), day.plusDays(400))) assertEquals(KhatamDay.Complete, khatamToday(khatam, later))
    }

    @Test
    fun `today's portion does not change however often the screen is opened or the mark taken back`() {
        val khatam = startKhatam(day, day.plusDays(29), end)
        val first = khatamToday(khatam, day) as KhatamDay.ToRead
        val read = markRead(khatam, day, first.portion.end)
        val done = khatamToday(read, day) as KhatamDay.Read
        assertEquals(first.portion, done.portion)
        assertEquals(AyahRange(first.portion.end, first.portion.end + first.portion.ayahs), done.more)
        val undone = undoToday(read, day)
        assertEquals(first, khatamToday(undone, day))
        // Taking back on a day with no mark changes nothing.
        assertEquals(read, undoToday(read, day.plusDays(1)))
    }

    @Test
    fun `a day without reading only lengthens the next portion and leaves no trace in the plan`() {
        val khatam = startKhatam(day, day.plusDays(9), end)
        val skippedAhead = khatamToday(khatam, day.plusDays(3)) as KhatamDay.ToRead
        assertEquals(AyahIndex.START, skippedAhead.portion.first)
        assertEquals(891, skippedAhead.portion.ayahs) // 6236 over the 7 days left, rounded up
        assertEquals(khatam, startKhatam(day, day.plusDays(9), end), "nothing about the days between is stored")
    }

    @Test
    fun `changing the target recomputes the portion from where the day began`() {
        val khatam = markRead(startKhatam(day, day.plusDays(29), end), day, AyahIndex(208))
        val sooner = khatamToday(khatam.withTarget(day.plusDays(9)), day.plusDays(1)) as KhatamDay.ToRead
        val later = khatamToday(khatam.withTarget(day.plusDays(99)), day.plusDays(1)) as KhatamDay.ToRead
        assertEquals(AyahIndex(208), sooner.portion.first)
        assertEquals(AyahIndex(208), later.portion.first)
        assertTrue(sooner.portion.ayahs > later.portion.ayahs)
        // Moving the target on the day of a mark keeps the mark: the day's portion is recomputed from the day's start.
        val sameDay = khatamToday(khatam.withTarget(day.plusDays(9)), day) as KhatamDay.ToRead
        assertEquals(AyahRange(AyahIndex.START, AyahIndex(624)), sameDay.portion)
        assertEquals(AyahIndex(208), sameDay.resumeAt)
    }

    // Travel: the date comes from the place's zone at the moment, so a flight can make "today" jump forward or back.
    @Test
    fun `a change of zone that moves the date back joins the day already marked and never splits or repeats a portion`() {
        val instant = Instant.parse("2026-11-28T20:30:00Z")
        val dhakaDate = instant.atZone(ZoneId.of("Asia/Dhaka")).toLocalDate() // 29 Nov, 02:30
        val londonDate = instant.atZone(ZoneId.of("Europe/London")).toLocalDate() // 28 Nov, 20:30
        assertEquals(dhakaDate.minusDays(1), londonDate)

        var khatam = startKhatam(day.minusDays(1), day.plusDays(20), end)
        val inDhaka = khatamToday(khatam, dhakaDate) as KhatamDay.ToRead
        khatam = markRead(khatam, dhakaDate, inDhaka.portion.end)
        // Flown west: the date is the day before. The day already read stays read; nothing new is handed out.
        val inLondon = khatamToday(khatam, londonDate)
        assertIs<KhatamDay.Read>(inLondon)
        assertEquals(inDhaka.portion, inLondon.portion)
        // Back east the next day: the next portion starts exactly where the last one ended.
        val next = khatamToday(khatam, dhakaDate.plusDays(1)) as KhatamDay.ToRead
        assertEquals(inDhaka.portion.end, next.portion.first)
    }

    // The behaviour property: any mix of reading, not reading, reading on, taking back, moving the date and travel.
    @Test
    fun `under any sequence of days and actions the plan stays consistent and never invents or loses an ayah`() {
        val random = Random(1515)
        repeat(300) { run ->
            var date = day.plusDays(random.nextLong(-500, 500))
            var khatam = startKhatam(date, date.plusDays(random.nextLong(0, 400)), end)
            repeat(random.nextInt(1, 120)) {
                date = when (random.nextInt(10)) {
                    0 -> date.minusDays(1)
                    1 -> date.plusDays(random.nextLong(2, 30))
                    else -> date.plusDays(1)
                }
                if (random.nextInt(15) == 0) khatam = khatam.withTarget(date.plusDays(random.nextLong(-20, 200)))
                val before = khatam
                val today = khatamToday(khatam, date)
                val effective = maxOf(date, khatam.markedOn)
                when (today) {
                    is KhatamDay.ToRead -> {
                        assertEquals(startOfDay(khatam, date), today.portion.first, "run $run")
                        assertTrue(today.portion.first <= today.resumeAt && today.resumeAt < today.portion.end, "run $run: $today")
                        assertTrue(today.portion.end <= end && today.portion.ayahs >= 1, "run $run: $today")
                        if (effective == khatam.target) assertEquals(end, today.portion.end, "run $run: the target day hands out the rest")
                        when (random.nextInt(4)) {
                            0 -> Unit
                            1 -> khatam = markRead(khatam, date, today.portion.end)
                            2 -> khatam = markRead(khatam, date, today.resumeAt + random.nextInt(0, today.portion.end - today.resumeAt + 1))
                            else -> khatam = undoToday(khatam, date)
                        }
                    }
                    is KhatamDay.Read -> {
                        assertEquals(khatam.position, today.more.first, "run $run")
                        assertTrue(today.more.ayahs >= 1 && today.more.end <= end, "run $run: $today")
                        if (random.nextBoolean()) khatam = markRead(khatam, date, today.more.end)
                    }
                    KhatamDay.Complete -> assertEquals(end, khatam.position, "run $run")
                    is KhatamDay.NewDateNeeded -> {
                        assertTrue(khatam.target < effective, "run $run")
                        assertEquals(khatam.position, today.resumeAt)
                    }
                }
                assertTrue(AyahIndex.START <= khatam.dayStart && khatam.dayStart <= khatam.position && khatam.position <= end, "run $run: $khatam")
                assertTrue(khatam.position >= before.position || khatam.position == khatam.dayStart, "run $run: moved back without an undo")
            }
        }
    }

    @Test
    fun `a stored row is used only when it describes a plan`() {
        assertIs<KhatamRestore.Restored>(restoreKhatam(day, day, 6236, 10, day, 5))
        for ((position, dayStart, total) in listOf(Triple(10, 11, 6236), Triple(-1, -1, 6236), Triple(7000, 0, 6236), Triple(0, 0, 0))) {
            assertEquals(KhatamRestore.Unreadable, restoreKhatam(day, day, total, position, day, dayStart))
        }
    }

    // INV-C4: nothing in the model can express a day without reading, a lag or a streak.
    @Test
    fun `no member of the khatam types is named for a miss, a lag, a streak or a count of days`() {
        val forbidden = Regex("miss|behind|late|skip|streak|overdue|lost|lag|daysLeft|count|score|fail", RegexOption.IGNORE_CASE)
        for (type in listOf(Khatam::class.java, KhatamDay.ToRead::class.java, KhatamDay.Read::class.java, KhatamDay.NewDateNeeded::class.java, AyahRange::class.java)) {
            val names = type.declaredFields.map { it.name } + type.declaredMethods.map { it.name }
            val offenders = names.filter { forbidden.containsMatchIn(it) }
            assertTrue(offenders.isEmpty(), "${type.simpleName} has $offenders")
        }
    }
}
