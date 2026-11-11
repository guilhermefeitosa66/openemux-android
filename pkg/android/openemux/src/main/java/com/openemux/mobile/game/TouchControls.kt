package com.openemux.mobile.game

import com.openemux.mobile.launch.OverlayName
import java.io.File

/**
 * The touch controls shown over a detected gamepad (issue 865): a phone may report a built-in device as a joystick, and
 * the gamepad layout leaves it with no on-screen pad. The player brings it back with the gamepad layout's key and turns
 * it off from the pause sheet. The choice is kept per controller: by the descriptors of the devices counted as gamepads
 * when it was made, so the false pad needs no tap on every launch, while a real controller plugged in later still gets
 * the gamepad layout.
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
 * Where [TouchControls]' choice lives: a file of descriptors, one per line, read afresh each time. The library and the
 * game run in two processes, so a cached preference would go stale in one of them; a write replaces the file whole.
 */
class TouchControlsStore(private val file: File) {
    fun read(): Set<String> = runCatching { file.readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun write(descriptors: Set<String>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(descriptors.sorted().joinToString("") { "$it\n" })
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    /** Whether the touch controls are forced for the [pads] detected now. */
    fun forced(pads: Set<String>): Boolean = TouchControls.forced(pads, read())

    /** Turns them [on] or off for the [pads] detected now. */
    fun set(pads: Set<String>, on: Boolean) = write(TouchControls.set(read(), pads, on))

    companion object {
        fun of(filesDir: File) = TouchControlsStore(File(filesDir, "touch_controls"))
    }
}
