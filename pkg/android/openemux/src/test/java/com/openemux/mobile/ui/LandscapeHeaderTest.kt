package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeHeaderTest {
    private val full = HeaderBand(steps = true, icon = true, shortName = false, count = true, caret = true)
    private val middle = HeaderBand(steps = true, icon = true, shortName = false, count = false, caret = false)
    private val narrow = HeaderBand(steps = false, icon = false, shortName = true, count = false, caret = true)

    @Test
    fun theWidestRowShowsEverything() {
        assertEquals(full, headerBand(HEADER_FULL_DP, padLayout = false))
        assertEquals(full, headerBand(HEADER_FULL_DP + 1f, padLayout = false))
        assertEquals(full, headerBand(900f, padLayout = false))
    }

    @Test
    fun theMiddleBandDropsTheCountAndTheCaret() {
        assertEquals(middle, headerBand(HEADER_FULL_DP - 0.5f, padLayout = false))
        assertEquals(middle, headerBand(HEADER_NARROW_DP + 1f, padLayout = false))
        assertEquals(middle, headerBand(HEADER_NARROW_DP, padLayout = false))
        // A 640x360 phone with the rail: 520dp of row.
        assertEquals(middle, headerBand(520f, padLayout = false))
    }

    @Test
    fun theNarrowestBandDropsTheStepsAndShortensTheName() {
        assertEquals(narrow, headerBand(HEADER_NARROW_DP - 0.5f, padLayout = false))
        assertEquals(narrow, headerBand(300f, padLayout = false))
    }

    @Test
    fun theGamepadLayoutNeverShowsTheCaret() {
        assertEquals(full.copy(caret = false), headerBand(HEADER_FULL_DP, padLayout = true))
        assertEquals(middle, headerBand(600f, padLayout = true))
        assertEquals(narrow.copy(caret = false), headerBand(400f, padLayout = true))
    }

    private val ids = listOf("ALL", "FAV", "FC", "SFC", "GBA")

    @Test
    fun stepFilterWalksTheListAndClampsAtTheEnds() {
        assertEquals("FAV", stepFilter(ids, "ALL", 1))
        assertEquals("ALL", stepFilter(ids, "ALL", -1))
        assertEquals("SFC", stepFilter(ids, "FC", 1))
        assertEquals("FAV", stepFilter(ids, "FC", -1))
        assertEquals("GBA", stepFilter(ids, "GBA", 1))
        assertEquals("SFC", stepFilter(ids, "GBA", -1))
    }

    @Test
    fun anUnknownFilterCountsAsTheFirst() {
        assertEquals("FAV", stepFilter(ids, "MD", 1))
        assertEquals("ALL", stepFilter(ids, "MD", -1))
    }

    @Test
    fun canStepIsFalseOnlyAtAnEnd() {
        assertFalse(canStep(ids, "ALL", -1))
        assertTrue(canStep(ids, "ALL", 1))
        assertTrue(canStep(ids, "FC", -1))
        assertTrue(canStep(ids, "FC", 1))
        assertTrue(canStep(ids, "GBA", -1))
        assertFalse(canStep(ids, "GBA", 1))
        assertFalse(canStep(listOf("ALL"), "ALL", 1))
        assertFalse(canStep(listOf("ALL"), "ALL", -1))
        // An unknown filter steps from the first one, and either way it leaves for a known one.
        assertTrue(canStep(ids, "MD", -1))
        assertTrue(canStep(ids, "MD", 1))
    }

    @Test
    fun theFullNameGivesWayToTheShortOneOnlyWhenItCannotFit() {
        assertEquals("Super Nintendo", nameThatFits("Super Nintendo", "SNES", false, fullWidthPx = 200, maxWidthPx = 260))
        assertEquals("Super Nintendo", nameThatFits("Super Nintendo", "SNES", false, fullWidthPx = 260, maxWidthPx = 260))
        assertEquals("SNES", nameThatFits("Super Nintendo", "SNES", false, fullWidthPx = 261, maxWidthPx = 260))
        // The narrowest band always asks for the short name.
        assertEquals("SNES", nameThatFits("Super Nintendo", "SNES", true, fullWidthPx = 10, maxWidthPx = 260))
    }
}
