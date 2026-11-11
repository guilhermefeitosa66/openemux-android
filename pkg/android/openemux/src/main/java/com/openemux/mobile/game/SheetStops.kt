package com.openemux.mobile.game

/** Where the phone-portrait pause sheet can rest (issue 849): stretched near the top, under the game, or gone. */
enum class SheetStop { EXPANDED, FIRST, CLOSED }

/**
 * The first stop's top, in the sheet area's pixels: [gap] under the game's bottom, so the picture stays whole, but
 * never so low that less than [minVisible] of the sheet shows (the header, Continue and the save row), nor above the
 * [expanded] stop.
 */
fun firstStopTop(gameBottom: Float, area: Float, expanded: Float, minVisible: Float, gap: Float): Float =
    (gameBottom + gap).coerceAtMost(area - minVisible).coerceAtLeast(expanded)

/**
 * Where a released drag lands. [top] is the sheet's top now and [velocity] the finger's, both in pixels, downward
 * positive; [first] is the first stop's top as it shows and [firstHeight] the sheet's height there. Below the first
 * stop, a drag that started there closes the sheet once past a third of that height and springs back short of it;
 * one that started at the taller stop never closes. Between the stops, a fling picks the way it went, else the
 * nearest stop wins.
 */
fun settle(
    top: Float,
    velocity: Float,
    expanded: Float,
    first: Float,
    firstHeight: Float,
    fromFirst: Boolean,
    fling: Float,
): SheetStop {
    if (top > first) return if (fromFirst && top - first > firstHeight / 3f) SheetStop.CLOSED else SheetStop.FIRST
    // Content that fits whole leaves no room to stretch into.
    if (first - expanded < 1f) return SheetStop.FIRST
    if (velocity <= -fling) return SheetStop.EXPANDED
    if (velocity >= fling) return SheetStop.FIRST
    return if (top - expanded < first - top) SheetStop.EXPANDED else SheetStop.FIRST
}
