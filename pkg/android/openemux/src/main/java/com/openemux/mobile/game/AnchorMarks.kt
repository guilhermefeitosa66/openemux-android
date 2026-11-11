package com.openemux.mobile.game

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

    /**
     * Whether the ring is round for a button drawn [pxW] x [pxH] pixels: a round button, or a rectangular box about
     * as wide as tall (a face button whose touch box is a square); a wider or taller one (a shoulder) gets a pill.
     */
    fun ringRound(pxW: Float, pxH: Float): Boolean =
        !rect || (pxH > 0f && pxW / pxH in ROUND_ASPECT)

    companion object {
        private val ROUND_ASPECT = 0.8f..1.25f

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

/**
 * How long the explanation card stays up on its own before it slides down (issue 841): about 22 words take ~6.5 s to
 * read, plus a second to notice the card, so 8 s; the system's "Time to take action" setting may ask for longer.
 */
object AnchorCardTimeout {
    const val READ_MS = 8000

    /**
     * The card's timeout, given what `AccessibilityManager.getRecommendedTimeoutMillis` [recommended] for [READ_MS]
     * (null before API 29, where there is no such setting): never shorter than [READ_MS].
     */
    fun millis(recommended: Int?): Long = maxOf(READ_MS, recommended ?: READ_MS).toLong()
}

/**
 * The explanation card's countdown (issue 841): it runs while nothing touches the card, keeps what is [left] while a
 * finger is on it, and starts again from the [total] when a drag springs back.
 */
class AnchorCardCountdown(val total: Long) {
    var left = total
        private set

    /** The countdown ran for [ms] before it stopped (a touch, a close, the card going away). */
    fun ran(ms: Long) {
        left = (left - ms.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /** A drag sprang back: the player is still reading, so the full time again. */
    fun restart() {
        left = total
    }
}

/**
 * Where the explanation card goes, in pixels of the frame its window is laid out in (gravity bottom-start): its left
 * edge, how far its bottom edge sits above the frame's, and its width.
 */
data class AnchorCardPlace(val x: Int, val bottom: Int, val width: Int) {
    companion object {
        /**
         * At the bottom of the game's window, like a system sheet, so swiping it down is the natural way to close it:
         * centred on it, [margin] above its bottom edge and at least that clear of its sides, at most [maxWidth] wide.
         * The game's window spans [gameLeft] to [gameLeft] + [gameW] across and ends at [gameBottom], in the same
         * screen pixels as the frame, which starts at [frameLeft] and ends at [frameBottom]; a window that sits inset
         * in the frame (a cutout bar beside it, a split screen) keeps the card on the game.
         */
        fun of(
            gameLeft: Int, gameW: Int, gameBottom: Int, frameLeft: Int, frameBottom: Int, margin: Int, maxWidth: Int,
        ): AnchorCardPlace {
            val width = minOf(maxWidth, gameW - 2 * margin).coerceAtLeast(0)
            val below = (frameBottom - gameBottom).coerceAtLeast(0)
            return AnchorCardPlace(gameLeft - frameLeft + (gameW - width) / 2, below + margin, width)
        }
    }
}
