package com.openemux.mobile.game

import com.openemux.mobile.game.PauseTile.AUDIO
import com.openemux.mobile.game.PauseTile.CHANGE_SKIN
import com.openemux.mobile.game.PauseTile.EDIT_SKIN
import com.openemux.mobile.game.PauseTile.FAST_FORWARD
import com.openemux.mobile.game.PauseTile.RETROARCH_MENU
import com.openemux.mobile.game.PauseTile.SCREENS
import com.openemux.mobile.game.PauseTile.SCREENSHOT
import com.openemux.mobile.game.PauseTile.SHADER
import com.openemux.mobile.game.PauseTile.SLOW_MOTION
import com.openemux.mobile.game.PauseTile.TOUCH_CONTROLS
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseTilesTest {
    @Test fun theRowsComeAfterTheTilesThatWereAlreadyThere() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, CHANGE_SKIN, EDIT_SKIN, RETROARCH_MENU),
            pauseTiles(nds = false, skin = true, tabletop = false),
        )
    }

    @Test fun anNdsGameHasItsScreensTileBeforeAudio() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, SCREENS, AUDIO, CHANGE_SKIN, EDIT_SKIN, RETROARCH_MENU),
            pauseTiles(nds = true, skin = true, tabletop = false),
        )
    }

    @Test fun noSkinMeansNoSkinTiles() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, RETROARCH_MENU),
            pauseTiles(nds = false, skin = false, tabletop = false),
        )
    }

    @Test fun aHalfOpenFoldNeverOffersTheSkinTiles() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, SCREENS, AUDIO, RETROARCH_MENU),
            pauseTiles(nds = true, skin = true, tabletop = true),
        )
    }

    @Test fun aDetectedGamepadAddsTouchControlsBeforeTheRetroArchMenu() {
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, CHANGE_SKIN, EDIT_SKIN, TOUCH_CONTROLS, RETROARCH_MENU),
            pauseTiles(nds = false, skin = true, tabletop = false, touch = true),
        )
        // On a half-open fold too: its row has the key.
        assertEquals(
            listOf(FAST_FORWARD, SLOW_MOTION, SCREENSHOT, SHADER, AUDIO, TOUCH_CONTROLS, RETROARCH_MENU),
            pauseTiles(nds = false, skin = true, tabletop = true, touch = true),
        )
    }

    @Test fun onlyTheTogglesCanLightUp() {
        assertEquals(listOf(FAST_FORWARD, SLOW_MOTION, AUDIO, TOUCH_CONTROLS), PauseTile.entries.filter { it.toggle })
    }

    @Test fun saveStatesHasNoTileSinceSeeAllOpensTheList() {
        // Issue 867: "See all" under the slot slider opens the same list.
        assertEquals(PauseTile.entries.map { it.name }.filter { "STATE" in it }, emptyList<String>())
    }

    @Test fun eightTilesWrapFourFourUprightAndFiveThreeElsewhere() {
        val tiles = pauseTiles(nds = false, skin = true, tabletop = false)
        assertEquals(listOf(4, 4), tiles.chunked(tileColumns(PauseLayout.PHONE)).map { it.size })
        assertEquals(listOf(5, 3), tiles.chunked(tileColumns(PauseLayout.LANDSCAPE)).map { it.size })
    }
}
