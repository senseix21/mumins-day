package com.senseix21.muminsday.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-23: the Quran database's SQL can only run on a phone; these check what a JVM can. */
class QuranSchemaTest {

    @Test
    fun `the quran history is numbered from 1 with no gap and creates every table the store uses`() {
        assertEquals((1..quranMigrations.size).toList(), quranMigrations.map { it.version })
        val sql = statementsBetween(0, latestVersion(quranMigrations), quranMigrations).joinToString("\n")
        for (table in listOf("TABLE meta", "TABLE surah", "TABLE ayah", "VIRTUAL TABLE arabic_index", "TABLE translation", "VIRTUAL TABLE translation_index")) {
            assertTrue(table in sql, table)
        }
    }

    @Test
    fun `version 2 drops the single translation column and forces a rebuild from the assets`() {
        val step = statementsBetween(1, 2, quranMigrations)
        assertTrue("DROP TABLE ayah_index" in step && "DROP TABLE ayah" in step && "DELETE FROM meta" in step)
        assertFalse(step.any { "translation TEXT" in it }, "the new ayah table has no translation column")
    }

    @Test
    fun `search uses FTS4, which API 26 has, and never FTS5, which it does not`() {
        val sql = quranMigrations.flatMap { it.statements }.joinToString("\n").lowercase()
        assertTrue("using fts4(" in sql)
        assertFalse("fts5" in sql)
        assertFalse("unicode61" in sql, "not established for API 26")
    }

    @Test
    fun `the quran database is never backed up`() {
        for (name in listOf("data_extraction_rules.xml", "full_backup_content.xml")) {
            val rules = File("src/main/res/xml/$name").readText()
            assertFalse(QURAN_FILE in rules, "$name must not name $QURAN_FILE")
            // Only listed paths are backed up, so not naming the file is what keeps it out.
            assertTrue("<include" in rules, "$name must be include-based")
        }
    }
}
