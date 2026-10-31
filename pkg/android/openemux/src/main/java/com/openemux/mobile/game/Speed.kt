package com.openemux.mobile.game

/**
 * The game's speed as the pause sheet sees it. Fast forward and slow motion never run together (the engine turns
 * one off when the other goes on), so the sheet can only end in one of the three.
 */
enum class Speed {
    NORMAL, FAST, SLOW;

    /** The speed after the sheet's [tapped] tile (FAST or SLOW) is pressed: that one, or NORMAL when it was on. */
    fun tap(tapped: Speed): Speed = if (this == tapped) NORMAL else tapped

    /**
     * The engine commands that take it from fast forward [fast] and slow motion [slow] to this speed, each a toggle
     * sent once. Turning one on is enough: the engine turns the other off itself.
     */
    fun commandsFrom(fast: Boolean, slow: Boolean): List<String> = when (this) {
        FAST -> if (fast) emptyList() else listOf(FAST_FORWARD)
        SLOW -> if (slow) emptyList() else listOf(SLOWMOTION)
        NORMAL -> listOfNotNull(FAST_FORWARD.takeIf { fast }, SLOWMOTION.takeIf { slow })
    }

    companion object {
        const val FAST_FORWARD = "FAST_FORWARD"
        const val SLOWMOTION = "SLOWMOTION"

        /** The speed the engine reports: fast forward wins, though the engine never has both on. */
        fun of(fast: Boolean, slow: Boolean): Speed = when {
            fast -> FAST
            slow -> SLOW
            else -> NORMAL
        }
    }
}
