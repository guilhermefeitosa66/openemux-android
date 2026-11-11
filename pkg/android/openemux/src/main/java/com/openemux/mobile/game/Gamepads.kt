package com.openemux.mobile.game

import android.view.InputDevice

/**
 * What decides whether an input device is a gamepad: its sources and whether it is virtual. Its [descriptor] (stable
 * across reconnections and reboots) and [name] say which one it is, for the remembered touch controls (issue 865) and
 * the log.
 */
class PadInfo(val sources: Int, val isVirtual: Boolean, val descriptor: String = "", val name: String = "") {
    /** For the log: which device the detection counted, so a report says what fooled it. */
    fun describe(): String = "\"$name\" descriptor=$descriptor sources=0x${Integer.toHexString(sources)}"
}

/**
 * Whether a gamepad is connected, for the skin's gamepad layout. Any real device with a gamepad or joystick source
 * counts, the built-in controls of an Android handheld too; a misread one is harmless, the gamepad layout keeps the
 * menu key on screen and its touch controls key brings the on-screen pad back (issue 865).
 */
object Gamepads {
    fun anyGamepad(devices: List<PadInfo>): Boolean = devices.any(::isPad)

    /** The devices among [devices] counted as gamepads. */
    fun pads(devices: List<PadInfo>): List<PadInfo> = devices.filter(::isPad)

    private fun isPad(d: PadInfo) =
        !d.isVirtual && (d.sources.has(InputDevice.SOURCE_GAMEPAD) || d.sources.has(InputDevice.SOURCE_JOYSTICK))

    private fun Int.has(source: Int) = this and source == source

    /** Every input device there is now. */
    fun devices(): List<PadInfo> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        .map { PadInfo(it.sources, it.isVirtual, it.descriptor.orEmpty(), it.name.orEmpty()) }
}
