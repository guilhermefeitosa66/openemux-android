package com.openemux.mobile.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayNameTest {
    /** Every name a skin wrote before the gamepad layout. */
    private val before = listOf(
        "portrait", "landscape", "keypad-portrait", "keypad-landscape", TABLETOP, "keypad-$TABLETOP",
        "portrait@top", "portrait@stacked", "landscape@side", "$TABLETOP@halves",
    )

    private fun all() = before.flatMap { n ->
        listOf(false, true).flatMap { pad -> listOf(false, true).map { min -> OverlayName.parse(n)!!.copy(gamepad = pad, collapsed = min) } }
    }

    @Test fun theNamesFromBeforeReadAndWriteTheSame() {
        before.forEach { n ->
            val parsed = OverlayName.parse(n)!!
            assertEquals(n, parsed.toString())
            assertFalse(parsed.gamepad || parsed.collapsed)
            assertEquals(parsed, parsed.touch)
        }
    }

    @Test fun theModesArePrefixesCollapsedFirst() {
        val n = OverlayName.parse("min-pad-landscape@side")!!
        assertTrue(n.collapsed && n.gamepad && !n.portrait && !n.tabletop && !n.keypad)
        assertEquals("side", n.layout)
        assertEquals("min-pad-keypad-$TABLETOP", OverlayName(portrait = true, tabletop = true, keypad = true, gamepad = true, collapsed = true).toString())
        assertEquals("pad-portrait", OverlayName(portrait = true, gamepad = true).toString())
        assertEquals("min-landscape", OverlayName(portrait = false, collapsed = true).toString())
        all().forEach { assertEquals(it, OverlayName.parse(it.toString())) }
    }

    @Test fun everyNameKeepsTheWordAutoRotateMatches() {
        all().forEach { val s = it.toString(); assertTrue(s, "portrait" in s || "landscape" in s) }
    }

    @Test fun namesNoSkinWritesAreNotRead() {
        listOf("", "pad-min-portrait", "keypad-pad-portrait", "portrait@", "landscape-tabletop", "upright", "min-", "package")
            .forEach { assertNull(it, OverlayName.parse(it)) }
    }

    @Test fun theOrientationIsWhatTheNdsLayoutsAreKeyedBy() {
        assertEquals("portrait", OverlayName.parse("min-pad-portrait@top")!!.orientation)
        assertEquals("landscape", OverlayName.parse("pad-landscape@side")!!.orientation)
        assertEquals(TABLETOP, OverlayName.parse("pad-$TABLETOP@halves")!!.orientation)
    }

    @Test fun modesChangeAloneAndTravelTogether() {
        val n = OverlayName.parse("keypad-$TABLETOP")!!
        assertEquals("pad-keypad-$TABLETOP", n.withGamepad(true).toString())
        assertEquals("min-keypad-$TABLETOP", n.withCollapsed(true).toString())
        assertEquals("keypad-$TABLETOP", n.withGamepad(true).withCollapsed(true).touch.toString())
        val shown = OverlayName.parse("min-pad-landscape")!!
        assertEquals("min-pad-portrait@top", OverlayName.parse("portrait@top")!!.withModeOf(shown).toString())
        assertEquals("portrait", OverlayName.parse("min-pad-portrait")!!.withModeOf(OverlayName(portrait = false)).toString())
    }

    @Test fun mapLeavesANameItCannotReadAlone() {
        assertEquals("pad-landscape", OverlayName.map("landscape") { it.withGamepad(true) })
        assertEquals("something", OverlayName.map("something") { it.withGamepad(true) })
    }
}
