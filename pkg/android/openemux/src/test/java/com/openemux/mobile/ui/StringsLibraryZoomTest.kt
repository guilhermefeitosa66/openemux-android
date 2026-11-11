package com.openemux.mobile.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The library zoom's strings exist, translated, in every locale. */
class StringsLibraryZoomTest {
    private val res = File("src/main/res")

    private fun read(folder: String, tag: String): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_library.xml")).documentElement
            .getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    @Test
    fun `every locale translates the zoom strings`() {
        val names = listOf("library_size", "library_size_compact", "library_size_normal", "library_size_large", "library_size_extra_large")
        val english = read("values", "string")
        val englishPlural = read("values", "plurals").getValue("library_size_columns")
        for (locale in listOf("de", "es", "fr", "ja", "pt-rBR", "ta", "zh-rCN")) {
            val strings = read("values-$locale", "string")
            for (name in names) {
                val text = strings[name]
                assertTrue("$name missing in $locale", !text.isNullOrBlank())
                // "Normal" is the same word in German, Spanish and Portuguese.
                if (!(name == "library_size_normal" && locale in listOf("de", "es", "pt-rBR"))) {
                    assertTrue("$name untranslated in $locale", text != english.getValue(name))
                }
            }
            val plural = read("values-$locale", "plurals")["library_size_columns"]
            assertTrue("library_size_columns missing in $locale", !plural.isNullOrBlank() && plural.contains("%1\$d"))
            assertTrue("library_size_columns untranslated in $locale", plural != englishPlural)
        }
    }
}
