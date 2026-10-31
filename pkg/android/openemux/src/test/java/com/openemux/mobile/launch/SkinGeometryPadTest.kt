package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_SCREEN_GAPS
import com.openemux.mobile.launch.SkinGeometry.PAD_FF
import com.openemux.mobile.launch.SkinGeometry.PAD_HIDE
import com.openemux.mobile.launch.SkinGeometry.PAD_KEYPAD
import com.openemux.mobile.launch.SkinGeometry.PAD_LOAD
import com.openemux.mobile.launch.SkinGeometry.PAD_MENU
import com.openemux.mobile.launch.SkinGeometry.PAD_SAVE
import com.openemux.mobile.launch.SkinGeometry.PAD_SCREENS
import com.openemux.mobile.launch.SkinGeometry.PAD_TURBO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinGeometryPadTest {
    private val six = SkinGeometry.padKeys(screens = false, keypad = false)
    private val eight = SkinGeometry.padKeys(screens = true, keypad = true)

    private fun slot(g: PadFrame, key: String) = g.keys.first { it.first == key }.second

    /** Keys inside the screen, at least 48 units, clear of each other and of the picture. */
    private fun assertKeysFit(g: PadFrame, w: Float, h: Float) {
        val boxes = g.keys.map { it.second }
        boxes.forEach { b ->
            assertTrue("$b outside", b.inside(w, h))
            assertTrue("$b under 48", b.w >= 48f && b.h >= 48f)
            assertTrue("$b over the picture", !b.intersects(g.picture))
        }
        // Side by side in a full strip, neighbours may touch, as the touch strip's cells do.
        fun inner(b: Box) = Box(b.x + 0.01f, b.y + 0.01f, b.w - 0.02f, b.h - 0.02f)
        boxes.forEachIndexed { i, a -> boxes.drop(i + 1).forEach { b -> assertTrue("$a over $b", !inner(a).intersects(inner(b))) } }
        assertTrue(g.picture.inside(w + 1e-3f, h + 1e-3f))
        g.panels.forEach { p -> assertTrue("$p over the picture", !p.intersects(g.picture)) }
    }

    @Test fun theKeysAreTheAppControlsHideLast() {
        assertEquals(listOf(PAD_MENU, PAD_SAVE, PAD_LOAD, PAD_TURBO, PAD_FF, PAD_HIDE), six)
        assertEquals(listOf(PAD_MENU, PAD_SAVE, PAD_LOAD, PAD_TURBO, PAD_FF, PAD_SCREENS, PAD_KEYPAD, PAD_HIDE), eight)
    }

    @Test fun landscapePutsTwoRailsAndTheFullHeightPictureBetweenThem() {
        // A 20:9 phone: 866 x 390 units.
        val g = SkinGeometry.padLandscape(866f, 390f, 4f / 3f, eight, collapsed = false)
        assertKeysFit(g, 866f, 390f)
        assertEquals(listOf(Box(0f, 0f, 88f, 390f), Box(778f, 0f, 88f, 390f)), g.panels)
        assertNull(g.shade)
        assertEquals(390f, g.picture.h, 1e-3f)
        assertEquals(433f, g.picture.cx, 1e-3f)
        // Menu on top of the left rail, save and load at its foot; turbo, fast forward, screens and keypad down the
        // right one, hide at its foot.
        listOf(PAD_MENU, PAD_SAVE, PAD_LOAD).forEach { assertEquals(44f, slot(g, it).cx, 1e-3f) }
        listOf(PAD_TURBO, PAD_FF, PAD_SCREENS, PAD_KEYPAD, PAD_HIDE).forEach { assertEquals(822f, slot(g, it).cx, 1e-3f) }
        assertTrue(slot(g, PAD_MENU).y < slot(g, PAD_SAVE).y && slot(g, PAD_SAVE).y < slot(g, PAD_LOAD).y)
        assertTrue(slot(g, PAD_TURBO).y < slot(g, PAD_FF).y && slot(g, PAD_FF).y < slot(g, PAD_SCREENS).y)
        assertTrue(slot(g, PAD_KEYPAD).bottom < slot(g, PAD_HIDE).y)
        assertEquals(slot(g, PAD_LOAD).y, slot(g, PAD_HIDE).y, 1e-3f)
        assertEquals(slot(g, PAD_MENU), g.menu)
    }

    @Test fun collapsedLandscapeFitsThePictureToTheScreenAndKeepsTheMenuSlot() {
        val open = SkinGeometry.padLandscape(866f, 390f, 2f, six, collapsed = false)
        val g = SkinGeometry.padLandscape(866f, 390f, 2f, six, collapsed = true)
        assertTrue(g.panels.isEmpty())
        assertEquals(780f, g.picture.w, 1e-3f)
        assertTrue(g.picture.w > open.picture.w)
        assertEquals(open.menu, g.menu)
    }

    @Test fun portraitPutsTheStripAtTheFootAndThePictureAcrossAboveIt() {
        val g = SkinGeometry.padPortrait(390f, 866f, 4f / 3f, six, collapsed = false)
        assertKeysFit(g, 390f, 866f)
        assertEquals(listOf(Box(0f, 866f - 96f, 390f, 96f)), g.panels)
        assertEquals(390f, g.picture.w, 1e-3f)
        // Centred in the room above the strip.
        assertEquals((866f - 96f) / 2f, g.picture.y + g.picture.h / 2f, 1e-3f)
        assertEquals(six, g.keys.map { it.first })
        g.keys.zipWithNext().forEach { (a, b) -> assertEquals(65f, b.second.cx - a.second.cx, 1e-3f) }
        // Eight keys still leave each at least 48 units.
        assertKeysFit(SkinGeometry.padPortrait(390f, 866f, 4f / 3f, eight, collapsed = false), 390f, 866f)
        val min = SkinGeometry.padPortrait(390f, 866f, 4f / 3f, six, collapsed = true)
        assertTrue(min.panels.isEmpty())
        assertEquals(g.menu, min.menu)
        assertEquals(433f, min.picture.y + min.picture.h / 2f, 1e-3f)
    }

    @Test fun tabletopPutsThePictureOnTopAndTheKeysInARowUnderTheBand() {
        val g = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 1.5f, six, collapsed = false)
        assertKeysFit(g, 390f, 820f)
        assertTrue(g.picture.bottom <= 400f)
        assertEquals(Box(0f, 400f, 390f, 30f), g.shade)
        assertEquals(listOf(Box(0f, 430f, 390f, 390f)), g.panels)
        g.keys.forEach { (_, b) -> assertEquals((430f + 820f) / 2f, b.y + b.h / 2f, 1e-3f) }
        // Centred, wider apart on a wide screen.
        val wide = SkinGeometry.padTabletop(700f, 820f, 400f, 430f, 1.5f, six, collapsed = false)
        assertEquals(350f, (wide.keys.first().second.x + wide.keys.last().second.right) / 2f, 1e-3f)
        assertEquals(64f, wide.menu.w, 1e-3f)
        val min = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 1.5f, six, collapsed = true)
        assertTrue(min.panels.isEmpty())
        assertNull(min.shade)
        assertEquals(g.picture, min.picture)
        assertEquals(g.menu, min.menu)
    }

    @Test fun ndsTabletopPutsTheRowAtTheFootUnderTheBottomScreen() {
        val keys = SkinGeometry.padKeys(screens = true, keypad = false)
        val top = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 256f / 192f, keys, false, "top", NDS_SCREEN_GAPS)
        assertTrue(top.picture.bottom <= 400f)
        assertEquals(listOf(Box(0f, 820f - 96f, 390f, 96f)), top.panels)
        val bottom = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 256f / 192f, keys, false, "bottom", NDS_SCREEN_GAPS)
        assertTrue(bottom.picture.y >= 430f && bottom.picture.bottom <= 820f - 96f)
        assertKeysFit(bottom, 390f, 820f)
        val halves = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 256f / 384f, keys, false, "halves", NDS_SCREEN_GAPS)
        assertTrue(halves.gap > 0)
        assertTrue(halves.picture.y < 400f && halves.picture.bottom > 430f && halves.picture.bottom <= 820f - 96f)
        // Collapsed, the bottom screen takes the whole bottom half.
        val min = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 256f / 192f, keys, true, "bottom", NDS_SCREEN_GAPS)
        assertTrue(min.picture.h > bottom.picture.h)
        // No gap covers the band: both screens in the top half.
        val none = SkinGeometry.padTabletop(390f, 820f, 400f, 430f, 256f / 384f, keys, false, "halves", emptyList())
        assertEquals(0, none.gap)
        assertTrue(none.picture.bottom <= 400f)
    }
}
