package com.openemux.mobile.game

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadsTest {
    @Test fun aRealGamepadOrJoystickCounts() {
        assertTrue(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD, false))))
        assertTrue(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_JOYSTICK, false))))
        assertTrue(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_KEYBOARD, false), PadInfo(InputDevice.SOURCE_JOYSTICK, false))))
    }

    @Test fun keyboardsTouchscreensAndVirtualDevicesDoNot() {
        assertFalse(Gamepads.anyGamepad(emptyList()))
        assertFalse(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_KEYBOARD, false))))
        assertFalse(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_TOUCHSCREEN, false))))
        // The virtual keyboard says it is everything.
        assertFalse(Gamepads.anyGamepad(listOf(PadInfo(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK, true))))
    }
}
