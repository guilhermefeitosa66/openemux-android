package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A user theme turned into the app's palette, shapes and sizes (issue 1018). */
class ResolvedThemeTest {
    private fun theme(base: String = ThemeBase.DARK, vararg values: Pair<String, Any>) =
        values.fold(UserTheme("t", "T", base)) { t, (k, v) -> t.with(k, v) }

    @Test fun anUntouchedThemePaintsLikeItsBase() {
        for (base in ThemeBase.ALL) {
            val r = ResolvedTheme.resolve(theme(base))
            assertTrue(r.tokens.toString(), r.tokens.isEmpty())
            assertEquals(base == ThemeBase.DARK, r.dark)
            assertEquals(1f, r.cornerScale)
            assertEquals(0f, r.borderWidth)
            assertNull(r.borderColor)
            assertEquals(0f, r.shadowStrength)
            assertEquals(1f, r.iconScale)
            assertEquals(1f, r.fontScale)
            assertEquals(1f, r.density)
            assertNull(r.gridGap)
            assertEquals(1f, r.cardScale)
            assertNull(r.libraryImage)
            assertNull(r.navImage)
            assertEquals("t", r.id)
        }
    }

    @Test fun surfacesFollowThePageUnlessSetThemselves() {
        val r = ResolvedTheme.resolve(theme(ThemeBase.DARK, "window_bg" to "#101020", "library_bg" to "#202030"))
        assertEquals(0xFF101020.toInt(), r.color(OeToken.BG))
        assertEquals(0xFF101020.toInt(), r.color(OeToken.NAV_BG))
        assertEquals(0xFF101020.toInt(), r.color(OeToken.DETAILS_BG))
        assertEquals(0xFF202030.toInt(), r.color(OeToken.LIBRARY_BG))
        assertTrue(r.color(OeToken.MUTED) != null && r.color(OeToken.FAINT) != null && r.color(OeToken.CHEVRON) != null)
        assertNull(r.color(OeToken.SURFACE))
    }

    @Test fun everyColorKeyReachesItsTokens() {
        val r = ResolvedTheme.resolve(
            theme(
                ThemeBase.LIGHT,
                "headerbar_bg" to "#010101", "sidebar_bg" to "#020202", "details_bg" to "#030303", "card_bg" to "#fefefe",
                "popover_bg" to "#050505", "border_color" to "#060606", "text_primary" to "#070707", "text_secondary" to "#080808",
                "sidebar_text_primary" to "#090909", "sidebar_text_secondary" to "#0a0a0a", "accent" to "#0b0b0b",
                "accent_button_bg" to "#0c0c0c", "accent_button_fg" to "#0d0d0d", "icon_color" to "#0e0e0e",
            ),
        )
        assertEquals(0xFF010101.toInt(), r.color(OeToken.CONTROL))
        assertEquals(0xFF020202.toInt(), r.color(OeToken.NAV_BG))
        assertEquals(0xFF030303.toInt(), r.color(OeToken.DETAILS_BG))
        assertEquals(0xFFFEFEFE.toInt(), r.color(OeToken.SURFACE))
        assertEquals(SkinColorMath.mix(0xFFFEFEFE.toInt(), 0xFF070707.toInt(), 0.06f), r.color(OeToken.SURFACE2))
        assertEquals(0xFF050505.toInt(), r.color(OeToken.SHEET))
        assertEquals(0xFF060606.toInt(), r.color(OeToken.BORDER))
        assertEquals(0xFF060606.toInt(), r.borderColor)
        assertEquals(0xFF070707.toInt(), r.color(OeToken.TEXT))
        assertEquals(0xFF080808.toInt(), r.color(OeToken.MUTED))
        assertEquals(0xFF090909.toInt(), r.color(OeToken.NAV_TEXT_ACTIVE))
        assertEquals(0xFF0A0A0A.toInt(), r.color(OeToken.NAV_TEXT))
        assertEquals(0xFF0B0B0B.toInt(), r.color(OeToken.ACCENT_TEXT))
        assertEquals(0xFF0C0C0C.toInt(), r.color(OeToken.ACCENT))
        assertEquals(SkinColorMath.lighten(0xFF0C0C0C.toInt(), 0.25f), r.color(OeToken.ACCENT_FOCUS))
        assertEquals(0xFF0D0D0D.toInt(), r.color(OeToken.ON_ACCENT))
        assertEquals(0xFF0E0E0E.toInt(), r.color(OeToken.ICON))
    }

