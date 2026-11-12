package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The Controllers screen's strings (issue 873) exist, translated, in every locale, and the rows it replaced are gone. */
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
            "strings_settings.xml" to listOf("auto", "touch", "controller").flatMap { listOf("settings_interface_mode_$it", "settings_interface_mode_${it}_desc") } +
                listOf("settings_interface_mode", "settings_interface_mode_lead", "settings_controllers", "settings_controllers_lead", "settings_button_labels_lead"),
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

    @Test
    fun `the rows the Controllers screen replaced are gone from every locale`() {
        val gone = listOf("settings_controls", "settings_controls_value", "settings_controller_layout", "settings_controller_layout_lead")
        for (folder in listOf("values", "values-de", "values-es", "values-fr", "values-ja", "values-pt-rBR", "values-ta", "values-zh-rCN")) {
            val strings = read(folder, "strings_settings.xml")
            for (name in gone) assertTrue("$name still in $folder", name !in strings)
        }
    }
}
