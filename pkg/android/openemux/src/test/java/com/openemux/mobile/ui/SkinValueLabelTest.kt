package com.openemux.mobile.ui

import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the skin row says in the console's settings and on a game's detail screen (issue 718). */
class SkinValueLabelTest {
    @Test fun nothingIsShownUntilTheChoiceIsKnown() {
        assertNull(skinValueLabel(null, "dark"))
    }

    @Test fun aConsoleWithADarkPackageShowsThePickedVariant() {
        assertEquals(R.string.settings_skin_console, skinValueLabel(SkinChoice(dark = true, imported = false), "console"))
        assertEquals(R.string.settings_skin_dark, skinValueLabel(SkinChoice(dark = true, imported = false), "dark"))
        assertEquals(R.string.settings_skin_console, skinValueLabel(SkinChoice(dark = true, imported = false), "neon"))
    }

    @Test fun anImportedSkinWinsOverThePick() {
        assertEquals(R.string.settings_skin_imported_value, skinValueLabel(SkinChoice(dark = true, imported = true), "dark"))
        assertEquals(R.string.settings_skin_imported_value, skinValueLabel(SkinChoice(dark = false, imported = true), "console"))
    }

    @Test fun aConsoleWithNothingToPickHasNoLabel() {
        assertNull(skinValueLabel(SkinChoice(dark = false, imported = false), "dark"))
    }
}
