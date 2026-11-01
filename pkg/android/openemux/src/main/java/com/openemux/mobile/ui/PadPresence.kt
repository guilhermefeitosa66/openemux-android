package com.openemux.mobile.ui

import com.openemux.mobile.game.Gamepads
import com.openemux.mobile.game.PadInfo

/**
 * Which controllers are connected: the devices the input manager reports as gamepads, plus the ones a press
 * showed to be one before they were reported (some pads report late), until they are removed. Keyboards, the
 * IME and virtual devices never count.
 */
class PadPresence {
    private val reported = mutableSetOf<Int>()
    private val pressed = mutableSetOf<Int>()

    val connected: Boolean get() = reported.isNotEmpty() || pressed.isNotEmpty()

    /** Starts over from the devices there are now, [devices] by id; a pad a press found stays while it is there. */
    fun reset(devices: Map<Int, PadInfo>) {
        reported.clear()
        devices.forEach { (id, info) -> if (isPad(info)) reported += id }
        pressed.retainAll(devices.keys)
    }

    fun added(id: Int, info: PadInfo) {
        if (isPad(info)) reported += id
    }

    fun changed(id: Int, info: PadInfo) {
        if (isPad(info)) reported += id else reported -= id
    }

    fun removed(id: Int) {
        reported -= id
        pressed -= id
    }

    /** A key from device [id]: a real pad the input manager did not report counts as connected from now on. */
    fun padPress(id: Int, info: PadInfo) {
        if (isPad(info)) pressed += id
    }

    private fun isPad(info: PadInfo) = Gamepads.anyGamepad(listOf(info))
}

/** Whether a controller counts as connected: a debug build's [override] wins over what the devices say. */
fun padConnected(override: Boolean?, connected: Boolean): Boolean = override ?: connected
