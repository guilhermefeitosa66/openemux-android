package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The button-label strings exist, translated, in every locale. */
class StringsButtonLabelsTest {
    private val res = File("src/main/res")
    private val locales = listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")

    private fun read(folder: String, file: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/$file")).documentElement
        val strings = root.getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun everyLocaleHasTheGlyphDescriptions() {
        val english = read("values", "strings_gamepad.xml")
        assertEquals(6, english.size)
        for (locale in locales) {
            val other = read("values-$locale", "strings_gamepad.xml")
            assertEquals(locale, english.keys, other.keys)
            other.values.forEach { assertTrue(locale, it.isNotBlank()) }
        }
    }

    @Test
    fun everyLocaleHasTheSettingTranslated() {
        val english = read("values", "strings_settings.xml").filterKeys { it.startsWith("settings_button_labels") }
        assertEquals(10, english.size)
        // Brand names stay as they are; everything else is translated.
        val brands = setOf("settings_button_labels_xbox", "settings_button_labels_nintendo", "settings_button_labels_playstation")
        for (locale in locales) {
            val other = read("values-$locale", "strings_settings.xml").filterKeys { it.startsWith("settings_button_labels") }
            assertEquals(locale, english.keys, other.keys)
            for ((key, text) in other) {
                assertTrue("$key in $locale", text.isNotBlank())
                if (key in brands) assertEquals("$key in $locale", english.getValue(key), text)
                else assertTrue("$key in $locale is not translated", text != english.getValue(key))
            }
        }
    }
}
