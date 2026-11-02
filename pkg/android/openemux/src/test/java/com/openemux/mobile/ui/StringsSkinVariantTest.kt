package com.openemux.mobile.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The skin picker's strings (issues 718, 720) exist in every locale, with their arguments. */
class StringsSkinVariantTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")
    private val keys = mapOf(
        "settings_skin_console" to emptyList(),
        "settings_skin_console_desc" to emptyList(),
        "settings_skin_dark" to emptyList(),
        "settings_skin_dark_desc" to emptyList(),
        "settings_skin_sheet_desc" to listOf("%1\$s"),
        "settings_skin_imported" to listOf("%1\$s"),
        "settings_skin_imported_value" to emptyList(),
    ) + listOf(
        "white", "black", "grey", "silver", "gold", "red", "pink", "orange", "yellow", "green", "teal", "turquoise",
        "blue", "purple", "clear", "clear_blue", "clear_pink",
    ).associate { "settings_skin_color_$it" to emptyList<String>() }

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_settings.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun everyLocaleHasTheSkinPickerStrings() {
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
