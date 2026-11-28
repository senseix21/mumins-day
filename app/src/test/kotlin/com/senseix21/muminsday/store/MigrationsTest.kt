package com.senseix21.muminsday.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MigrationsTest {

    private val history = listOf(
        Migration(1, listOf("a1", "a2")),
        Migration(2, listOf("b1")),
        Migration(3, listOf("c1", "c2")),
    )

    private val released = mapOf("alarm log" to alarmLogMigrations, "deed days" to deedMigrations)

    @Test
    fun `each released history is numbered 1 to n with no gap or repeat`() {
        for ((name, migrations) in released) {
            assertEquals((1..migrations.size).toList(), migrations.map { it.version }, name)
            assertEquals(migrations.size, latestVersion(migrations), name)
        }
    }

    @Test
    fun `every released migration has statements and none is blank`() {
        for ((name, migrations) in released) {
            migrations.forEach { migration ->
                assertTrue(migration.statements.isNotEmpty(), "$name migration ${migration.version} is empty")
                assertTrue(migration.statements.all { it.isNotBlank() }, name)
            }
        }
    }

    @Test
    fun `a new install runs everything in order`() {
        assertEquals(listOf("a1", "a2", "b1", "c1", "c2"), statementsBetween(0, 3, history))
    }

    @Test
    fun `an upgrade runs only the steps after the old version`() {
        assertEquals(listOf("b1", "c1", "c2"), statementsBetween(1, 3, history))
        assertEquals(listOf("c1", "c2"), statementsBetween(2, 3, history))
        assertEquals(listOf("b1"), statementsBetween(1, 2, history))
    }

    @Test
    fun `no change runs nothing and a downgrade is refused`() {
        assertEquals(emptyList(), statementsBetween(2, 2, history))
        assertFailsWith<IllegalArgumentException> { statementsBetween(3, 1, history) }
    }

    @Test
    fun `upgrading from every version equals the fresh install's tail`() {
        for (from in 0..history.size) {
            val fresh = statementsBetween(0, history.size, history)
            val tail = statementsBetween(from, history.size, history)
            assertEquals(fresh.takeLast(tail.size), tail)
        }
    }

    @Test
    fun `the alarm log history creates the alarm log and nothing about deeds`() {
        val sql = statementsBetween(0, latestVersion(alarmLogMigrations), alarmLogMigrations).joinToString("\n")
        for (column in listOf("prayer", "kind", "planned_utc", "delivered_utc", "outcome")) assertTrue(column in sql, column)
        assertTrue("CREATE TABLE alarm_log" in sql)
        assertFalse("deed_day" in sql, "G34: the deed table must not live in the alarm log's file")
    }

    @Test
    fun `the deed history creates the deed days and nothing about alarms`() {
        val sql = statementsBetween(0, latestVersion(deedMigrations), deedMigrations).joinToString("\n")
        for (column in listOf("local_date", "deed_id", "content_version", "swapped", "done_at")) assertTrue(column in sql, column)
        assertTrue("CREATE TABLE deed_day" in sql)
        assertFalse("alarm_log" in sql, "G34: the alarm log must not live in the deed file")
    }

    // ADR-18: the tracker's table lives in the backed-up file, as a later step of the deed history.
    @Test
    fun `the Salah log is the second step of the deed history and holds only marked prayers`() {
        assertEquals(2, deedMigrations[1].version)
        val sql = statementsBetween(1, 2, deedMigrations).joinToString("\n")
        assertTrue("CREATE TABLE salah_log" in sql)
        for (column in listOf("local_date", "salah", "prayed_at", "PRIMARY KEY (local_date, salah)")) assertTrue(column in sql, column)
        // INV-C5: nothing in the table can say a prayer was not marked.
        for (word in listOf("missed", "skipped", "count", "streak", "status")) assertFalse(word in sql.lowercase(), word)
        assertFalse("alarm_log" in sql)
    }

    // ADR-24: the khatam plan is the third step of the same backed-up history, so the backup rules did not change.
    @Test
    fun `the khatam plan is the third step of the deed history and holds one row and no days`() {
        assertEquals(3, latestVersion(deedMigrations))
        val sql = statementsBetween(2, 3, deedMigrations).joinToString("\n")
        assertTrue("CREATE TABLE khatam" in sql)
        for (column in listOf("started_on", "target_on", "end_ayahs", "position", "marked_on", "day_start", "CHECK (id = 1)")) assertTrue(column in sql, column)
        // INV-C4: nothing in the table can say a day went by without reading.
        for (word in listOf("missed", "skipped", "behind", "late", "streak", "count", "local_date")) assertFalse(word in sql.lowercase(), word)
        assertFalse("alarm_log" in sql)
    }

    @Test
    fun `the two databases and the retired one are three different files`() {
        assertEquals(3, setOf(ALARM_LOG_FILE, DEED_DAYS_FILE, LEGACY_DATABASE_FILE).size)
        assertNotEquals(ALARM_LOG_FILE, DEED_DAYS_FILE)
    }

    // G34: the reason for the split. Read from the real resource files, so editing a rule breaks a test, not a restore.
    @Test
    fun `both backup rule files include the deed database and not the alarm log`() {
        for (name in listOf("data_extraction_rules.xml", "full_backup_content.xml")) {
            val rules = File("src/main/res/xml/$name").readText()
            val tags = Regex("<(include|exclude)\\b[^>]*>").findAll(rules).map { it.value }.toList()
            assertTrue(tags.any { it.startsWith("<include") && "domain=\"database\"" in it && "path=\"$DEED_DAYS_FILE\"" in it }, "$name includes $DEED_DAYS_FILE")
            assertFalse(tags.any { it.startsWith("<include") && ALARM_LOG_FILE in it }, "$name must not include $ALARM_LOG_FILE")
            // A whole-domain include would take the alarm log with it.
            assertFalse(tags.any { it.startsWith("<include") && "domain=\"database\"" in it && "path=" !in it }, "$name includes the whole database domain")
            // A whole-domain exclude could cancel the deed include.
            assertFalse(tags.any { it.startsWith("<exclude") && "domain=\"database\"" in it && "path=" !in it }, "$name excludes the whole database domain")
        }
    }
}
