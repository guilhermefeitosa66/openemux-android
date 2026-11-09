package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The strings of a transfer left running (issue 829), in every locale. */
class StringsBackgroundTransferTest {
    private val res = File("src/main/res")
    private val locales = listOf("-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")
    private val keys = listOf("share_progress_note", "share_progress_background", "share_chip_open", "share_chip_receiving", "share_chip_sending")

    private fun strings(folder: String, file: String = "strings_share.xml"): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/$file")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun everyLocaleTranslatesThem() {
        val english = strings("values")
        for (key in keys) assertFalse(key, english[key].isNullOrBlank())
        for (locale in locales) {
            val s = strings("values$locale")
            for (key in keys) {
                assertTrue("values$locale/$key", !s[key].isNullOrBlank())
                assertNotEquals("values$locale/$key", english[key], s[key])
            }
        }
    }

    @Test
    fun theBackupNoticeIsTranslated() {
        val key = "backup_transfer_running"
        val english = strings("values", "strings_settings.xml")[key]
        assertFalse(english.isNullOrBlank())
        for (locale in locales) {
            val text = strings("values$locale", "strings_settings.xml")[key]
            assertTrue("values$locale", !text.isNullOrBlank())
            assertNotEquals("values$locale", english, text)
        }
    }

    @Test
    fun theNoteSaysTheAppCanBeUsed() {
        assertEquals(
            "You can keep using the app, play a game, leave the app or turn off the screen. The transfer keeps going.",
            strings("values")["share_progress_note"],
        )
    }
}
