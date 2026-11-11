package com.openemux.mobile.game

import androidx.annotation.StringRes
import com.openemux.mobile.ui.Icons
import com.retroarch.R

/**
 * How the pause sheet draws its Audio tile (issue 861): the label says what a tap does, and a muted game shows a
 * crossed-out speaker on a warning-toned tile rather than the accent the other "on" tiles use.
 */
data class MuteTile(@StringRes val label: Int, val icon: String, val warn: Boolean) {
    companion object {
        fun of(muted: Boolean): MuteTile =
            if (muted) MuteTile(R.string.game_unmute, Icons.Mute, warn = true)
            else MuteTile(R.string.game_mute, Icons.Audio, warn = false)
    }
}
