package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The phone Continue row's long-press label (issue 925) exists, translated, in every locale. */
class StringsContinueRowTest {
    private val res = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_library.xml")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the game details label`() {
        val english = read("values")
        assertTrue(!english["library_game_details"].isNullOrBlank())
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val text = read("values-$locale")["library_game_details"]
            assertTrue("missing in $locale", !text.isNullOrBlank())
            assertTrue("untranslated in $locale", text != english.getValue("library_game_details"))
        }
    }
}
