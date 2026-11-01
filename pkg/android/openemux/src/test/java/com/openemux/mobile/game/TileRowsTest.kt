package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TileRowsTest {
    @Test fun oneRowWhereverEveryTileFits() {
        assertEquals(1, tileRows(5, 520f))
        assertEquals(1, tileRows(6, 6 * TILE_SLOT_DP))
        assertEquals(1, tileRows(5, 340f))
    }

    @Test fun aNarrowRowWrapsIntoEqualColumns() {
        assertEquals(2, tileRows(6, 328f))
        assertEquals(3, tilesPerRow(6, 2))
        assertEquals(2, tileRows(5, 300f))
        assertEquals(3, tilesPerRow(5, 2))
    }

    @Test fun neverMoreRowsThanTiles() {
        assertEquals(3, tileRows(3, 10f))
        assertEquals(1, tileRows(1, 10f))
        assertEquals(1, tileRows(0, 10f))
    }
}
