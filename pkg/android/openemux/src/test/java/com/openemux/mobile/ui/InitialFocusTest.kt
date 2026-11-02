package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 701: a screen opened with the gamepad focuses its game or Play, not the back arrow. */
class InitialFocusTest {

    @Test
    fun nothingFocusedTakesIt() {
        assertTrue(takesInitialFocus(0, inScreens = false, pressed = false))
        assertTrue(takesInitialFocus(0, inScreens = null, pressed = true))
    }

    @Test
    fun focusThePlatformPutOnTheNewScreenIsTaken() {
        // A opened the screen; the old item left and the platform entered on the top left item.
        assertTrue(takesInitialFocus(1, inScreens = true, pressed = false))
    }

    @Test
    fun focusThePlayerMovedStays() {
        assertFalse(takesInitialFocus(1, inScreens = true, pressed = true))
    }

    @Test
    fun focusOnTheTabsOrTheRailStays() {
        assertFalse(takesInitialFocus(1, inScreens = false, pressed = false))
    }

    @Test
    fun aSheetsWindowKeepsTheOldRule() {
        assertFalse(takesInitialFocus(1, inScreens = null, pressed = false))
    }
}
