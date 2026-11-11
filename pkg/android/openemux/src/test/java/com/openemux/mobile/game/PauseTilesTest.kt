package com.openemux.mobile.game

import com.openemux.mobile.game.PauseTile.AUDIO
import com.openemux.mobile.game.PauseTile.CHANGE_SKIN
import com.openemux.mobile.game.PauseTile.EDIT_SKIN
import com.openemux.mobile.game.PauseTile.FAST_FORWARD
import com.openemux.mobile.game.PauseTile.RETROARCH_MENU
import com.openemux.mobile.game.PauseTile.SAVE_STATES
import com.openemux.mobile.game.PauseTile.SCREENS
import com.openemux.mobile.game.PauseTile.SCREENSHOT
import com.openemux.mobile.game.PauseTile.SHADER
import com.openemux.mobile.game.PauseTile.SLOW_MOTION
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseTilesTest {
    @Test fun theRowsComeAfterTheTilesThatWereAlreadyThere() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, SAVE_STATES, CHANGE_SKIN, EDIT_SKIN, RETROARCH_MENU),
            pauseTiles(nds = false, skin = true, tabletop = false),
        )
    }

    @Test fun anNdsGameHasItsScreensTileBeforeAudio() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, SCREENS, AUDIO, SAVE_STATES, CHANGE_SKIN, EDIT_SKIN, RETROARCH_MENU),
            pauseTiles(nds = true, skin = true, tabletop = false),
        )
    }

    @Test fun noSkinMeansNoSkinTiles() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, SAVE_STATES, RETROARCH_MENU),
            pauseTiles(nds = false, skin = false, tabletop = false),
        )
    }

    @Test fun aHalfOpenFoldNeverOffersTheSkinTiles() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, SCREENS, AUDIO, SAVE_STATES, RETROARCH_MENU),
            pauseTiles(nds = true, skin = true, tabletop = true),
        )
    }

    @Test fun onlyFastForwardSlowMotionAndAudioCanLightUp() {
        assertEquals(listOf(FAST_FORWARD, SLOW_MOTION, AUDIO), PauseTile.entries.filter { it.toggle })
    }

    @Test fun nineTilesWrapFourFourOneUprightAndFiveFourElsewhere() {
        val tiles = pauseTiles(nds = false, skin = true, tabletop = false)
        assertEquals(listOf(4, 4, 1), tiles.chunked(tileColumns(PauseLayout.PHONE)).map { it.size })
        assertEquals(listOf(5, 4), tiles.chunked(tileColumns(PauseLayout.LANDSCAPE)).map { it.size })
    }
}
