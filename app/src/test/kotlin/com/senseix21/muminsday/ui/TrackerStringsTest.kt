package com.senseix21.muminsday.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INV-C5, T-UI-2: every string the tracker shows, in both languages, is scanned for the words and shapes of judgement.
 * The tracker's strings are the ones whose key starts with `tracker_`; the screen and the notification action take no
 * other copy. A reviewer's read is still needed (T-UI-2); this stops a careless edit between reviews.
 */
class TrackerStringsTest {

    // English: a count, a streak, a comparison, or any word that blames or hurries.
    private val forbiddenEnglish = Regex(
        "\\b(miss(ed|es|ing)?|streaks?|percent(age)?|behind|skip(ped|s)?|fail(ed|s|ure)?|lost|broken?|in a row|out of|of five|" +
            "total|counts?|scores?|points?|goals?|keep it up|catch up|overdue|late|qa[dz]a|unprayed|not prayed|not yet|" +
            "forgot(ten)?|forget|neglect(ed)?|lapse[ds]?|must|should|never|always|every day|daily|best|worst|better|worse)\\b",
        RegexOption.IGNORE_CASE,
    )

    // Bengali: missed, make-up prayer (kaza), streak, unbroken, percent, failed, remaining, absent, lost, broken, forgotten,
    // neglect, late, score, points. Drafted by Claude; a native speaker extends this list when reviewing.
    private val forbiddenBengali = Regex(
        "মিস|কাজা|বাদ পড়|ছুটে|টানা|ধারাবাহিক|অবিচ্ছিন্ন|শতাংশ|ব্যর্থ|বাকি|অনুপস্থিত|হারিয়ে|ভেঙে|ভুলে|গাফিলতি|অবহেলা|দেরি|স্ট্রিক|স্কোর|পয়েন্ট|এখনো না|মোট|প্রতিদিন",
    )

    private val digit = Regex("[0-9\\u09E6-\\u09EF]")

    // A literal percent sign, or a number placeholder such as %d or %1$d. "%1$s" (a name) is allowed.
    private val numberOrPercent = Regex("%(?!\\d\\\$s)")

    private fun trackerStrings(directory: String): Map<String, List<String>> {
        val file = File("src/main/res/$directory/strings.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val found = mutableMapOf<String, List<String>>()
        for (i in 0 until root.childNodes.length) {
            val element = root.childNodes.item(i) as? Element ?: continue
            val name = element.getAttribute("name")
            if (!name.startsWith("tracker_")) continue
            val items = element.getElementsByTagName("item")
            found[name] = if (element.tagName == "plurals") (0 until items.length).map { items.item(it).textContent } else listOf(element.textContent)
        }
        return found
    }

    @Test
    fun `both languages have the same tracker strings and there are some`() {
        val bengali = trackerStrings("values")
        val english = trackerStrings("values-en")
        assertTrue(bengali.size >= 5, "the scan must see the tracker's strings, saw ${bengali.keys}")
        assertEquals(bengali.keys, english.keys)
    }

    @Test
    fun `no tracker string is a plural, because a plural is a count`() {
        for (directory in listOf("values", "values-en")) {
            val file = File("src/main/res/$directory/strings.xml").readText()
            assertTrue(!Regex("<plurals\\s+name=\"tracker_").containsMatchIn(file), "$directory has a tracker plural")
        }
    }

    @Test
    fun `no tracker string in either language judges, counts or compares`() {
        for ((directory, forbidden) in listOf("values" to forbiddenBengali, "values-en" to forbiddenEnglish)) {
            for ((name, texts) in trackerStrings(directory)) {
                for (raw in texts) {
                    // A name placeholder such as %1$s carries a digit that is not a number the user sees.
                    val text = raw.replace(Regex("%\\d\\\$s"), "%s")
                    assertTrue(!forbidden.containsMatchIn(text), "$directory $name: \"$text\" has ${forbidden.find(text)?.value}")
                    assertTrue(!digit.containsMatchIn(text), "$directory $name: \"$text\" has a digit (a number is a count)")
                    assertTrue(!numberOrPercent.containsMatchIn(raw), "$directory $name: \"$text\" has a number placeholder or a percent sign")
                }
            }
        }
    }

    @Test
    fun `the scan itself catches what it is meant to catch`() {
        for (bad in listOf("You missed Fajr", "3 day streak", "5 of 5", "Not prayed yet", "Catch up", "80%", "Qaza")) {
            assertTrue(forbiddenEnglish.containsMatchIn(bad) || digit.containsMatchIn(bad) || numberOrPercent.containsMatchIn(bad), bad)
        }
        for (bad in listOf("ফজর মিস হয়েছে", "টানা ৩ দিন", "কাজা নামাজ", "৫০%")) {
            assertTrue(forbiddenBengali.containsMatchIn(bad) || digit.containsMatchIn(bad) || numberOrPercent.containsMatchIn(bad), bad)
        }
    }
}