    @Test fun stepGoesTheReadableWay() {
        assertEquals(SkinColorMath.darken(0xFFFFFFFF.toInt(), 0.2f), ResolvedTheme.step(0xFFFFFFFF.toInt(), 0.2f))
        assertEquals(SkinColorMath.lighten(0xFF000000.toInt(), 0.2f), ResolvedTheme.step(0xFF000000.toInt(), 0.2f))
    }

    @Test fun theFocusStaysVisibleWhateverTheTheme() {
        for (base in ThemeBase.ALL) {
            val floor = minOf(ResolvedTheme.FOCUS_FLOOR, ResolvedTheme.baseFloor(base))
            for (bg in listOf("#000000", "#ffffff", "#7c3aed", "#3b2a6b", "#808080")) {
                val t = theme(base, "window_bg" to bg, "library_bg" to bg, "card_bg" to bg, "popover_bg" to bg, "details_bg" to bg, "sidebar_bg" to bg, "selection_bg" to bg, "accent" to bg)
                val soft = ResolvedTheme.resolve(t).color(OeToken.SOFT)!!
                val ratio = SkinColorMath.contrast(soft, SkinColorMath.parseHex(bg)!!)
                assertTrue("$base $bg: $ratio", ratio >= floor - 1e-9)
                val onSoft = ResolvedTheme.resolve(t).color(OeToken.ON_SOFT)!!
                assertTrue("$base $bg on soft", SkinColorMath.contrast(onSoft, soft) >= ResolvedTheme.ON_SOFT_FLOOR - 1e-9)
            }
        }
    }

    @Test fun aFocusAlreadyVisibleIsKept() {
        val r = ResolvedTheme.resolve(theme(ThemeBase.DARK, "selection_bg" to "#9a7bff"))
        assertEquals(0xFF9A7BFF.toInt(), r.color(OeToken.SOFT))
        assertEquals(SkinColorMath.mix(0xFF9A7BFF.toInt(), 0xFF121216.toInt(), 0.5f), r.color(OeToken.TINT))
    }

    @Test fun surfacesSpanningBlackToWhiteGetTheBestTheFloorAllows() {
        val c = ResolvedTheme.withFloor(0xFF808080.toInt(), listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), dark = true, floor = 10.0)
        val best = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()).minOf { SkinColorMath.contrast(c, it) }
        assertTrue(best > 2.0)
        // On a light base it searches darker first.
        val dark = ResolvedTheme.withFloor(0xFFEEEEEE.toInt(), listOf(0xFFFFFFFF.toInt()), dark = false)
        assertTrue(SkinColorMath.luminance(dark) < SkinColorMath.luminance(0xFFEEEEEE.toInt()))
    }

    @Test fun shapesSizesAndPictures() {
        val r = ResolvedTheme.resolve(
            theme(
                ThemeBase.DARK,
                "corner_radius" to 9, "border_width" to 2, "shadow_strength" to 0.5, "shadow_color" to "#112233", "icon_scale" to 1.25,
                "font_scale" to 1.2, "density" to "comfortable", "grid_gap" to 20, "card_scale" to 0.8,
                "library_image" to "images/library-a.jpg", "library_image_fit" to "tile", "library_image_dim" to 0.3, "library_image_blur" to 8,
                "sidebar_image" to "images/sidebar-b.jpg",
            ),
        )
        assertEquals(0.5f, r.cornerScale)
        assertEquals(2f, r.borderWidth)
        assertEquals(0xFF2B2B35.toInt(), r.borderColor)
        assertEquals(0.5f, r.shadowStrength)
        assertEquals(0xFF112233.toInt(), r.shadowColor)
        assertEquals(1.25f, r.iconScale)
        assertEquals(1.2f, r.fontScale)
        assertEquals(1.15f, r.density)
        assertEquals(20f, r.gridGap)
        assertEquals(0.8f, r.cardScale)
        assertEquals(ThemeImage("images/library-a.jpg", "tile", 0.3f, 8f), r.libraryImage)
        assertEquals(ThemeImage("images/sidebar-b.jpg", "cover", 0.5f, 0f), r.navImage)
    }
}
