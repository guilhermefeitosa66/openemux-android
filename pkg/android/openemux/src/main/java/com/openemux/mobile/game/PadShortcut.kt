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
}
