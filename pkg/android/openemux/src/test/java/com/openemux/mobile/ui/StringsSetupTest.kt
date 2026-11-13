package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The emulator download's strings (issue 922) exist, translated, in every locale, and the gate's line is gone. */
class StringsSetupTest {
    private val res = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_settings.xml")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the setup strings and dropped the don't-close line`() {
        val names = listOf("pill_idle", "pill_running", "pill_failed", "all", "all_desc", "all_done", "later", "background_note", "missing", "intro").map { "settings_setup_$it" }
        val english = read("values")
        for (name in names) assertTrue("$name missing in English", !english[name].isNullOrBlank())
        assertTrue("settings_setup_dont_close" !in english)
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val other = read("values-$locale")
            assertTrue("don't-close line still in $locale", "settings_setup_dont_close" !in other)
            for (name in names) {
                val text = other[name]
                assertTrue("$name missing in $locale", !text.isNullOrBlank())
                assertTrue("$name untranslated in $locale", text != english.getValue(name))
            }
            assertTrue("running count lost a placeholder in $locale", "%1\$d" in other.getValue("settings_setup_pill_running") && "%2\$d" in other.getValue("settings_setup_pill_running"))
        }
    }
}
