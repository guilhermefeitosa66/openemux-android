package com.openemux.mobile.launch

import com.openemux.mobile.data.Console

/**
 * What a skin package's input names press: the RetroArch overlay bind of each name a Delta or Manic skin (and the
 * templates in tools/skins/templates) may use. The skin names the console's own buttons; the RetroPad has them where
 * data/Skin.kt puts them for that console. Kept apart from [ImportedSkinRenderer], which draws the package, so the
 * table can be tested and the templates checked against it.
 */
object SkinBinds {
    /** The Famicom Disk System's disk-side key: the app's own input name (tools/skins), which other emulators ignore. */
    const val DISK_INPUT = "disk"

    /** Genesis Plus GX and PicoDrive's six-button pad: A on Y, B on B, C on A, X on L, Y on X, Z on R, Mode on Select. */
    private val SIX_BUTTON = mapOf("a" to "y", "b" to "b", "c" to "a", "x" to "l", "y" to "x", "z" to "r", "mode" to "select")

    /** Where the skins' buttons sit on the RetroPad, for the consoles whose names differ (as in data/Skin.kt). */
    val CONSOLE_BINDS: Map<Console, Map<String, String>> = mapOf(
        Console.N64 to mapOf(
            "a" to "b", "b" to "y", "z" to "l2", "cUp" to "r_y_minus", "cDown" to "r_y_plus",
            "cLeft" to "r_x_minus", "cRight" to "r_x_plus",
        ),
        Console.MD to SIX_BUTTON,
        // The 32X and the Mega-CD play with the Mega Drive's pad, on the same cores.
        Console.S32X to SIX_BUTTON,
        Console.MCD to SIX_BUTTON,
        // Beetle Saturn: A on B, B on A, C on R, X on Y, Y on X, Z on L; its L and R on L2 and R2.
        Console.SATURN to mapOf(
            "a" to "b", "b" to "a", "c" to "r", "x" to "y", "y" to "x", "z" to "l", "l" to "l2", "r" to "r2",
        ),
        Console.GC to mapOf("z" to "r", "l" to "l2", "r" to "r2"),
        // Nestopia turns the disk over on L: the skin's disk key, an input name of the app's own.
        Console.FDS to mapOf(DISK_INPUT to "l"),
    )

    /** The generic names, whatever the console, and their RetroArch overlay binds. */
    private val GENERIC_BINDS: Map<String, String> =
        listOf("up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select")
            .associateWith { it } + mapOf(
            "l1" to "l", "r1" to "r", "menu" to "menu_toggle", "quickSave" to "save_state", "quickLoad" to "load_state",
            "fastForward" to "hold_fast_forward", "toggleFastForward" to "toggle_fast_forward",
            "turbo" to "turbo_fire_toggle", "screenshot" to "screenshot", "restart" to "reset", "reset" to "reset",
        )

    /** The speed keys, which RetroAchievements hardcore leaves out. */
    private val SPEED_KEYS = setOf("fastForward", "toggleFastForward")

    /**
     * Every name [bind] maps on some console: the generic ones plus each console's own. The OpenEmux skin format
     * (data/SkinFormat.kt) accepts these and adds its own few.
     */
    val NAMES: Set<String> = GENERIC_BINDS.keys + CONSOLE_BINDS.values.flatMap { it.keys }

    /** [input] on [console] as a RetroArch overlay bind, or null for one left out ([hardcore] leaves the speed keys out). */
    fun bind(console: Console?, input: String, hardcore: Boolean): String? =
        CONSOLE_BINDS[console]?.get(input) ?: if (hardcore && input in SPEED_KEYS) null else GENERIC_BINDS[input]
}
