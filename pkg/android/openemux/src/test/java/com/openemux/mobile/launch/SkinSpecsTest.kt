package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_LAYOUTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinSpecsTest {
    private val stacked = NDS_LAYOUTS.getValue("portrait").first { it.id == "stacked" }
    private val side = NDS_LAYOUTS.getValue("landscape").first { it.id == "side" }
    private val top = NDS_LAYOUTS.getValue("portrait").first()

    private fun names(specs: List<OverlaySpec>) = specs.map { it.name }

    @Test fun withoutAFoldTheOverlaysAreTheOnesBefore() {
        assertEquals(listOf("portrait", "landscape"), names(overlaySpecs(1.5f, keypad = false, nds = null)))
        assertEquals(
            listOf("portrait", "landscape", "keypad-portrait", "keypad-landscape"),
            names(overlaySpecs(1.5f, keypad = true, nds = null)),
        )
        val nds = overlaySpecs(1.5f, keypad = false, nds = top to side)
        assertEquals("portrait@top", nds.first().name)
        assertEquals(8, nds.size)
        assertTrue(nds.none { it.tabletop || TABLETOP in it.name })
    }

    @Test fun aFoldAppendsTheTabletopOverlaysAfterTheRest() {
        val flat = overlaySpecs(1.5f, keypad = true, nds = null)
        val specs = overlaySpecs(1.5f, keypad = true, nds = null, tabletop = true)
        assertEquals(names(flat), names(specs).take(flat.size))
        assertEquals(listOf(TABLETOP, "keypad-$TABLETOP"), names(specs).drop(flat.size))
        val t = specs.first { it.name == TABLETOP }
        assertTrue(t.portrait && t.tabletop && !t.keypadOpen)
        assertEquals("landscape", t.rotate)
        val k = specs.first { it.name == "keypad-$TABLETOP" }
        assertTrue(k.keypadOpen)
        assertEquals("keypad-landscape", k.rotate)
        assertEquals(listOf(TABLETOP), names(overlaySpecs(1.5f, keypad = false, nds = null, tabletop = true)).drop(2))
    }

    @Test fun ndsGetsEveryTabletopLayoutFromTheSavedOne() {
        val specs = overlaySpecs(1.5f, keypad = false, nds = stacked to side, tabletop = true, ndsTabletop = top)
        val tabletop = specs.filter { it.tabletop }
        assertEquals(listOf("$TABLETOP@top", "$TABLETOP@bottom", "$TABLETOP@halves", "$TABLETOP@side"), names(tabletop))
        assertEquals("portrait@stacked", specs.first().name)
        assertEquals("$TABLETOP@bottom", tabletop[0].layoutNext)
        assertEquals("$TABLETOP@top", tabletop[2].layoutNext)
        // Side by side is in the list, not in the quick cycle: its key steps to the cycle's first.
        assertEquals("$TABLETOP@top", tabletop[3].layoutNext)
        tabletop.forEach { assertEquals("landscape@side", it.rotate) }
        // Nothing saved yet: one per half first.
        val fresh = overlaySpecs(1.5f, keypad = false, nds = stacked to side, tabletop = true)
        assertEquals("$TABLETOP@halves", fresh.first { it.tabletop }.name)
    }

    @Test fun noFlatOverlayLeadsIntoTabletopAndTabletopTurnsToLandscape() {
        for (specs in listOf(
            overlaySpecs(1.5f, keypad = true, nds = null, tabletop = true),
            overlaySpecs(1.5f, keypad = false, nds = stacked to side, tabletop = true),
        )) {
            specs.filter { !it.tabletop }.forEach { s ->
                assertFalse(s.name, TABLETOP in s.rotate)
                assertFalse(s.name, TABLETOP in s.layoutNext.orEmpty())
            }
            specs.filter { it.tabletop }.forEach { s ->
                assertTrue("portrait" in s.name)
                assertTrue("landscape" in s.rotate)
            }
        }
    }

    @Test fun aPackageSkinGetsTheDrawnTabletopOverlaysTurningToItsLandscape() {
        val plain = packageTabletopSpecs(1.5f, nds = false)
        assertEquals(listOf(TABLETOP), names(plain))
        assertTrue(plain[0].tabletop && plain[0].portrait && !plain[0].keypadOpen)
        assertEquals("landscape", plain[0].rotate)
        val nds = packageTabletopSpecs(1.5f, nds = true, ndsTabletop = top)
        assertEquals(listOf("$TABLETOP@top", "$TABLETOP@bottom", "$TABLETOP@halves", "$TABLETOP@side"), names(nds))
        nds.forEach { assertTrue(it.tabletop && it.layout != null); assertEquals("landscape", it.rotate) }
        assertEquals("$TABLETOP@halves", packageTabletopSpecs(1.5f, nds = true).first().name)
        // A package with one page per layout: turning lands on its saved landscape one.
        packageTabletopSpecs(1.5f, nds = true, ndsLandscape = side).forEach { assertEquals("landscape@side", it.rotate) }
    }

    @Test fun overlayNamesFollowTheIndexNotTheLineOrder() {
        val cfg = "overlays = 2\noverlay1_name = \"landscape\"\noverlay0_name = \"portrait\"\noverlay0_overlay = \"p.png\"\n"
        assertEquals(listOf("portrait", "landscape"), overlayNames(cfg))
        assertEquals(emptyList<String>(), overlayNames(""))
    }

    @Test fun swappingTwoOverlaysMovesEveryKeyOfEach() {
        val cfg = "overlays = 3\noverlay0_name = \"landscape\"\noverlay0_desc0 = \"a\"\noverlay1_name = \"portrait\"\n" +
            "overlay2_name = \"pad-landscape\"\noverlay2_desc0 = \"b\"\n"
        val swapped = swapOverlays(cfg, 0, 2)
        assertEquals(listOf("pad-landscape", "portrait", "landscape"), overlayNames(swapped))
        assertTrue("overlay0_desc0 = \"b\"" in swapped && "overlay2_desc0 = \"a\"" in swapped)
        assertEquals(cfg, swapOverlays(cfg, 1, 1))
    }

    @Test fun aPackageSkinOpensOnItsFirstPagesGamepadTwinWhenOneIsWanted() {
        val flat = listOf("landscape", "portrait")
        val names = flat + listOf("pad-portrait", "pad-landscape", "min-pad-landscape")
        val cfg = names.mapIndexed { i, n -> "overlay${i}_name = \"$n\"" }.joinToString("\n", "overlays = 5\n", "\n")
        assertEquals("pad-landscape", overlayNames(packageOpening(cfg, flat, names, gamepad = true)).first())
        // No gamepad wanted, no twin drawn (the app's pages failed), or no page of the package's own: as it was.
        assertEquals(cfg, packageOpening(cfg, flat, names, gamepad = false))
        assertEquals(cfg, packageOpening(cfg, flat, flat, gamepad = true))
        assertEquals(cfg, packageOpening(cfg, emptyList(), names, gamepad = true))
        assertEquals(cfg, packageOpening(cfg, listOf("skin-page"), names, gamepad = true))
    }

    @Test fun withOverlaysCountsTheAddedOnesAndAppendsThem() {
        val cfg = "overlays = 2\noverlay0_name = \"portrait\"\noverlay1_name = \"landscape\"\n\n"
        val body = "overlay2_name = \"$TABLETOP\"\n"
        val merged = withOverlays(cfg, 1, body)
        assertEquals("overlays = 3\noverlay0_name = \"portrait\"\noverlay1_name = \"landscape\"\n$body", merged)
        assertEquals(listOf("portrait", "landscape", TABLETOP), overlayNames(merged))
        assertEquals(cfg.trimEnd('\n') + "\n", withOverlays(cfg, 0, ""))
        assertEquals("x\n$body", withOverlays("x", 1, body))
    }

    @Test fun everyTouchPageGetsItsCollapsedAndGamepadTwinsAfterTheTouchPages() {
        val touch = overlaySpecs(1.5f, keypad = true, nds = null, tabletop = true)
        val all = withAppModes(touch)
        assertEquals(names(touch), names(all).take(touch.size))
        assertEquals(touch.size * 4, all.size)
        assertEquals(names(touch).map { "min-$it" }, names(all).subList(touch.size, 2 * touch.size))
        assertEquals(names(touch).map { "pad-$it" }, names(all).subList(2 * touch.size, 3 * touch.size))
        assertEquals(names(touch).map { "min-pad-$it" }, names(all).drop(3 * touch.size))
        all.forEach { s ->
            val n = OverlayName.parse(s.name)!!
            assertEquals(s.gamepad, n.gamepad)
            assertEquals(s.collapsed, n.collapsed)
            // Turning keeps the mode and lands on the other orientation.
            val r = OverlayName.parse(s.rotate)!!
            assertEquals(n.gamepad to n.collapsed, r.gamepad to r.collapsed)
            assertEquals(!s.portrait, r.portrait)
            // Every collapsed page has its expanded twin, and the other way around.
            assertTrue(n.withCollapsed(!n.collapsed).toString() in names(all))
        }
        val k = all.first { it.name == "pad-keypad-$TABLETOP" }
        assertTrue(k.keypadOpen && k.tabletop && k.gamepad && !k.collapsed)
        assertEquals("pad-keypad-landscape", k.rotate)
    }

    @Test fun ndsTwinsStepTheirLayoutsInTheirOwnMode() {
        val all = withAppModes(overlaySpecs(1.5f, keypad = false, nds = stacked to side, tabletop = true, ndsTabletop = top))
        all.filter { it.layoutNext != null }.forEach { s ->
            val n = OverlayName.parse(s.name)!!
            val next = OverlayName.parse(s.layoutNext!!)!!
            assertEquals(n.copy(layout = next.layout), next)
        }
        val pad = all.first { it.name == "pad-portrait@stacked" }
        assertEquals("pad-landscape@side", pad.rotate)
        assertEquals(stacked, pad.layout)
    }

    @Test fun withoutAHideKeyThereIsNoCollapsedTouchPage() {
        val touch = packageTabletopSpecs(1.5f, nds = false)
        assertEquals(listOf(TABLETOP, "pad-$TABLETOP", "min-pad-$TABLETOP"), names(withAppModes(touch, collapsible = false)))
    }

    @Test fun aPackageSkinGetsTheGamepadLayoutForItsOwnPagesAndTheTabletopOnes() {
        assertEquals(
            listOf("pad-portrait", "pad-landscape", "min-pad-portrait", "min-pad-landscape"),
            names(packageSpecs(1.5f, nds = false, tabletop = emptyList())),
        )
        val folded = packageSpecs(1.5f, nds = false, tabletop = packageTabletopSpecs(1.5f, nds = false))
        assertEquals(
            listOf(TABLETOP, "pad-portrait", "pad-landscape", "pad-$TABLETOP", "min-pad-portrait", "min-pad-landscape", "min-pad-$TABLETOP"),
            names(folded),
        )
        assertEquals("pad-landscape", folded.first { it.name == "pad-$TABLETOP" }.rotate)
        assertEquals(1.5f, folded.first { it.name == "pad-portrait" }.aspect, 0f)
        // NDS: its art stacks the two screens.
        packageSpecs(1.5f, nds = true, tabletop = emptyList()).forEach { assertEquals(PACKAGE_NDS_ASPECT, it.aspect, 0f) }
    }

    @Test fun anNdsPackageWithItsLayoutsGetsAGamepadPagePerLayoutWithItsOwnCycle() {
        val specs = packageSpecs(1.5f, nds = true, tabletop = emptyList(), ndsLayouts = stacked to side)
        val pad = specs.filter { !it.collapsed }
        assertEquals(8, pad.size)
        assertEquals("pad-portrait@stacked", pad.first().name)
        pad.forEach { s ->
            assertTrue(s.gamepad && s.layout != null)
            // The picture follows the layout: one screen 4:3, side by side 8:3.
            assertEquals(s.layout!!.aspect, s.aspect, 0f)
        }
        val byName = specs.associateBy { it.name }
        // Landscape with a pad steps on to side by side, and from it back to the first.
        assertEquals("pad-landscape@side", byName.getValue("pad-landscape@stacked").layoutNext)
        assertEquals("pad-landscape@top", byName.getValue("pad-landscape@side").layoutNext)
        // Portrait's cycle has no side by side, but the page exists (picked from the list).
        assertEquals("pad-portrait@top", byName.getValue("pad-portrait@stacked").layoutNext)
        assertEquals("pad-portrait@top", byName.getValue("pad-portrait@side").layoutNext)
        assertEquals("pad-landscape@side", byName.getValue("pad-portrait@top").rotate)
        assertEquals(16, specs.size)
        // Without the layouts' art, NDS keeps one page per orientation.
        assertEquals(4, packageSpecs(1.5f, nds = true, tabletop = emptyList()).size)
        // A non-NDS console ignores them.
        assertEquals(4, packageSpecs(1.5f, nds = false, tabletop = emptyList(), ndsLayouts = stacked to side).size)
    }

    @Test fun theTouchCycleOfAPhoneLeavesSideBySideOutInLandscapeButATabletKeepsIt() {
        val phone = ndsFlatSpecs(stacked to stacked).associateBy { it.name }
        assertEquals("landscape@top", phone.getValue("landscape@stacked").layoutNext)
        assertEquals("landscape@top", phone.getValue("landscape@side").layoutNext)
        val tablet = ndsFlatSpecs(stacked to stacked, tablet = true).associateBy { it.name }
        assertEquals("landscape@side", tablet.getValue("landscape@stacked").layoutNext)
        assertEquals("portrait@top", tablet.getValue("portrait@stacked").layoutNext)
        // A tablet's gamepad twins keep the tablet's cycle.
        val twin = tablet.getValue("landscape@side").twin(gamepad = true, collapsed = false)
        assertEquals("pad-landscape@top", twin.layoutNext)
        assertTrue(twin.tablet)
        // Each orientation opens on its saved layout, portrait first.
        assertEquals(listOf("portrait@side", "portrait@top"), ndsFlatSpecs(side to top).map { it.name }.take(2))
        assertEquals("landscape@top", ndsFlatSpecs(side to top)[4].name)
    }

    @Test fun underAPackageAConsoleWithAKeypadGetsTheDrawnKeypadWithAGamepadAndHalfOpen() {
        // Issue 772: the gamepad layout's keypad key opens the drawn keypad, turning keeps it open.
        val specs = packageSpecs(1.5f, nds = false, tabletop = emptyList(), keypad = true)
        assertEquals(
            listOf("pad-portrait", "pad-landscape", "pad-keypad-portrait", "pad-keypad-landscape",
                "min-pad-portrait", "min-pad-landscape", "min-pad-keypad-portrait", "min-pad-keypad-landscape"),
            names(specs),
        )
        val open = specs.first { it.name == "pad-keypad-portrait" }
        assertTrue(open.keypadOpen)
        assertEquals("pad-keypad-landscape", open.rotate)
        // Half open: the drawn keypad's tabletop page, turning to the package's keypad page when it has one.
        val tabletop = packageTabletopSpecs(1.5f, nds = false, keypad = true, packageKeypad = true)
        assertEquals(listOf(TABLETOP, "keypad-$TABLETOP"), names(tabletop))
        assertEquals("keypad-landscape", tabletop[1].rotate)
        assertTrue(tabletop[1].keypadOpen && tabletop[1].tabletop)
        assertEquals("landscape", packageTabletopSpecs(1.5f, nds = false, keypad = true)[1].rotate)
        // NDS has no keypad, and no keypad at all leaves the lists as they were.
        assertEquals(names(packageTabletopSpecs(1.5f, nds = true)), names(packageTabletopSpecs(1.5f, nds = true, keypad = true)))
        assertEquals(listOf(TABLETOP), names(packageTabletopSpecs(1.5f, nds = false)))
    }
}
