package com.openemux.mobile.game

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadShortcutTest {
    @Test fun r3IsTakenOnlyForTheNdsScreens() {
        assertTrue(PadShortcut.claims(KeyEvent.KEYCODE_BUTTON_THUMBR, ndsScreens = true))
        assertFalse(PadShortcut.claims(KeyEvent.KEYCODE_BUTTON_THUMBR, ndsScreens = false))
        assertFalse(PadShortcut.claims(KeyEvent.KEYCODE_BUTTON_THUMBL, ndsScreens = true))
        assertFalse(PadShortcut.claims(KeyEvent.KEYCODE_BUTTON_A, ndsScreens = true))
    }

    @Test fun thePressActsOnItsRelease() {
        assertTrue(PadShortcut.acts(KeyEvent.ACTION_UP))
        assertFalse(PadShortcut.acts(KeyEvent.ACTION_DOWN))
    }

    @Test fun aReleaseActsOnlyWhereItsPressWentDown() {
        val press = PadShortcut.Press()
        val game = PadShortcut.Place.GAME
        val sheet = PadShortcut.Place.SHEET
        assertFalse(press.act(game, KeyEvent.ACTION_DOWN))
        assertTrue(press.act(game, KeyEvent.ACTION_UP))
        // Down in game, the sheet opens, up on the sheet: nothing.
        press.act(game, KeyEvent.ACTION_DOWN)
        assertFalse(press.act(sheet, KeyEvent.ACTION_UP))
        // A release with no press before it, and one already used: nothing.
        assertFalse(press.act(sheet, KeyEvent.ACTION_UP))
        press.act(sheet, KeyEvent.ACTION_DOWN)
        assertTrue(press.act(sheet, KeyEvent.ACTION_UP))
        assertFalse(press.act(sheet, KeyEvent.ACTION_UP))
        // Any other action (a long-press repeat is a DOWN; MULTIPLE is neither): nothing.
        assertFalse(press.act(game, KeyEvent.ACTION_MULTIPLE))
    }
}
