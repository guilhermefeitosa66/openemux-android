package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraStripeTest {
    @Test
    fun aGroupsFirstRowIsPlainAndEverySecondOneStriped() {
        assertFalse(zebraStriped(0))
        assertTrue(zebraStriped(1))
        assertFalse(zebraStriped(2))
        assertTrue(zebraStriped(3))
        assertTrue(zebraStriped(1001))
        assertFalse(zebraStriped(1000))
    }
}
