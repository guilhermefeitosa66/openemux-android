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
}
