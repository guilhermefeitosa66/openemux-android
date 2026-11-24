package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The user themes' strings (issue 1018) exist in every locale, translated, with the same placeholders. */
class StringsThemeTest {
    private val res = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_theme.xml")).documentElement
            .getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun placeholders(text: String) = Regex("%\\d\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `every locale translates the theme strings`() {
        val english = read("values")
        assertTrue(english.size > 100)
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val strings = read("values-$locale")
            assertEquals("keys in $locale", english.keys, strings.keys)
            for ((name, text) in english) {
                val translated = strings.getValue(name)
                assertTrue("$name empty in $locale", translated.isNotBlank())
                assertEquals("$name placeholders in $locale", placeholders(text), placeholders(translated))
            }
            // A few words are the same in some languages ("Navigation", "Base", the subtitle's pattern); most are not.
            val same = english.count { (name, text) -> strings[name] == text }
            assertTrue("$same untranslated in $locale", same < english.size / 8)
        }
    }
}
