package com.senseix21.muminsday.hadithbooks

import com.senseix21.muminsday.l10n.toBengaliDigits
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-28 on OBVIOUSLY FAKE fixtures (`src/test/resources/hadithbooks-fixture`, every text "TEST ..." or "পরীক্ষা ...").
 * The real files are checked by [HadithBooksRealFilesTest] where they exist.
 */
class HadithBooksTest {

    private val fixture = File("src/test/resources/hadithbooks-fixture")
    private val bengali = File(fixture, "ben-test.json").readText(Charsets.UTF_8)
    private val arabic = File(fixture, "ara-test.json").readText(Charsets.UTF_8)

    private fun accepted(ben: String = bengali, ara: String = arabic): Book =
        assertIs<BookCheck.Accepted>(checkHadithBook(HadithBook.Nawawi, ben, ara)).book

    private fun refused(ben: String = bengali, ara: String = arabic): List<String> =
        assertIs<BookCheck.Refused>(checkHadithBook(HadithBook.Nawawi, ben, ara)).reasons

    private fun hadith(book: Book, number: String): Hadith = book.hadiths.single { it.number == HadithNumber(number) }

    @Test
    fun the_two_editions_pair_by_the_source_number_in_ascending_order() {
        val book = accepted()
        assertEquals("TEST BOOK", book.name)
        assertEquals(listOf("1", "2", "2.2", "3", "4"), book.hadiths.map { it.number.written })
        assertEquals(1, book.onlyBengali)
        assertEquals(1, book.onlyArabic)
        assertNull(hadith(book, "3").arabic, "3 is only in the Bengali file")
        assertNull(hadith(book, "4").bengali, "4 is only in the Arabic file")
    }

    @Test
    fun sections_are_the_listed_ones_holding_a_hadith_grouped_by_reference_book() {
        val book = accepted()
        assertEquals(listOf(0, 1, 2), book.sections.map { it.id.value })
        assertEquals(listOf("", "TEST SECTION ONE", "TEST SECTION TWO"), book.sections.map { it.name })
        assertEquals(listOf(1, 2, 2), book.sections.map { it.hadithCount })
        assertEquals(listOf("1", "2"), book.hadithsOf(SectionId(1)).map { it.number.written })
        assertEquals(listOf("2.2"), book.hadithsOf(SectionId(0)).map { it.number.written })
        assertEquals(emptyList(), book.hadithsOf(SectionId(3)))
    }

    @Test
    fun texts_are_the_source_strings_exactly_escapes_decoded_nothing_else_changed() {
        val book = accepted()
        assertEquals("পরীক্ষা হাদিস ১ \"TEST\" ১/২", hadith(book, "1").bengali)
        assertEquals("TEST ARABIC 1 ‫‬\\\\ END", hadith(book, "1").arabic)
        assertEquals("TEST ARABIC 2\r\nTEST LINE", hadith(book, "2").arabic)
        assertEquals("", hadith(book, "2").bengali, "an empty text stays the source's empty text")
    }

    @Test
    fun numbers_grades_and_reference_are_as_the_source_gives_them() {
        val book = accepted()
        val one = hadith(book, "1")
        assertEquals("1", one.arabicNumber)
        assertEquals(listOf(Grade("TEST GRADER B", "TEST GRADE 2"), Grade("TEST GRADER A", "TEST GRADE 1")), one.grades)
        assertEquals(Reference(1, 1), one.reference)
        assertEquals("2.01", hadith(book, "2").arabicNumber, "a text arabicnumber is kept as written")
        assertNull(hadith(book, "2.2").arabicNumber, "no arabicnumber in the source")
        assertEquals(SectionId(0), hadith(book, "2.2").section)
    }

    @Test
    fun malformed_files_are_refused_as_values_naming_the_file() {
        assertTrue(refused(ben = "not json").single().startsWith("ben-nawawi.min.json: not JSON"))
        assertEquals(2, refused(ben = "[]", ara = "{}").size)
        assertTrue(refused(ara = arabic.replace("\"text\":\"TEST ARABIC 2.2\"", "\"text\":\"TEST ARABIC 2.2\",\"extra\":1")).single().contains("a key outside"))
        assertTrue(refused(ben = bengali.replace("\"hadithnumber\":3,", "\"hadithnumber\":2,")).single().contains("appears more than once"))
        assertTrue(refused(ben = bengali.replace("\"book\":2,\"hadith\":1", "\"book\":9,\"hadith\":1")).single().contains("not a listed section"))
        assertTrue(refused(ben = bengali.replace("\"hadithnumber\":3,", "\"hadithnumber\":0,")).single().contains("not a positive number"))
        assertTrue(refused(ben = bengali.replace("\"hadithnumber\":3,", "\"hadithnumber\":\"3\",")).single().contains("not a positive number"))
        assertTrue(refused(ben = bengali.replace("\"grades\":[],\"reference\":{\"book\":2", "\"grades\":[{\"name\":\"\",\"grade\":\"X\"}],\"reference\":{\"book\":2")).single().contains("no name"))
    }

