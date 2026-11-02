package com.openemux.mobile.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The skin picked for a console is written, read back after a restart, and is the one a launch uses (issue 718). */
class SkinChoicePersistenceTest {
    private lateinit var root: File
    private lateinit var paths: Paths

    @Before fun setUp() {
        root = Files.createTempDirectory("skin-choice").toFile()
        paths = Paths(File(root, "external"), File(root, "internal"))
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun library() = Library(paths, { true }, storageRetryMs = 0)

    @Test fun aConsoleNeverTouchedPlaysInItsOwnColors() {
        assertEquals(SkinVariant.CONSOLE, library().skinVariant(Console.SFC))
    }

    @Test fun thePickIsSavedPerConsoleAndSurvivesARestart() {
        val lib = library()
        lib.updateConsole(Console.SFC) { it.copy(skin = SkinVariant.DARK.id) }
        assertEquals(SkinVariant.DARK, lib.skinVariant(Console.SFC))
        assertEquals(SkinVariant.CONSOLE, lib.skinVariant(Console.GBA))

        val reopened = library()
        assertEquals(SkinVariant.DARK, reopened.skinVariant(Console.SFC))
        assertEquals(SkinVariant.CONSOLE, reopened.skinVariant(Console.GBA))
    }

    @Test fun pickingTheConsolesColorsAgainIsSavedToo() {
        library().updateConsole(Console.SFC) { it.copy(skin = SkinVariant.DARK.id) }
        library().updateConsole(Console.SFC) { it.copy(skin = SkinVariant.CONSOLE.id) }
        assertEquals(SkinVariant.CONSOLE, library().skinVariant(Console.SFC))
    }

    @Test fun changingTheSkinKeepsTheConsolesOtherSettings() {
        val lib = library()
        lib.updateConsole(Console.SFC) { it.copy(color = "red") }
        val before = lib.consoleSettings(Console.SFC)
        lib.updateConsole(Console.SFC) { it.copy(skin = SkinVariant.DARK.id) }
        assertEquals(before.copy(skin = "dark"), library().consoleSettings(Console.SFC))
    }
}
