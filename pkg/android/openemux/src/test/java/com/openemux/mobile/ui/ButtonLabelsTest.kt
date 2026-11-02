package com.openemux.mobile.ui

import com.openemux.mobile.ui.Face.Glyph
import com.openemux.mobile.ui.Face.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonLabelsTest {
    private val sony = PadId(VENDOR_SONY, 0x09CC, "Wireless Controller")
    private val nintendo = PadId(VENDOR_NINTENDO, 0x2009, "Pro Controller")
    private val xbox = PadId(0x045E, 0x0B13, "Xbox Wireless Controller")

    @Test
    fun theVendorDecidesTheFamily() {
        assertEquals(PadFamily.PLAYSTATION, familyOf(VENDOR_SONY, 0, null))
        assertEquals(PadFamily.NINTENDO, familyOf(VENDOR_NINTENDO, 0, null))
        assertEquals(PadFamily.XBOX, familyOf(0x045E, 0, "Wireless Controller"))
        // 8BitDo in X-input mode, or anything else: Xbox style.
        assertEquals(PadFamily.XBOX, familyOf(0x2DC8, 0, "Pro Controller"))
    }

    @Test
    fun withNoVendorTheNameDecides() {
        assertEquals(PadFamily.PLAYSTATION, familyOf(0, 0, "DualSense Wireless Controller"))
        assertEquals(PadFamily.PLAYSTATION, familyOf(0, 0, "DUALSHOCK 4"))
        assertEquals(PadFamily.PLAYSTATION, familyOf(0, 0, "Wireless Controller"))
        assertEquals(PadFamily.NINTENDO, familyOf(0, 0, "Pro Controller"))
        assertEquals(PadFamily.NINTENDO, familyOf(0, 0, "Joy-Con (L)"))
        assertEquals(PadFamily.XBOX, familyOf(0, 0, "Xbox Wireless Controller"))
        assertEquals(PadFamily.XBOX, familyOf(0, 0, "Generic USB Joystick"))
        assertEquals(PadFamily.XBOX, familyOf(0, 0, null))
    }

    @Test
    fun onlySonysDualSenseProductsAreDualSense() {
        assertTrue(isDualSense(VENDOR_SONY, 0x0CE6))
        assertTrue(isDualSense(VENDOR_SONY, 0x0DF2))
        assertFalse(isDualSense(VENDOR_SONY, 0x09CC))
        assertFalse(isDualSense(0x045E, 0x0CE6))
    }

    @Test
    fun xboxFaces() {
        val f = PadFamily.XBOX
        assertEquals(listOf(Text("A")), faces("A", f, false))
        assertEquals(listOf(Text("B")), faces("B", f, false))
        assertEquals(listOf(Text("X")), faces("X", f, false))
        assertEquals(listOf(Text("Y")), faces("Y", f, false))
        assertEquals(listOf(Text("LB"), Text("RB")), faces("L1 R1", f, false))
        assertEquals(listOf(Text("LB")), faces("L1", f, false))
        assertEquals(listOf(Text("RB")), faces("R1", f, false))
        assertEquals(listOf(Glyph(GlyphKind.MENU)), faces("START", f, false))
        assertEquals(listOf(Glyph(GlyphKind.VIEW)), faces("SELECT", f, true))
    }

    @Test
    fun nintendoFaces() {
        val f = PadFamily.NINTENDO
        assertEquals(listOf(Text("B")), faces("A", f, false))
        assertEquals(listOf(Text("A")), faces("B", f, false))
        assertEquals(listOf(Text("Y")), faces("X", f, false))
        assertEquals(listOf(Text("X")), faces("Y", f, false))
        assertEquals(listOf(Text("L"), Text("R")), faces("L1 R1", f, false))
        assertEquals(listOf(Text("L")), faces("L1", f, false))
        assertEquals(listOf(Text("R")), faces("R1", f, false))
        assertEquals(listOf(Text("+")), faces("START", f, false))
        assertEquals(listOf(Text("−")), faces("SELECT", f, false))
    }

    @Test
    fun playStationFaces() {
        val f = PadFamily.PLAYSTATION
        assertEquals(listOf(Glyph(GlyphKind.CROSS)), faces("A", f, false))
        assertEquals(listOf(Glyph(GlyphKind.CIRCLE)), faces("B", f, false))
        assertEquals(listOf(Glyph(GlyphKind.SQUARE)), faces("X", f, false))
        assertEquals(listOf(Glyph(GlyphKind.TRIANGLE)), faces("Y", f, false))
        assertEquals(listOf(Text("L1"), Text("R1")), faces("L1 R1", f, false))
        assertEquals(listOf(Text("OPTIONS")), faces("START", f, false))
        assertEquals(listOf(Text("SHARE")), faces("SELECT", f, false))
        assertEquals(listOf(Text("CREATE")), faces("SELECT", f, true))
    }

    @Test
    fun theRightStickClickReadsRsOnXboxAndR3Elsewhere() {
        assertEquals(listOf(Text("RS")), faces("R3", PadFamily.XBOX, false))
        assertEquals(listOf(Text("R3")), faces("R3", PadFamily.NINTENDO, false))
        assertEquals(listOf(Text("R3")), faces("R3", PadFamily.PLAYSTATION, true))
    }

    @Test
    fun anUnknownTokenIsDrawnAsItIs() {
        for (f in PadFamily.values()) assertEquals(listOf(Text("L2")), faces("L2", f, false))
    }

    @Test
    fun textFacesJoinIntoOneBadge() {
        assertEquals(listOf(Text("LB RB")), badgeFaces(listOf(Text("LB"), Text("RB"))))
        assertEquals(listOf(Text("A")), badgeFaces(listOf(Text("A"))))
        val glyphs = listOf(Glyph(GlyphKind.CROSS), Text("x"))
        assertEquals(glyphs, badgeFaces(glyphs))
    }

    @Test
    fun theStoredSettingReadsAsOneOfFour() {
        for (v in listOf(ButtonLabelsKeys.AUTO, ButtonLabelsKeys.XBOX, ButtonLabelsKeys.NINTENDO, ButtonLabelsKeys.PLAYSTATION)) {
            assertEquals(v, ButtonLabelsKeys.parse(v))
        }
        assertEquals(ButtonLabelsKeys.AUTO, ButtonLabelsKeys.parse("sega"))
        assertEquals(ButtonLabelsKeys.AUTO, ButtonLabelsKeys.parse(null))
    }

    @Test
    fun aForcedFamilyWinsOverTheDetectedOne() {
        for (detected in PadFamily.values()) {
            assertEquals(detected, effectiveFamily(ButtonLabelsKeys.AUTO, detected))
            assertEquals(PadFamily.XBOX, effectiveFamily(ButtonLabelsKeys.XBOX, detected))
            assertEquals(PadFamily.NINTENDO, effectiveFamily(ButtonLabelsKeys.NINTENDO, detected))
            assertEquals(PadFamily.PLAYSTATION, effectiveFamily(ButtonLabelsKeys.PLAYSTATION, detected))
        }
    }

    @Test
    fun createOnlyForADetectedDualSense() {
        assertTrue(effectiveDualSense(ButtonLabelsKeys.AUTO, true))
        assertFalse(effectiveDualSense(ButtonLabelsKeys.AUTO, false))
        assertFalse(effectiveDualSense(ButtonLabelsKeys.PLAYSTATION, true))
    }

    @Test
    fun detectionOfAPad() {
        assertEquals(PadFamily.XBOX to false, detection(null))
        assertEquals(PadFamily.PLAYSTATION to true, detection(PadId(VENDOR_SONY, 0x0CE6, null)))
        assertEquals(PadFamily.NINTENDO to false, detection(nintendo))
    }

    @Test
    fun noPadsMeansNoCurrentPad() {
        val t = PadFamilyTracker()
        assertNull(t.current)
        t.reset(emptyMap())
        assertNull(t.current)
    }

    @Test
    fun beforeAnyPressTheLowestDeviceIdDecides() {
        val t = PadFamilyTracker()
        t.reset(mapOf(9 to sony))
        assertEquals(sony, t.current)
        t.added(4, nintendo)
        assertEquals(nintendo, t.current)
    }

    @Test
    fun aPressMakesThatPadCurrentUntilItLeaves() {
        val t = PadFamilyTracker()
        t.reset(mapOf(4 to nintendo, 9 to sony))
        t.pressed(9, sony)
        assertEquals(sony, t.current)
        t.removed(9)
        assertEquals(nintendo, t.current)
        t.removed(4)
        assertNull(t.current)
    }

    @Test
    fun aChangedPadIsReadAgain() {
        val t = PadFamilyTracker()
        t.added(4, nintendo)
        t.changed(4, xbox)
        assertEquals(xbox, t.current)
    }

    @Test
    fun theDebugOverrideWinsAndClears() {
        val t = PadFamilyTracker()
        t.reset(mapOf(4 to nintendo))
        t.pressed(4, nintendo)
        t.override = sony
        assertEquals(sony, t.current)
        t.override = null
        assertEquals(nintendo, t.current)
    }
}
