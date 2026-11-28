package com.senseix21.muminsday.quran

/**
 * A strict reader of JSON (RFC 8259) for QuranEnc's sura responses (ADR-26), because `core` has no library and gets
 * none (INV-P4). A string's value is its decoded characters, every escape included, so the translation shown is the
 * string QuranEnc sent; `QuranEncTest` re-encodes every response and gets its bytes back.
 */
internal sealed interface Json {
    /** Fields in the order the source has them; a repeated key refuses the document. */
    data class Obj(val fields: Map<String, Json>) : Json

    data class Arr(val items: List<Json>) : Json

    data class Str(val value: String) : Json

    /** The number as written; nothing here does arithmetic on it. */
    data class Num(val raw: String) : Json

    data class Bool(val value: Boolean) : Json

    data object Null : Json
}

internal sealed interface JsonParse {
    data class Parsed(val value: Json) : JsonParse

    data class Refused(val reason: String) : JsonParse
}

internal fun parseJson(text: String): JsonParse = try {
    val reader = JsonReader(text)
    val value = reader.value()
    reader.space()
    if (reader.at != text.length) throw Malformed("text after the value at character ${reader.at}")
    JsonParse.Parsed(value)
} catch (malformed: Malformed) {
    JsonParse.Refused(malformed.reason)
}

/** Thrown inside the reader only and turned into [JsonParse.Refused] above; never escapes this file. */
private class Malformed(val reason: String) : Exception(reason)

private class JsonReader(private val text: String) {
    var at: Int = 0

    fun space() {
        while (at < text.length && text[at] in " \t\r\n") at++
    }

    private fun fail(what: String): Nothing = throw Malformed("$what at character $at")

    private fun take(char: Char) {
        if (at < text.length && text[at] == char) at++ else fail("expected '$char'")
    }

    private fun word(literal: String, value: Json): Json {
        if (!text.startsWith(literal, at)) fail("unknown value")
        at += literal.length
        return value
    }

    fun value(): Json {
        space()
        if (at >= text.length) fail("no value")
        return when (text[at]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> word("true", Json.Bool(true))
            'f' -> word("false", Json.Bool(false))
            'n' -> word("null", Json.Null)
            else -> number()
        }
    }

    private fun obj(): Json {
        take('{')
        val fields = LinkedHashMap<String, Json>()
        space()
        if (at < text.length && text[at] == '}') {
            at++
            return Json.Obj(fields)
        }
        while (true) {
            space()
            val key = string()
            space()
            take(':')
            if (fields.put(key, value()) != null) fail("repeated key \"$key\"")
            space()
            if (at < text.length && text[at] == ',') at++ else break
        }
        take('}')
        return Json.Obj(fields)
    }

    private fun arr(): Json {
        take('[')
        val items = mutableListOf<Json>()
        space()
        if (at < text.length && text[at] == ']') {
            at++
            return Json.Arr(items)
        }
        while (true) {
            items += value()
            space()
            if (at < text.length && text[at] == ',') at++ else break
        }
        take(']')
        return Json.Arr(items)
    }

    private fun string(): String {
        take('"')
        val out = StringBuilder()
        while (true) {
            if (at >= text.length) fail("an unclosed string")
            val char = text[at++]
            when {
                char == '"' -> return out.toString()
                char == '\\' -> out.append(escape())
                char < ' ' -> fail("a control character in a string")
                else -> out.append(char)
            }
        }
    }

    private fun escape(): Char {
        if (at >= text.length) fail("an unfinished escape")
        return when (val char = text[at++]) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val hex = text.substring(at, minOf(text.length, at + 4))
                val code = hex.takeIf { it.length == 4 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }?.toInt(16)
                    ?: fail("a bad \\u escape")
                at += 4
                code.toChar()
            }
            else -> fail("an unknown escape")
        }
    }

    /**
     * `-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?`, scanned by hand: on Android a regex match at an offset copies
     * the whole input into native memory, so a 14 MB file with 7,589 numbers took minutes (KNOWN, ADR-28, emulator).
     * As with the regex, a fraction or exponent without its digits is not part of the number.
     */
    private fun number(): Json {
        val start = at
        var end = at
        if (end < text.length && text[end] == '-') end++
        when {
            end < text.length && text[end] == '0' -> end++
            end < text.length && text[end] in '1'..'9' -> end = digitsFrom(end)
            else -> fail("unknown value")
        }
        if (end + 1 < text.length && text[end] == '.' && text[end + 1].isAsciiDigit()) end = digitsFrom(end + 1)
        if (end < text.length && (text[end] == 'e' || text[end] == 'E')) {
            val sign = if (end + 1 < text.length && (text[end + 1] == '+' || text[end + 1] == '-')) end + 2 else end + 1
            if (sign < text.length && text[sign].isAsciiDigit()) end = digitsFrom(sign)
        }
        at = end
        return Json.Num(text.substring(start, end))
    }

    private fun digitsFrom(from: Int): Int {
        var end = from
        while (end < text.length && text[end].isAsciiDigit()) end++
        return end
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
