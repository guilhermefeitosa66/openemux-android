package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** When a session that ended is explained, and which BIOS a launch is missing (issue 923). */
class LaunchOutcomeTest {
    @Test fun aQuitThroughTheSheetIsNeverReported() {
        assertNull(launchOutcome(started = true, quit = true, elapsedMs = 100))
        assertNull(launchOutcome(started = false, quit = true, elapsedMs = 100))
    }

    @Test fun anEngineThatNeverDrewIsReportedWheneverItEnded() {
        assertEquals(LaunchOutcome.NOT_STARTED, launchOutcome(started = false, quit = false, elapsedMs = 500))
        assertEquals(LaunchOutcome.NOT_STARTED, launchOutcome(started = false, quit = false, elapsedMs = 60_000))
    }

    @Test fun aShortSessionWithoutQuitIsReportedAndALongOneIsNot() {
        assertEquals(LaunchOutcome.STOPPED_EARLY, launchOutcome(started = true, quit = false, elapsedMs = 5_000))
        assertEquals(LaunchOutcome.STOPPED_EARLY, launchOutcome(started = true, quit = false, elapsedMs = EARLY_END_MS - 1))
        assertNull(launchOutcome(started = true, quit = false, elapsedMs = EARLY_END_MS))
        assertNull(launchOutcome(started = true, quit = false, elapsedMs = 120_000))
    }

    @Test fun theMissingBiosIsTheFirstRequiredOneWithNoneOfItsFiles() {
        val system = Files.createTempDirectory("system").toFile()
        // PlayStation: any one of the listed dumps makes it ready.
        assertEquals("scph5500.bin", missingBios(Console.PS, system)!!.files.first())
        File(system, "scph1001.bin").writeText("bios")
        assertNull(missingBios(Console.PS, system))
        // Game Boy Advance: its BIOS is optional, so nothing is missing.
        assertNull(missingBios(Console.GBA, system))
        // A console with no catalog entry.
        assertNull(missingBios(Console.FC, system))
        // Intellivision needs two files: the first missing one is named.
        File(system, "exec.bin").writeText("x")
        assertEquals("grom.bin", missingBios(Console.INTV, system)!!.files.first())
    }

    @Test fun theLogTailIsTheLastLinesOrNothing() {
        val dir = Files.createTempDirectory("log").toFile()
        assertEquals("", logTail(File(dir, "none.log")))
        val log = File(dir, "retroarch.log").apply { writeText((1..50).joinToString("\n") { "line $it" }) }
        val tail = logTail(log, 3)
        assertEquals("line 48\nline 49\nline 50", tail)
    }
}
