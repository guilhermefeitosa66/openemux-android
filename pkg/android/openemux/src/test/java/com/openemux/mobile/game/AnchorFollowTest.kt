package com.openemux.mobile.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a report that changes anchor mode is kept and told to the player (issue 841). */
class AnchorFollowTest {
    @Test fun theLaunchValueIsNotAChange() {
        assertFalse(AnchorFollow(true).report(true))
        assertFalse(AnchorFollow(false).report(false))
    }

    @Test fun eachFlipIsOneChange() {
        val follow = AnchorFollow(false)
        assertTrue(follow.report(true))
        assertFalse(follow.report(true))
        assertTrue(follow.report(false))
        assertFalse(follow.report(false))
    }
}
