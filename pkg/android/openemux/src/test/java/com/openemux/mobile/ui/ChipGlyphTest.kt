package com.openemux.mobile.ui

import com.openemux.mobile.data.Console
import org.junit.Assert.assertEquals
import org.junit.Test

class ChipGlyphTest {
    @Test
    fun `all games has no glyph`() {
        assertEquals(ChipGlyph.NONE, chipGlyph("ALL"))
    }

    @Test
    fun `favorites shows the star`() {
        assertEquals(ChipGlyph.STAR, chipGlyph("FAV"))
    }

    @Test
    fun `a console with pixel art shows it`() {
        assertEquals(ChipGlyph.CONSOLE, chipGlyph(Console.GBA.id))
    }

    @Test
    fun `an unknown id shows nothing`() {
        assertEquals(ChipGlyph.NONE, chipGlyph("NOPE"))
    }

    @Test
    fun `every console follows whether it has art`() {
        Console.entries.forEach { c ->
            val expected = if (c.icon != null) ChipGlyph.CONSOLE else ChipGlyph.NONE
            assertEquals(c.id, expected, chipGlyph(c.id))
        }
    }
}
