package com.openemux.mobile.data

import com.openemux.mobile.ui.SkinChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalog of the variants each console comes in (issue 720), and the choice the picker builds from it. */
class SkinCatalogTest {
    private val json = """
        {"GBA": [{"id": "console", "body": "#5B5293", "key": "#C9C9CB"}, {"id": "dark", "body": "#050506", "key": "#232327"},
                 {"id": "white", "body": "#E9E9E6", "key": "bad"}, {"id": "neon", "body": "#000000", "key": "#000000"},
                 {"id": "white", "body": "#000000", "key": "#000000"}, "not an object", {"id": "clear-blue"}],
         "MD": []}
    """

    @Test fun aConsolesVariantsAreListedInOrderWithTheirSwatches() {
        val gba = SkinCatalog.parse(json, Console.GBA)
        assertEquals(listOf(SkinVariant.CONSOLE, SkinVariant.DARK, SkinVariant.WHITE, SkinVariant.CLEAR_BLUE), gba.map { it.variant })
        assertEquals(SkinSwatch(SkinVariant.CONSOLE, 0xFF5B5293, 0xFFC9C9CB), gba[0])
        // A color that is not #RRGGBB, or none, leaves the swatch without it; a repeated id keeps the first.
        assertEquals(SkinSwatch(SkinVariant.WHITE, 0xFFE9E9E6, null), gba[2])
        assertEquals(SkinSwatch(SkinVariant.CLEAR_BLUE, null, null), gba[3])
    }

    @Test fun aConsoleTheCatalogDoesNotListOrAnUnreadableCatalogListsNothing() {
        assertTrue(SkinCatalog.parse(json, Console.MD).isEmpty())
        assertTrue(SkinCatalog.parse(json, Console.PS).isEmpty())
        assertTrue(SkinCatalog.parse("not json", Console.GBA).isEmpty())
        assertTrue(SkinCatalog.parse(null, Console.GBA).isEmpty())
        assertTrue(SkinCatalog.parse("""{"GBA": "console"}""", Console.GBA).isEmpty())
    }

    @Test fun onlyHexColorsAreRead() {
        assertEquals(0xFF0A0B0C, SkinCatalog.color("#0a0b0c"))
        assertEquals(0xFFFFFFFF, SkinCatalog.color("#FFFFFF"))
        assertNull(SkinCatalog.color("FFFFFF"))
        assertNull(SkinCatalog.color("#FFF"))
        assertNull(SkinCatalog.color("#-12345"))
        assertNull(SkinCatalog.color("#GGGGGG"))
        assertNull(SkinCatalog.color(""))
    }

    @Test fun theChoiceKeepsTheVariantsThatShipAPackage() {
        val choice = SkinChoice.of(json, Console.GBA) { it != SkinVariant.CLEAR_BLUE }
        assertEquals(listOf(SkinVariant.CONSOLE, SkinVariant.DARK, SkinVariant.WHITE), choice.options.map { it.variant })
        assertTrue(choice.pickable)
        assertEquals(SkinVariant.WHITE, choice.option("white")?.variant)
        assertEquals(SkinVariant.CONSOLE, choice.option("clear-blue")?.variant)
    }

    @Test fun withoutACatalogEveryVariantWithAPackageIsListedWithoutSwatches() {
        val choice = SkinChoice.of(null, Console.GBA) { it == SkinVariant.CONSOLE || it == SkinVariant.DARK }
        assertEquals(listOf(SkinSwatch(SkinVariant.CONSOLE, null, null), SkinSwatch(SkinVariant.DARK, null, null)), choice.options)
    }

    @Test fun aConsoleWithOnlyItsOwnPackageHasNothingToPick() {
        assertFalse(SkinChoice.of(json, Console.GBA) { it == SkinVariant.CONSOLE }.pickable)
        assertFalse(SkinChoice.of(json, Console.MD) { it == SkinVariant.CONSOLE }.pickable)
    }

    @Test fun aConsoleWithNoPackageOffersItsDrawnSkin() {
        val choice = SkinChoice.of(json, Console.GBA) { false }
        assertEquals(listOf(SkinSwatch(SkinVariant.CONSOLE, null, null)), choice.options)
        assertFalse(choice.pickable)
    }
}
