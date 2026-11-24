package com.openemux.mobile.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The theme editor's state (issue 1018): changes, resets, warnings and pictures. */
class ThemeDraftTest {
    @Test fun aNewDraftIsAlwaysWorthSaving() {
        val d = ThemeDraft.new("  Mid  night ", ThemeBase.DARK)
        assertTrue(d.isNew)
        assertTrue(d.dirty)
        assertEquals("Mid night", d.theme.name)
        assertEquals(ThemeSection.COLORS, d.section)
        assertEquals(ThemeSection.SIZES, d.pick(ThemeSection.SIZES).section)
    }

    @Test fun editingTracksChangesAgainstTheSavedTheme() {
        val saved = UserTheme("m", "M", ThemeBase.LIGHT, mapOf("accent" to 0xFF112233.toInt()))
        val d = ThemeDraft.edit(saved)
        assertFalse(d.isNew)
        assertFalse(d.dirty)
        val changed = d.setColor("card_bg", 0xFFABCDEF.toInt())
        assertTrue(changed.dirty)
        assertEquals(0xFFABCDEF.toInt(), changed.theme.color("card_bg"))
        assertFalse(changed.reset("card_bg").dirty)
        assertTrue(changed.resetAll().theme.values.isEmpty())
        assertEquals(1.2, d.set("font_scale", 1.2).theme.number("font_scale"), 0.0)
    }

    @Test fun warningsPerKeyAndSection() {
        val d = ThemeDraft.new("x", ThemeBase.DARK).set("text_secondary", "#20202a")
        assertTrue(d.problems.isNotEmpty())
        assertTrue(d.warnings("text_secondary").isNotEmpty())
        assertTrue(d.hasWarning(ThemeSection.TEXT))
        assertTrue(d.hasWarning(ThemeSection.COLORS))
        assertFalse(d.hasWarning(ThemeSection.SIZES))
        assertTrue(d.warnings("accent").isEmpty())
    }

    @Test fun picturesStageShowTheirSettingsAndGo() {
        val folder = File("/themes/m")
        val d0 = ThemeDraft.new("x", ThemeBase.DARK)
        assertEquals(listOf("library_image", "sidebar_image"), d0.rows(ThemeSection.BACKGROUNDS).map { it.key })
        assertNull(d0.imageFile("library_image", folder))
        val file = File("/cache/library-1.jpg")
        val d1 = d0.stageImage("library_image", "images/library-1.jpg", file).set("library_image_dim", 0.2)
        assertEquals(file, d1.imageFile("library_image", folder))
        assertEquals(
            listOf("library_image", "library_image_fit", "library_image_dim", "library_image_blur", "sidebar_image"),
            d1.rows(ThemeSection.BACKGROUNDS).map { it.key },
        )
        val d2 = d1.reset("library_image")
        assertFalse(d2.theme.values.keys.any { it.startsWith("library_image") })
        assertTrue(d2.staged.isEmpty())
        // A saved theme's picture lives in its folder.
        val saved = ThemeDraft.edit(UserTheme("m", "M", ThemeBase.DARK, mapOf("sidebar_image" to "images/sidebar-1.jpg")))
        assertEquals(File(folder, "images/sidebar-1.jpg"), saved.imageFile("sidebar_image", folder))
        assertNull(saved.imageFile("sidebar_image", null))
        assertEquals(saved.staged, saved.removeImage("library_image").staged)
        assertTrue(saved.resetAll().staged.isEmpty())
    }
}
