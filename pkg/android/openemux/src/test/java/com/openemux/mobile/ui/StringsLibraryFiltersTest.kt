package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The sort menu's and the facet chips' strings (issue 927) exist, translated, in every locale. */
class StringsLibraryFiltersTest {
    private val res = File("src/main/res")

    private fun read(folder: String): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_library.xml")).documentElement
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every locale translates the sort and facet strings`() {
        val names = listOf("library_sort_heading", "library_sort_added", "library_sort_most_played", "library_sort_unplayed", "library_section_added", "library_section_played",
            "library_section_unplayed", "library_flag_favorites", "library_flag_saved", "library_flag_missing_art", "library_flag_unplayed", "library_flags_clear", "library_no_match")
        val english = read("values")
        for (name in names) assertTrue("$name missing in English", !english[name].isNullOrBlank())
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val other = read("values-$locale")
            for (name in names) {
                val text = other[name]
                assertTrue("$name missing in $locale", !text.isNullOrBlank())
                assertTrue("$name untranslated in $locale", text != english.getValue(name))
            }
        }
    }
}
