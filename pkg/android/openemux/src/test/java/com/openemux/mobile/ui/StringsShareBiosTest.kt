package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The BIOS strings of Share nearby (#678) exist in every locale, with their argument, and are translated. */
class StringsShareBiosTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")

    private val strings = mapOf(
        "share_items_bios" to emptyList(),
        "share_kind_bios" to emptyList(),
        "share_kind_bios_file" to listOf("%1\$s"),
        "share_offer_part_bios" to listOf("%1\$s"),
        "share_result_bios_received" to listOf("%1\$s"),
        "share_result_bios_sent" to listOf("%1\$s"),
    )

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_share.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private fun args(text: String) = Regex("%(\\d\\$)?[sd]").findAll(text).map { it.value }.toList().sorted()

    @Test
    fun everyLocaleHasTheBiosStrings() {
        for (locale in locales) {
            val s = strings("values$locale")
            for ((key, expected) in strings) {
                val text = s[key]
                assertTrue("values$locale/$key", !text.isNullOrBlank())
                assertEquals("values$locale/$key", expected, args(text!!))
            }
        }
    }

    @Test
    fun resultLinesAreTranslated() {
        val english = strings("values")
        for (locale in locales.drop(1)) {
            val s = strings("values$locale")
            for (key in listOf("share_items_bios", "share_result_bios_received", "share_result_bios_sent")) {
                assertFalse("values$locale/$key", s[key] == english[key])
            }
        }
    }
}
