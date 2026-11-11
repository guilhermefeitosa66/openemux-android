package com.openemux.mobile.game

import com.openemux.mobile.launch.ScreenBox
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

    @Test fun theCardSitsOnTheGameNearItsTop() {
        // Portrait phone: the game in the top 40%, the whole width.
        assertEquals(AnchorCardPlace(12, 12, 1056), AnchorCardPlace.of(ScreenBox(0f, 0f, 1f, 0.4f), 1080, 2400, 12, 1200))
        // Landscape: the game in the middle; a narrower card is centred on it.
        assertEquals(AnchorCardPlace(870, 112, 660), AnchorCardPlace.of(ScreenBox(0.25f, 0.1f, 0.5f, 0.8f), 2400, 1000, 12, 660))
    }

    @Test fun withNoGameBoxTheCardSitsAtTheTopOfTheScreen() {
        assertEquals(AnchorCardPlace(200, 12, 600), AnchorCardPlace.of(null, 1000, 2000, 12, 600))
        // A box narrower than the margins gets no width rather than a negative one.
        assertEquals(0, AnchorCardPlace.of(ScreenBox(0f, 0f, 0.01f, 0.1f), 1000, 2000, 12, 600).width)
    }
}
