package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetStopsTest {
    // A 2000 px tall area, the taller stop at 40 and the first one at 900, where the sheet is 1100 tall.
    private val expanded = 40f
    private val first = 900f
    private val height = 1100f
    private val fling = 1000f

    private fun land(top: Float, velocity: Float = 0f, fromFirst: Boolean = true) =
        settle(top, velocity, expanded, first, height, fromFirst, fling)

    @Test fun theFirstStopSitsUnderTheGame() {
        assertEquals(820f, firstStopTop(gameBottom = 800f, area = 2000f, expanded = 40f, minVisible = 700f, gap = 20f))
    }

    @Test fun aTallGameStillLeavesTheEssentialsShowing() {
        assertEquals(1300f, firstStopTop(gameBottom = 1600f, area = 2000f, expanded = 40f, minVisible = 700f, gap = 20f))
    }

    @Test fun theFirstStopIsNeverAboveTheTallerOne() {
        assertEquals(40f, firstStopTop(gameBottom = 0f, area = 2000f, expanded = 40f, minVisible = 1990f, gap = 0f))
    }

    @Test fun aShortDragDownFromTheFirstStopSpringsBack() {
        assertEquals(SheetStop.FIRST, land(first + height / 3f - 1f))
        assertEquals(SheetStop.FIRST, land(first + height / 3f))
    }

    @Test fun pastAThirdItCloses() {
        assertEquals(SheetStop.CLOSED, land(first + height / 3f + 1f))
    }

    @Test fun aDragFromTheTallerStopNeverCloses() {
        assertEquals(SheetStop.FIRST, land(1900f, velocity = 5000f, fromFirst = false))
    }

    @Test fun aFlingBetweenTheStopsGoesItsWay() {
        assertEquals(SheetStop.EXPANDED, land(800f, velocity = -fling))
        assertEquals(SheetStop.FIRST, land(100f, velocity = fling))
    }

    @Test fun withoutAFlingTheNearestStopWins() {
        assertEquals(SheetStop.EXPANDED, land(400f, velocity = 300f))
        assertEquals(SheetStop.FIRST, land(500f, velocity = -300f))
        assertEquals(SheetStop.FIRST, land(first))
    }

    @Test fun contentThatFitsHasNoTallerStop() {
        assertEquals(SheetStop.FIRST, settle(500f, -5000f, expanded = 600f, first = 600f, firstHeight = 400f, fromFirst = true, fling = fling))
    }
}
