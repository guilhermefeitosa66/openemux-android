package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The ready-made themes the app ships (issue 1018). */
class ReadyThemesTest {
    private val assets = File("src/main/assets/themes")
    private val work = Files.createTempDirectory("ready").toFile()

    @After fun tearDown() {
        work.deleteRecursively()
    }

    private fun install(stamp: String = "1") = ReadyThemes.install(
        work, stamp,
        list = { (File(assets, it).list() ?: emptyArray()).toList() },
        open = { File(assets, it).inputStream() },
    )

    @Test fun theFiveShipWithTheirPictureAndOnlyKnownKeys() {
        assertEquals(ReadyThemes.IDS.toSet(), assets.list()!!.toSet())
        for (id in ReadyThemes.IDS) {
            val dropped = mutableListOf<String>()
            val theme = UserTheme.parse(ThemeYaml.parse(File(assets, "$id/theme.yaml").readText()), id) { dropped += it }
            assertTrue("$id dropped $dropped", dropped.isEmpty())
            // The consoles' own colors were made for Light and Dark: each ready theme gives its console list one.
            assertTrue(id, theme.isChanged("console_list_bg"))
            val image = theme.values[ThemeKeys.LIBRARY_IMAGE] as String
            assertTrue(id, File(assets, "$id/$image").isFile)
            assertTrue("$id image too large", File(assets, "$id/$image").length() < 200 * 1024)
        }
    }

    @Test fun everyTextPairPassesAA() {
        for (id in ReadyThemes.IDS) {
            val theme = UserTheme.parse(ThemeYaml.parse(File(assets, "$id/theme.yaml").readText()), id)
            for ((fg, backgrounds) in ThemeKeys.CONTRAST_PAIRS) for (bg in backgrounds) {
                val ratio = SkinColorMath.contrast(theme.color(fg), theme.color(bg))
                assertTrue("$id: $fg on $bg is $ratio", ratio >= ThemeKeys.WCAG_AA)
            }
        }
    }

    @Test fun installCopiesOncePerStampAndLoads() {
        install()
        val all = ReadyThemes.all(work)
        assertEquals(listOf("Pastel", "Dracula", "Kitty", "Neon", "Tokyo Night"), all.map { it.name })
        assertEquals(ThemeBase.LIGHT, all.first().base)
        val dracula = all.first { it.id == "dracula" }
        assertEquals(0xFF282A36.toInt(), dracula.color("window_bg"))
        assertEquals(1, ReadyThemes.images(work, dracula).size)
        // The same stamp leaves the files alone; a new one copies them again.
        val marker = File(work, "dracula/theme.yaml").apply { writeText("broken") }
        install()
        assertEquals("broken", marker.readText())
        assertNull(ReadyThemes.load(work, "dracula"))
        install("2")
        assertNotEquals("broken", marker.readText())
        assertNull(ReadyThemes.load(work, "midnight"))
    }

    @Test fun readyIdsAreNeverAUserTheme() {
        for (id in ReadyThemes.IDS) {
            assertTrue(ReadyThemes.isReadyId(id))
            assertFalse(UserTheme.isCustomId(id))
        }
        assertFalse(ReadyThemes.isReadyId(null))
        val store = ThemeStore(File(work, "themes"), { _, _ -> false })
        assertEquals("dracula-2", store.newId("Dracula"))
        assertEquals("tokyo-night-2", store.create("Tokyo Night", ThemeBase.DARK).id)
    }
}
