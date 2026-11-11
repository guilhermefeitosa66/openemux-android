package com.openemux.mobile.data

import java.io.File

/** The numbered slots the in-game menu saves to, each ROM's current one (issue 857), and removing a saved state (issue 828). */
object SaveSlots {
    /** The slots a player can make current. [SCRATCH_SLOT] is out of it; a state already past the end stays listed and loadable. */
    val RANGE = 0..99

    /** The current slot of a ROM that never had one (issue 857). */
    const val DEFAULT = 0

    /**
     * Where a ROM's current slot is kept (issue 857): `<stem>.slot` beside its states, so the game's process and the
     * library's both reach it, a backup of the states carries it and no state listing sees it (they all read `<stem>.state…`).
     */
    fun currentFile(statesDir: File, stem: String) = File(statesDir, "$stem.slot")

    /** The slot the menu opens on and Save, Load, the strip and the controller use: [DEFAULT] when none was ever chosen. */
    fun current(statesDir: File, stem: String): Int =
        runCatching { currentFile(statesDir, stem).readText().trim().toInt() }.getOrNull()?.takeIf { it in RANGE } ?: DEFAULT

    /**
     * Makes [slot] the ROM's current slot; whether it was kept. One outside [RANGE] (the auto state, the scratch slot)
     * changes nothing.
     */
    fun setCurrent(statesDir: File, stem: String, slot: Int): Boolean {
        if (slot !in RANGE) return false
        val target = currentFile(statesDir, stem)
        // Through a temporary file: the other process never reads half a number.
        val tmp = File(statesDir, ".${target.name}.tmp")
        val kept = runCatching {
            statesDir.mkdirs()
            tmp.writeText("$slot\n")
            tmp.renameTo(target)
        }.getOrDefault(false)
        if (!kept) tmp.delete()
        return kept
    }

    /** Removes the state in [state] and its picture; whether the state is gone. A state that stays keeps its picture. */
    fun delete(state: File): Boolean {
        if (state.exists() && !state.delete()) return false
        File(state.path + ".png").delete()
        return true
    }
}
