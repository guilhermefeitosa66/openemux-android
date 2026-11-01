package com.openemux.mobile.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowMotionSlotTest {

    private fun assertBox(expected: SkinBox, actual: SkinBox?) {
        requireNotNull(actual)
        listOf(expected.x to actual.x, expected.y to actual.y, expected.w to actual.w, expected.h to actual.h)
            .forEach { (e, a) -> assertEquals("$expected vs $actual", e, a, 0.01f) }
    }

    @Test
    fun `the bundled phone portrait strip makes a fifth cell right before fast forward`() {
        // SFC iPhone portrait: save, load, turbo, fast forward, edge to edge; a D-pad and a button elsewhere.
        val items = listOf(
            null,
            SkinBox(6f, 264f, 87f, 30f), SkinBox(93f, 264f, 87f, 30f), SkinBox(180f, 264f, 87f, 30f), SkinBox(267f, 264f, 87f, 30f),
            SkinBox(280f, 500f, 60f, 60f),
        )
        val layout = slowMotionLayout(items, 4)
        val cell = 348f / 5f
        assertBox(SkinBox(6f + cell * 3, 264f, cell, 30f), layout.slowMotion)
        assertEquals(setOf(1, 2, 3, 4), layout.moved.keys)
        assertBox(SkinBox(6f, 264f, cell, 30f), layout.moved[1])
        assertBox(SkinBox(6f + cell, 264f, cell, 30f), layout.moved[2])
        assertBox(SkinBox(6f + cell * 2, 264f, cell, 30f), layout.moved[3])
        assertBox(SkinBox(6f + cell * 4, 264f, cell, 30f), layout.moved[4])
    }

    @Test
    fun `the strip keeps to its own span and leaves the keys around it alone`() {
        // FC iPhone landscape: select and start on the same row, apart from the strip; frames rounded by the export.
        val items = listOf(
            SkinBox(86.7f, 326f, 54f, 28f),
            SkinBox(157.7f, 326f, 107.6f, 28f), SkinBox(265.3f, 326f, 107.7f, 28f), SkinBox(373f, 326f, 107.7f, 28f),
            SkinBox(480.7f, 326f, 107.6f, 28f), SkinBox(605.3f, 326f, 54f, 28f),
        )
        val layout = slowMotionLayout(items, 4)
        assertEquals(setOf(1, 2, 3, 4), layout.moved.keys)
        val cell = (588.3f - 157.7f) / 5f
        assertBox(SkinBox(157.7f + cell * 3, 326f, cell, 28f), layout.slowMotion)
        assertBox(SkinBox(157.7f + cell * 4, 326f, cell, 28f), layout.moved[4])
        // Nothing moves over select or start.
        val all = layout.moved.values + layout.slowMotion
        assertTrue(all.all { it.x >= 140.7f && it.right <= 605.3f + 0.01f })
    }

    @Test
    fun `keys after fast forward move one cell right`() {
        val items = listOf(SkinBox(0f, 0f, 10f, 10f), SkinBox(10f, 0f, 10f, 10f), SkinBox(20f, 0f, 10f, 10f))
        val layout = slowMotionLayout(items, 1)
        assertBox(SkinBox(0f, 0f, 7.5f, 10f), layout.moved[0])
        assertBox(SkinBox(7.5f, 0f, 7.5f, 10f), layout.slowMotion)
        assertBox(SkinBox(15f, 0f, 7.5f, 10f), layout.moved[1])
        assertBox(SkinBox(22.5f, 0f, 7.5f, 10f), layout.moved[2])
    }

    @Test
    fun `a key on another row or with a gap is not part of the strip`() {
        val items = listOf(
            SkinBox(0f, 40f, 10f, 10f), // right edge meets fast forward's left, but on another row
            SkinBox(0f, 0f, 9f, 10f), // a gap before fast forward
            SkinBox(10f, 0f, 10f, 14f), // fast forward's row, but taller
            SkinBox(10f, 0f, 20f, 10f),
        )
        val layout = slowMotionLayout(items, 3)
        assertEquals(setOf(3), layout.moved.keys)
    }

    @Test
    fun `a lone wide fast forward is split, slow motion on the left`() {
        val layout = slowMotionLayout(listOf(SkinBox(100f, 50f, 60f, 30f)), 0)
        assertBox(SkinBox(100f, 50f, 30f, 30f), layout.slowMotion)
        assertBox(SkinBox(130f, 50f, 30f, 30f), layout.moved[0])
    }

    @Test
    fun `a lone tall fast forward is split, slow motion on top`() {
        val layout = slowMotionLayout(listOf(null, SkinBox(100f, 50f, 30f, 60f)), 1)
        assertBox(SkinBox(100f, 50f, 30f, 30f), layout.slowMotion)
        assertBox(SkinBox(100f, 80f, 30f, 30f), layout.moved[1])
    }

    @Test(expected = IllegalStateException::class)
    fun `fast forward must be a button`() {
        slowMotionLayout(listOf(null), 0)
    }

    @Test
    fun `fast forward inputs prefer the toggle`() {
        assertEquals(listOf("toggleFastForward", "fastForward"), PACKAGE_FAST_FORWARD_INPUTS)
        assertEquals(126f, SkinBox(6f, 4f, 120f, 30f).right, 0f)
        assertEquals(34f, SkinBox(6f, 4f, 120f, 30f).bottom, 0f)
    }

    @Test
    fun `side by side speed keys stop their touch areas at the side they share`() {
        // A lone wide fast forward split in two, the skin's extended edges 10 units all round.
        val slow = SkinBox(100f, 80f, 30f, 30f)
        val ff = SkinBox(130f, 80f, 30f, 30f)
        val (slowTouch, ffTouch) = speedTouches(slow, ff, SkinBox(90f, 70f, 50f, 50f), SkinBox(120f, 70f, 50f, 50f))
        assertBox(SkinBox(90f, 70f, 40f, 50f), slowTouch)
        assertBox(SkinBox(130f, 70f, 40f, 50f), ffTouch)
    }

    @Test
    fun `stacked speed keys stop their touch areas at the side they share`() {
        // A tall fast forward split in two: slow motion on top.
        val slow = SkinBox(100f, 80f, 30f, 30f)
        val ff = SkinBox(100f, 110f, 30f, 30f)
        val (slowTouch, ffTouch) = speedTouches(slow, ff, SkinBox(90f, 70f, 50f, 50f), SkinBox(90f, 100f, 50f, 50f))
        assertBox(SkinBox(90f, 70f, 50f, 40f), slowTouch)
        assertBox(SkinBox(90f, 110f, 50f, 40f), ffTouch)
    }

    @Test
    fun `touch areas with no extended edges are left as they are`() {
        val slow = SkinBox(100f, 80f, 30f, 30f)
        val ff = SkinBox(130f, 80f, 30f, 30f)
        val (slowTouch, ffTouch) = speedTouches(slow, ff, slow, ff)
        assertBox(slow, slowTouch)
        assertBox(ff, ffTouch)
    }
}