    @Test
    fun the_two_editions_must_agree_on_book_sections_and_each_shared_hadith() {
        assertTrue(refused(ara = arabic.replace("\"name\":\"TEST BOOK\"", "\"name\":\"TEST OTHER\"")).single().contains("different books"))
        assertTrue(refused(ara = arabic.replace("TEST SECTION TWO", "TEST SECTION 2")).single().contains("different sections"))
        assertTrue(refused(ara = arabic.replace("TEST GRADE 2", "TEST GRADE X")).single().contains("hadith 1: the editions give different grades"))
        assertTrue(refused(ara = arabic.replace("\"arabicnumber\":\"2.01\"", "\"arabicnumber\":2")).single().contains("hadith 2: the editions give different arabicnumber"))
        assertTrue(refused(ara = arabic.replace("\"book\":1,\"hadith\":2", "\"book\":2,\"hadith\":2")).single().contains("different references"))
    }

    @Test
    fun a_text_that_could_close_the_screen_isolate_is_refused() {
        assertTrue(refused(ara = arabic.replace("TEST ARABIC 2.2", "TEST ARABIC 2.2 ⁩")).single().contains("unbalanced direction control"))
        assertTrue(refused(ben = bengali.replace("TEST SECTION ONE", "TEST ⁧SECTION ONE")).single().contains("unbalanced direction control"))
    }

    @Test
    fun direction_controls_are_accepted_only_in_balanced_nested_pairs() {
        assertTrue("".directionControlsBalanced())
        assertTrue("a‫b‬c".directionControlsBalanced())
        assertTrue("⁦‪x‬⁩".directionControlsBalanced())
        assertTrue("‏ mark only".directionControlsBalanced(), "U+200F is a mark, not an embedding")
        assertFalse("⁩".directionControlsBalanced())
        assertFalse("‬".directionControlsBalanced())
        assertFalse("‫".directionControlsBalanced())
        assertFalse("⁦‬⁩".directionControlsBalanced(), "a PDF may not close an isolate")
        assertFalse("‪⁦‬⁩".directionControlsBalanced(), "crossed pairs")
    }

    @Test
    fun typed_numbers_read_in_any_of_the_three_digit_sets() {
        assertEquals(TypedNumber.Number(HadithNumber("7")), readTypedNumber("৭"))
        assertEquals(TypedNumber.Number(HadithNumber("402")), readTypedNumber("٤٠٢"))
        assertEquals(TypedNumber.Number(HadithNumber("402.2")), readTypedNumber(" 402.2 "))
        assertEquals(TypedNumber.Number(HadithNumber("12")), readTypedNumber("০০১২"))
        for (nonsense in listOf("", " ", "0", "০", "abc", "1-2", "1.", ".5", "1.2.3", "1234567")) {
            assertEquals(TypedNumber.NotANumber, readTypedNumber(nonsense), nonsense)
        }
    }

    @Test
    fun property_any_whole_number_typed_in_bengali_digits_finds_its_source_spelling() {
        val random = Random(28)
        repeat(2_000) {
            val whole = random.nextInt(1, 999_999)
            val written = if (random.nextBoolean()) "$whole" else "$whole.${random.nextInt(1, 9)}"
            assertEquals(TypedNumber.Number(HadithNumber(written)), readTypedNumber(toBengaliDigits(written)), written)
            assertEquals(TypedNumber.Number(HadithNumber(written)), readTypedNumber("0$written"), written)
        }
    }

    @Test
    fun property_pairing_never_loses_or_duplicates_a_number() {
        val random = Random(2828)
        repeat(200) {
            val inBengali = (1..30).filter { random.nextInt(4) != 0 }
            val inArabic = (1..30).filter { random.nextInt(4) != 0 }
            if (inBengali.isEmpty() || inArabic.isEmpty()) return@repeat
            val book = accepted(edition(inBengali, "পরীক্ষা"), edition(inArabic, "TEST"))
            assertEquals((inBengali + inArabic).distinct().sorted(), book.hadiths.map { it.number.written.toInt() })
            assertEquals(inBengali.count { it !in inArabic }, book.onlyBengali)
            assertEquals(inArabic.count { it !in inBengali }, book.onlyArabic)
            assertEquals(book.hadiths.size, book.sections.sumOf { it.hadithCount })
        }
    }

    /** A fake edition holding [numbers], each in section 1 or 2 by parity, its text "[word] n". */
    private fun edition(numbers: List<Int>, word: String): String =
        """{"metadata":{"name":"TEST BOOK","sections":{"1":"TEST ODD","2":"TEST EVEN"}},"hadiths":[""" +
            numbers.joinToString(",") { """{"hadithnumber":$it,"arabicnumber":$it,"text":"$word $it","grades":[],"reference":{"book":${2 - it % 2},"hadith":$it}}""" } +
            "]}"
}
