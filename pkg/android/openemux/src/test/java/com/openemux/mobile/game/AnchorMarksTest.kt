package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorMarksTest {
    private fun mark(rect: Boolean, x: Float, y: Float) = AnchorMark(0.2f, 0.6f, 0.2f, 0.1f, rect, x, y)

    @Test fun parsesSevenFloatsAMark() {
        val marks = AnchorMark.parse(floatArrayOf(0.1f, 0.6f, 0.2f, 0.1f, 1f, 0.4f, 0.8f, 0.5f, 0.5f, 0.1f, 0.1f, 0f, 0.9f, 0.9f))
        assertEquals(listOf(AnchorMark(0.1f, 0.6f, 0.2f, 0.1f, true, 0.4f, 0.8f), AnchorMark(0.5f, 0.5f, 0.1f, 0.1f, false, 0.9f, 0.9f)), marks)
        assertEquals(0.2f, marks[0].centerX, 1e-6f)
        assertEquals(0.65f, marks[0].centerY, 1e-6f)
    }

    @Test fun nothingHeldIsNoMarkAndAPartialOneIsIgnored() {
        assertEquals(emptyList<AnchorMark>(), AnchorMark.parse(FloatArray(0)))
        assertEquals(1, AnchorMark.parse(FloatArray(AnchorMark.FLOATS + 3)).size)
    }

    @Test fun aRoundButtonIsLeftOutsideItsEllipse() {
        assertFalse(mark(false, 0.3f, 0.65f).fingerOff())
        assertFalse(mark(false, 0.39f, 0.65f).fingerOff())
        // Inside the box's corner, outside the ellipse.
        assertTrue(mark(false, 0.39f, 0.69f).fingerOff())
        assertTrue(mark(false, 0.5f, 0.65f).fingerOff())
    }

    @Test fun aRectButtonIsLeftOutsideItsBox() {
        assertFalse(mark(true, 0.39f, 0.69f).fingerOff())
        assertTrue(mark(true, 0.41f, 0.65f).fingerOff())
        assertTrue(mark(true, 0.3f, 0.71f).fingerOff())
        assertTrue(mark(true, 0.3f, 0.59f).fingerOff())
    }

    @Test fun aSquareOrRoundButtonGetsARoundRingAndAShoulderAPill() {
        assertTrue(mark(false, 0f, 0f).ringRound(300f, 100f))
        assertTrue(mark(true, 0f, 0f).ringRound(100f, 110f))
        assertFalse(mark(true, 0f, 0f).ringRound(300f, 100f))
        assertFalse(mark(true, 0f, 0f).ringRound(100f, 300f))
        assertFalse(mark(true, 0f, 0f).ringRound(100f, 0f))
    }

    @Test fun aButtonWithNoSizeAlwaysDrawsTheLine() {
        assertTrue(AnchorMark(0.2f, 0.2f, 0f, 0.1f, true, 0.2f, 0.25f).fingerOff())
        assertTrue(AnchorMark(0.2f, 0.2f, 0.1f, 0f, false, 0.25f, 0.2f).fingerOff())
    }

    @Test fun theCardFollowsTheFingerDownOnly() {
        assertEquals(30f, AnchorCardSwipe.drag(10f, 20f), 0f)
        assertEquals(0f, AnchorCardSwipe.drag(10f, -40f), 0f)
    }

    @Test fun theCardClosesPastAThirdOrOnAFling() {
        assertFalse(AnchorCardSwipe.closes(30f, 0f, 120f))
        assertTrue(AnchorCardSwipe.closes(41f, 0f, 120f))
        assertTrue(AnchorCardSwipe.closes(5f, AnchorCardSwipe.FLING + 1f, 120f))
        assertFalse(AnchorCardSwipe.closes(5f, -AnchorCardSwipe.FLING * 2, 120f))
        // Not measured yet: only a fling closes it.
        assertFalse(AnchorCardSwipe.closes(50f, 0f, 0f))
    }

    @Test fun theCardFadesAsItGoes() {
        assertEquals(1f, AnchorCardSwipe.alpha(0f, 120f), 0f)
        assertEquals(0.5f, AnchorCardSwipe.alpha(60f, 120f), 1e-6f)
        assertEquals(0f, AnchorCardSwipe.alpha(200f, 120f), 0f)
        assertEquals(1f, AnchorCardSwipe.alpha(50f, 0f), 0f)
    }

    @Test fun theCardSitsAtTheBottomOfTheScreenCentred() {
        // Portrait phone, the game filling the screen: narrower than the cap, it spans the width less the margins.
        assertEquals(AnchorCardPlace(8, 8, 1064), AnchorCardPlace.of(0, 1080, 2400, 0, 2400, 8, 1200))
        // Landscape with a cutout bar on the left: capped, and centred on the game's window, not the display.
        assertEquals(AnchorCardPlace(933, 8, 660), AnchorCardPlace.of(126, 2274, 1080, 0, 1080, 8, 660))
    }

    @Test fun inSplitScreenTheCardStaysOnTheGamesWindow() {
        // The game in the right half: the frame starts where the game does.
        assertEquals(AnchorCardPlace(270, 8, 660), AnchorCardPlace.of(1200, 1200, 1080, 1200, 1080, 8, 660))
        // A frame that runs below the game's window: the card is lifted to the game's bottom.
        assertEquals(AnchorCardPlace(8, 1208, 1064), AnchorCardPlace.of(0, 1080, 1200, 0, 2400, 8, 1200))
    }

    @Test fun aScreenNarrowerThanTheMarginsGivesTheCardNoWidth() {
        assertEquals(AnchorCardPlace(5, 8, 0), AnchorCardPlace.of(0, 10, 100, 0, 100, 8, 600))
    }
}
