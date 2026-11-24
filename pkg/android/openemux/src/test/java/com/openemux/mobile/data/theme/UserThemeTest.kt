package com.openemux.mobile.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The theme model and its whitelist (issue 1018). */
class UserThemeTest {
    private fun key(name: String) = ThemeKeys.BY_NAME.getValue(name)

    @Test fun whitelistHasThePart1ColorsAndUniqueKeys() {
        val part1 = listOf(
            "window_bg", "sidebar_bg", "headerbar_bg", "library_bg", "card_bg", "popover_bg", "details_bg",
            "text_primary", "text_secondary", "sidebar_text_primary", "sidebar_text_secondary",
            "accent", "selection_bg", "accent_button_bg", "accent_button_fg",
        )
        part1.forEach { assertEquals(it, ThemeKind.COLOR, key(it).kind) }
        assertEquals(ThemeKeys.ALL.size, ThemeKeys.BY_NAME.size)
        ThemeSection.entries.forEach { assertTrue(it.name, ThemeKeys.inSection(it).isNotEmpty()) }
        // Every default the app paints passes AA where it is read on today.
        val problems = UserTheme.contrastProblems(UserTheme("t", "T", ThemeBase.DARK, mapOf("text_primary" to key("text_primary").dark!!)))
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test fun validatesEachKind() {
        assertEquals(0xFF24283B.toInt(), ThemeKeys.validate(key("library_bg"), "#24283B"))
        assertEquals(0xFF24283B.toInt(), ThemeKeys.validate(key("library_bg"), " #24283b "))
        assertNull(ThemeKeys.validate(key("library_bg"), "24283b"))
        assertNull(ThemeKeys.validate(key("library_bg"), "#2428"))
        assertNull(ThemeKeys.validate(key("library_bg"), 12))
        assertEquals(28.0, ThemeKeys.validate(key("corner_radius"), 400))
        assertEquals(0.0, ThemeKeys.validate(key("corner_radius"), -3.5))
        assertEquals(12.5, ThemeKeys.validate(key("corner_radius"), 12.5))
        assertNull(ThemeKeys.validate(key("corner_radius"), true))
        assertNull(ThemeKeys.validate(key("corner_radius"), Double.NaN))
        assertNull(ThemeKeys.validate(key("corner_radius"), "12"))
        assertEquals("tile", ThemeKeys.validate(key("library_image_fit"), "tile"))
        assertNull(ThemeKeys.validate(key("library_image_fit"), "stretch"))
        assertNull(ThemeKeys.validate(key("density"), 1))
        assertEquals("images/a-1.webp", ThemeKeys.validate(key("library_image"), "images/a-1.webp"))
        listOf("../x.png", "images/../x.png", "/images/x.png", "images/x.gif", "images/X.png", "images/sub/x.png", 3).forEach {
            assertNull(it.toString(), ThemeKeys.validate(key("library_image"), it))
        }
    }

    @Test fun validateAllKeepsOnlyKnownGoodKeys() {
        val dropped = mutableListOf<String>()
        val values = ThemeKeys.validateAll(
            mapOf("version" to 1, "name" to "x", "base" to "dark", "accent" to "#ffffff", "sidebar_width" to 300, "font_scale" to "big", "nested" to ThemeYaml.Nested),
            dropped::add,
        )
        assertEquals(mapOf("accent" to 0xFFFFFFFF.toInt()), values)
        assertEquals(listOf("sidebar_width", "font_scale", "nested"), dropped)
        assertEquals(emptyMap<String, Any>(), ThemeKeys.validateAll(mapOf("accent" to 1.5)))
    }

    @Test fun densityFactors() {
        assertEquals(0.85f, ThemeDensity.factor("compact"))
        assertEquals(1.15f, ThemeDensity.factor("comfortable"))
        assertEquals(1f, ThemeDensity.factor("default"))
        assertEquals(1f, ThemeDensity.factor(null))
    }

    @Test fun idsNamesAndSlugs() {
        assertTrue(UserTheme.isCustomId("midnight-2"))
        listOf(null, "system", "light", "dark", "", "-x", "Upper", "a/b", "x".repeat(49)).forEach { assertFalse(it.toString(), UserTheme.isCustomId(it)) }
        assertEquals("Mid night", UserTheme.cleanName("  Mid\t\n night\u0007 "))
        assertEquals("", UserTheme.cleanName(null))
        assertEquals(64, UserTheme.cleanName("x".repeat(100)).length)
        assertEquals("ceu-azul", UserTheme.slugify("Céu Azul!"))
        assertEquals("theme", UserTheme.slugify("夜"))
        assertEquals(40, UserTheme.slugify("a".repeat(60)).length)
    }

    @Test fun effectiveWithAndWithout() {
        val t = UserTheme("t", "T", ThemeBase.LIGHT)
        assertEquals(key("card_bg").light, t.effective("card_bg"))
        assertEquals(1.0, t.number("font_scale"), 0.0)
        assertEquals("default", t.choice("density"))
        assertFalse(t.isChanged("accent"))
        val changed = t.with("accent", "#ff0000").with("font_scale", 9).with("unknown", 1).with("density", "nope")
        assertEquals(0xFFFF0000.toInt(), changed.color("accent"))
        assertEquals(1.3, changed.number("font_scale"), 0.0)
        assertEquals(setOf("accent", "font_scale"), changed.values.keys)
        assertEquals(t, changed.without("accent").without("font_scale"))
        assertEquals(key("card_bg").dark, UserTheme("t", "T", ThemeBase.DARK).effective("card_bg"))
    }

    @Test fun parseAndSerialize() {
        val t = UserTheme.parse(mapOf("version" to 2, "name" to "  N  ", "base" to "dark", "accent" to "#ABCDEF", "corner_radius" to 4), "n")
        assertEquals(UserTheme("n", "N", "dark", mapOf("accent" to 0xFFABCDEF.toInt(), "corner_radius" to 4.0)), t)
        assertEquals("n", UserTheme.parse(mapOf("base" to "light"), "n").name)
        assertEquals("m", UserTheme.parse(mapOf("base" to "light", "name" to 5), "m").name)
        listOf(
            mapOf("base" to "sepia"), mapOf("version" to 0, "base" to "dark"), mapOf("version" to "1", "base" to "dark"),
            mapOf("version" to true, "base" to "dark"), emptyMap(),
        ).forEach { assertThrows(it.toString(), ThemeFileError::class.java) { UserTheme.parse(it, "x") } }
        val out = UserTheme.serialize(t.with("font_scale", 1.1))
        assertEquals(listOf("version", "name", "base", "accent", "corner_radius", "font_scale"), out.keys.toList())
        assertEquals("#abcdef", out["accent"])
        assertEquals(1, out["version"])
    }

    @Test fun contrastProblemsOnlyForChangedPairs() {
        val base = UserTheme("t", "T", ThemeBase.DARK)
        assertTrue(UserTheme.contrastProblems(base).isEmpty())
        val bad = base.with("text_secondary", "#2a2a33").with("card_bg", "#1c1c23")
        val problems = UserTheme.contrastProblems(bad)
        assertTrue(problems.isNotEmpty())
        assertTrue(problems.zipWithNext().all { (a, b) -> a.ratio <= b.ratio })
        assertTrue(problems.all { it.ratio < ThemeKeys.WCAG_AA })
        val forText = UserTheme.warningsFor("text_secondary", problems)
        assertTrue(forText.any { it.first == "card_bg" })
        val forCard = UserTheme.warningsFor("card_bg", problems)
        assertEquals(listOf("text_secondary"), forCard.map { it.first }.distinct())
        assertTrue(UserTheme.warningsFor("accent", problems).isEmpty())
        // The console list's color is checked with the text on it.
        val consoles = UserTheme.contrastProblems(base.with("console_list_bg", "#f3f3f6"))
        assertTrue(consoles.any { it.fg == "text_primary" && it.bg == "console_list_bg" })
        assertSame(ThemeKeys.BY_NAME["accent"], key("accent"))
    }
}
