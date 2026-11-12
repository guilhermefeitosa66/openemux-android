package com.openemux.mobile.game

/** Which shape the pause sheet takes in a window. */
enum class PauseLayout {
    /** A phone upright: a bottom sheet under the game, dragged by its handle (issue 849). */
    PHONE,
    /** A phone on its side: the whole screen, in two columns. */
    LANDSCAPE,
    /** A tablet: a side panel on the right, the game still showing on the left. */
    PANEL,
    /** A half-open fold: the menu on the bottom half, the game on the top one. */
    TABLETOP,
}

/** The pause sheet's layout: tabletop wins over a tablet, which wins over a wide (landscape) window. */
fun pauseLayout(tablet: Boolean, tabletop: Boolean, wide: Boolean): PauseLayout = when {
    tabletop -> PauseLayout.TABLETOP
    tablet -> PauseLayout.PANEL
    wide -> PauseLayout.LANDSCAPE
    else -> PauseLayout.PHONE
}
