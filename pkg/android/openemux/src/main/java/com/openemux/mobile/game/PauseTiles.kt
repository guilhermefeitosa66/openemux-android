package com.openemux.mobile.game

/** An action tile of the pause sheet's main page. */
enum class PauseTile(
    /** A toggle lights up while it is on; every other tile is an action and never does (issue 862). */
    val toggle: Boolean = false,
) {
    FAST_FORWARD(toggle = true),
    SLOW_MOTION(toggle = true),
    SCREENSHOT,
    SHADER,
    /** NDS only: the screens' layout. */
    SCREENS,
    AUDIO(toggle = true),
    SAVE_STATES,
    CHANGE_SKIN,
    EDIT_SKIN,
    RETROARCH_MENU,
}

/**
 * The tiles the pause sheet shows, in reading order (issue 862): every action is a tile, the ones that were list rows
 * after the rest. [nds] adds the screens' layout before Audio; [skin] (a game drawn with a skin) adds Change skin and
 * Edit skin, which a half-open fold ([tabletop]) never offers.
 */
fun pauseTiles(nds: Boolean, skin: Boolean, tabletop: Boolean): List<PauseTile> = buildList {
    add(PauseTile.FAST_FORWARD)
    add(PauseTile.SLOW_MOTION)
    add(PauseTile.SCREENSHOT)
    add(PauseTile.SHADER)
    if (nds) add(PauseTile.SCREENS)
    add(PauseTile.AUDIO)
    add(PauseTile.SAVE_STATES)
    if (skin && !tabletop) {
        add(PauseTile.CHANGE_SKIN)
        add(PauseTile.EDIT_SKIN)
    }
    add(PauseTile.RETROARCH_MENU)
}
