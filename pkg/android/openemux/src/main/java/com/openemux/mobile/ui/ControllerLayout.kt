package com.openemux.mobile.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings › Controllers › Interface mode: when the app shows the gamepad layout (the focus ring, the hint bar, the
 * larger targets). "auto" (Automatic) while a controller is connected, "always" (Controller) for handhelds with
 * built-in controls, "never" (Touch screen) for the touch layout, games included (issue 873).
 */
object ControllerLayoutPref {
    const val AUTO = "auto"
    const val ALWAYS = "always"
    const val NEVER = "never"

    private const val KEY = "controller_layout"

    var value by mutableStateOf(AUTO)
        private set

    /** A stored value, or [AUTO] for none or one this version does not know. */
    fun parse(stored: String?): String = when (stored) {
        ALWAYS, NEVER -> stored
        else -> AUTO
    }

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    /** The stored choice, without touching [value]: for a game launch off the main thread. */
    fun read(context: Context): String = parse(prefs(context).getString(KEY, AUTO))

    fun load(context: Context) {
        value = read(context)
    }

    fun set(context: Context, layout: String) {
        value = parse(layout)
        prefs(context).edit().putString(KEY, value).apply()
    }
}

/** Whether the gamepad layout is on, from the setting [pref] and whether a controller is [connected]. */
fun padLayoutOn(pref: String, connected: Boolean): Boolean = when (pref) {
    ControllerLayoutPref.ALWAYS -> true
    ControllerLayoutPref.NEVER -> false
    else -> connected
}

/**
 * Whether the gamepad look shows: always in the gamepad layout, and otherwise after a pad press until the next
 * touch ([lastPad]), as the touch layout always did.
 */
fun gamepadLook(pref: String, connected: Boolean, lastPad: Boolean): Boolean = padLayoutOn(pref, connected) || lastPad

/**
 * Whether a game shows the gamepad skin: a pad is [detected] and the player did not bring the touch controls back for
 * it ([forced], issue 865), unless the interface mode [pref] is Touch screen, which keeps the on-screen controls for
 * a phone that reports a pad it does not have (issue 873).
 */
fun gamepadSkin(pref: String, detected: Boolean, forced: Boolean): Boolean =
    pref != ControllerLayoutPref.NEVER && detected && !forced
