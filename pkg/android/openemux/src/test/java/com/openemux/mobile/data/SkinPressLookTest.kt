package com.openemux.mobile.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/** The pressed look's tint and the rounded triangle's corners (issue 891). */
class SkinPressLookTest {
    @Test fun aLightFaceDarkensADarkOneLightens() {
        assertEquals(SkinPaintDefaults.PRESS_DARK, SkinPaintDefaults.pressTint(IntArray(4) { 0xFFE0E0E8.toInt() }))
        assertEquals(SkinPaintDefaults.PRESS_LIGHT, SkinPaintDefaults.pressTint(IntArray(4) { 0xFF2E2E35.toInt() }))
        // Nothing opaque reads as dark.
        assertEquals(SkinPaintDefaults.PRESS_LIGHT, SkinPaintDefaults.pressTint(IntArray(4)))
        assertEquals(0x4D, SkinPaintDefaults.PRESS_DARK ushr 24)
        assertEquals(0x59, SkinPaintDefaults.PRESS_LIGHT ushr 24)
    }

    @Test fun theFaceIsWhatIsOpaque() {
        // A white face on a transparent box, with a half-transparent black edge: the face counts, the box does not.
        val pixels = intArrayOf(0x00000000, 0x00000000, 0xFFFFFFFF.toInt(), 0x80000000.toInt())
        assertEquals(1.0 / (1.0 + 128.0 / 255.0), SkinColorMath.meanLuminance(pixels), 1e-6)
        assertEquals(0.0, SkinColorMath.meanLuminance(IntArray(0)), 0.0)
    }

    @Test fun aRightTrianglesCornersAreRoundedAlike() {
        // Square corner at (0, 100), the others at (0, 0) and (100, 100).
        val c = SkinShapes.roundedCorners(floatArrayOf(0f, 0f, 0f, 100f, 100f, 100f), 16f)
        // The square corner: 16 along each leg.
        assertArrayEquals(floatArrayOf(0f, 84f, 0f, 100f, 16f, 100f), c.copyOfRange(6, 12), 1e-3f)
        // A 45° corner takes 16 / tan(22.5°) of each edge.
        val d = 16f / 0.41421357f
        assertArrayEquals(floatArrayOf(100f - d, 100f, 100f, 100f, 100f - d / sqrt(2f), 100f - d / sqrt(2f)), c.copyOfRange(12, 18), 1e-3f)
        assertEquals(d, hypot(c[4], c[5]), 1e-3f)
    }

    @Test fun shortEdgesAndStraightCornersLimitTheRounding() {
        // A tiny triangle: each corner takes at most half its shorter edge.
        val c = SkinShapes.roundedCorners(floatArrayOf(0f, 0f, 0f, 4f, 4f, 4f), 16f)
        assertArrayEquals(floatArrayOf(0f, 2f, 0f, 4f, 2f, 4f), c.copyOfRange(6, 12), 1e-3f)
        // Three points on a line, and two on one spot: no corner to round.
        val line = SkinShapes.roundedCorners(floatArrayOf(0f, 0f, 5f, 0f, 10f, 0f), 2f)
        assertArrayEquals(floatArrayOf(5f, 0f, 5f, 0f, 5f, 0f), line.copyOfRange(6, 12), 1e-3f)
        val same = SkinShapes.roundedCorners(floatArrayOf(1f, 1f, 1f, 1f, 3f, 3f), 2f)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f), same.copyOfRange(0, 6), 1e-3f)
    }
}
