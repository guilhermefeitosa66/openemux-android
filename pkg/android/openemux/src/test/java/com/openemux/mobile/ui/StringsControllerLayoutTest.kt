package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The controller layout strings exist, translated, in every locale. */
class StringsControllerLayoutTest {
    private val res = File("src/main/res")

    private fun read(folder: String, file: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/$file")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the controller layout strings`() {
        val keys = mapOf(
            "strings_settings.xml" to listOf("auto", "always", "never").flatMap { listOf("settings_controller_layout_$it", "settings_controller_layout_${it}_desc") } +
                listOf("settings_controller_layout", "settings_controller_layout_lead"),
            "strings_library.xml" to listOf("library_hint_console"),
        )
        for ((file, names) in keys) {
            val english = read("values", file)
            for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
                val other = read("values-$locale", file)
                for (name in names) {
                    val text = other[name]
                    assertTrue("$name missing in $locale", !text.isNullOrBlank())
                    // "Console" is the same word in French and Portuguese; every longer string is translated.
                    if (name != "library_hint_console") assertTrue("$name untranslated in $locale", text != english.getValue(name))
                }
            }
        }
    }
}
