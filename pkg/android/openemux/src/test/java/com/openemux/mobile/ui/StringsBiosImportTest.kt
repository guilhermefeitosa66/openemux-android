package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The per-console BIOS import's strings exist in every locale with their arguments; the folder import's are gone. */
class StringsBiosImportTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")

    private val strings = mapOf(
        "settings_bios_import" to emptyList(),
        "settings_bios_import_a11y" to listOf("%s"),
        "settings_bios_import_help" to emptyList(),
        "settings_bios_result_none" to emptyList(),
        "settings_bios_section_imported" to emptyList(),
        "settings_bios_section_rejected" to emptyList(),
        "settings_bios_saved_as" to listOf("%s"),
        "settings_bios_by_content" to emptyList(),
        "settings_bios_replaced" to emptyList(),
        "settings_bios_reason_name" to emptyList(),
        "settings_bios_reason_io" to emptyList(),
        "settings_bios_reason_size" to emptyList(),
        "settings_bios_expects" to listOf("%s"),
        "settings_bios_done" to emptyList(),
    )
    private val plurals = mapOf(
        "settings_bios_result_all" to listOf("%d"),
        "settings_bios_result_some" to listOf("%1\$d", "%2\$d"),
    )
    private val removed = listOf(
        "settings_bios_checking", "settings_bios_import_from_device", "settings_bios_pick_help",
        "settings_bios_imported", "settings_bios_skipped", "settings_bios_none_found",
    )

    private fun root(folder: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_settings.xml")).documentElement

    private fun named(root: Element, tag: String): Map<String, Element> {
        val nodes = root.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("name") }
    }

    private fun args(text: String) = Regex("%(\\d\\$)?[sd]").findAll(text).map { it.value }.toList().sorted()

    @Test
    fun everyLocaleHasTheNewStrings() {
        for (locale in locales) {
            val folder = "values$locale"
            val root = root(folder)
            val s = named(root, "string")
            for ((key, expected) in strings) {
                val text = s[key]?.textContent
                assertTrue("$folder/$key", !text.isNullOrBlank())
                assertEquals("$folder/$key", expected.sorted(), args(text!!))
            }
            val p = named(root, "plurals")
            for ((key, expected) in plurals) {
                val items = p[key]?.getElementsByTagName("item")
                assertTrue("$folder/$key", items != null && items.length > 0)
                val quantities = (0 until items!!.length).map { (items.item(it) as Element) }
                assertTrue("$folder/$key needs other", quantities.any { it.getAttribute("quantity") == "other" })
                for (item in quantities) assertEquals("$folder/$key/${item.getAttribute("quantity")}", expected.sorted(), args(item.textContent))
            }
        }
    }

    @Test
    fun theFolderImportStringsAreGone() {
        for (locale in locales) {
            val root = root("values$locale")
            val keys = named(root, "string").keys + named(root, "plurals").keys
            for (key in removed) assertFalse("values$locale/$key", key in keys)
        }
    }

    @Test
    fun translationsAreNotLeftInEnglish() {
        val english = named(root("values"), "string")
        for (locale in locales.drop(1)) {
            val s = named(root("values$locale"), "string")
            for (key in listOf("settings_bios_import_help", "settings_bios_reason_name", "settings_bios_replaced")) {
                assertFalse("values$locale/$key", s[key]?.textContent == english[key]?.textContent)
            }
        }
    }
}
