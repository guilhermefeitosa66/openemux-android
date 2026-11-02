package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 701: a screen opened with the gamepad focuses its game or Play, never chrome, and keeps it. */
class InitialFocusTest {

    @Test
    fun nothingFocusedTakesIt() {
        assertTrue(takesInitialFocus(0, inScreens = false, byPlatform = false, woke = true))
        assertTrue(takesInitialFocus(0, inScreens = null, byPlatform = false, woke = false))
    }

    @Test
    fun focusThePlatformMovedInTheScreensIsTaken() {
        // A opened the screen (or L1/R1 changed the filter): the old game left and the platform entered top left.
        assertTrue(takesInitialFocus(1, inScreens = true, byPlatform = true, woke = false))
    }

    @Test
    fun focusAPressPutSomewhereStays() {
        // On Sort when a rescan drops the remembered game: no gain since the press that moved there.
        assertFalse(takesInitialFocus(1, inScreens = true, byPlatform = false, woke = false))
    }

    @Test
    fun theWakingPressKeepsWhereItLanded() {
        assertFalse(takesInitialFocus(1, inScreens = true, byPlatform = true, woke = true))
    }

    @Test
    fun focusOnTheTabsOrTheRailStays() {
        assertFalse(takesInitialFocus(1, inScreens = false, byPlatform = true, woke = false))
    }

    @Test
    fun outsideTheScreensKeepsTheOldRule() {
        assertFalse(takesInitialFocus(1, inScreens = null, byPlatform = true, woke = false))
    }

    @Test
    fun theHoldLastsUntilThePlayersFirstPress() {
        assertTrue(holdsFocus(0, pressed = false))
        assertTrue(holdsFocus(FocusHoldMs - 1, pressed = false))
        assertFalse(holdsFocus(10, pressed = true))
    }

    @Test
    fun theHoldEndsWhenTheScreenHasSettled() {
        assertFalse(holdsFocus(FocusHoldMs, pressed = false))
    }

    @Test
    fun theHintsAreReadLive() {
        var label = "Choose"
        val actions = GamepadActions(hints = { listOf("A" to label) })
        assertTrue(actions.hints() == listOf("A" to "Choose"))
        label = "Play"
        assertTrue(actions.hints() == listOf("A" to "Play"))
    }
}
