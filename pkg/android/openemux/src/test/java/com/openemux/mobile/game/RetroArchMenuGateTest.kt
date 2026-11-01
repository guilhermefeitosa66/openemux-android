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
        assertTrue(gate.arm(gate.request()))
        assertTrue(gate.consume())
        assertFalse(gate.consume())
    }

    @Test
    fun aDisarmedGateLetsNothingThrough() {
        val gate = RetroArchMenuGate()
        val request = gate.request()
        gate.arm(request)
        gate.disarm(request)
        assertFalse(gate.consume())
    }

    @Test
    fun theGateCanBeArmedAgainAfterUse() {
        val gate = RetroArchMenuGate()
        gate.arm(gate.request())
        gate.consume()
        gate.arm(gate.request())
        assertTrue(gate.consume())
    }

    @Test
    fun aCancelledRequestCannotArm() {
        val gate = RetroArchMenuGate()
        val request = gate.request()
        gate.cancel()
        assertFalse(gate.arm(request))
        assertFalse(gate.consume())
    }

    @Test
    fun cancelDisarms() {
        val gate = RetroArchMenuGate()
        gate.arm(gate.request())
        gate.cancel()
        assertFalse(gate.consume())
    }

    @Test
    fun anOlderRequestNeitherArmsNorDisarmsANewerOne() {
        val gate = RetroArchMenuGate()
        val older = gate.request()
        val newer = gate.request()
        assertFalse(gate.arm(older))
        assertTrue(gate.arm(newer))
        gate.disarm(older)
        assertTrue(gate.consume())
    }
}
