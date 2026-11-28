package com.senseix21.muminsday.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INV-C4 for the khatam plan (phase 15, ADR-24), in the manner of `TrackerStringsTest`: every string the khatam screen,
 * its nudge words and its setting show starts with `khatam_`, and each, in both languages, is scanned for the words of
 * lagging, missing, hurrying and blame. Numbers reach these strings only as `%N$s` arguments (a surah, an ayah, a juz,
 * a date), never as a count placeholder. A reviewer's read is still needed (T-UI-2).
 */
class KhatamStringsTest {

    private val forbiddenEnglish = Regex(
        "\\b(miss(ed|es|ing)?|behind|late(r|ness)?|overdue|skip(ped|s)?|streaks?|catch(ing)? up|fall(en)? behind|lag(ging)?|" +
            "fail(ed|s|ure)?|lost|broken?|in a row|percent(age)?|scores?|points?|keep it up|hurry|must|should|" +
            "days? left|remaining|left over|not read|unread|forgot(ten)?|neglect(ed)?|again and again)\\b",
        RegexOption.IGNORE_CASE,
    )

    // Bengali: missed, behind/lagging, late, dropped, left out, in a row, streak, percent, failed, remaining, lost, broken,
    // should, must, hurry, not read, score, points. Drafted by Claude; a native speaker extends it when reviewing.
    private val forbiddenBengali = Regex(
        "মিস|পিছিয়ে|পিছনে|দেরি|বাদ পড়|ছুটে|টানা|ধারাবাহিক|স্ট্রিক|শতাংশ|ব্যর্থ|বাকি|হারিয়ে|ভেঙে|উচিত|অবশ্যই|তাড়াতাড়ি|পড়া হয়নি|পড়েননি|স্কোর|পয়েন্ট|ভুলে",
    )

    private val digit = Regex("[0-9\\u09E6-\\u09EF]")

    // A literal percent sign or a number placeholder such as %d; "%1$s" is the only placeholder allowed.
    private val numberOrPercent = Regex("%(?!\\d\\\$s)")

    private fun khatamStrings(directory: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$directory/strings.xml")).documentElement
        val found = mutableMapOf<String, String>()
        for (i in 0 until root.childNodes.length) {
            val element = root.childNodes.item(i) as? Element ?: continue
            if (element.getAttribute("name").startsWith("khatam_")) found[element.getAttribute("name")] = element.textContent
        }
        return found
    }

    @Test
    fun `both languages have the same khatam strings and the scan sees them`() {
        val bengali = khatamStrings("values")
        assertTrue(bengali.size >= 20, "saw ${bengali.keys}")
        assertEquals(bengali.keys, khatamStrings("values-en").keys)
    }

    @Test
    fun `no khatam string is a plural, because a plural is a count`() {
        for (directory in listOf("values", "values-en")) {
            assertTrue(!Regex("<plurals\\s+name=\"khatam_").containsMatchIn(File("src/main/res/$directory/strings.xml").readText()), directory)
        }
    }

    @Test
    fun `no khatam string in either language says behind, missed or late, or counts anything`() {
        for ((directory, forbidden) in listOf("values" to forbiddenBengali, "values-en" to forbiddenEnglish)) {
            for ((name, raw) in khatamStrings(directory)) {
                val text = raw.replace(Regex("%\\d\\\$s"), "%s")
                assertTrue(!forbidden.containsMatchIn(text), "$directory $name: \"$text\" has ${forbidden.find(text)?.value}")
                assertTrue(!digit.containsMatchIn(text), "$directory $name: \"$text\" has a digit")
                assertTrue(!numberOrPercent.containsMatchIn(raw), "$directory $name: \"$text\" has a number placeholder or a percent sign")
            }
        }
    }

    @Test
    fun `the scan itself catches what it is meant to catch`() {
        for (bad in listOf("You are 3 days behind", "Missed yesterday", "You are late", "Catch up today", "2 days left", "%d ayahs remaining", "80%")) {
            assertTrue(forbiddenEnglish.containsMatchIn(bad) || digit.containsMatchIn(bad) || numberOrPercent.containsMatchIn(bad), bad)
        }
        for (bad in listOf("আপনি পিছিয়ে আছেন", "দেরি হয়ে গেছে", "বাকি ৩ দিন", "গতকাল পড়া হয়নি")) {
            assertTrue(forbiddenBengali.containsMatchIn(bad) || digit.containsMatchIn(bad), bad)
        }
    }
}
