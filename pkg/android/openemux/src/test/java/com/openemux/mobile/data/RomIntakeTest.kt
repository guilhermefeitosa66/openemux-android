package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class RomIntakeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun importableIsEveryConsoleExtensionPlusZip() {
        val expected = Console.entries.flatMap { it.extensions }.toSet() + "zip"
        assertEquals(expected, RomIntake.importableExtensions())
        assertTrue("nes" in expected && "cue" in expected && "chd" in expected)
    }

    @Test fun nameFilterLeavesOutGenericExtensions() {
        val path = RomIntake.pathFilterExtensions()
        RomIntake.GENERIC.forEach { assertFalse(it, it in path) }
        assertTrue("gba" in path && "sfc" in path && "cue" in path)
        assertEquals(RomIntake.importableExtensions() - RomIntake.GENERIC, path)
        // Markdown is a document; the Mega Drive's .md only comes in by MIME type or the in-app import.
        assertFalse("md" in path)
    }

    @Test fun pathPatternsCoverEveryDotDepth() {
        assertEquals(
            listOf(".*\\\\.nes", ".*\\\\..*\\\\.nes", ".*\\\\..*\\\\..*\\\\.nes", ".*\\\\..*\\\\..*\\\\..*\\\\.nes", ".*\\\\..*\\\\..*\\\\..*\\\\..*\\\\.nes"),
            RomIntake.pathPatterns("nes"),
        )
        assertEquals(RomIntake.PATH_DEPTH + 1, RomIntake.pathPatterns("gb").size)
    }

    @Test fun extensionAndImportableIgnoreCase() {
        assertEquals("nes", RomIntake.extensionOf("Super Mario Bros. 3 (USA).NES"))
        assertEquals("", RomIntake.extensionOf("README"))
        assertTrue(RomIntake.isImportable("Game.GBA"))
        assertTrue(RomIntake.isImportable("pack.zip"))
        assertTrue(RomIntake.isImportable("disc.bin"))
        assertFalse(RomIntake.isImportable("report.pdf"))
        assertFalse(RomIntake.isImportable("photo.jpg"))
        assertFalse(RomIntake.isImportable("app.apk"))
        assertFalse(RomIntake.isImportable("noextension"))
    }

    @Test fun placeCopiesANewGame() {
        val dir = File(tmp.root, "roms/GBA")
        assertEquals(RomIntake.Placed.COPIED, RomIntake.place(dir, "game.gba", "abc".byteInputStream()))
        assertEquals("abc", File(dir, "game.gba").readText())
        assertEquals(listOf("game.gba"), dir.list()!!.toList())
    }

    @Test fun placeKeepsAGameAlreadyThere() {
        val dir = tmp.newFolder("GBA")
        File(dir, "game.gba").writeText("old")
        assertEquals(RomIntake.Placed.DUPLICATE, RomIntake.place(dir, "game.gba", "new".byteInputStream()))
        assertEquals("old", File(dir, "game.gba").readText())
        assertEquals(listOf("game.gba"), dir.list()!!.toList())
    }

    @Test fun placeFlattensSlashesInNames() {
        val dir = tmp.newFolder("NES")
        RomIntake.place(dir, "a/b.nes", ByteArrayInputStream(byteArrayOf(1)))
        assertTrue(File(dir, "a_b.nes").isFile)
    }

    @Test fun aBrokenCopyLeavesNothingBehind() {
        val dir = tmp.newFolder("SFC")
        val failing = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 10) 1 else throw IOException("gone")
        }
        val error = runCatching { RomIntake.place(dir, "game.sfc", failing) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}
