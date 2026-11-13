package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The launch overlay's, the BIOS pre-flight's and the ended-session sheet's strings (issue 923) exist, translated, in every locale. */
class StringsLaunchTest {
    private val res = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_game.xml")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the launch strings`() {
        val names = listOf(
            "launch_stage_controls", "launch_stage_core", "launch_stage_download", "launch_cancel", "launch_bios_title", "launch_bios_body",
            "launch_bios_import", "launch_bios_anyway", "launch_failed_title", "launch_died_title", "launch_stopped_title", "launch_died_body",
            "launch_details", "launch_hide_details", "launch_retry", "launch_close", "launch_no_log", "game_starting",
        )
        val english = read("values")
        for (name in names) assertTrue("$name missing in English", !english[name].isNullOrBlank())
        // The strings with a placeholder keep it in every language.
        val placeholders = listOf("launch_stage_core", "launch_stage_download", "launch_bios_title", "launch_bios_body", "launch_died_body", "game_starting")
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val other = read("values-$locale")
            for (name in names) {
                val text = other[name]
                assertTrue("$name missing in $locale", !text.isNullOrBlank())
                assertTrue("$name untranslated in $locale", text != english.getValue(name))
                if (name in placeholders) assertTrue("$name lost its placeholder in $locale", "%1\$" in text!!)
            }
        }
    }
}
