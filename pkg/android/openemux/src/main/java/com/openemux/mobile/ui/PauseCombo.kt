package com.openemux.mobile.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.retroarch.R

/**
 * Settings › Controllers › Pause with the controller (issue 924): the controller combination that opens the pause
 * sheet while a game plays, RetroArch's `input_menu_toggle_gamepad_combo`. The engine's menu toggle reaches the
 * sheet the way the skin's menu key does (android_app_external_menu), so the engine needs nothing else.
 */
object PauseComboPref {
    const val HOLD_START = "hold_start"
    const val START_SELECT = "start_select"
    const val L3_R3 = "l3_r3"
    const val OFF = "off"

    private const val KEY = "pause_combo"

    var value by mutableStateOf(HOLD_START)
        private set

    /** A stored value, or [HOLD_START] for none or one this version does not know. */
    fun parse(stored: String?): String = when (stored) {
        START_SELECT, L3_R3, OFF -> stored
        else -> HOLD_START
    }

    /** RetroArch's input_combo_type for the choice (input/input_defines.h), as the generated config writes it. */
    fun engineCombo(id: String?): String = when (parse(id)) {
        HOLD_START -> "7"
        START_SELECT -> "4"
        L3_R3 -> "2"
        else -> "0"
    }

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    /** The stored choice, without touching [value]: for a game launch off the main thread. */
    fun read(context: Context): String = parse(prefs(context).getString(KEY, HOLD_START))

    fun load(context: Context) {
        value = read(context)
    }

    fun set(context: Context, combo: String) {
        value = parse(combo)
        prefs(context).edit().putString(KEY, value).apply()
    }
}

/** The one-time in-game hint naming the combination, or null when it is off. */
fun pauseComboHint(id: String?): Int? = when (PauseComboPref.parse(id)) {
    PauseComboPref.HOLD_START -> R.string.game_toast_pause_hold_start
    PauseComboPref.START_SELECT -> R.string.game_toast_pause_start_select
    PauseComboPref.L3_R3 -> R.string.game_toast_pause_l3_r3
    else -> null
}
