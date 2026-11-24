package com.openemux.mobile.data.theme

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The background pictures' blur (issue 1018). */
class ThemeBlurTest {
    @Test fun noRadiusIsACopy() {
        val px = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val out = ThemeBlur.blur(px, 2, 1, 0)
        assertArrayEquals(px, out)
        assertNotSame(px, out)
        assertArrayEquals(IntArray(0), ThemeBlur.blur(IntArray(0), 0, 0, 3))
        assertThrows(IllegalArgumentException::class.java) { ThemeBlur.blur(IntArray(3), 2, 2, 1) }
    }

    @Test fun aFlatPictureStaysFlatAndAnEdgeSoftens() {
        val grey = IntArray(16) { 0xFF808080.toInt() }
        assertArrayEquals(grey, ThemeBlur.blur(grey, 4, 4, 2))
        // Black left half, white right half: the middle goes grey, the corners keep their side.
        val w = 8
        val px = IntArray(w * 2) { i -> if (i % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = ThemeBlur.blur(px, w, 2, 1)
        val mid = out[w / 2] and 0xFF
        assertTrue("$mid", mid in 60..200)
        assertTrue((out[0] and 0xFF) < 40)
        assertTrue((out[w - 1] and 0xFF) > 215)
        assertEquals(0xFF, out[0] ushr 24)
    }

    @Test fun radiusAtTheWorkingSize() {
        assertEquals(0, ThemeBlur.workRadius(0f, 1f, 512, 400f))
        assertEquals(0, ThemeBlur.workRadius(8f, 1f, 512, 0f))
        assertEquals(10, ThemeBlur.workRadius(8f, 1f, 512, 400f))
        assertEquals(1, ThemeBlur.workRadius(0.1f, 1f, 512, 400f))
    }
}
