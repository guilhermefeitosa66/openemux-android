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
        assertEquals("stacked" to "stacked", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        assertEquals("halves", NdsLayouts.loadTabletop(dir).id)
        assertEquals("stacked", NdsLayouts.default("landscape").id)
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
        assertEquals("top" to "stacked", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        NdsLayouts.save(dir, "landscape", layout("landscape", "top"))
        assertEquals("bottom", NdsLayouts.loadTabletop(dir).id)
        assertEquals("portrait=top\nlandscape=top\nportrait-tabletop=bottom\n", File(dir, "nds-layout").readText())
    }

    @Test fun aLayoutTheOrientationNoLongerHasFallsBackToItsDefault() {
        val dir = tmp.newFolder()
        File(dir, "nds-layout").writeText("portrait-tabletop=stacked\nlandscape=halves\nnot a line\n")
        assertEquals("halves", NdsLayouts.loadTabletop(dir).id)
        assertEquals("stacked", NdsLayouts.load(dir).second.id)
    }

    @Test fun aFileFromBeforeEveryLayoutWasListedStillOpensOnItsLayouts() {
        val dir = tmp.newFolder()
        File(dir, "nds-layout").writeText("portrait=stacked\nlandscape=side\nportrait-tabletop=bottom\n")
        assertEquals("stacked" to "side", NdsLayouts.load(dir).let { (p, l) -> p.id to l.id })
        assertEquals("bottom", NdsLayouts.loadTabletop(dir).id)
    }

    @Test fun theTabletopCycleIsTopBottomOnePerHalf() {
        assertEquals("top", NdsLayouts.next("portrait-tabletop", "halves").id)
        assertEquals("bottom", NdsLayouts.next("portrait-tabletop", "top").id)
        assertEquals("halves", NdsLayouts.next("portrait-tabletop", "bottom").id)
    }

    @Test fun everyOrientationListsEveryLayout() {
        assertEquals(listOf("top", "bottom", "stacked", "side"), NdsLayouts.of("portrait").map { it.id })
        assertEquals(listOf("top", "bottom", "stacked", "side"), NdsLayouts.of("landscape").map { it.id })
        assertEquals(listOf("top", "bottom", "halves", "side"), NdsLayouts.of("portrait-tabletop").map { it.id })
        assertEquals(emptyList<ScreenLayout>(), NdsLayouts.of("sideways"))
    }

    @Test fun theQuickCycleHasSideBySideOnlyWhereItHasRoom() {
        fun ids(o: String, gamepad: Boolean, tablet: Boolean) = NdsLayouts.cycle(o, gamepad, tablet).map { it.id }
        assertEquals(listOf("top", "bottom", "stacked"), ids("landscape", gamepad = false, tablet = false))
        assertEquals(listOf("top", "bottom", "stacked", "side"), ids("landscape", gamepad = true, tablet = false))
        assertEquals(listOf("top", "bottom", "stacked", "side"), ids("landscape", gamepad = false, tablet = true))
        for (gamepad in listOf(false, true)) for (tablet in listOf(false, true)) {
            assertEquals(listOf("top", "bottom", "stacked"), ids("portrait", gamepad, tablet))
            assertEquals(listOf("top", "bottom", "halves"), ids("portrait-tabletop", gamepad, tablet))
        }
    }

    @Test fun aLayoutOutsideTheCycleStepsToItsFirst() {
        val phone = NdsLayouts.cycle("landscape", gamepad = false, tablet = false)
        assertEquals("bottom", NdsLayouts.step(phone, "top").id)
        assertEquals("top", NdsLayouts.step(phone, "stacked").id)
        assertEquals("top", NdsLayouts.step(phone, "side").id)
        assertEquals("top", NdsLayouts.step(phone, null).id)
        assertEquals("side", NdsLayouts.next("landscape", "stacked", gamepad = true).id)
        assertEquals("top", NdsLayouts.next("landscape", "side", gamepad = true).id)
        assertEquals("top", NdsLayouts.next("landscape", "stacked").id)
        assertEquals("side", NdsLayouts.next("landscape", "stacked", tablet = true).id)
        assertEquals("top", NdsLayouts.next("portrait", "side").id)
        assertEquals("top", NdsLayouts.next("portrait-tabletop", "side").id)
    }

    @Test fun aLayoutIsFoundOnlyInItsOwnOrientation() {
        assertEquals("side", NdsLayouts.find("landscape", "side")?.id)
        assertEquals("side", NdsLayouts.find("portrait", "side")?.id)
        assertEquals(null, NdsLayouts.find("portrait", "halves"))
        assertEquals(null, NdsLayouts.find("portrait-tabletop", "stacked"))
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
