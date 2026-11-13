package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLayoutTest {
    @Test
    fun columnsFollowTheWidth() {
        assertEquals(1, shelfColumns(0f))
        assertEquals(1, shelfColumns(-10f))
        assertEquals(1, shelfColumns(100f))
        assertEquals(1, shelfColumns(320f))
        assertEquals(1, shelfColumns(412f))
        // A 5" phone in landscape, after the side rail: two cards would be too narrow.
        assertEquals(1, shelfColumns(489f))
        assertEquals(2, shelfColumns(560f))
        assertEquals(2, shelfColumns(835f))
        assertEquals(2, shelfColumns(1200f))
    }

    @Test
    fun columnsAreCapped() {
        assertEquals(3, shelfColumns(1200f, maxColumns = 3))
        assertEquals(2, shelfColumns(560f, maxColumns = 3))
        assertEquals(1, shelfColumns(1200f, maxColumns = 0))
    }

    @Test
    fun phoneCardIconIsTheHero() {
        assertEquals(ShelfCardSpec(box = 76f, icon = 64f, star = 42f, nameSp = 20f, startPadding = 16f), shelfCardSpec(false))
    }

    @Test
    fun tabletCardIconIsLarger() {
        assertEquals(ShelfCardSpec(box = 112f, icon = 96f, star = 62f, nameSp = 24f, startPadding = 20f), shelfCardSpec(true))
    }

    private val slots = listOf(
        Slot("FAV", 0f, 0f, 100f, 50f), Slot("SFC", 110f, 0f, 100f, 50f),
        Slot("GBA", 0f, 60f, 100f, 50f), Slot("MD", 110f, 60f, 100f, 50f),
    )
    private val movable = { k: String -> k != "FAV" }

    @Test
    fun targetIsTheSlotUnderTheCentre() {
        assertEquals("MD", reorderTarget(150f, 80f, "GBA", slots, movable))
        // Across columns and rows.
        assertEquals("GBA", reorderTarget(10f, 70f, "SFC", slots, movable))
    }

    @Test
    fun noTargetOnAGapTheCardItselfOrAFixedSlot() {
        assertNull(reorderTarget(105f, 20f, "GBA", slots, movable))
        assertNull(reorderTarget(105f, 55f, "GBA", slots, movable))
        assertNull(reorderTarget(150f, 20f, "SFC", slots, movable))
        assertNull(reorderTarget(20f, 20f, "SFC", slots, movable))
        // Right and bottom edges belong to the next slot, not this one.
        assertNull(reorderTarget(210f, 80f, "GBA", slots, movable))
        assertNull(reorderTarget(150f, 110f, "GBA", slots, movable))
        assertNull(reorderTarget(-1f, 80f, "MD", slots, movable))
        assertNull(reorderTarget(10f, 59f, "MD", slots, movable))
    }

    @Test
    fun pinnedShelvesFollowTheLibrary() {
        assertEquals(emptyList<String>(), pinnedShelves(0, 0))
        assertEquals(listOf("ALL"), pinnedShelves(5, 0))
        assertEquals(listOf("ALL", "FAV"), pinnedShelves(5, 2))
    }

    @Test
    fun pinnedShelvesPairOnlyInOneColumnWithBoth() {
        assertTrue(pinnedAsPair(1, listOf("ALL", "FAV")))
        assertFalse(pinnedAsPair(1, listOf("ALL")))
        assertFalse(pinnedAsPair(1, emptyList()))
        assertFalse(pinnedAsPair(2, listOf("ALL", "FAV")))
        assertFalse(pinnedAsPair(3, listOf("ALL", "FAV")))
    }

    @Test
    fun aLoneContinueCardTakesTheWidthAndMoreThanOneLeaveAPeek() {
        // One game: the whole shelf width, like the console cards under it.
        assertEquals(320f, continueCardWidth(360f, 1))
        assertEquals(320f, continueCardWidth(360f, 0))
        // Two or more: a fixed card on a wide phone, the next card peeking at the edge.
        assertEquals(PHONE_CONTINUE_CARD_DP, continueCardWidth(411f, 2))
        assertEquals(PHONE_CONTINUE_CARD_DP, continueCardWidth(800f, 3))
        // A narrow phone: narrower than the fixed card, so the peek survives.
        assertEquals(320f * 0.86f, continueCardWidth(360f, 2), 0.01f)
        assertTrue(continueCardWidth(360f, 2) < continueCardWidth(360f, 1))
        // Nothing to lay out in no room.
        assertEquals(0f, continueCardWidth(10f, 1))
        assertEquals(0f, continueCardWidth(10f, 2))
    }
}
