package com.openemux.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The controller focus as a background (issue 951): which tone an item takes, and that its content stays readable. */
class FocusFillTest {
    private val darkSoft = Color(0xFF3B2A6B)
    private val lightSoft = Color(0xFFE4D9FD)

    @Test
    fun anItemWithNoBackgroundOrAPlainOneTakesTheToneOfAToggleThatIsOn() {
        assertEquals(darkSoft, focusFill(null, dark = true))
        assertEquals(lightSoft, focusFill(null, dark = false))
        // A card, a quiet button, a warning row: their own colour gives way to the same tone.
        assertEquals(darkSoft, focusFill(Color(0xFF1C1C23), dark = true))
        assertEquals(lightSoft, focusFill(Color(0xFFEFEEF4), dark = false))
        assertEquals(lightSoft, focusFill(Color.Transparent, dark = false))
    }

    @Test
    fun aSelectedItemGoesAStepDeeperSoTheFocusStillShows() {
        assertEquals(Color(0xFF4E3A8C), focusFill(darkSoft, dark = true))
        assertEquals(Color(0xFFD3C2FB), focusFill(lightSoft, dark = false))
        // A selection drawn in the lighter tint (a tab, a picked tile) deepens the same way.
        assertEquals(Color(0xFF4E3A8C), focusFill(Color(0xFF221C33), dark = true))
        assertEquals(Color(0xFFD3C2FB), focusFill(Color(0xFFEFE8FD), dark = false))
        // The other theme's tone is just a background there.
        assertEquals(darkSoft, focusFill(lightSoft, dark = true))
    }

    @Test
    fun anAccentButtonGoesADeeperVioletInBothThemes() {
        assertEquals(Color(0xFF5B21B6), focusFill(Color(0xFF7C3AED), dark = true))
        assertEquals(Color(0xFF5B21B6), focusFill(Color(0xFF7C3AED), dark = false))
    }

    @Test
    fun textAndIconsStayReadableOnTheFocusTone() {
        // Text, muted text, accent text and the selected accent text of each theme (WCAG AA, 4.5:1).
        for (ink in listOf(Color(0xFFF3F3F6), Color(0xFFA3A3B2), Color(0xFFB79CFF), Color(0xFFD4C4FF))) assertReadable(ink, focusFill(null, dark = true))
        for (ink in listOf(Color(0xFF17171C), Color(0xFF5C5C6B), Color(0xFF6D28D9), Color(0xFF5B21B6))) assertReadable(ink, focusFill(null, dark = false))
        assertReadable(Color.White, focusFill(Color(0xFF7C3AED), dark = true))
    }

    @Test
    fun aSelectedAndFocusedItemKeepsItsLabelReadable() {
        // A selected item's label is the text or the selected accent text (4.5:1); its muted line stays above 3:1.
        for (ink in listOf(Color(0xFFF3F3F6), Color(0xFFD4C4FF))) assertReadable(ink, focusFill(darkSoft, dark = true))
        for (ink in listOf(Color(0xFF17171C), Color(0xFF5B21B6))) assertReadable(ink, focusFill(lightSoft, dark = false))
        assertReadable(Color(0xFFA3A3B2), focusFill(darkSoft, dark = true), least = 3f)
        assertReadable(Color(0xFF5C5C6B), focusFill(lightSoft, dark = false), least = 3f)
    }

    private fun assertReadable(ink: Color, background: Color, least: Float = 4.5f) {
        val (hi, lo) = listOf(ink.luminance(), background.luminance()).sorted().reversed()
        val ratio = (hi + 0.05f) / (lo + 0.05f)
        assertTrue("$ink on $background: $ratio", ratio >= least)
    }
}
