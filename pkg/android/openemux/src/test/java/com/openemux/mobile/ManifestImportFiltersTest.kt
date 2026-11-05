package com.openemux.mobile

import com.openemux.mobile.data.RomIntake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The "Open with"/"Share" filters stay in step with the consoles and never claim unrelated files. */
class ManifestImportFiltersTest {
    private val manifest = listOf(File("src/main/AndroidManifest.xml"), File("pkg/android/openemux/src/main/AndroidManifest.xml")).first { it.isFile }
    private val text = manifest.readText()

    private fun importActivity(): Element {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val activities = doc.getElementsByTagName("activity")
        return (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") == "com.openemux.mobile.ImportActivity" }
    }

    private fun Element.children(tag: String): List<Element> {
        val list = getElementsByTagName(tag)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.data(attr: String) = children("data").map { it.getAttributeNS(ANDROID, attr) }.filter { it.isNotEmpty() }

    @Test fun nameFilterListsEveryConsoleSpecificExtensionAtEveryDepth() {
        // The block is generated text: compare it as the manifest writes it.
        val block = text.substringAfter("<!-- rom-path-patterns:begin -->").substringBefore("<!-- rom-path-patterns:end -->")
        val written = Regex("android:pathPattern=\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toList()
        val expected = RomIntake.pathFilterExtensions().sorted().flatMap { RomIntake.pathPatterns(it) }
        assertEquals(expected, written)
    }

    @Test fun theActivityIsExportedAndHasNoScreenInRecents() {
        val activity = importActivity()
        assertEquals("true", activity.getAttributeNS(ANDROID, "exported"))
        assertEquals("true", activity.getAttributeNS(ANDROID, "excludeFromRecents"))
        assertEquals("true", activity.getAttributeNS(ANDROID, "noHistory"))
    }

    @Test fun noFilterClaimsUnrelatedFiles() {
        val filters = importActivity().children("intent-filter")
        assertEquals(3, filters.size)
        val unrelated = listOf("image/", "text/", "video/", "audio/", "application/pdf", "application/vnd.android.package-archive", "application/*")
        for (filter in filters) {
            val types = filter.data("mimeType")
            types.forEach { type -> unrelated.forEach { assertFalse("$type claims $it", type.startsWith(it)) } }
            // A wildcard type only ever comes with name patterns, which narrow it to ROM extensions.
            if ("*/*" in types) {
                assertEquals(listOf("*/*"), types)
                assertTrue(filter.data("pathPattern").isNotEmpty())
                assertEquals(listOf("android.intent.action.VIEW"), filter.children("action").map { it.getAttributeNS(ANDROID, "name") })
            }
            val actions = filter.children("action").map { it.getAttributeNS(ANDROID, "name") }.toSet()
            // A SEND intent has no data URI: a filter naming a scheme would never match it.
            val schemes = if ("android.intent.action.VIEW" in actions) setOf("content", "file") else emptySet()
            assertEquals(schemes, filter.data("scheme").toSet())
        }
    }

    @Test fun mimeFiltersTakeOpenWithAndBothSharesWithTheSameTypes() {
        val filters = importActivity().children("intent-filter").filter { "application/octet-stream" in it.data("mimeType") }
        assertEquals(2, filters.size)
        val actions = filters.map { f -> f.children("action").map { it.getAttributeNS(ANDROID, "name") }.toSet() }
        assertEquals(
            listOf(setOf("android.intent.action.VIEW"), setOf("android.intent.action.SEND", "android.intent.action.SEND_MULTIPLE")),
            actions,
        )
        assertEquals(filters[0].data("mimeType"), filters[1].data("mimeType"))
        assertTrue("application/zip" in filters[0].data("mimeType"))
        assertTrue(filters[1].data("scheme").isEmpty())
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
