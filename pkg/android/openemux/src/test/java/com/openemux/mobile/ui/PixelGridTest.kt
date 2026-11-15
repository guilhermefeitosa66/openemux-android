package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelGridTest {
    @Test
    fun cellIsTheWholeMultipleThatFits() {
        assertEquals(5, iconCell(168, 32))   // 64 dp at density 2.625
        assertEquals(6, iconCell(192, 32))   // 64 dp at 3x
        assertEquals(4, iconCell(128, 32))
        assertEquals(3, iconCell(127, 32))
        assertEquals(1, iconCell(10, 32))    // too small: still drawn, at 1x
        assertEquals(1, iconCell(0, 32))
        assertEquals(7, iconCell(7, 0))      // no art width: one pixel per pixel, no division by zero
    }

    @Test
    fun seamIsAnEighthOfTheCellRounded() {
        assertEquals(1, softGridSeam(1))
        assertEquals(1, softGridSeam(4))
        assertEquals(1, softGridSeam(7))
        assertEquals(1, softGridSeam(11))
        assertEquals(2, softGridSeam(12))
        assertEquals(2, softGridSeam(16))
        assertEquals(3, softGridSeam(20))
    }

    @Test
    fun shadeDarkensTheColourAndKeepsTheAlpha() {
        assertEquals(0xFFCCCCCC.toInt(), softGridShade(0xFFFFFFFF.toInt()))
        assertEquals(0x80000000.toInt(), softGridShade(0x80000000.toInt()))
        // 0x0A * 0.8 = 8, 0x0B * 0.8 = 8.8 -> 9, 0x0D * 0.8 = 10.4 -> 10
        assertEquals(0xFF08090A.toInt(), softGridShade(0xFF0A0B0D.toInt()))
        assertEquals(0x00000000, softGridShade(0))
    }

    @Test
    fun opaquePixelGetsSeamsOnItsRightAndBottom() {
        val red = 0xFFFF0000.toInt()
        val shaded = softGridShade(red)
        val out = softGridPixels(intArrayOf(red), 1, 1, 4)
        assertEquals(16, out.size)
        // Cell 4, seam 1: a 3x3 core in the colour, the last column and row shaded.
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals("($x,$y)", if (x < 3 && y < 3) red else shaded, out[y * 4 + x])
        }
    }

    @Test
    fun transparentPixelStaysTransparentAndOpaqueOnesDoNotBleed() {
        val blue = 0xFF0000FF.toInt()
        val shaded = softGridShade(blue)
        // Two pixels side by side: transparent, then blue; at cell 8, seam 1.
        val out = softGridPixels(intArrayOf(0, blue), 2, 1, 8)
        assertEquals(16 * 8, out.size)
        for (y in 0 until 8) {
            for (x in 0 until 8) assertEquals("left ($x,$y)", 0, out[y * 16 + x])
            for (x in 8 until 16) {
                val inside = x < 15 && y < 7
                assertEquals("right ($x,$y)", if (inside) blue else shaded, out[y * 16 + x])
            }
        }
    }

    @Test
    fun rowsLandInPlace() {
        val a = 0xFF112233.toInt()
        val b = 0x80445566.toInt()
        // One column, two rows, cell 12 (seam 2): the second row starts at y = 12.
        val out = softGridPixels(intArrayOf(a, b), 1, 2, 12)
        assertEquals(12 * 24, out.size)
        assertEquals(a, out[0])
        assertEquals(softGridShade(a), out[10 * 12])
        assertEquals(softGridShade(a), out[11 * 12 + 9])
        assertEquals(b, out[12 * 12])
        assertEquals(b, out[21 * 12 + 9])
        assertEquals(softGridShade(b), out[22 * 12 + 9])
        assertEquals(softGridShade(b), out[13 * 12 + 11])
    }
}
