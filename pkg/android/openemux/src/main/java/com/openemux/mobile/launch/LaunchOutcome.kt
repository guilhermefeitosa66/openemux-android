package com.openemux.mobile.launch

import com.openemux.mobile.data.BiosDef
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KNOWN_BIOS
import java.io.File

/** What a launch is doing, for the overlay (issue 923). */
sealed interface LaunchStage {
    /** The cartridge picture and the controller skin. */
    data object Controls : LaunchStage
    /** The engine is being started with [core]. */
    data class Core(val core: String) : LaunchStage
    /** The console's core is being fetched first (issue 922); [total] is 0 while the size is unknown. */
    data class Download(val core: String, val bytes: Long, val total: Long) : LaunchStage
}

/** How a game session ended, as the library sees it when it comes back (issue 923). */
enum class LaunchOutcome {
    /** The engine never showed a frame: the core refused the content, or died loading. */
    NOT_STARTED,
    /** It played, then closed within seconds without the pause sheet's Quit. */
    STOPPED_EARLY,
}

/**
 * Whether a session that is over deserves an explanation: nothing when the player quit through the sheet, or when
 * the engine ran for at least [earlyMs] (RetroArch's own menu can close the content); [started] says the engine
 * showed its first overlay.
 */
fun launchOutcome(started: Boolean, quit: Boolean, elapsedMs: Long, earlyMs: Long = EARLY_END_MS): LaunchOutcome? = when {
    quit -> null
    !started -> LaunchOutcome.NOT_STARTED
    elapsedMs < earlyMs -> LaunchOutcome.STOPPED_EARLY
    else -> null
}

/** A session shorter than this that ended without Quit is reported. */
const val EARLY_END_MS = 20_000L

/** The first required BIOS of [console] with none of its files in [systemDir], or null when the console is ready. */
fun missingBios(console: Console, systemDir: File): BiosDef? =
    KNOWN_BIOS.firstOrNull { def -> def.console == console && def.required && def.files.none { File(systemDir, it).isFile } }

/** The last [lines] lines of RetroArch's log for the session, "" without one. */
fun logTail(logFile: File, lines: Int = 40): String =
    runCatching { logFile.readLines().takeLast(lines).joinToString("\n") }.getOrDefault("")

/** The session markers the game activity writes next to its config: the first frame, and a quit through the sheet. */
object SessionMarks {
    const val STARTED = "started"
    const val QUIT = "quit"
    const val LOG = "retroarch.log"
}
