package com.openemux.mobile.game

/** Where the game is drawn, as the skin in use tells the engine (OverlayWriter's `overlayN_viewport`). */
object GameViewport {
    private val names = Regex("""^overlay(\d+)_name = "(.*)"$""", RegexOption.MULTILINE)
    private val boxes = Regex("""^overlay(\d+)_viewport = "([^"]*)"$""", RegexOption.MULTILINE)

    /**
     * Each overlay's game box bottom, as a fraction of the window's height from its top, by overlay name. The box is
     * written in GL coordinates (x, y, width, height, y counted from the bottom), so its bottom is `1 - y`. The engine
     * keeps the game's aspect inside the box: the picture never reaches below it. Overlays with no box, or a box that
     * does not read as four numbers, are left out.
     */
    fun bottoms(cfg: String): Map<String, Float> {
        val named = names.findAll(cfg).associate { it.groupValues[1] to it.groupValues[2] }
        return boxes.findAll(cfg).mapNotNull { m ->
            val name = named[m.groupValues[1]] ?: return@mapNotNull null
            val parts = m.groupValues[2].split(',').map { it.trim().toFloatOrNull() ?: return@mapNotNull null }
            if (parts.size != 4) return@mapNotNull null
            name to (1f - parts[1]).coerceIn(0f, 1f)
        }.toMap()
    }

    /** No skin box to go by: the engine puts the game at the top of a portrait window, as wide as it is, at 4:3. */
    const val UNKNOWN_ASPECT = 0.75f
}
