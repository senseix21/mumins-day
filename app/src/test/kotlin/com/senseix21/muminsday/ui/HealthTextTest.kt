package com.senseix21.muminsday.ui

import com.senseix21.muminsday.R
import com.senseix21.muminsday.health.AlarmRecord
import com.senseix21.muminsday.health.BrandSteps
import com.senseix21.muminsday.health.DegradedReason
import com.senseix21.muminsday.health.Health
import com.senseix21.muminsday.health.HealthFacts
import com.senseix21.muminsday.health.assessHealth
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * T-UI-1, JVM half: every [DegradedReason] has a message and a fix label that exist and are not blank in Bengali and in
 * English. (The emulator half: the home screen shows them; see the log.)
 */
class HealthTextTest {

    private val now = Instant.parse("2026-11-27T06:00:00Z")

    /** Every reason, produced the only way core allows: through assessHealth. Names the five by their facts. */
    private val everyReason: List<DegradedReason> = run {
        val late = AlarmRecord(now.minusSeconds(3600), now.minusSeconds(3600 - 600))
        val health = assessHealth(
            now,
            HealthFacts(
                exactAlarmsAllowed = false,
                notificationsAllowed = false,
                exemptFromBatteryOptimisation = false,
                brandSteps = BrandSteps.Needed,
                alarmLog = listOf(late),
            ),
        )
        (health as Health.Degraded).reasons
    }

    @Test
    fun `the sample covers every reason core defines`() {
        // Five kinds. If core adds a sixth, noticeFor stops compiling; this keeps the sample honest too.
        assertEquals(5, everyReason.map { it::class }.toSet().size)
    }

    @Test
    fun `each reason maps to a message and a fix label that exist in both languages`() {
        val bengali = resourceTexts("values")
        val english = resourceTexts("values-en")
        for (reason in everyReason) {
            val notice = noticeFor(reason)
            for (id in listOf(notice.message, notice.fixLabel)) {
                val name = resourceName(id)
                for ((language, texts) in listOf("Bengali" to bengali, "English" to english)) {
                    val values = assertNotNull(texts[name], "$language has no resource $name for $reason")
                    assertTrue(values.isNotEmpty() && values.all { it.isNotBlank() }, "$language $name is blank for $reason")
                }
            }
        }
    }

    @Test
    fun `only the reason that carries a number has a plural message, and its count is the late plus missed`() {
        val counts = everyReason.map { noticeFor(it).count }
        assertEquals(1, counts.count { it != null })
        assertEquals(listOf(1), counts.filterNotNull())
    }

    @Test
    fun `every reason has its own fix`() {
        assertEquals(everyReason.size, everyReason.map { noticeFor(it).fix }.toSet().size)
    }

    private fun resourceName(id: Int): String {
        for (holder in listOf(R.string::class.java, R.plurals::class.java)) {
            holder.fields.firstOrNull { it.getInt(null) == id }?.let { return it.name }
        }
        error("resource id $id is not in R.string or R.plurals")
    }

    /** name to its values: one for a string, the items for a plural. Read from the source resources the app ships. */
    private fun resourceTexts(directory: String): Map<String, List<String>> {
        val file = File("src/main/res/$directory/strings.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val found = mutableMapOf<String, List<String>>()
        for (i in 0 until root.childNodes.length) {
            val element = root.childNodes.item(i) as? Element ?: continue
            val items = element.getElementsByTagName("item")
            found[element.getAttribute("name")] =
                if (element.tagName == "plurals") (0 until items.length).map { items.item(it).textContent } else listOf(element.textContent)
        }
        return found
    }
}
