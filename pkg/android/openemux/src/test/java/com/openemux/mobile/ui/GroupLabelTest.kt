package com.openemux.mobile.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupLabelTest {
    @Test
    fun startsAreTheRunningSumOfTheGroupSizes() {
        assertArrayEquals(intArrayOf(), GroupLabel.starts(emptyList()))
        assertArrayEquals(intArrayOf(0), GroupLabel.starts(listOf(5)))
        assertArrayEquals(intArrayOf(0, 3, 4, 9), GroupLabel.starts(listOf(3, 1, 5, 2)))
    }

    @Test
    fun groupAtFindsTheGroupOfAnItem() {
        val starts = intArrayOf(0, 3, 4, 9)
        assertEquals(0, GroupLabel.groupAt(0, starts))
        assertEquals(0, GroupLabel.groupAt(2, starts))
        // A group's first item belongs to it, the one before to the previous group.
        assertEquals(1, GroupLabel.groupAt(3, starts))
        assertEquals(2, GroupLabel.groupAt(4, starts))
        assertEquals(2, GroupLabel.groupAt(8, starts))
        assertEquals(3, GroupLabel.groupAt(9, starts))
        assertEquals(3, GroupLabel.groupAt(10, starts))
        assertEquals(0, GroupLabel.groupAt(7, intArrayOf(0)))
    }

    @Test
    fun aLabelRestsOverItsFirstGameAfterTheEdge() {
        assertEquals(200, GroupLabel.stickyX(200, null, 120, 32, 12))
        assertEquals(32, GroupLabel.stickyX(32, null, 120, 32, 12))
    }

    @Test
    fun aLabelSticksAtTheEdgeOnceItsFirstGameScrollsPast() {
        assertEquals(32, GroupLabel.stickyX(-150, null, 120, 32, 12))
        // The first game out of the row altogether.
        assertEquals(32, GroupLabel.stickyX(null, null, 120, 32, 12))
    }

    @Test
    fun theNextLabelPushesTheCurrentOneOut() {
        // Far enough: the next label leaves it alone.
        assertEquals(32, GroupLabel.stickyX(null, 400, 120, 32, 12))
        // Closer than the gap: it slides back, past the edge.
        assertEquals(32, GroupLabel.stickyX(null, 164, 120, 32, 12))
        assertEquals(-32, GroupLabel.stickyX(null, 100, 120, 32, 12))
        // A label resting after the edge is pushed as well.
        assertEquals(68, GroupLabel.stickyX(150, 200, 120, 32, 12))
    }

    @Test
    fun aLabelWiderThanTheRoomLeftGoesWhollyOut() {
        assertEquals(-292, GroupLabel.stickyX(null, 40, 320, 32, 12))
    }
}
