package com.openemux.mobile.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The landscape header's strings (issue 669) exist in every locale, with their arguments. */
class StringsLandscapeHeaderTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")
    private val keys = mapOf(
        "library_previous_filter" to listOf("%1\$s"),
        "library_next_filter" to listOf("%1\$s"),
        "library_filter_label" to listOf("%1\$s", "%2\$s"),
        "library_search_in" to listOf("%1\$s"),
        "library_search_all" to emptyList(),
    )

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_library.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun everyLocaleHasTheHeaderStrings() {
        for (locale in locales) {
            val all = strings("values$locale")
            for ((key, args) in keys) {
                val value = all[key]
                assertNotNull("values$locale/$key", value)
                assertTrue("values$locale/$key is empty", value!!.isNotBlank())
                for (arg in args) assertTrue("values$locale/$key lacks $arg", arg in value)
                if (args.isEmpty()) assertTrue("values$locale/$key takes no argument", "%" !in value)
            }
        }
    }
}
