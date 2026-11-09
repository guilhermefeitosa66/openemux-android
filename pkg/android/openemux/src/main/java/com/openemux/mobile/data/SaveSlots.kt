package com.openemux.mobile.data

import java.io.File

/** The numbered slots the in-game menu saves to, and removing a saved state (issue 828). */
object SaveSlots {
    /** What the arrows cover. [SCRATCH_SLOT] is out of it; a state already past the end stays listed and loadable. */
    val RANGE = 0..99

    /**
     * The slot the menu opens on, of the slots in [used]: the one after the highest in use, so a plain "Save" keeps
     * overwriting nothing. Past the end, the lowest free one; with every slot taken, the first numbered slot.
     */
    fun opening(used: Collection<Int>): Int {
        val next = (used.filter { it > 0 }.maxOrNull() ?: 0) + 1
        if (next in RANGE) return next
        return (1..RANGE.last).firstOrNull { it !in used } ?: if (RANGE.first !in used) RANGE.first else 1
    }

    /** [slot] moved by [delta], wrapping at both ends of [RANGE]. */
    fun step(slot: Int, delta: Int): Int {
        val size = RANGE.last - RANGE.first + 1
        return Math.floorMod(slot - RANGE.first + delta, size) + RANGE.first
    }

    /** Removes the state in [state] and its picture; whether the state is gone. A state that stays keeps its picture. */
    fun delete(state: File): Boolean {
        if (state.exists() && !state.delete()) return false
        File(state.path + ".png").delete()
        return true
    }
}
