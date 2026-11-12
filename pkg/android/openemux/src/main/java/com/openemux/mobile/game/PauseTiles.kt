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
    CHANGE_SKIN,
    EDIT_SKIN,
    /** Only while a gamepad is detected: the on-screen controls over it (issue 865). */
    TOUCH_CONTROLS(toggle = true),
    RETROARCH_MENU,
}

/**
 * The tiles the pause sheet shows, in reading order (issue 862): every action is a tile, the ones that were list rows
 * after the rest. Save states has no tile: "See all" under the slot slider opens that list (issue 867). [nds] adds the screens' layout before Audio; [skin] (a game drawn with a skin) adds Change skin and
 * Edit skin, which a half-open fold ([tabletop]) never offers; [touch] (a gamepad detected) adds Touch controls before
 * the RetroArch menu.
 */
fun pauseTiles(nds: Boolean, skin: Boolean, tabletop: Boolean, touch: Boolean = false): List<PauseTile> = buildList {
    add(PauseTile.FAST_FORWARD)
    add(PauseTile.SLOW_MOTION)
    add(PauseTile.SCREENSHOT)
    add(PauseTile.SHADER)
    if (nds) add(PauseTile.SCREENS)
    add(PauseTile.AUDIO)
    if (skin && !tabletop) {
        add(PauseTile.CHANGE_SKIN)
        add(PauseTile.EDIT_SKIN)
    }
    if (touch) add(PauseTile.TOUCH_CONTROLS)
    add(PauseTile.RETROARCH_MENU)
}

/**
 * The pause sheet's Touch controls tile (issue 865), offered while a gamepad is detected: [on] while the on-screen
 * controls are shown over it; [onChange] turns them on or off.
 */
class TouchToggle(val on: Boolean, val onChange: (Boolean) -> Unit)
