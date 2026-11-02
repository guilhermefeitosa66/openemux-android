package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue 701: a screen opened with the gamepad focuses its game or Play, not the back arrow. */
class InitialFocusTest {

    @Test
    fun nothingFocusedTakesIt() {
        assertTrue(takesInitialFocus(0, inScreens = false, byPlatform = false))
        assertTrue(takesInitialFocus(0, inScreens = null, byPlatform = false))
    }

    @Test
    fun focusThePlatformPutOnTheScreenIsTaken() {
        // A opened the screen (or L1/R1 changed the filter); the old game left and the platform entered on the top left.
        assertTrue(takesInitialFocus(1, inScreens = true, byPlatform = true))
    }

    @Test
    fun focusThePlayerPutThereStays() {
        assertFalse(takesInitialFocus(1, inScreens = true, byPlatform = false))
    }

    @Test
    fun focusOnTheTabsOrTheRailStays() {
        assertFalse(takesInitialFocus(1, inScreens = false, byPlatform = true))
    }

    @Test
    fun outsideTheScreensKeepsTheOldRule() {
        assertFalse(takesInitialFocus(1, inScreens = null, byPlatform = true))
    }

    @Test
    fun aGainWithNoPressSinceIsThePlatforms() {
        assertTrue(focusByPlatform(woke = false, pressedSince = false, gains = 5, gainsAtPress = 4))
    }

    @Test
    fun focusThatStayedPutIsNot() {
        // A on a filter chip: the chip keeps focus, no gain since the press.
        assertFalse(focusByPlatform(woke = false, pressedSince = false, gains = 4, gainsAtPress = 4))
    }

    @Test
    fun aLaterPressOrTheWakingPressIsThePlayers() {
        assertFalse(focusByPlatform(woke = false, pressedSince = true, gains = 5, gainsAtPress = 4))
        assertFalse(focusByPlatform(woke = true, pressedSince = false, gains = 5, gainsAtPress = 4))
    }
}
