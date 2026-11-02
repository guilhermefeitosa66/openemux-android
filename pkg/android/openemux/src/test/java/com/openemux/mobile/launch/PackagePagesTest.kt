package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_LAYOUTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackagePagesTest {
    private fun layout(id: String) = NDS_LAYOUTS.getValue("landscape").first { it.id == id }

    @Test fun aPackageWithoutLayoutsHasItsTwoPagesTheShownOrientationFirst() {
        val pages = packagePages(null, tablet = false, first = null)
        assertEquals(listOf("portrait", "landscape"), pages.map { it.name })
        assertEquals(listOf("landscape", "portrait"), pages.map { it.rotate })
        pages.forEach { assertNull(it.layout); assertNull(it.screensNext) }
        assertEquals(listOf("landscape", "portrait"), packagePages(null, false, "pad-landscape").map { it.name })
        assertEquals(listOf("portrait", "landscape"), packagePages(null, false, "min-portrait").map { it.name })
        assertEquals(listOf("portrait", "landscape"), packagePages(null, false, "nothing of ours").map { it.name })
    }

    @Test fun anNdsPackageHasAPagePerLayoutOpeningOnTheOneShown() {
        val nds = layout("stacked") to layout("top")
        val pages = packagePages(nds, tablet = false, first = null)
        assertEquals(8, pages.size)
        assertEquals("portrait@stacked", pages.first().name)
        val byName = pages.associateBy { it.name }
        assertEquals("landscape@top", byName.getValue("portrait@side").rotate)
        assertEquals("portrait@stacked", byName.getValue("landscape@bottom").rotate)
        assertEquals("side", byName.getValue("landscape@side").layout)
        assertEquals("landscape@top", byName.getValue("landscape@side").screensNext)
        assertEquals("landscape@stacked", byName.getValue("landscape@bottom").screensNext)
        // A redraw keeps the page shown, whatever its mode; a layout of none of its pages opens the orientation's saved one.
        assertEquals("landscape@side", packagePages(nds, false, "pad-landscape@side").first().name)
        assertEquals("landscape@top", packagePages(nds, false, "landscape").first().name)
        assertEquals("portrait@stacked", packagePages(nds, false, "portrait@halves").first().name)
        assertEquals("portrait@stacked", packagePages(nds, false, "portrait-tabletop@side").first().name)
        assertEquals("portrait@bottom", packagePages(nds, false, "min-pad-portrait@bottom").first().name)
        assertEquals(8, packagePages(nds, false, "landscape@side").size)
    }

    @Test fun aGameStartsOnItsOrientationsSavedLayout() {
        val nds = layout("stacked") to layout("top")
        assertEquals("top", ndsLaunchLayout(nds, landscape = true).id)
        assertEquals("stacked", ndsLaunchLayout(nds, landscape = false).id)
    }
}
