package com.senseix21.muminsday.packs

import com.senseix21.muminsday.quran.SurahNumber
import com.senseix21.muminsday.quran.VerseRef
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 16 (ADR-25), the pure parts of the pack machinery. Every pack here is a TEST PACK with made-up bytes; no tafsir,
 * translation or recitation exists in this repository.
 */
class PacksTest {

    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    // ----- manifest -----

    @Test
    fun `a manifest with only comments is an empty list, which is what a release ships`() {
        assertEquals(ManifestParse.Accepted(emptyList()), parseManifest("# no packs yet\n\n# id|kind|name|size|sha256|path\n"))
        assertEquals(ManifestParse.Accepted(emptyList()), parseManifest(""))
    }

    @Test
    fun `a tafsir line and an audio line with timings are read exactly`() {
        val text = "test-tafsir|tafsir|TEST PACK tafsir|1200|$hashA|t/tafsir.txt\r\n" +
            "test-tone|audio|TEST PACK tone|48044|$hashB|a/tone.wav|31|$hashA|a/tone-timings.txt\n"
        val packs = (parseManifest(text) as ManifestParse.Accepted).packs
        assertEquals(2, packs.size)
        assertEquals(PackKind.Tafsir, packs[0].kind)
        assertEquals(null, packs[0].timings)
        assertEquals(ByteCount(1200), packs[0].size)
        assertEquals("t/tafsir.txt", packs[0].main.path.value)
        assertEquals(PackKind.Audio, packs[1].kind)
        assertEquals(ByteCount(48044 + 31), packs[1].size)
        assertEquals(listOf("a/tone.wav", "a/tone-timings.txt"), packs[1].files.map { it.path.value })
    }

    @Test
    fun `every kind of bad line is refused with its line number, and one bad line refuses the whole file`() {
        val good = "ok|tafsir|TEST PACK|10|$hashA|ok.txt"
        val cases = mapOf(
            "ok|tafsir|TEST|10|$hashA" to ManifestProblemKind.FieldCount,
            "Bad_Id|tafsir|TEST|10|$hashA|b.txt" to ManifestProblemKind.BadId,
            "ok|tafsir|TEST|10|$hashA|other.txt" to ManifestProblemKind.DuplicateId,
            "x|video|TEST|10|$hashA|x.txt" to ManifestProblemKind.UnknownKind,
            "x|tafsir|  |10|$hashA|x.txt" to ManifestProblemKind.BlankName,
            "x|tafsir|TEST|0|$hashA|x.txt" to ManifestProblemKind.BadSize,
            "x|tafsir|TEST|-5|$hashA|x.txt" to ManifestProblemKind.BadSize,
            "x|tafsir|TEST|১০|$hashA|x.txt" to ManifestProblemKind.BadSize,
            "x|tafsir|TEST|10|${hashA.uppercase()}|x.txt" to ManifestProblemKind.BadHash,
            "x|tafsir|TEST|10|${hashA.drop(1)}|x.txt" to ManifestProblemKind.BadHash,
            "x|tafsir|TEST|10|$hashA|ok.txt" to ManifestProblemKind.DuplicatePath,
            "x|tafsir|TEST|10|$hashA|x.txt|5|$hashB|y.txt" to ManifestProblemKind.TimingsOnTafsir,
        )
        for ((line, kind) in cases) {
            val refused = parseManifest("$good\n# comment\n$line\n")
            assertIs<ManifestParse.Refused>(refused, line)
            assertTrue(ManifestProblem(3, kind) in refused.problems, "$line: ${refused.problems}")
        }
    }

    @Test
    fun `no path that could leave the host's root or the pack's folder is accepted`() {
        val bad = listOf("", "/abs.txt", "../up.txt", "a/../b", "a/./b", "a//b", "a\\b", "c:/x", "a?b", "a#b", "%2e%2e/x", "a b", "a/", "x".repeat(201))
        for (path in bad) assertEquals(null, PackPath.of(path), path)
        for (path in listOf("a", "a.txt", "dir/sub/file-1_2.mp3", "...x")) assertEquals(path, PackPath.of(path)?.value)
        // Property: any accepted path, joined to a host, stays under that host's root and has no dot segment.
        val random = Random(16)
        val alphabet = "ab./\\:-_%?#"
        repeat(2000) {
            val candidate = String(CharArray(random.nextInt(1, 12)) { alphabet[random.nextInt(alphabet.length)] })
            val path = PackPath.of(candidate) ?: return@repeat
            val url = packUrl(packSource("packs.example.org") as PackSource.Host, path)
            assertTrue(url.startsWith("https://packs.example.org/"), url)
            assertTrue(path.value.split('/').none { it == "." || it == ".." || it.isEmpty() }, path.value)
        }
    }

