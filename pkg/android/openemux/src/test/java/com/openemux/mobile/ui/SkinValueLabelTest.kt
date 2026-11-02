package com.openemux.mobile.ui

import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the skin row says in the console's settings and on a game's detail screen (issues 718, 720). */
class SkinValueLabelTest {
    private fun choice(vararg v: SkinVariant, imported: Boolean = false) = SkinChoice(v.map { SkinSwatch(it, null, null) }, imported)

    @Test fun nothingIsShownUntilTheChoiceIsKnown() {
        assertNull(skinValueLabel(null, "dark"))
    }

    @Test fun aConsoleWithVariantsShowsThePickedOne() {
        val c = choice(SkinVariant.CONSOLE, SkinVariant.DARK, SkinVariant.WHITE)
        assertEquals(R.string.settings_skin_console, skinValueLabel(c, "console"))
        assertEquals(R.string.settings_skin_dark, skinValueLabel(c, "dark"))
        assertEquals(R.string.settings_skin_color_white, skinValueLabel(c, "white"))
    }

    @Test fun aSavedVariantTheConsoleDoesNotComeInShowsItsOwnColors() {
        val c = choice(SkinVariant.CONSOLE, SkinVariant.DARK)
        assertEquals(R.string.settings_skin_console, skinValueLabel(c, "teal"))
        assertEquals(R.string.settings_skin_console, skinValueLabel(c, "neon"))
    }

    @Test fun anImportedSkinWinsOverThePick() {
        assertEquals(R.string.settings_skin_imported_value, skinValueLabel(choice(SkinVariant.CONSOLE, SkinVariant.DARK, imported = true), "dark"))
        assertEquals(R.string.settings_skin_imported_value, skinValueLabel(choice(SkinVariant.CONSOLE, imported = true), "console"))
    }

    @Test fun aConsoleWithNothingToPickHasNoLabel() {
        assertNull(skinValueLabel(choice(SkinVariant.CONSOLE), "dark"))
        assertNull(skinValueLabel(choice(), "dark"))
    }

    @Test fun withoutTheConsolesOwnPackageAnUnknownPickNamesNoOption() {
        val c = choice(SkinVariant.DARK, SkinVariant.WHITE)
        assertNull(c.option("teal"))
        assertNull(skinValueLabel(c, "teal"))
        assertEquals(SkinVariant.WHITE, c.option("white")?.variant)
    }
}
