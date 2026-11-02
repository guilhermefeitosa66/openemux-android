package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 701: a screen opened with the gamepad focuses its game or Play, never chrome, and keeps it. */
class InitialFocusTest {

    @Test
    fun nothingFocusedTakesIt() {
        assertTrue(takesInitialFocus(0, inScreens = false, pressed = true, woke = true))
        assertTrue(takesInitialFocus(0, inScreens = null, pressed = false, woke = false))
    }

    @Test
    fun focusThePlatformLeftInTheScreensIsTaken() {
        // A opened the screen (or L1/R1 changed the filter): the old game left, or still holds focus on its way out.
        assertTrue(takesInitialFocus(1, inScreens = true, pressed = false, woke = false))
    }

    @Test
    fun focusThePlayerMovedStays() {
        assertFalse(takesInitialFocus(1, inScreens = true, pressed = true, woke = false))
    }

    @Test
    fun theWakingPressKeepsWhereItLanded() {
        assertFalse(takesInitialFocus(1, inScreens = true, pressed = false, woke = true))
    }

    @Test
    fun focusOnTheTabsOrTheRailStays() {
        assertFalse(takesInitialFocus(1, inScreens = false, pressed = false, woke = false))
    }

    @Test
    fun outsideTheScreensKeepsTheOldRule() {
        assertFalse(takesInitialFocus(1, inScreens = null, pressed = false, woke = false))
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
