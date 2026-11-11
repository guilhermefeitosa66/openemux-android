package com.openemux.mobile.game

import com.openemux.mobile.launch.ScreenBox
import kotlin.math.abs

/**
 * A button anchor mode holds (issue 841), as the engine reports it (onAnchorFeedback): the button's drawn box and
 * shape and the finger holding it, all as fractions of the screen, y down. The game draws an amber ring around the
 * button and, while the finger is off it, a guide line from the button's centre to the finger.
 */
data class AnchorMark(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** A rectangular button (a shoulder, a trigger); false for a round one, ringed as an ellipse. */
    val rect: Boolean,
    val fingerX: Float,
    val fingerY: Float,
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    /** Whether the finger has slid off the button's shape: the guide line is drawn only then. */
    fun fingerOff(): Boolean {
        if (width <= 0f || height <= 0f) return true
        val dx = (fingerX - centerX) / (width / 2f)
        val dy = (fingerY - centerY) / (height / 2f)
        return if (rect) abs(dx) > 1f || abs(dy) > 1f else dx * dx + dy * dy > 1f
    }

    companion object {
        /** Floats per mark in the engine's report (OVERLAY_ANCHOR_FEEDBACK_FLOATS). */
        const val FLOATS = 7

        /** The engine's report: [FLOATS] floats a mark; a trailing partial one is ignored. */
        fun parse(data: FloatArray): List<AnchorMark> = (0 until data.size / FLOATS).map { i ->
            val o = i * FLOATS
            AnchorMark(data[o], data[o + 1], data[o + 2], data[o + 3], data[o + 4] != 0f, data[o + 5], data[o + 6])
        }
    }
}

/**
 * The explanation card's swipe down (issue 841): it follows the finger, only downwards, and fades as it goes; let go
 * past a third of its height, or flung down, it slides out and closes, otherwise it springs back.
 */
object AnchorCardSwipe {
    /** How far down a fling must go, in pixels a second, to close the card wherever it is let go. */
    const val FLING = 1200f

    /** The card's offset after a drag of [delta] from [offset]: never above where it rests. */
    fun drag(offset: Float, delta: Float): Float = (offset + delta).coerceAtLeast(0f)

    /** Whether letting go at [offset] with a [velocity] (down positive) closes a card [height] tall. */
    fun closes(offset: Float, velocity: Float, height: Float): Boolean =
        velocity > FLING || (height > 0f && offset > height / 3f)

    /** How opaque the card is at [offset]: fully at rest, nothing once it has moved its own [height]. */
    fun alpha(offset: Float, height: Float): Float =
        if (height <= 0f) 1f else (1f - offset / height).coerceIn(0f, 1f)
}

/** Where the explanation card goes on the screen, in pixels: its left and top edges and its width. */
data class AnchorCardPlace(val x: Int, val y: Int, val width: Int) {
    companion object {
        /**
         * Over the game, so the touch controls stay free to try the anchor right away: centred on the game's box
         * [viewport] (fractions of a [screenW] x [screenH] screen), [margin] below its top, at most [maxWidth] wide.
         * With no viewport, at the top of the screen.
         */
        fun of(viewport: ScreenBox?, screenW: Int, screenH: Int, margin: Int, maxWidth: Int): AnchorCardPlace {
            val left = (viewport?.left ?: 0f) * screenW
            val top = (viewport?.top ?: 0f) * screenH
            val span = (viewport?.width ?: 1f) * screenW
            val width = minOf(maxWidth.toFloat(), span - 2 * margin).coerceAtLeast(0f)
            return AnchorCardPlace((left + (span - width) / 2f).toInt(), (top + margin).toInt(), width.toInt())
        }
    }
}
