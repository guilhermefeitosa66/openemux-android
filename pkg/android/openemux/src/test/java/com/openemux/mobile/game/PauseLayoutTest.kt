package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseLayoutTest {
    @Test fun aHalfOpenFoldIsTabletopWhateverElseHolds() {
        for (tablet in listOf(false, true)) for (wide in listOf(false, true)) {
            assertEquals(PauseLayout.TABLETOP, pauseLayout(tablet = tablet, tabletop = true, wide = wide))
        }
    }

    @Test fun aTabletGetsThePanelUprightOrOnItsSide() {
        assertEquals(PauseLayout.PANEL, pauseLayout(tablet = true, tabletop = false, wide = false))
        assertEquals(PauseLayout.PANEL, pauseLayout(tablet = true, tabletop = false, wide = true))
    }

    @Test fun aPhoneIsLandscapeOnItsSideAndPhoneUpright() {
        assertEquals(PauseLayout.LANDSCAPE, pauseLayout(tablet = false, tabletop = false, wide = true))
        assertEquals(PauseLayout.PHONE, pauseLayout(tablet = false, tabletop = false, wide = false))
    }

    @Test fun onlyAPhoneUprightOrOnItsSideGetsTheQuickRows() {
        assertTrue(PauseLayout.PHONE.quickRows)
        assertTrue(PauseLayout.LANDSCAPE.quickRows)
        assertFalse(PauseLayout.PANEL.quickRows)
        assertFalse(PauseLayout.TABLETOP.quickRows)
    }
}
