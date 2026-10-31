package com.openemux.mobile.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchMenuGateTest {

    @Test
    fun aGateNeverArmedLetsNothingThrough() {
        assertFalse(RetroArchMenuGate().consume())
    }

    @Test
    fun anArmedGateLetsExactlyOneRequestThrough() {
        val gate = RetroArchMenuGate()
        gate.arm()
        assertTrue(gate.consume())
        assertFalse(gate.consume())
    }

    @Test
    fun aDisarmedGateLetsNothingThrough() {
        val gate = RetroArchMenuGate()
        gate.arm()
        gate.disarm()
        assertFalse(gate.consume())
    }

    @Test
    fun theGateCanBeArmedAgainAfterUse() {
        val gate = RetroArchMenuGate()
        gate.arm()
        gate.consume()
        gate.arm()
        assertTrue(gate.consume())
    }
}
