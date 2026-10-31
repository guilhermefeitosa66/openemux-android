package com.openemux.mobile.game

/**
 * Lets one menu request through to RetroArch's own menu. Every request the
 * engine makes goes to the pause sheet ([GameActivity.onExternalMenuRequested]);
 * the sheet's "RetroArch menu" row arms this gate, and the next request is
 * answered "not handled", so the engine opens its menu. Consumed once: the
 * request after that opens the pause sheet again.
 *
 * The row asks for a [request] first and arms with it later, once the game
 * plays again. [cancel] (a new sheet, the activity leaving) voids every request
 * still waiting, so a stale one can no longer arm; [disarm] with a request only
 * takes back that request's own arming, never a newer one's.
 *
 * Requested and armed from a background thread, consumed on the engine thread.
 */
class RetroArchMenuGate {
    private var current = 0
    private var armed = false

    /** A new request; any older one still waiting can no longer arm. */
    @Synchronized
    fun request(): Int = ++current

    /** Arms for [request] if it is still the current one; false when it was voided. */
    @Synchronized
    fun arm(request: Int): Boolean {
        if (request != current) return false
        armed = true
        return true
    }

    /** True once after [arm], then false until armed again. */
    @Synchronized
    fun consume(): Boolean {
        val was = armed
        armed = false
        return was
    }

    /** Takes back [request]'s arming, if it is still the current request. */
    @Synchronized
    fun disarm(request: Int) {
        if (request == current) armed = false
    }

    /** Voids every waiting request and disarms. */
    @Synchronized
    fun cancel() {
        current++
        armed = false
    }
}
