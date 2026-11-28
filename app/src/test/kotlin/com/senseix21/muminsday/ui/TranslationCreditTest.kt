package com.senseix21.muminsday.ui

import com.senseix21.muminsday.quran.Edition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-26, QuranEnc conditions 2 and 3: what the app says about each translation (title, version, terms) is what the
 * record in `content/` says, so a new download cannot ship with the old version on screen.
 */
class TranslationCreditTest {

    private val sources = File("../content/quran-SOURCES.md").readText()

    @Test
    fun `each version shown is the one recorded, and Rowwad's is the one its own file names`() {
        for (edition in Edition.entries) {
            val version = editionVersion(edition)
            assertTrue("V$version" in sources && "version $version" in sources, "${edition.key} $version in quran-SOURCES.md")
            assertTrue("Version $version" in editionCredit(edition), edition.key)
        }
        val rwwad = File("../content/quran/translations/bengali_rwwad").listFiles().orEmpty().single().readText()
        assertTrue("(v${editionVersion(Edition.BengaliRwwad)}-xml.1)" in rwwad.take(4096))
    }

    @Test
    fun `the terms on the Licences screen are the published ones, line for line`() {
        val recorded = File("../content/quranenc-terms.txt").readLines()
        val start = recorded.indexOf(QURANENC_TERMS.first())
        assertTrue(start >= 0)
        assertEquals(QURANENC_TERMS, recorded.subList(start, start + QURANENC_TERMS.size))
        for (line in QURANENC_TERMS) assertTrue(line in sources, line)
    }

    @Test
    fun `the titles shown are QuranEnc's own, in both languages`() {
        for ((file, keys) in listOf("values" to "bn", "values-en" to "en")) {
            val strings = File("src/main/res/$file/strings.xml").readText()
            for (key in listOf("quran_edition_zakaria", "quran_edition_rwwad")) {
                val title = Regex("<string name=\"$key\">([^<]*)</string>").find(strings)?.groupValues?.get(1)
                assertTrue(title != null && "\"$title\"" in sources, "$keys $key: $title is quoted in quran-SOURCES.md")
            }
        }
    }
}
