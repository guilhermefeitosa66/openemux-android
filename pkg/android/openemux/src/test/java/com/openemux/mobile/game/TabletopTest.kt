package com.openemux.mobile.game

import com.openemux.mobile.launch.OverlayName
import com.openemux.mobile.launch.TABLETOP
import com.openemux.mobile.launch.TabletopBand
import com.openemux.mobile.ui.Fold
import com.openemux.mobile.ui.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletopTest {
    private val skin = TabletopSkin(438f, 462f, listOf("portrait", "landscape", "keypad-portrait", "keypad-landscape", TABLETOP, "keypad-$TABLETOP"), emptyMap())
    private val nds = listOf("portrait@stacked", "portrait@top", "landscape@side", "$TABLETOP@halves", "$TABLETOP@top", "$TABLETOP@bottom")

    private fun fold(top: Float = 450f, halfOpened: Boolean = true, vertical: Boolean = false) =
        Fold(vertical, halfOpened, 0, (top * 2).toInt(), 1680, (top * 2).toInt())

    private fun window(w: Float = 840f, h: Float = 900f, fold: Fold? = fold()) = WindowLayout.of(w, h, fold = fold, density = 2f)

    @Test fun parseReadsTheBandTheOverlaysAndTheGaps() {
        val parsed = TabletopSkin.parse("band_start_dp=438.00\nband_end_dp=462.00\noverlays=portrait,$TABLETOP@halves\ngap.$TABLETOP@halves=18\ngap.bad=x\n\n")!!
        assertEquals(438f, parsed.bandStartDp, 0.001f)
        assertEquals(462f, parsed.bandEndDp, 0.001f)
        assertEquals(listOf("portrait", "$TABLETOP@halves"), parsed.overlays)
        assertEquals(mapOf("$TABLETOP@halves" to 18), parsed.gaps)
        assertNull(parsed.flatLayout)
    }

    @Test fun parseReadsThePackageSkinsFlatLayout() {
        val text = "band_start_dp=438\nband_end_dp=462\noverlays=portrait,landscape,$TABLETOP@halves\n"
        assertEquals("top/bottom", TabletopSkin.parse(text + "flat_layout=top/bottom\n")!!.flatLayout)
        assertNull(TabletopSkin.parse(text + "flat_layout=\n")!!.flatLayout)
    }

    @Test fun parseWithoutABandOrOverlaysIsNull() {
        assertNull(TabletopSkin.parse(""))
        assertNull(TabletopSkin.parse("band_start_dp=1\noverlays=a"))
        assertNull(TabletopSkin.parse("band_start_dp=1\nband_end_dp=x\noverlays=a"))
        assertNull(TabletopSkin.parse("band_start_dp=1\nband_end_dp=2\noverlays="))
        assertNull(TabletopSkin.parse("band_start_dp=1\nband_end_dp=2"))
    }

    @Test fun tabletopWantsAHalfOpenFoldAcrossAPortraitWindowWhereTheSkinPutsIt() {
        assertTrue(wantsTabletop(window(), skin))
        assertTrue(wantsTabletop(window(fold = fold(top = 457f)), skin))
        assertFalse(wantsTabletop(window(), null))
        assertFalse(wantsTabletop(window(fold = fold(halfOpened = false)), skin))
        assertFalse(wantsTabletop(window(fold = null), skin))
        assertFalse(wantsTabletop(window(w = 900f, h = 840f), skin))
        assertFalse(wantsTabletop(window(fold = Fold(true, true, 840, 0, 840, 1800)), skin))
        // Rotated the other way round an off-centre hinge: not where the skin drew the band.
        assertFalse(wantsTabletop(window(fold = fold(top = 470f)), skin))
        assertFalse(wantsTabletop(window(fold = fold(top = 440f)), skin))
    }

    @Test fun intoTabletopGoesToTheTwinOfThePortraitOverlay() {
        assertEquals(TABLETOP, tabletopTarget("portrait", true, skin.overlays, null, null))
        assertEquals("keypad-$TABLETOP", tabletopTarget("keypad-portrait", true, skin.overlays, null, null))
        assertEquals("$TABLETOP@top", tabletopTarget("portrait@stacked", true, nds, null, "top"))
    }

    @Test fun aPackageSkinsPlainPortraitGoesToTheSavedNdsTabletopLayoutAndBack() {
        val packaged = listOf("portrait", "landscape", "$TABLETOP@halves", "$TABLETOP@top", "$TABLETOP@bottom")
        assertEquals("$TABLETOP@halves", tabletopTarget("portrait", true, packaged, null, "halves"))
        assertEquals("portrait", tabletopTarget("$TABLETOP@halves", false, packaged, "portrait", "halves"))
        assertEquals(TABLETOP, tabletopTarget("portrait", true, listOf("portrait", "landscape", TABLETOP), null, null))
    }

    @Test fun outOfTabletopGoesBackToTheFlatOverlay() {
        assertEquals("portrait", tabletopTarget(TABLETOP, false, skin.overlays, null, null))
        assertEquals("keypad-portrait", tabletopTarget("keypad-$TABLETOP", false, skin.overlays, null, null))
        assertEquals("keypad-portrait", tabletopTarget(TABLETOP, false, skin.overlays, "keypad-portrait", null))
        assertEquals("portrait@top", tabletopTarget("$TABLETOP@halves", false, nds, "portrait@top", "halves"))
    }

    @Test fun nothingChangesWhenAlreadyThereOrInLandscapeOrUnknown() {
        assertNull(tabletopTarget(TABLETOP, true, skin.overlays, null, null))
        assertNull(tabletopTarget("portrait", false, skin.overlays, null, null))
        assertNull(tabletopTarget("landscape", true, skin.overlays, null, null))
        assertNull(tabletopTarget("portrait", true, listOf("portrait", "landscape"), null, null))
        assertNull(tabletopTarget("portrait@stacked", true, nds, null, "gone"))
        assertNull(tabletopTarget("$TABLETOP@halves", false, nds, null, "halves"))
    }

    @Test fun aSkinMissesAFoldWithoutTabletopOverlaysOrWithThemElsewhere() {
        // The skin's band, 438..462 dp, at 2 px per dp.
        assertFalse(skinMissesFold(null, 2f, null))
        assertFalse(skinMissesFold(null, 2f, skin))
        assertTrue(skinMissesFold(TabletopBand(876f, 924f), 2f, null))
        assertFalse(skinMissesFold(TabletopBand(876f, 924f), 2f, skin))
        assertFalse(skinMissesFold(TabletopBand(890f, 910f), 2f, skin))
        assertTrue(skinMissesFold(TabletopBand(840f, 924f), 2f, skin))
        assertTrue(skinMissesFold(TabletopBand(876f, 960f), 2f, skin))
    }

    /** A drawn skin's pages in every mode (SkinRenderer): each touch page and its min-, pad- and min-pad- twins. */
    private fun modes(touch: List<String>) = touch.flatMap { listOf(it, "min-$it", "pad-$it", "min-pad-$it") }

    @Test fun thePostureKeepsTheGamepadLayoutAndTheCollapsedControls() {
        val all = modes(skin.overlays)
        assertEquals("pad-$TABLETOP", tabletopTarget("pad-portrait", true, all, null, null))
        assertEquals("min-pad-keypad-$TABLETOP", tabletopTarget("min-pad-keypad-portrait", true, all, null, null))
        assertEquals("min-portrait", tabletopTarget("min-$TABLETOP", false, all, null, null))
        // The flat page left before, in the mode shown now.
        assertEquals("min-pad-keypad-portrait", tabletopTarget("min-pad-$TABLETOP", false, all, "keypad-portrait", null))
        assertEquals("pad-$TABLETOP@halves", tabletopTarget("pad-portrait@stacked", true, modes(nds), null, "halves"))
        assertNull(tabletopTarget("pad-landscape", true, all, null, null))
        assertNull(tabletopTarget("something", true, all, null, null))
    }

    @Test fun aPackageSkinHasNoCollapsedTouchPageSoTheExpandedOneShows() {
        val packaged = listOf("portrait", "landscape", TABLETOP, "pad-portrait", "pad-landscape", "pad-$TABLETOP", "min-pad-portrait", "min-pad-landscape", "min-pad-$TABLETOP")
        assertEquals("min-pad-$TABLETOP", tabletopTarget("min-pad-portrait", true, packaged, null, null))
        assertEquals("portrait", gamepadTarget("min-pad-portrait", false, packaged))
        assertEquals("portrait", available(OverlayName(portrait = true, collapsed = true), packaged))
        // No keypad page of its own (issue 772): its controls.
        assertEquals("portrait", available(OverlayName(portrait = true, keypad = true), packaged))
        assertNull(available(OverlayName(portrait = true, keypad = true), listOf("landscape")))
    }

    @Test fun theGamepadSwitchesTheLayoutAndKeepsEverythingElse() {
        val all = modes(skin.overlays) + modes(nds)
        assertEquals("pad-portrait", gamepadTarget("portrait", true, all))
        assertEquals("landscape", gamepadTarget("pad-landscape", false, all))
        assertEquals("min-pad-keypad-$TABLETOP", gamepadTarget("min-keypad-$TABLETOP", true, all))
        assertEquals("min-landscape@side", gamepadTarget("min-pad-landscape@side", false, all))
        // Nothing to change, or nothing to change to.
        assertNull(gamepadTarget("pad-portrait", true, all))
        assertNull(gamepadTarget("landscape", false, all))
        assertNull(gamepadTarget("portrait", true, listOf("portrait", "landscape")))
        assertNull(gamepadTarget("something", true, all))
    }

    @Test fun aPackageWithNoKeypadPageLeavesTheDrawnKeypadForItsControls() {
        // Issue 772: under a package with no keypad page, the gamepad layout's drawn keypad closes when the gamepad goes.
        val overlays = listOf("portrait", "landscape", "pad-portrait", "pad-keypad-portrait", "min-pad-keypad-portrait")
        assertEquals("portrait", gamepadTarget("pad-keypad-portrait", gamepad = false, overlays))
        assertEquals("portrait", gamepadTarget("min-pad-keypad-portrait", gamepad = false, overlays))
        // A package with its keypad pages keeps the keypad open.
        assertEquals("keypad-portrait", gamepadTarget("pad-keypad-portrait", gamepad = false, overlays + "keypad-portrait"))
        assertEquals(null, gamepadTarget("pad-keypad-portrait", gamepad = false, listOf("landscape")))
    }
}
