package com.openemux.mobile.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtEditTest {
    private fun near(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 1e-4f)
        assertEquals(expected.top, actual.top, 1e-4f)
        assertEquals(expected.right, actual.right, 1e-4f)
        assertEquals(expected.bottom, actual.bottom, 1e-4f)
    }

    @Test
    fun `rotations swap the sides, flips keep them, a crop keeps its part`() {
        val s = EditSession().rotateLeft().flipHorizontal().flipVertical()
        assertEquals(300 to 200, s.size(200, 300))
        assertEquals(200 to 300, s.rotateRight().size(200, 300))
        val cropped = EditSession().toggleCrop().moveCrop(CropRect(0.25f, 0f, 0.75f, 0.5f)).applyCrop()
        assertEquals(100 to 150, cropped.size(200, 300))
        assertTrue(cropped.changed)
        assertFalse(EditSession().changed)
    }

    @Test
    fun `a rotation or a flip drops the box being drawn`() {
        val boxed = EditSession().toggleCrop()
        assertEquals(CropRect.WHOLE, boxed.crop)
        assertNull(boxed.rotateLeft().crop)
        assertNull(boxed.flipVertical().crop)
        assertNull(boxed.toggleCrop().crop)
    }

    @Test
    fun `applying a box over the whole image adds no step, and no box adds nothing`() {
        val s = EditSession().toggleCrop().applyCrop()
        assertTrue(s.ops.isEmpty())
        assertNull(s.crop)
        val plain = EditSession()
        assertSame(plain, plain.applyCrop())
    }

    @Test
    fun `reset goes back to the image as imported`() {
        assertEquals(EditSession(), EditSession().rotateLeft().toggleCrop().reset())
    }

    @Test
    fun `moving keeps the box inside the image at its size`() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.5f)
        near(CropRect(0.6f, 0.2f, 1f, 0.5f), r.moved(0.9f, 0f))
        near(CropRect(0f, 0f, 0.4f, 0.3f), r.moved(-1f, -1f))
    }

    @Test
    fun `each corner resizes from the opposite one, never past the image nor below the minimum`() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        near(CropRect(0f, 0f, 0.6f, 0.6f), r.resized(Corner.TOP_LEFT, -1f, -1f))
        near(CropRect(0.2f, 0f, 1f, 0.6f), r.resized(Corner.TOP_RIGHT, 1f, -1f))
        near(CropRect(0f, 0.2f, 0.6f, 1f), r.resized(Corner.BOTTOM_LEFT, -1f, 1f))
        near(CropRect(0.2f, 0.2f, 1f, 1f), r.resized(Corner.BOTTOM_RIGHT, 1f, 1f))
        // Dragged through the opposite corner, it stops at the minimum size.
        near(CropRect(0.6f - CropRect.MIN_SIDE, 0.6f - CropRect.MIN_SIDE, 0.6f, 0.6f), r.resized(Corner.TOP_LEFT, 1f, 1f))
        near(CropRect(0.2f, 0.2f, 0.2f + CropRect.MIN_SIDE, 0.2f + CropRect.MIN_SIDE), r.resized(Corner.BOTTOM_RIGHT, -1f, -1f))
    }

    @Test
    fun `the pixels are whole, inside, and at least one each way`() {
        assertArrayEquals(intArrayOf(25, 0, 50, 50), CropRect(0.25f, 0f, 0.75f, 0.5f).pixels(100, 100))
        assertArrayEquals(intArrayOf(9, 9, 1, 1), CropRect(0.999f, 0.999f, 0.999f, 0.999f).pixels(10, 10))
        assertEquals(0.4f, CropRect(0.2f, 0.1f, 0.6f, 0.9f).width, 1e-6f)
        assertEquals(0.8f, CropRect(0.2f, 0.1f, 0.6f, 0.9f).height, 1e-6f)
        assertFalse(CropRect(0.2f, 0.1f, 0.6f, 0.9f).whole)
    }
}
