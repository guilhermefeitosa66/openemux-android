package com.openemux.mobile.game

import android.view.InputDevice

/** What decides whether an input device is a gamepad: its sources and whether it is virtual. */
class PadInfo(val sources: Int, val isVirtual: Boolean)

/**
 * Whether a gamepad is connected, for the skin's gamepad layout. Any real device with a gamepad or joystick source
 * counts, the built-in controls of an Android handheld too; a misread one is harmless, the gamepad layout keeps the
 * menu key on screen.
 */
object Gamepads {
    fun anyGamepad(devices: List<PadInfo>): Boolean = devices.any { d ->
        !d.isVirtual && (d.sources.has(InputDevice.SOURCE_GAMEPAD) || d.sources.has(InputDevice.SOURCE_JOYSTICK))
    }

    private fun Int.has(source: Int) = this and source == source

    fun connected(): Boolean = anyGamepad(
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.map { PadInfo(it.sources, it.isVirtual) },
    )
}
