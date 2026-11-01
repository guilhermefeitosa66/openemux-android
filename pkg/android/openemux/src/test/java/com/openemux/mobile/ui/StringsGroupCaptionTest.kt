package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The console group's caption exists in every locale, with both of its arguments. */
class StringsGroupCaptionTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")

    private fun caption(folder: String): String? {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_library.xml")).documentElement
        val strings = root.getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.firstOrNull { it.getAttribute("name") == "library_group_caption" }?.textContent
    }

    @Test
    fun everyLocaleHasTheCaption() {
        // The abbreviation and the name are proper names: the dash between them is the same everywhere.
        for (locale in locales) assertEquals("values$locale", "%1\$s – %2\$s", caption("values$locale"))
    }
}
