package com.openemux.mobile.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A skin picked in the game's process reaches the library the main process keeps (issue 827). */
class SkinPicksTest {
    private lateinit var root: File
    private lateinit var paths: Paths
    private lateinit var dir: File

    @Before fun setUp() {
        root = Files.createTempDirectory("skin-picks").toFile()
        // The shared storage is there: the skins migrate on the first pass.
        File(root, "external").mkdirs()
        paths = Paths(File(root, "external"), File(root, "internal"))
        dir = paths.skinPicks
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun library() = Library(paths, { true }, storageRetryMs = 0)

    @Test fun aBundledPickComesBackAsWritten() {
        assertTrue(SkinPicks.record(dir, "SFC", "dark", null, at = 5))
        val pick = SkinPicks.drain(dir).single()
        assertEquals(SkinPicks.Pick("SFC", "dark", null, 5), pick)
    }

    @Test fun aCustomPickComesBackAsWritten() {
        assertTrue(SkinPicks.record(dir, "SFC", null, "own:mine", at = 5))
        assertEquals(SkinPicks.Pick("SFC", null, "own:mine", 5), SkinPicks.drain(dir).single())
    }

    @Test fun aPickIsExactlyOneSkin() {
        assertFalse(SkinPicks.record(dir, "SFC", null, null))
        assertFalse(SkinPicks.record(dir, "SFC", "dark", "own:mine"))
        assertTrue(SkinPicks.drain(dir).isEmpty())
    }

    @Test fun drainingTakesThemOutOldestFirst() {
        SkinPicks.record(dir, "SFC", "dark", null, at = 20)
        SkinPicks.record(dir, "SFC", "console", null, at = 10)
        assertEquals(listOf(10L, 20L), SkinPicks.drain(dir).map { it.at })
        assertTrue(SkinPicks.drain(dir).isEmpty())
    }

    @Test fun whatIsNotAPickIsDropped() {
        dir.mkdirs()
        File(dir, "a.pick").writeText("not json")
        File(dir, "b.pick").writeText("""{"console":"SFC","at":1}""")
        File(dir, "c.pick").writeText("""{"console":"SFC","skin":"dark","custom":"own:a","at":1}""")
        assertTrue(SkinPicks.drain(dir).isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun anAbandonedWriteGoesAndOneInFlightStays() {
        dir.mkdirs()
        val old = File(dir, "old.tmp").apply { writeText("x"); setLastModified(1_000L) }
        val fresh = File(dir, "new.tmp").apply { writeText("x"); setLastModified(100_000L) }
        SkinPicks.drain(dir, now = 100_000L)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun anUnwritableFolderIsNotAnError() {
        val file = File(root, "file").apply { writeText("x") }
        assertFalse(SkinPicks.record(File(file, "picks"), "SFC", "dark", null))
    }

    @Test fun theLibraryFoldsInABundledPickAndGoesBackToIt() {
        val lib = library()
        lib.useCustomSkin(Console.SFC, "own:mine")
        SkinPicks.record(dir, "SFC", SkinVariant.DARK.id, null)
        lib.absorbSkinPicks()
        assertEquals(SkinVariant.DARK, lib.skinVariant(Console.SFC))
        assertNull(lib.customSkin(Console.SFC))
    }

    @Test fun theLibraryFoldsInAPlayersSkinKeepingTheBundledOne() {
        val lib = library()
        lib.useBundledSkin(Console.SFC, SkinVariant.DARK.id)
        SkinPicks.record(dir, "SFC", null, "own:mine")
        lib.absorbSkinPicks()
        assertEquals("own:mine", lib.customSkin(Console.SFC))
        assertEquals(SkinVariant.DARK, lib.skinVariant(Console.SFC))
    }

    @Test fun theNewestPickOfAConsoleWinsAndSurvivesARestart() {
        val lib = library()
        SkinPicks.record(dir, "SFC", SkinVariant.DARK.id, null, at = 20)
        SkinPicks.record(dir, "SFC", SkinVariant.CONSOLE.id, null, at = 10)
        lib.absorbSkinPicks()
        assertEquals(SkinVariant.DARK, library().skinVariant(Console.SFC))
    }

    @Test fun picksOfOneConsoleLeaveTheOthersAlone() {
        val lib = library()
        SkinPicks.record(dir, "SFC", SkinVariant.DARK.id, null)
        lib.absorbSkinPicks()
        assertEquals(SkinVariant.CONSOLE, lib.skinVariant(Console.GBA))
    }

    @Test fun picksThisBuildCannotReadAreDropped() {
        val lib = library()
        lib.useBundledSkin(Console.SFC, SkinVariant.DARK.id)
        SkinPicks.record(dir, "NOPE", SkinVariant.CONSOLE.id, null, at = 1)
        SkinPicks.record(dir, "SFC", "no-such-variant", null, at = 2)
        SkinPicks.record(dir, "SFC", null, "weird:ref", at = 3)
        lib.absorbSkinPicks()
        assertEquals(SkinVariant.DARK, lib.skinVariant(Console.SFC))
        assertNull(lib.customSkin(Console.SFC))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun nothingToAbsorbIsNothing() {
        val lib = library()
        lib.absorbSkinPicks()
        assertEquals(SkinVariant.CONSOLE, lib.skinVariant(Console.SFC))
    }

    @Test fun picksWaitForTheMigration() {
        // Shared storage that cannot be listed: the skins are not migrated, and a pick folded in now would not count.
        val blocked = File(root, "blocked").apply { writeText("not a folder") }
        val lib = Library(Paths(blocked, File(root, "internal")), { true }, storageRetryMs = 0)
        SkinPicks.record(dir, "SFC", SkinVariant.DARK.id, null)
        lib.absorbSkinPicks()
        assertEquals(1, dir.listFiles()!!.size)
    }
}
