package com.openemux.mobile.game

import com.openemux.mobile.launch.OverlayName
import java.io.File

/**
 * The touch controls turned on or off by hand (issue 865). A controller connected shows the gamepad layout and one gone
 * brings the on-screen controls back; the gamepad layout's key and the pause sheet's tile override that, with or
 * without a controller, until the next connect or disconnect. A phone may report a built-in device as a joystick, so
 * the touch controls brought back over a pad are kept per controller: by the descriptors of the devices counted as
 * gamepads when it was done, so the false pad needs no tap on every launch, while a real controller plugged in later
 * still gets the gamepad layout. With no pad, the gamepad layout picked is kept per console.
 */
object TouchControls {
    /** The pads the debug broadcast says are connected, which have no device of their own. */
    const val DEBUG_PAD = "debug"

    /** The pads a debug broadcast's "pads" extra names ("a,b"); null for none, the one [DEBUG_PAD] then. */
    fun debugPads(extra: String?): Set<String>? = extra?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()?.ifEmpty { null }

    /** What each of the [pads] counted as gamepads is remembered by: its descriptor, else its name. */
    fun keys(pads: List<PadInfo>): Set<String> = pads.map { it.descriptor.ifEmpty { it.name } }.filter { it.isNotEmpty() }.toSet()

    /** Whether the touch controls are forced for the [pads] (descriptors) detected now, from the [stored] choice. */
    fun forced(pads: Set<String>, stored: Set<String>): Boolean = pads.isNotEmpty() && stored.containsAll(pads)

    /** The choice to store when the player turns the touch controls [on] or off with [pads] detected. */
    fun set(stored: Set<String>, pads: Set<String>, on: Boolean): Set<String> = if (on) stored + pads else stored - pads

    /**
     * Whether the skin has a gamepad page to turn the touch controls off to: every page has one, unless drawing a
     * package skin's failed. Without one the tile is not offered, so it never leaves the game with no controls.
     */
    fun offered(overlays: Collection<String>): Boolean = overlays.any { OverlayName.parse(it)?.gamepad == true }

    /**
     * Whether the overlay going from [previous] to [current] is the touch controls key: from an expanded gamepad page to
     * a touch page of the same orientation while the app still [wantGamepad]. The app itself only switches to a touch
     * page once it no longer wants the gamepad, and the engine's auto-rotate changes the orientation.
     */
    fun tapped(previous: String?, current: String, wantGamepad: Boolean): Boolean {
        if (!wantGamepad || previous == null) return false
        val from = OverlayName.parse(previous) ?: return false
        val to = OverlayName.parse(current) ?: return false
        return from.gamepad && !from.collapsed && !to.gamepad && from.orientation == to.orientation
    }
}

/**
 * Where [TouchControls]' choice lives: a file of keys, one per line, read afresh each time. [of]: the descriptors the
 * touch controls were brought back for over a pad; [consoles]: the consoles the gamepad layout was picked for with none. The library and the
 * game run in two processes, so a cached preference would go stale in one of them; a write replaces the file whole.
 */
class TouchControlsStore(private val file: File) {
    fun read(): Set<String> = runCatching { file.readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun write(keys: Set<String>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(keys.sorted().joinToString("") { "$it\n" })
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    /** Whether the touch controls are forced for the [pads] detected now. */
    fun forced(pads: Set<String>): Boolean = TouchControls.forced(pads, read())

    /** Turns them [on] or off for the [pads] detected now. */
    fun set(pads: Set<String>, on: Boolean) = write(TouchControls.set(read(), pads, on))

    /** Whether [key] is kept: for [consoles], whether the console plays on the gamepad layout with no pad. */
    operator fun contains(key: String): Boolean = key in read()

    companion object {
        fun of(filesDir: File) = TouchControlsStore(File(filesDir, "touch_controls"))

        fun consoles(filesDir: File) = TouchControlsStore(File(filesDir, "gamepad_layout_consoles"))
    }
}