    // ----- host -----

    @Test
    fun `an empty host means no packs, and only a plain DNS name is a host`() {
        assertEquals(PackSource.NotAvailable, packSource(""))
        for (bad in listOf("localhost", "https://packs.example.org", "packs.example.org/x", "packs.example.org:443", "Packs.Example.org", "user@packs.example.org", " packs.example.org", "-a.example.org", "a..org")) {
            assertIs<PackSource.Invalid>(packSource(bad), bad)
        }
        val host = packSource("packs.example.org")
        assertIs<PackSource.Host>(host)
        assertEquals("https://packs.example.org/t/a.txt", packUrl(host, checkNotNull(PackPath.of("t/a.txt"))))
    }

    // ----- hash -----

    @Test
    fun `the streamed hash equals the one-shot hash however the bytes are split, and one changed byte is a mismatch`() {
        val random = Random(256)
        repeat(200) {
            val bytes = random.nextBytes(random.nextInt(0, 5000))
            val expected = checkNotNull(Sha256.of(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
            val builder = Sha256Builder()
            var at = 0
            while (at < bytes.size) {
                val length = random.nextInt(1, 700).coerceAtMost(bytes.size - at)
                builder.update(bytes, at, length)
                at += length
            }
            val actual = builder.finish()
            assertEquals(HashCheck.Matches, checkHash(expected, actual))
            if (bytes.isNotEmpty()) {
                val at = random.nextInt(bytes.size)
                val changed = bytes.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }
                assertIs<HashCheck.Mismatch>(checkHash(expected, Sha256Builder().update(changed, 0, changed.size).finish()))
            }
        }
        // The known SHA-256 of no bytes.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256Builder().finish().hex)
    }

    // ----- resume -----

    @Test
    fun `where a file starts depends only on how much of it is on the phone`() {
        val size = ByteCount(1000)
        assertEquals(Start.Fresh, startFrom(ByteCount.ZERO, size))
        assertEquals(Start.From(ByteCount(1)), startFrom(ByteCount(1), size))
        assertEquals(Start.From(ByteCount(999)), startFrom(ByteCount(999), size))
        assertEquals(Start.AlreadyWhole, startFrom(size, size))
        assertEquals(Start.Discard, startFrom(ByteCount(1001), size))
        assertEquals("bytes=999-", rangeHeader(ByteCount(999)))
    }

    @Test
    fun `a reply is written only if it is the whole file or exactly the rest of it`() {
        val size = ByteCount(1000)
        val from = ByteCount(400)
        assertEquals(Reply.Write(ByteCount.ZERO, append = false), readReply(from, 200, null, size))
        assertEquals(Reply.Write(from, append = true), readReply(from, 206, "bytes 400-999/1000", size))
        assertEquals(Reply.Write(ByteCount.ZERO, append = false), readReply(ByteCount.ZERO, 206, "bytes 0-999/1000", size))
        for (range in listOf(null, "", "bytes 401-999/1000", "bytes 400-998/1000", "bytes 400-999/*", "bytes 400-999/1001", "items 400-999/1000")) {
            assertEquals(Reply.Refused(Refusal.WrongRange, 206), readReply(from, 206, range, size), "$range")
        }
        assertEquals(Reply.Refused(Refusal.RangeNotSatisfiable, 416), readReply(from, 416, null, size))
        for (status in listOf(301, 302, 307, 308)) assertEquals(Reply.Refused(Refusal.Redirect, status), readReply(from, status, null, size))
        for (status in listOf(204, 404, 500, 503)) assertEquals(Reply.Refused(Refusal.Status, status), readReply(from, status, null, size))
    }

    // Property: chopping a file into any number of interrupted pieces and resuming each time rebuilds it exactly.
    @Test
    fun `resuming after any pattern of interruptions rebuilds the file byte for byte`() {
        val random = Random(1616)
        repeat(300) {
            val file = random.nextBytes(random.nextInt(1, 3000))
            val size = ByteCount(file.size.toLong())
            var onPhone = ByteArray(0)
            var guard = 0
            while (true) {
                val reply = when (val start = startFrom(ByteCount(onPhone.size.toLong()), size)) {
                    Start.AlreadyWhole -> break
                    Start.Discard -> error("never more than the file")
                    Start.Fresh -> readReply(ByteCount.ZERO, 200, null, size)
                    // Some servers ignore the range and send everything again.
                    is Start.From -> if (random.nextInt(5) == 0) {
                        readReply(start.offset, 200, null, size)
                    } else {
                        readReply(start.offset, 206, "bytes ${start.offset.bytes}-${file.size - 1}/${file.size}", size)
                    }
                }
                val write = reply as Reply.Write
                val body = file.copyOfRange(write.from.bytes.toInt(), file.size)
                val arrived = body.copyOf(random.nextInt(0, body.size + 1))
                onPhone = (if (write.append) onPhone else ByteArray(0)) + arrived
                assertTrue(onPhone.contentEquals(file.copyOf(onPhone.size)), "the partial file is always a prefix of the file")
                check(++guard < 10_000)
            }
            assertTrue(onPhone.contentEquals(file))
        }
    }

    // ----- the download's life -----

    private val events: List<(Random) -> DownloadEvent> = listOf(
        { DownloadEvent.Tap },
        { r -> DownloadEvent.Progress(ByteCount(r.nextLong(0, 1100))) },
        { DownloadEvent.BodiesEnded },
        { DownloadEvent.HashesMatched },
        { DownloadEvent.HashMismatched },
        { DownloadEvent.Cancel },
        { r -> DownloadEvent.Lost(DownloadFailure.entries[r.nextInt(DownloadFailure.entries.size)]) },
        { DownloadEvent.Delete },
    )

    @Test
    fun `under any sequence of events no request starts without a tap and nothing is ready unless its hash matched`() {
        val random = Random(1600)
        val total = ByteCount(1000)
        repeat(3000) {
            var state: DownloadState = stateOnPhone(ByteCount(random.nextLong(0, 1200)), random.nextInt(4) == 0, total)
            repeat(random.nextInt(1, 60)) {
                val event = events[random.nextInt(events.size)](random)
                val next = step(state, event, total)
                if (!mayRequest(state) && mayRequest(next)) assertEquals(DownloadEvent.Tap, event, "$state -> $next")
                if (next is DownloadState.Ready && state !is DownloadState.Ready) {
                    assertEquals(DownloadState.Verifying, state)
                    assertEquals(DownloadEvent.HashesMatched, event)
                }
                if (event == DownloadEvent.Delete) assertEquals(DownloadState.Absent, next)
                if (next is DownloadState.Failed && next.failure == DownloadFailure.HashMismatch) assertEquals(ByteCount.ZERO, next.received)
                when (next) {
                    is DownloadState.Downloading -> assertTrue(next.received <= total)
                    is DownloadState.Stopped -> assertTrue(next.received <= total)
                    is DownloadState.Failed -> assertTrue(next.received <= total)
                    DownloadState.Absent, DownloadState.Verifying, is DownloadState.Ready -> Unit
                }
                if (state is DownloadState.Downloading && event == DownloadEvent.Tap) assertEquals(state, next, "a second tap starts nothing")
                state = next
            }
        }
    }

    @Test
    fun `a whole download, a cancel and a resume, and a mismatch, step by step`() {
        val total = ByteCount(100)
        var state: DownloadState = DownloadState.Absent
        state = step(state, DownloadEvent.Tap, total)
        state = step(state, DownloadEvent.Progress(ByteCount(40)), total)
        state = step(state, DownloadEvent.Cancel, total)
        assertEquals(DownloadState.Stopped(ByteCount(40), total), state)
        // A late byte count after the cancel changes nothing.
        assertEquals(state, step(state, DownloadEvent.Progress(ByteCount(50)), total))
        state = step(state, DownloadEvent.Tap, total)
        assertEquals(DownloadState.Downloading(ByteCount(40), total), state)
        // A server that ignored the range starts again from nothing: the count follows the bytes on the phone.
        state = step(state, DownloadEvent.Progress(ByteCount(10)), total)
        assertEquals(DownloadState.Downloading(ByteCount(10), total), state)
        state = step(state, DownloadEvent.Progress(ByteCount(100)), total)
        state = step(state, DownloadEvent.BodiesEnded, total)
        assertEquals(DownloadState.Verifying, state)
        assertEquals(DownloadState.Ready(total), step(state, DownloadEvent.HashesMatched, total))
        assertEquals(DownloadState.Failed(DownloadFailure.HashMismatch, ByteCount.ZERO, total), step(state, DownloadEvent.HashMismatched, total))
        // An early end is a network failure that keeps what came.
        val short = step(DownloadState.Downloading(ByteCount(70), total), DownloadEvent.BodiesEnded, total)
        assertEquals(DownloadState.Failed(DownloadFailure.Network, ByteCount(70), total), short)
        assertEquals(DownloadState.Failed(DownloadFailure.TooLarge, ByteCount.ZERO, total), step(DownloadState.Downloading(ByteCount(70), total), DownloadEvent.Progress(ByteCount(101)), total))
        assertEquals(DownloadState.Stopped(ByteCount(70), total), stateOnPhone(ByteCount(70), allFilesChecked = false, total))
        assertEquals(DownloadState.Ready(total), stateOnPhone(total, allFilesChecked = true, total))
    }

    // ----- timings -----

    private fun ref(surah: Int, ayah: Int) = VerseRef(checkNotNull(SurahNumber.of(surah)), ayah)

    @Test
    fun `timings are read in order with gaps allowed, and every malformed line is refused`() {
        val parsed = parseTimings("# TEST PACK timings\n1|1|0|500\n1|2|600|1200\r\n2|1|1200|1500\n")
        assertEquals(
            TimingsParse.Parsed(
                listOf(
                    AyahTiming(ref(1, 1), Duration.ZERO, Duration.ofMillis(500)),
                    AyahTiming(ref(1, 2), Duration.ofMillis(600), Duration.ofMillis(1200)),
                    AyahTiming(ref(2, 1), Duration.ofMillis(1200), Duration.ofMillis(1500)),
                ),
            ),
            parsed,
        )
        val cases = mapOf(
            "1|1|0" to TimingProblemKind.Malformed,
            "1|1|0|x" to TimingProblemKind.Malformed,
            "1|1|-1|5" to TimingProblemKind.Malformed,
            "115|1|0|5" to TimingProblemKind.OutOfRange,
            "1|0|0|5" to TimingProblemKind.OutOfRange,
            "1|1|5|5" to TimingProblemKind.EmptyOrBackwards,
        )
        for ((line, kind) in cases) assertEquals(TimingsParse.Refused(listOf(TimingProblem(1, kind))), parseTimings(line), line)
        assertEquals(TimingProblemKind.OutOfOrder, (parseTimings("1|2|0|5\n1|1|5|9") as TimingsParse.Refused).problems.single().kind)
        assertEquals(TimingProblemKind.Overlaps, (parseTimings("1|1|0|5\n1|2|4|9") as TimingsParse.Refused).problems.single().kind)
        assertEquals(TimingProblemKind.NoAyahs, (parseTimings("# nothing") as TimingsParse.Refused).problems.single().kind)
    }

    @Test
    fun `the ayah at any moment is the one whose span holds it, and gaps show none`() {
        val random = Random(160)
        repeat(100) {
            var clock = 0L
            val timings = (1..random.nextInt(1, 40)).map { ayah ->
                clock += random.nextLong(0, 300)
                val start = clock
                clock += random.nextLong(1, 2000)
                AyahTiming(ref(2, ayah), Duration.ofMillis(start), Duration.ofMillis(clock))
            }
            repeat(200) {
                val at = Duration.ofMillis(random.nextLong(-100, clock + 500))
                val expected = timings.firstOrNull { at >= it.start && at < it.end }?.let { AyahAt.At(it.ref) } ?: AyahAt.Between
                assertEquals(expected, ayahAt(timings, at))
            }
        }
    }

    @Test
    fun `megabytes are exact for the screen to round up`() {
        assertEquals(BigDecimal("0.000001"), megabytes(ByteCount(1)))
        assertEquals(BigDecimal("48.044031"), megabytes(ByteCount(48_044_031)))
        assertEquals(0, BigDecimal.ZERO.compareTo(megabytes(ByteCount.ZERO)))
    }
}
