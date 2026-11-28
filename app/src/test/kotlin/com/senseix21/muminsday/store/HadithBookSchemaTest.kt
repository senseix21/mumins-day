package com.senseix21.muminsday.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-28: the hadith books' cache can only run on a phone; these check what a JVM can. */
class HadithBookSchemaTest {

    @Test
    fun `the history is numbered from 1 with no gap and creates every table the store uses`() {
        assertEquals((1..hadithBookMigrations.size).toList(), hadithBookMigrations.map { it.version })
        val sql = statementsBetween(0, latestVersion(hadithBookMigrations), hadithBookMigrations).joinToString("\n")
        for (table in listOf("TABLE book", "TABLE section", "TABLE hadith", "TABLE grade", "INDEX hadith_by_section")) assertTrue(table in sql, table)
        // A number is kept as the source writes it ("402.2"), so it is a text, and one number appears once per book.
        assertTrue("number TEXT NOT NULL" in sql && "UNIQUE (book, number)" in sql)
    }

    @Test
    fun `the cache is never backed up`() {
        for (name in listOf("data_extraction_rules.xml", "full_backup_content.xml")) {
            val rules = File("src/main/res/xml/$name").readText()
            assertFalse(HADITH_BOOKS_FILE in rules, "$name must not name $HADITH_BOOKS_FILE")
            assertTrue("<include" in rules, "$name must be include-based")
        }
    }
}
