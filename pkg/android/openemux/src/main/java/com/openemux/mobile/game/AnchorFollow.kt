package com.openemux.mobile.game

/**
 * Anchor mode as the game activity last knew it (issue 841), from the launch ([last], the console's setting) and then
 * from the engine's reports (onToggleStates), which come for any toggle: only a report that changes it is kept for the
 * console and told to the player.
 */
class AnchorFollow(private var last: Boolean) {
    /** Whether the engine's report [on] changes anchor mode. */
    @Synchronized
    fun report(on: Boolean): Boolean {
        if (on == last) return false
        last = on
        return true
    }
}
