package com.openemux.mobile.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarInsetTest {

    @Test
    fun theRailAndTheTabletopTakeNoRoomAtTheBottom() {
        assertEquals(0.dp, tabBarInset(rail = true, tabletop = false, measured = 70.dp))
        assertEquals(0.dp, tabBarInset(rail = false, tabletop = true, measured = 70.dp))
        assertEquals(0.dp, tabBarInset(rail = true, tabletop = true, measured = Dp.Unspecified))
    }

    @Test
    fun theBarTakesItsMeasuredHeight() {
        // A thin gesture handle: shorter than the old 86dp, which left a gap under the hint bar.
        assertEquals(70.dp, tabBarInset(rail = false, tabletop = false, measured = 70.dp))
        // Three-button navigation: taller, which the hint bar used to overlap.
        assertEquals(117.dp, tabBarInset(rail = false, tabletop = false, measured = 117.dp))
    }

    @Test
    fun beforeItIsMeasuredTheBarTakesTheDefault() {
        assertEquals(DefaultTabBarHeight, tabBarInset(rail = false, tabletop = false, measured = Dp.Unspecified))
        assertEquals(86.dp, DefaultTabBarHeight)
    }
}
