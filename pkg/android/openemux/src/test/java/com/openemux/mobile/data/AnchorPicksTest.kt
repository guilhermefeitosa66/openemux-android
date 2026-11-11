package com.openemux.mobile.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/** Anchor mode turned on or off in the game's process reaches the console's settings (issue 841). */
class AnchorPicksTest {
    private lateinit var root: File
    private lateinit var paths: Paths
    private lateinit var dir: File

    @Before fun setUp() {
        root = Files.createTempDirectory("anchor-picks").toFile()
        File(root, "external").mkdirs()
        paths = Paths(File(root, "external"), File(root, "internal"))
        dir = paths.anchorPicks
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun library() = Library(paths, { true }, storageRetryMs = 0)

    @Test fun aPickComesBackAsWritten() {
        assertTrue(AnchorPicks.record(dir, "SFC", true, at = 5))
        assertEquals(AnchorPicks.Pick("SFC", true, 5), AnchorPicks.drain(dir).single())
    }

    @Test fun drainingTakesThemOutOldestFirst() {
        AnchorPicks.record(dir, "SFC", true, at = 20)
        AnchorPicks.record(dir, "SFC", false, at = 10)
        assertEquals(listOf(10L, 20L), AnchorPicks.drain(dir).map { it.at })
        assertTrue(AnchorPicks.drain(dir).isEmpty())
    }

    @Test fun whatIsNotAPickIsDropped() {
        dir.mkdirs()
        File(dir, "a.pick").writeText("not json")
        File(dir, "b.pick").writeText("""{"console":"SFC","at":1}""")
        assertTrue(AnchorPicks.drain(dir).isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun anAbandonedWriteGoesAndOneInFlightStays() {
        dir.mkdirs()
        val old = File(dir, "old.tmp").apply { writeText("x"); setLastModified(1_000L) }
        val fresh = File(dir, "new.tmp").apply { writeText("x"); setLastModified(100_000L) }
        AnchorPicks.drain(dir, now = 100_000L)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun anUnwritableFolderIsNotAnError() {
        val file = File(root, "file").apply { writeText("x") }
        assertFalse(AnchorPicks.record(File(file, "picks"), "SFC", true))
    }

    @Test fun anchorIsOffUntilTurnedOnAndThenKeptPerConsole() {
        val lib = library()
        assertFalse(lib.anchor(Console.SFC))
        AnchorPicks.record(dir, "SFC", true, at = 10)
        lib.absorbAnchorPicks()
        assertTrue(lib.anchor(Console.SFC))
        assertFalse(lib.anchor(Console.GBA))
        assertTrue(library().anchor(Console.SFC))
    }

    @Test fun theNewestPickWinsAndUnknownConsolesAreDropped() {
        val lib = library()
        AnchorPicks.record(dir, "SFC", false, at = 20)
        AnchorPicks.record(dir, "SFC", true, at = 10)
        AnchorPicks.record(dir, "NOPE", true, at = 30)
        lib.absorbAnchorPicks()
        assertFalse(lib.anchor(Console.SFC))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun nothingToAbsorbIsNothing() {
        val lib = library()
        lib.absorbAnchorPicks()
        assertFalse(lib.anchor(Console.SFC))
    }

    @Test fun theSettingRoundTripsAndIsLeftOutWhileOff() {
        val on = ConsoleSettings("snes9x", "none", anchor = true)
        assertTrue(on.toJson().getBoolean("anchor"))
        assertEquals(on, ConsoleSettings.fromJson(on.toJson()))
        val off = ConsoleSettings("snes9x", "none")
        assertFalse(off.toJson().has("anchor"))
        assertFalse(ConsoleSettings.fromJson(JSONObject(off.toJson().toString())).anchor)
    }
}
