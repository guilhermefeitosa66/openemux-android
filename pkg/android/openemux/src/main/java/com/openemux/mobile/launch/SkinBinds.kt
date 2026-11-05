package com.openemux.mobile.launch

import com.openemux.mobile.data.Console

/**
 * What a skin package's input names press: the RetroArch overlay bind of each name a Delta or Manic skin (and the
 * templates in tools/skins/templates) may use. The skin names the console's own buttons; the RetroPad has them where
 * data/Skin.kt puts them for that console. Kept apart from [ImportedSkinRenderer], which draws the package, so the
 * table can be tested and the templates checked against it.
 */
object SkinBinds {
    /** Where the skins' buttons sit on the RetroPad, for the consoles whose names differ (as in data/Skin.kt). */
    val CONSOLE_BINDS: Map<Console, Map<String, String>> = mapOf(
        Console.N64 to mapOf(
            "a" to "b", "b" to "y", "z" to "l2", "cUp" to "r_y_minus", "cDown" to "r_y_plus",
            "cLeft" to "r_x_minus", "cRight" to "r_x_plus",
        ),
        Console.MD to mapOf("a" to "y", "b" to "b", "c" to "a", "x" to "l", "y" to "x", "z" to "r", "mode" to "select"),
        Console.GC to mapOf("z" to "r", "l" to "l2", "r" to "r2"),
    )

    /**
     * Every name [bind] maps on some console: the generic ones plus each console's own. The OpenEmux skin format
     * (data/SkinFormat.kt) accepts these and adds its own few.
     */
    val NAMES: Set<String> = setOf(
        "up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select",
        "l1", "r1", "menu", "quickSave", "quickLoad", "fastForward", "toggleFastForward", "turbo", "screenshot",
        "restart", "reset",
    ) + CONSOLE_BINDS.values.flatMap { it.keys }

    /** [input] on [console] as a RetroArch overlay bind, or null for one left out ([hardcore] leaves the speed keys out). */
    fun bind(console: Console?, input: String, hardcore: Boolean): String? {
        CONSOLE_BINDS[console]?.get(input)?.let { return it }
        return when (input) {
            "up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select" -> input
            "l1" -> "l"
            "r1" -> "r"
            "menu" -> "menu_toggle"
            "quickSave" -> "save_state"
            "quickLoad" -> "load_state"
            "fastForward" -> if (hardcore) null else "hold_fast_forward"
            "toggleFastForward" -> if (hardcore) null else "toggle_fast_forward"
            "turbo" -> "turbo_fire_toggle"
            "screenshot" -> "screenshot"
            "restart", "reset" -> "reset"
            else -> null
        }
    }
}
