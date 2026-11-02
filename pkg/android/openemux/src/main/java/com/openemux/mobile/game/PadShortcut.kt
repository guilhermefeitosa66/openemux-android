package com.openemux.mobile.game

import android.view.KeyEvent

/**
 * The gamepad shortcut the game activity takes while a game plays. The engine offers it R3 (the right stick click)
 * only, and only then: paused, the pause sheet gets every key instead.
 */
object PadShortcut {
    /** R3 steps the NDS screen layout ([ndsScreens]), standing in for DeSmuME's own quick screen switch. */
    fun claims(keycode: Int, ndsScreens: Boolean): Boolean = ndsScreens && keycode == KeyEvent.KEYCODE_BUTTON_THUMBR

    /** A press acts on its release, so holding R3 down steps once. */
    fun acts(action: Int): Boolean = action == KeyEvent.ACTION_UP

    /** Where a press goes: the game playing, or the pause sheet. */
    enum class Place { GAME, SHEET }

    /**
     * Where the last R3 press went down: its release acts only there. A press that started in game and ends on the
     * sheet (or the other way round, the sheet closing in between) does nothing.
     */
    class Press {
        private var down: Place? = null

        /** Records [action] in [place]; true for the release of a press that went down there too. */
        @Synchronized
        fun act(place: Place, action: Int): Boolean {
            if (action == KeyEvent.ACTION_DOWN) { down = place; return false }
            if (!acts(action)) return false
            val same = down == place
            down = null
            return same
        }
    }
}
