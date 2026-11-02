package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_LAYOUTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripActionsTest {
    @Test fun slowMotionSitsBetweenTurboAndFastForward() {
        assertEquals(
            listOf("save_state", "load_state", "turbo_fire_toggle", "toggle_slowmotion", "toggle_fast_forward"),
            stripActions(layoutKey = false),
        )
    }

    @Test fun theNdsLayoutKeyStaysLast() {
        val nds = stripActions(layoutKey = true)
        assertEquals(6, nds.size)
        assertEquals("screens", nds.last())
        assertEquals(stripActions(layoutKey = false), nds.dropLast(1))
    }

    @Test fun slowMotionLightsByItsOwnState() {
        assertEquals("slow_motion", OverlayWriter.LIT_BY["toggle_slowmotion"])
        assertEquals("fast_forward", OverlayWriter.LIT_BY["toggle_fast_forward"])
        assertEquals("turbo", OverlayWriter.LIT_BY["turbo_fire_toggle"])
    }

    @Test fun hardcoreTurnsBothSpeedTogglesOffAndNothingElse() {
        assertTrue(stripActionOff("toggle_slowmotion", hardcore = true))
        assertTrue(stripActionOff("toggle_fast_forward", hardcore = true))
        stripActions(layoutKey = true).filter { it !in STRIP_SPEED_ACTIONS }.forEach {
            assertFalse(it, stripActionOff(it, hardcore = true))
        }
        stripActions(layoutKey = true).forEach { assertFalse(it, stripActionOff(it, hardcore = false)) }
    }

    @Test fun theSlowMotionGlyphIsStrokedLikeTheBolt() {
        assertTrue(stripActionStroked("toggle_slowmotion"))
        assertTrue(stripActionStroked("turbo_fire_toggle"))
        assertTrue(stripActionStroked("screens"))
        assertFalse(stripActionStroked("toggle_fast_forward"))
        assertFalse(stripActionStroked("save_state"))
    }

    @Test fun hardcoreUnbindsTheSlowMotionKeysBesideFastForward() {
        assertEquals(
            setOf("input_toggle_fast_forward", "input_hold_fast_forward", "input_toggle_slowmotion", "input_hold_slowmotion"),
            HARDCORE_UNBOUND_KEYS.toSet(),
        )
    }

    private val floor = STRIP_SLOT_FLOOR

    private val consoles = listOf(4f / 3f, 8f / 7f, 10f / 9f, 3f / 2f)

    /** The pictures a strip goes under in [orientation]: the consoles' and that orientation's NDS layouts. */
    private fun aspects(orientation: String) = consoles + NDS_LAYOUTS.getValue(orientation).map { it.aspect }

    @Test fun phonePortraitSlotsStayWide() {
        assertEquals(78f, 390f / stripActions(false).size, 1e-3f)
        assertEquals(65f, 390f / stripActions(true).size, 1e-3f)
    }

    @Test fun phoneLandscapeSlotsStayAboveTheFloor() {
        // From a 16:10 phone (624 units across a 390-unit height) to a 21:9 one.
        listOf(624f, 693f, 844f, 910f).forEach { wd ->
            aspects("landscape").forEach { aspect ->
                val keys = stripActions(layoutKey = true).size
                val slot = phoneLandscapeStripW(phoneLandscapeGameW(wd, aspect), keys) / keys
                assertTrue("$wd x $aspect: $slot", slot >= floor)
            }
        }
    }

    @Test fun tabletAndTabletopSlotsStayAboveTheFloor() {
        val strips = aspects("portrait").map { SkinGeometry.portrait(600f, 960f, it).strip.w } +
            aspects("landscape").map { SkinGeometry.landscape(960f, 600f, it, 260f, 200f, layoutKey = true).strip.w } +
            aspects("portrait-tabletop").flatMap {
                listOf(
                    SkinGeometry.tabletop(700f, 840f, 400f, 430f, it, wide = true).strip.w,
                    SkinGeometry.tabletop(560f, 720f, 340f, 370f, it, wide = false).strip.w,
                )
            } + SkinGeometry.tabletopNds(700f, 840f, 430f, 260f).strip.w
        strips.forEach { assertTrue("$it", it / stripActions(layoutKey = true).size >= floor) }
    }

    @Test fun aNarrowPictureGetsAStripWiderThanItself() {
        assertEquals(400f, phoneLandscapeStripW(400f, 6), 0f)
        assertEquals(240f, phoneLandscapeStripW(236f, 6), 0f)
    }
}
