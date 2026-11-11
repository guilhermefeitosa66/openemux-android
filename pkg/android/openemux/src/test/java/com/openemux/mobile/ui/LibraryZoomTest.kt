package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryZoomTest {
    @Test fun onlyAPhoneHeldUprightZooms() {
        // Phones upright, small to large, and a closed fold's cover screen.
        for (w in listOf(360f, 393f, 412f, 430f)) assertTrue("$w", zoomApplies(WindowLayout.of(w, 860f), tabletop = false))
        // The same phone on its side is short.
        assertFalse(zoomApplies(WindowLayout.of(860f, 412f), tabletop = false))
        // A 7" tablet upright is Medium wide, a 10" one on its side Expanded; an open fold is about square.
        assertFalse(zoomApplies(WindowLayout.of(600f, 960f), tabletop = false))
        assertFalse(zoomApplies(WindowLayout.of(1280f, 800f), tabletop = false))
        assertFalse(zoomApplies(WindowLayout.of(673f, 841f), tabletop = false))
        // Tabletop takes its own layout, whatever the window.
        assertFalse(zoomApplies(WindowLayout.of(412f, 860f), tabletop = true))
        // A narrow window that is not taller than wide (a split-screen square) keeps its columns.
        assertFalse(zoomApplies(WindowLayout.of(500f, 500f), tabletop = false))
        // A tall, narrow split-screen half is laid out as a small phone.
        assertTrue(zoomApplies(WindowLayout.of(320f, 520f), tabletop = false))
    }

    @Test fun columnsStepOneAtATimeAndStopAtTheEnds() {
        assertEquals(3, stepColumns(2, bigger = false))
        assertEquals(1, stepColumns(2, bigger = true))
        assertEquals(ZOOM_MAX_COLUMNS, stepColumns(ZOOM_MAX_COLUMNS, bigger = false))
        assertEquals(ZOOM_MIN_COLUMNS, stepColumns(ZOOM_MIN_COLUMNS, bigger = true))
        // Pinching in from the default walks 2 → 3 → 4 → 5 and stays.
        val walk = generateSequence(ZOOM_DEFAULT_CARTS) { stepColumns(it, bigger = false) }.take(6).toList()
        assertEquals(listOf(2, 3, 4, 5, 5, 5), walk)
    }

    @Test fun listSizesStepAndStopAtTheEnds() {
        assertEquals(ListSize.Large, ListSize.Normal.step(bigger = true))
        assertEquals(ListSize.Compact, ListSize.Normal.step(bigger = false))
        assertEquals(ListSize.ExtraLarge, ListSize.ExtraLarge.step(bigger = true))
        assertEquals(ListSize.Compact, ListSize.Compact.step(bigger = false))
    }

    @Test fun listRowsMatchTheRequest() {
        assertEquals(listOf(40, 64, 96, 128), ListSize.entries.map { it.artDp })
        assertEquals(listOf(56, 80, 112, 144), ListSize.entries.map { it.rowDp })
        assertEquals(listOf(1, 1, 2, 2), ListSize.entries.map { it.titleLines })
        // Normal is today's row: 64dp art, the body title on one line, the console small, 14dp between.
        assertEquals(15, ListSize.Normal.titleSp)
        assertEquals(14, ListSize.Normal.textGapDp)
        assertTrue(ListSize.ExtraLarge.titleSp > ListSize.Large.titleSp)
    }

    @Test fun aPinchStepsOnceItCrossesAThreshold() {
        assertNull(pinchStep(1f))
        assertNull(pinchStep(1.2f))
        assertNull(pinchStep(0.85f))
        assertEquals(true, pinchStep(PINCH_BIGGER))
        assertEquals(true, pinchStep(2f))
        assertEquals(false, pinchStep(PINCH_SMALLER))
        assertEquals(false, pinchStep(0.5f))
    }

    @Test fun narrowTilesTightenTheirGapsAndCaptions() {
        // Up to three columns, today's spacing and captions.
        for (n in 1..3) {
            assertEquals(ZoomGrid(GridSpec.Carts.gap, 18f, 13), zoomGrid(n, carts = true))
            assertEquals(ZoomGrid(GridSpec.Covers.gap, 18f, 12), zoomGrid(n, carts = false))
        }
        assertEquals(ZoomGrid(10f, 12f, 11), zoomGrid(4, carts = true))
        assertEquals(ZoomGrid(8f, 12f, 11), zoomGrid(4, carts = false))
        assertEquals(ZoomGrid(10f, 12f, null), zoomGrid(5, carts = true))
        assertEquals(ZoomGrid(8f, 12f, null), zoomGrid(5, carts = false))
    }

    @Test fun storedValuesParse() {
        assertEquals(4, LibraryZoomPref.parseColumns(4, ZOOM_DEFAULT_CARTS))
        assertEquals(1, LibraryZoomPref.parseColumns(1, ZOOM_DEFAULT_CARTS))
        assertEquals(5, LibraryZoomPref.parseColumns(5, ZOOM_DEFAULT_CARTS))
        assertEquals(ZOOM_DEFAULT_CARTS, LibraryZoomPref.parseColumns(0, ZOOM_DEFAULT_CARTS))
        assertEquals(ZOOM_DEFAULT_COVERS, LibraryZoomPref.parseColumns(6, ZOOM_DEFAULT_COVERS))
        assertEquals(ListSize.Large, LibraryZoomPref.parseList("Large"))
        assertEquals(ListSize.ExtraLarge, LibraryZoomPref.parseList("ExtraLarge"))
        assertEquals(ListSize.Normal, LibraryZoomPref.parseList("Huge"))
        assertEquals(ListSize.Normal, LibraryZoomPref.parseList(null))
    }

    @Test fun theDefaultsAreTodaysPhoneColumns() {
        // On every phone width the automatic columns were 2 cartridges and 3 covers: the defaults change nothing.
        for (w in listOf(360f, 393f, 412f, 430f)) {
            val avail = w - 40f
            assertEquals(ZOOM_DEFAULT_CARTS, gridColumns(avail, GridSpec.Carts.min, GridSpec.Carts.gap, GridSpec.Carts.floor))
            assertEquals(ZOOM_DEFAULT_COVERS, gridColumns(avail, GridSpec.Covers.min, GridSpec.Covers.gap, GridSpec.Covers.floor))
        }
        assertEquals(ListSize.Normal, LibraryZoomPref.list)
    }

    @Test fun thePinchAnchorsOnTheGameUnderTheFingers() {
        // Two columns of 100px tiles, 20px apart, two rows; a full-width header above them.
        val header = ZoomSlot(0, 0, 0, 220, 80)
        val tiles = listOf(ZoomSlot(1, 0, 100, 100, 100), ZoomSlot(2, 120, 100, 100, 100), ZoomSlot(3, 0, 220, 100, 100), ZoomSlot(4, 120, 220, 100, 100))
        val slots = listOf(header) + tiles
        assertEquals(2, anchorItem(slots, 150f, 150f)?.index)
        assertEquals(3, anchorItem(slots, 30f, 300f)?.index)
        // In the gap between two tiles on a row, the nearer one.
        assertEquals(1, anchorItem(slots, 105f, 150f)?.index)
        assertEquals(2, anchorItem(slots, 115f, 150f)?.index)
        // Between rows, the row whose middle is nearer; below everything, the last row.
        assertEquals(3, anchorItem(slots, 30f, 215f)?.index)
        assertEquals(3, anchorItem(slots, 200f, 900f)?.index)
        assertEquals(0, anchorItem(slots, 100f, 40f)?.index)
        assertNull(anchorItem(emptyList(), 0f, 0f))
    }
}
