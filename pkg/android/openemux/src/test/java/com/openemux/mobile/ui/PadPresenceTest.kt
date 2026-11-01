package com.openemux.mobile.ui

import android.view.InputDevice
import com.openemux.mobile.game.PadInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadPresenceTest {
    private val pad = PadInfo(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD, false)
    private val stick = PadInfo(InputDevice.SOURCE_JOYSTICK, false)
    private val keyboard = PadInfo(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD, false)
    private val virtual = PadInfo(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD, true)

    @Test fun aResetCountsOnlyRealPads() {
        val p = PadPresence()
        p.reset(emptyMap())
        assertFalse(p.connected)
        p.reset(mapOf(1 to keyboard, -1 to virtual))
        assertFalse(p.connected)
        p.reset(mapOf(1 to keyboard, 5 to pad))
        assertTrue(p.connected)
        p.reset(mapOf(7 to stick))
        assertTrue(p.connected)
        p.reset(mapOf(1 to keyboard))
        assertFalse(p.connected)
    }

    @Test fun addChangeAndRemove() {
        val p = PadPresence()
        p.added(3, keyboard)
        assertFalse(p.connected)
        p.added(4, pad)
        assertTrue(p.connected)
        p.changed(4, keyboard)
        assertFalse(p.connected)
        p.changed(4, pad)
        assertTrue(p.connected)
        p.removed(4)
        assertFalse(p.connected)
    }

    @Test fun aPressFromALatePadCountsUntilItIsRemoved() {
        val p = PadPresence()
        p.reset(mapOf(9 to keyboard))
        p.padPress(9, pad)
        assertTrue(p.connected)
        // Still on the device list: a reset keeps it.
        p.reset(mapOf(9 to keyboard))
        assertTrue(p.connected)
        p.removed(9)
        assertFalse(p.connected)
        p.padPress(9, pad)
        // Gone from the device list: a reset drops it.
        p.reset(emptyMap())
        assertFalse(p.connected)
    }

    @Test fun pressesFromKeyboardsAndVirtualDevicesDoNotCount() {
        val p = PadPresence()
        p.padPress(-1, virtual)
        p.padPress(2, keyboard)
        assertFalse(p.connected)
    }

    @Test fun theDebugOverrideWinsAndClears() {
        assertTrue(padConnected(override = true, connected = false))
        assertFalse(padConnected(override = false, connected = true))
        assertTrue(padConnected(override = null, connected = true))
        assertFalse(padConnected(override = null, connected = false))
    }
}
