package com.openemux.mobile.game

import com.openemux.mobile.ui.Icons
import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MuteTileTest {
    @Test fun withSoundTheTileOffersToMute() {
        assertEquals(MuteTile(R.string.game_mute, Icons.Audio, warn = false), MuteTile.of(muted = false))
    }

    @Test fun mutedTheTileOffersToUnmuteInTheWarningTone() {
        assertEquals(MuteTile(R.string.game_unmute, Icons.Mute, warn = true), MuteTile.of(muted = true))
    }
}
