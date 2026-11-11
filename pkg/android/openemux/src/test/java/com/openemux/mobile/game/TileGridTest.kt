package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGridTest {
    @Test fun fourColumnsOnAPhoneUprightFiveElsewhere() {
        assertEquals(4, tileColumns(PauseLayout.PHONE))
        assertEquals(5, tileColumns(PauseLayout.LANDSCAPE))
        assertEquals(5, tileColumns(PauseLayout.PANEL))
        assertEquals(5, tileColumns(PauseLayout.TABLETOP))
    }

    @Test fun aPhoneUprightSharesTheRowOutAfterEightDpGaps() {
        // 412 dp wide, 20 dp padding each side: 372 dp of content.
        assertEquals(TileGrid(4, 87f, 8f), tileGrid(372f, 4))
        // A 360 dp phone keeps four columns.
        assertEquals(TileGrid(4, 74f, 8f), tileGrid(320f, 4))
    }

    @Test fun theGapShrinksBeforeAColumnGetsNarrowerThanATile() {
        // The landscape half: five tiles in one row, as before.
        val grid = tileGrid(350f, 5)
        assertEquals(5, grid.columns)
        assertEquals(2.5f, grid.gapDp, 0.001f)
        assertEquals(TILE_SLOT_DP, grid.columnDp, 0.001f)
        assertEquals(TileGrid(5, TILE_SLOT_DP, 0f), tileGrid(5 * TILE_SLOT_DP, 5))
    }

    @Test fun fewerColumnsOnlyWhereTheTilesWouldNotFit() {
        assertEquals(TileGrid(4, TILE_SLOT_DP, 6f), tileGrid(290f, 5))
        assertEquals(TileGrid(1, 60f, 0f), tileGrid(60f, 4))
        assertEquals(TileGrid(1, 100f, 0f), tileGrid(100f, 1))
        assertEquals(1, tileGrid(500f, 0).columns)
    }

    @Test fun aWideRowKeepsTheColumnCount() {
        assertEquals(TileGrid(5, 152f, 8f), tileGrid(792f, 5))
    }

    @Test fun rowsWrapLeftAlignedNeverRebalanced() {
        val five = (1..5).toList()
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(5)), five.chunked(tileGrid(372f, 4).columns))
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(5, 6)), (1..6).toList().chunked(tileGrid(372f, 4).columns))
        assertEquals(listOf(listOf(1, 2, 3, 4, 5), listOf(6)), (1..6).toList().chunked(tileGrid(350f, 5).columns))
    }

    @Test fun aLineBreakInsideAWordSplitsIt() {
        assertTrue(splitsAWord("Bildschirmfoto", listOf(10, 14)))
        assertTrue(splitsAWord("திரைப்பிடிப்பு", listOf(5)))
        assertTrue(splitsAWord("スクリーンショット", listOf(3)).not())
        assertFalse(splitsAWord("Slow motion", listOf(5, 11)))
        assertFalse(splitsAWord("Slow motion", listOf(4)))
        assertFalse(splitsAWord("Capture d’écran", listOf(8)))
        assertFalse(splitsAWord("Fast-forward", listOf(5)))
        assertFalse(splitsAWord("Shader", listOf(6)))
        assertFalse(splitsAWord("Shader", listOf(0)))
        assertFalse(splitsAWord("快进", listOf(1)))
        assertFalse(splitsAWord("x‐y", listOf(2)))
        assertFalse(splitsAWord("Save–load", listOf(5)))
        assertFalse(splitsAWord("Save/load", listOf(5)))
    }
}
