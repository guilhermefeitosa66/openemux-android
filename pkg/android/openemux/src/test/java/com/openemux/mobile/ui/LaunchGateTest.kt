package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchGateTest {
    @Test
    fun aRecreatedActivityIsOpenFromTheStart() {
        val gate = LaunchGate(fresh = false)
        assertTrue(gate.open)
        assertTrue(gate.update(barsVisible = true, multiWindow = false, elapsedMs = 0))
    }

    @Test
    fun aLaunchWaitsWhileTheBarsShow() {
        val gate = LaunchGate(fresh = true)
        assertFalse(gate.open)
        assertFalse(gate.update(barsVisible = true, multiWindow = false, elapsedMs = 100))
        assertFalse(gate.open)
    }

    @Test
    fun itOpensOnceTheBarsAreHidden() {
        val gate = LaunchGate(fresh = true)
        assertTrue(gate.update(barsVisible = false, multiWindow = false, elapsedMs = 100))
        assertTrue(gate.open)
    }

    @Test
    fun multiWindowOpensWithTheBarsStillShowing() {
        val gate = LaunchGate(fresh = true)
        assertTrue(gate.update(barsVisible = true, multiWindow = true, elapsedMs = 0))
    }

    @Test
    fun theCapOpensItWhateverTheBarsDo() {
        val gate = LaunchGate(fresh = true, capMs = 500)
        assertFalse(gate.update(barsVisible = true, multiWindow = false, elapsedMs = 499))
        assertTrue(gate.update(barsVisible = true, multiWindow = false, elapsedMs = 500))
        assertTrue(LaunchGate(fresh = true).update(barsVisible = true, multiWindow = false, elapsedMs = LaunchGate.CAP_MS))
    }

    @Test
    fun onceOpenItStaysOpen() {
        val gate = LaunchGate(fresh = true)
        gate.update(barsVisible = false, multiWindow = false, elapsedMs = 10)
        // The bars swiped back in later do not cover the app again.
        assertTrue(gate.update(barsVisible = true, multiWindow = false, elapsedMs = 20))
    }

    @Test
    fun theWindowStartsSingleAndOpen() {
        assertFalse(AppWindow.multiWindow)
        assertTrue(AppWindow.gate.open)
        assertTrue(AppWindow.firstLaunch)
    }

    @Test
    fun aNewLaunchIsFresh() {
        assertTrue(LaunchGate.fresh(restored = false, firstInProcess = true))
        assertTrue(LaunchGate.fresh(restored = false, firstInProcess = false))
    }

    @Test
    fun aRestoreAfterTheProcessDiedIsFresh() {
        // Reopened from Recents after the system killed the app: the splash shows, so the gate holds it.
        assertTrue(LaunchGate.fresh(restored = true, firstInProcess = true))
    }

    @Test
    fun aRecreationInTheSameProcessIsNot() {
        assertFalse(LaunchGate.fresh(restored = true, firstInProcess = false))
    }
}
