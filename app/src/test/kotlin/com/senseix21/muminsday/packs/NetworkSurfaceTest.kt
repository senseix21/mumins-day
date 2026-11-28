package com.senseix21.muminsday.packs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-25: the whole network surface of the app, checked from the sources, so it cannot grow by accident. Until the
 * founder names a host and a pack has permission, the app has no `INTERNET` (the permission gate, T-BUILD-1), no host,
 * and an empty manifest; when they arrive, these checks change in the same commit as the manifest line.
 */
class NetworkSurfaceTest {

    private val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun filesContaining(text: String): List<String> =
        sources.filter { text in it.readText() }.map { it.relativeTo(File("src/main/kotlin")).path }

    @Test
    fun `there is no host while there is no INTERNET permission, and the screen therefore offers nothing`() {
        assertTrue("android.permission.INTERNET" !in manifest)
        assertEquals("", PACK_HOST)
        assertEquals(PackSource.NotAvailable, packSource(PACK_HOST))
    }

    @Test
    fun `the host is written in exactly one place`() {
        assertEquals(listOf("com/senseix21/muminsday/packs/PackHost.kt"), filesContaining("const val PACK_HOST"))
        // No address is written anywhere else in the app's code: every request URL is built by packUrl from PACK_HOST.
        assertEquals(emptyList(), filesContaining("\"http://") + filesContaining("\"https://"))
    }

    @Test
    fun `only the downloader opens a connection, and only the downloads screen starts one`() {
        val downloader = "com/senseix21/muminsday/packs/Downloader.kt"
        assertEquals(listOf(downloader), filesContaining("openConnection("))
        for (call in listOf("HttpURLConnection", "HttpsURLConnection", "Socket(", "URL(", "DatagramSocket", "WebView")) {
            assertTrue(filesContaining(call).all { it == downloader }, "$call outside the downloader: ${filesContaining(call)}")
        }
        // Defined in the downloader, called only by the one pack run.
        assertEquals(setOf(downloader, "com/senseix21/muminsday/packs/PackRun.kt"), filesContaining("fetchFile(").toSet())
        assertEquals(setOf("com/senseix21/muminsday/packs/PackRun.kt", "com/senseix21/muminsday/packs/PackDownloads.kt"), filesContaining("downloadPack(").toSet())
        assertEquals(listOf("com/senseix21/muminsday/ui/PacksActivity.kt"), filesContaining("PackDownloads.start("))
    }

    @Test
    fun `the bundled manifest is empty`() {
        assertEquals(ManifestParse.Accepted(emptyList()), parseManifest(File("src/main/assets/packs/manifest.txt").readText()))
    }

    @Test
    fun `cleartext is refused by the platform as well, and packs are never backed up`() {
        assertTrue("android:networkSecurityConfig=\"@xml/network_security_config\"" in manifest)
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(Regex("<base-config\\s+cleartextTrafficPermitted=\"false\"").containsMatchIn(config))
        assertTrue("<domain-config" !in config, "no host gets an exception")
        assertTrue("cleartextTrafficPermitted=\"true\"" !in config)
        // Packs live under filesDir/packs: the backup rules exclude the whole file domain.
        for (name in listOf("data_extraction_rules.xml", "full_backup_content.xml")) {
            val rules = File("src/main/res/xml/$name").readText()
            assertTrue("<exclude domain=\"file\" />" in rules, name)
            assertTrue(Regex("<include domain=\"file\"").find(rules) == null, name)
        }
    }
}
