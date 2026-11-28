package com.senseix21.muminsday.quran

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader scans numbers by hand (ADR-28: a regex at an offset is quadratic on Android). Property: for any text, it
 * accepts and refuses exactly what the former regex `-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?` did, and keeps
 * the same characters.
 */
class JsonNumberTest {

    private val former = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    private fun expected(candidate: String): JsonParse {
        val match = former.matchAt(candidate, 0) ?: return JsonParse.Refused("")
        return if (match.value.length == candidate.length) JsonParse.Parsed(Json.Num(candidate)) else JsonParse.Refused("")
    }

    @Test
    fun property_the_hand_scanner_agrees_with_the_former_regex() {
        val random = Random(2028)
        val alphabet = "0123456789-+.eE"
        repeat(50_000) {
            val candidate = buildString { repeat(random.nextInt(1, 9)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val actual = parseJson("[$candidate]")
            val wanted = expected(candidate)
            when (wanted) {
                is JsonParse.Parsed -> assertEquals(JsonParse.Parsed(Json.Arr(listOf(wanted.value))), actual, candidate)
                is JsonParse.Refused -> assertEquals(true, actual is JsonParse.Refused, candidate)
            }
        }
    }

    @Test
    fun the_source_numbers_read_as_written() {
        for (written in listOf("0", "7", "402.2", "-1", "1e5", "2.5E-3", "3533.3")) {
            assertEquals(JsonParse.Parsed(Json.Num(written)), parseJson(written), written)
        }
    }
}
