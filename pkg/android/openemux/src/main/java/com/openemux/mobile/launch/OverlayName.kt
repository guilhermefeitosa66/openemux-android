package com.openemux.mobile.launch

/**
 * The name of one overlay page of a skin, the one place that reads and writes it. A touch page is
 * "[keypad-]portrait|landscape[-tabletop][@<nds layout>]", as before the gamepad layout; the gamepad layout
 * ([gamepad], "pad-") and the collapsed app controls ([collapsed], "min-") are prefixes, in that order:
 * "min-pad-landscape@side". Every name keeps "portrait" or "landscape", which the engine's auto-rotate matches.
 */
data class OverlayName(
    val portrait: Boolean,
    val tabletop: Boolean = false,
    val keypad: Boolean = false,
    /** The NDS screen layout the page shows, its "@<id>" suffix. */
    val layout: String? = null,
    val gamepad: Boolean = false,
    val collapsed: Boolean = false,
) {
    /** What the NDS code keys its layouts by: "portrait", "landscape" or [TABLETOP]. */
    val orientation: String get() = if (tabletop) TABLETOP else if (portrait) "portrait" else "landscape"

    /** The touch page this one is drawn from: the same page with the app controls expanded and no gamepad. */
    val touch: OverlayName get() = copy(gamepad = false, collapsed = false)

    fun withGamepad(on: Boolean) = copy(gamepad = on)

    fun withCollapsed(on: Boolean) = copy(collapsed = on)

    /** The same layout and mode as [other] (gamepad, collapsed). */
    fun withModeOf(other: OverlayName) = copy(gamepad = other.gamepad, collapsed = other.collapsed)

    override fun toString(): String = buildString {
        if (collapsed) append(COLLAPSED)
        if (gamepad) append(GAMEPAD)
        if (keypad) append(KEYPAD)
        append(if (tabletop) TABLETOP else if (portrait) "portrait" else "landscape")
        layout?.let { append('@').append(it) }
    }

    companion object {
        const val COLLAPSED = "min-"
        const val GAMEPAD = "pad-"
        const val KEYPAD = "keypad-"

        /** Null for a name no skin of the app writes (a tabletop page is always portrait). */
        fun parse(name: String): OverlayName? {
            var rest = name
            fun take(prefix: String) = rest.startsWith(prefix).also { if (it) rest = rest.removePrefix(prefix) }
            val collapsed = take(COLLAPSED)
            val gamepad = take(GAMEPAD)
            val keypad = take(KEYPAD)
            val layout = rest.substringAfter('@', "").takeIf { '@' in rest }
            if (layout != null && layout.isEmpty()) return null
            val base = OverlayName(portrait = true, keypad = keypad, layout = layout, gamepad = gamepad, collapsed = collapsed)
            return when (rest.substringBefore('@')) {
                "portrait" -> base
                "landscape" -> base.copy(portrait = false)
                TABLETOP -> base.copy(tabletop = true)
                else -> null
            }
        }

        /** [name] with [change] applied, or [name] itself when it is not one of the app's. */
        fun map(name: String, change: (OverlayName) -> OverlayName): String = parse(name)?.let { change(it).toString() } ?: name
    }
}
