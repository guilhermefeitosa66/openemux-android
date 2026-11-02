package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdsLayoutsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun layout(orientation: String, id: String) = NDS_LAYOUTS.getValue(orientation).first { it.id == id }

    @Test fun nothingSavedOpensEachOrientationOnItsDefault() {
        val dir = tmp.newFolder()
        assertEquals("stacked" to "side", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        assertEquals("halves", NdsLayouts.loadTabletop(dir).id)
    }

    @Test fun aFileFromBeforeTabletopOpensTabletopOnOnePerHalf() {
        val dir = tmp.newFolder()
        File(dir, "nds-layout").writeText("portrait=top\nlandscape=bottom\n")
        assertEquals("top" to "bottom", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        assertEquals("halves", NdsLayouts.loadTabletop(dir).id)
    }

    @Test fun eachOrientationIsSavedWithoutTouchingTheOthers() {
        val dir = tmp.newFolder()
        NdsLayouts.save(dir, "portrait-tabletop", layout("portrait-tabletop", "bottom"))
        NdsLayouts.save(dir, "portrait", layout("portrait", "top"))
        assertEquals("bottom", NdsLayouts.loadTabletop(dir).id)
        assertEquals("top" to "side", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        NdsLayouts.save(dir, "landscape", layout("landscape", "top"))
        assertEquals("bottom", NdsLayouts.loadTabletop(dir).id)
        assertEquals("portrait=top\nlandscape=top\nportrait-tabletop=bottom\n", File(dir, "nds-layout").readText())
    }

    @Test fun aLayoutTheOrientationNoLongerHasFallsBackToItsDefault() {
        val dir = tmp.newFolder()
        File(dir, "nds-layout").writeText("portrait-tabletop=side\nnot a line\n")
        assertEquals("halves", NdsLayouts.loadTabletop(dir).id)
    }

    @Test fun theTabletopCycleIsTopBottomOnePerHalf() {
        assertEquals("top", NdsLayouts.next("portrait-tabletop", "halves").id)
        assertEquals("bottom", NdsLayouts.next("portrait-tabletop", "top").id)
        assertEquals("halves", NdsLayouts.next("portrait-tabletop", "bottom").id)
    }

    @Test fun eachOrientationListsItsLayoutsInCycleOrder() {
        assertEquals(listOf("top", "bottom", "stacked"), NdsLayouts.of("portrait").map { it.id })
        assertEquals(listOf("top", "bottom", "side"), NdsLayouts.of("landscape").map { it.id })
        assertEquals(listOf("top", "bottom", "halves"), NdsLayouts.of("portrait-tabletop").map { it.id })
        assertEquals(emptyList<ScreenLayout>(), NdsLayouts.of("sideways"))
    }

    @Test fun aLayoutIsFoundOnlyInItsOwnOrientation() {
        assertEquals("side", NdsLayouts.find("landscape", "side")?.id)
        assertEquals(null, NdsLayouts.find("portrait", "side"))
        assertEquals(null, NdsLayouts.find("portrait", null))
        assertEquals(null, NdsLayouts.find("sideways", "top"))
    }

    @Test fun theTabletopOverlayWinsOverTheWindowsOrientation() {
        assertEquals("portrait-tabletop", NdsLayouts.orientation(tabletopShown = true, landscape = true))
        assertEquals("portrait-tabletop", NdsLayouts.orientation(tabletopShown = true, landscape = false))
        assertEquals("landscape", NdsLayouts.orientation(tabletopShown = false, landscape = true))
        assertEquals("portrait", NdsLayouts.orientation(tabletopShown = false, landscape = false))
    }

    @Test fun onlyALayoutChangeInTheSameOrientationIsNamed() {
        assertEquals(true, NdsLayouts.announce(sameOrientation = true, previous = "stacked", shown = "top"))
        // Turning lands on the remembered layout; a mode switch keeps it; nothing known yet names nothing.
        assertEquals(false, NdsLayouts.announce(sameOrientation = false, previous = "stacked", shown = "top"))
        assertEquals(false, NdsLayouts.announce(sameOrientation = true, previous = "top", shown = "top"))
        assertEquals(false, NdsLayouts.announce(sameOrientation = true, previous = null, shown = "top"))
    }

    @Test fun aSwitchTheAppHasSinceSupersededIsSkipped() {
        val asked = NdsShown("portrait", "stacked")
        assertEquals(true, NdsLayouts.superseded(asked, "portrait", "bottom"))
        assertEquals(false, NdsLayouts.superseded(asked, "portrait", "stacked"))
        assertEquals(false, NdsLayouts.superseded(asked, "landscape", "bottom"))
        assertEquals(false, NdsLayouts.superseded(null, "portrait", "bottom"))
    }
}
