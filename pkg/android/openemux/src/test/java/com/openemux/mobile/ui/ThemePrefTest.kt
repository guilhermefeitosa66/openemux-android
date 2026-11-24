package com.openemux.mobile.ui

import com.openemux.mobile.data.theme.ResolvedTheme
import com.openemux.mobile.data.theme.ThemeBase
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.theme.ThemeFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored theme choice, theme files by name and the grid under a theme (issue 1018). */
class ThemePrefTest {
    @After fun tearDown() = ActiveTheme.apply(null, null)

    @Test fun storedChoices() {
        assertEquals("light", ThemePref.parse("light"))
        assertEquals("dark", ThemePref.parse("dark"))
        assertEquals("midnight", ThemePref.parse("midnight"))
        listOf(null, "", "system", "../x", "Upper").forEach { assertEquals(it.toString(), "system", ThemePref.parse(it)) }
    }

    @Test fun themeFilesByName() {
        assertTrue(ThemeFiles.isThemeFile("Midnight.openemux-theme"))
        assertTrue(ThemeFiles.isThemeFile("x.OPENEMUX-THEME"))
        assertFalse(ThemeFiles.isThemeFile("game.zip"))
        assertFalse(ThemeFiles.isThemeFile("openemux-theme"))
        assertFalse(ThemeFiles.isThemeFile(null))
        assertEquals("ceu.openemux-theme", ThemeFiles.fileName(UserTheme("x", "Céu", ThemeBase.DARK)))
    }

    @Test fun gridFollowsTheTheme() {
        val carts = GridSpec(150f, 220f, 16f, 2)
        assertSame(carts, GridSpec.themed(carts, 1f, null))
        assertEquals(GridSpec(75f, 110f, 4f, 2), GridSpec.themed(carts, 0.5f, 4f))
        assertEquals(GridSpec(150f, 220f, 8f, 2), GridSpec.themed(carts, 1f, 8f))
        assertEquals(carts, GridSpec.Carts)
        ActiveTheme.apply(UserTheme("t", "T", ThemeBase.DARK).with("card_scale", 1.5).with("grid_gap", 4), null)
        assertEquals(GridSpec(225f, 330f, 4f, 2), GridSpec.Carts)
        assertEquals(ResolvedTheme.resolve(UserTheme("t", "T", ThemeBase.DARK).with("card_scale", 1.5).with("grid_gap", 4)).cardScale, ActiveTheme.resolved!!.cardScale)
        ActiveTheme.apply(null, null)
        assertNull(ActiveTheme.resolved)
    }
}
