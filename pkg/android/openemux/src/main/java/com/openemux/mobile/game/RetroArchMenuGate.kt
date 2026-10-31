package com.openemux.mobile.game

/**
 * Lets one menu request through to RetroArch's own menu. Every request the
 * engine makes goes to the pause sheet ([GameActivity.onExternalMenuRequested]);
 * the sheet's "RetroArch menu" row arms this gate, and the next request is
 * answered "not handled", so the engine opens its menu. Consumed once: the
 * request after that opens the pause sheet again.
 *
 * Armed from a background thread, consumed on the engine thread.
 */
class RetroArchMenuGate {
    @Volatile private var armed = false

    fun arm() {
        armed = true
    }

    /** True once after [arm], then false until armed again. */
    @Synchronized
    fun consume(): Boolean {
        val was = armed
        armed = false
        return was
    }

    fun disarm() {
        armed = false
    }
}
