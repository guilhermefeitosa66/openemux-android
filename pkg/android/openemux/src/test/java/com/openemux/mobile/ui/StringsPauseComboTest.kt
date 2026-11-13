package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The pause combination's strings (issue 924) exist, translated, in every locale. */
class StringsPauseComboTest {
    private val res = File("src/main/res")

    private fun read(folder: String, file: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/$file")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the pause combination strings`() {
        // The button names (Start + Select, L3 + R3) are the same in every language: only the sentences are checked for a translation.
        val same = setOf("settings_pause_combo_start_select", "settings_pause_combo_l3_r3")
        val keys = mapOf(
            "strings_settings.xml" to listOf("hold_start", "start_select", "l3_r3", "off").flatMap { listOf("settings_pause_combo_$it", "settings_pause_combo_${it}_desc") } +
                listOf("settings_pause_combo", "settings_pause_combo_lead"),
            "strings_game.xml" to listOf("game_toast_pause_hold_start", "game_toast_pause_start_select", "game_toast_pause_l3_r3"),
        )
        for ((file, names) in keys) {
            val english = read("values", file)
            for (name in names) assertTrue("$name missing in English", !english[name].isNullOrBlank())
            for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
                val other = read("values-$locale", file)
                for (name in names) {
                    val text = other[name]
                    assertTrue("$name missing in $locale", !text.isNullOrBlank())
                    if (name !in same) assertTrue("$name untranslated in $locale", text != english.getValue(name))
                }
            }
        }
    }
}
