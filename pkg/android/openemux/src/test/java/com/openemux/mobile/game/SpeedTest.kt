package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTest {
    @Test fun aTileTurnsItsSpeedOnOrBackToNormal() {
        assertEquals(Speed.SLOW, Speed.NORMAL.tap(Speed.SLOW))
        assertEquals(Speed.FAST, Speed.NORMAL.tap(Speed.FAST))
        assertEquals(Speed.NORMAL, Speed.SLOW.tap(Speed.SLOW))
        assertEquals(Speed.NORMAL, Speed.FAST.tap(Speed.FAST))
    }

    @Test fun turningOneOnTurnsTheOtherOff() {
        assertEquals(Speed.SLOW, Speed.FAST.tap(Speed.SLOW))
        assertEquals(Speed.FAST, Speed.SLOW.tap(Speed.FAST))
    }

    @Test fun theEngineStateReadsAsOneSpeed() {
        assertEquals(Speed.NORMAL, Speed.of(fast = false, slow = false))
        assertEquals(Speed.FAST, Speed.of(fast = true, slow = false))
        assertEquals(Speed.SLOW, Speed.of(fast = false, slow = true))
        assertEquals(Speed.FAST, Speed.of(fast = true, slow = true))
    }

    @Test fun onlyTheToggleThatDiffersIsSent() {
        // From normal speed.
        assertEquals(listOf("SLOWMOTION"), Speed.SLOW.commandsFrom(fast = false, slow = false))
        assertEquals(listOf("FAST_FORWARD"), Speed.FAST.commandsFrom(fast = false, slow = false))
        assertEquals(emptyList<String>(), Speed.NORMAL.commandsFrom(fast = false, slow = false))
        // Across: the engine turns the other one off by itself.
        assertEquals(listOf("SLOWMOTION"), Speed.SLOW.commandsFrom(fast = true, slow = false))
        assertEquals(listOf("FAST_FORWARD"), Speed.FAST.commandsFrom(fast = false, slow = true))
        // Back to normal.
        assertEquals(listOf("SLOWMOTION"), Speed.NORMAL.commandsFrom(fast = false, slow = true))
        assertEquals(listOf("FAST_FORWARD"), Speed.NORMAL.commandsFrom(fast = true, slow = false))
        assertEquals(listOf("FAST_FORWARD", "SLOWMOTION"), Speed.NORMAL.commandsFrom(fast = true, slow = true))
        // Already there.
        assertEquals(emptyList<String>(), Speed.SLOW.commandsFrom(fast = false, slow = true))
        assertEquals(emptyList<String>(), Speed.FAST.commandsFrom(fast = true, slow = false))
    }
}
