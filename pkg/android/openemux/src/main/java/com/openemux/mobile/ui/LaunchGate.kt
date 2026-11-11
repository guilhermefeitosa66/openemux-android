package com.openemux.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the app's content may show yet at launch (issue 850). The app runs full screen, but on a cold start the
 * system splash before it shows the status and navigation bars, and they are still on screen for a moment once the
 * app's window takes over: the content waits behind the splash look until they are gone, so it is never drawn under
 * them. A start that is not [fresh] (the activity recreated) opens straight away.
 *
 * It opens once the bars are hidden, or in multi-window (where they cannot be hidden and the content is padded
 * away from them instead), or after [capMs] in any case, so a device that keeps them never holds the app back.
 * Once open, it stays open.
 */
class LaunchGate(fresh: Boolean, private val capMs: Long = CAP_MS) {
    var open by mutableStateOf(!fresh)
        private set

    /** Reads the window's state again; true once the content may show. */
    fun update(barsVisible: Boolean, multiWindow: Boolean, elapsedMs: Long): Boolean {
        if (!open && (!barsVisible || multiWindow || elapsedMs >= capMs)) open = true
        return open
    }

    companion object {
        /** The longest the content waits for the bars to leave. */
        const val CAP_MS = 700L
    }
}

/** The library window's state the layout follows: set by MainActivity. */
object AppWindow {
    /** Split screen or a freeform window: the system bars stay, so the content is padded away from them. */
    var multiWindow by mutableStateOf(false)

    /** This launch's gate; open unless the activity has just been created by a launch. */
    var gate by mutableStateOf(LaunchGate(fresh = false))
}
