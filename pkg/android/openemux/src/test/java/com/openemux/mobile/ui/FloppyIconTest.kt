package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Save and load are the desktop's floppy (issue 945), its line heavier as the glyph shrinks. */
class FloppyIconTest {
    @Test fun theLineFollowsTheGlyphsSizeInDp() {
        assertEquals(1.8f, Icons.floppyStroke(22f))
        assertEquals(1.8f, Icons.floppyStroke(20f))
        assertEquals(2f, Icons.floppyStroke(19.9f))
        assertEquals(2f, Icons.floppyStroke(18f))
        assertEquals(2f, Icons.floppyStroke(16f))
        assertEquals(2.2f, Icons.floppyStroke(15.9f))
        assertEquals(2.2f, Icons.floppyStroke(12f))
    }

    @Test fun saveAndLoadShareTheFloppyAndPointOppositeWays() {
        val floppy = "M16 13V5.5L12.5 2H3.5A1.5 1.5 0 0 0 2 3.5V15a1.5 1.5 0 0 0 1.5 1.5H12.8M6 2v4h6V2M5.5 16.5V12h7v4.5"
        assertTrue(Icons.SaveState.startsWith(floppy))
        assertTrue(Icons.LoadState.startsWith(floppy))
        // Save's arrow points down (its head at y 21.5), load's up (its head at y 15).
        assertTrue(Icons.SaveState.endsWith("M15.5 18.5l3 3 3-3"))
        assertTrue(Icons.LoadState.endsWith("M15.5 18l3-3 3 3"))
    }
}
