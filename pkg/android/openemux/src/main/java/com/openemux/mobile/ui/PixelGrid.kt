package com.openemux.mobile.ui

// The console icons' soft pixel grid (issue 967), kept free of Android types so the JVM tests reach it.
// Same numbers as the desktop's console_icons.py (issue 966), so both apps draw the same icon.

/** From this many physical pixels per art pixel up, the art gets the soft grid. */
const val SOFT_GRID_FROM = 4

/** A seam's colour: its pixel's, this much as bright (same alpha). */
const val SOFT_GRID_SHADE = 0.8f

/** Whole physical pixels per art pixel that fit a [slotPx] slot for [art]-pixel art; never below 1. */
fun iconCell(slotPx: Int, art: Int): Int = (slotPx / art.coerceAtLeast(1)).coerceAtLeast(1)

/** The seam of a [cell]-px art pixel: an eighth of it, rounded, at least one pixel. */
fun softGridSeam(cell: Int): Int = maxOf(1, (cell + 4) / 8)

/** [argb] with its red, green and blue at [SOFT_GRID_SHADE], alpha kept. */
fun softGridShade(argb: Int): Int {
    fun shade(c: Int) = Math.round(c * SOFT_GRID_SHADE)
    val a = argb ushr 24
    val r = shade((argb shr 16) and 0xFF)
    val g = shade((argb shr 8) and 0xFF)
    val b = shade(argb and 0xFF)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * The [width]×[height] ARGB art in [src] (row-major) blown up [cell] times, with the soft grid:
 * every opaque pixel's right and bottom [softGridSeam] pixels in its own colour at [SOFT_GRID_SHADE].
 * A transparent pixel gets nothing, so no grid shows on what is behind the icon.
 */
fun softGridPixels(src: IntArray, width: Int, height: Int, cell: Int): IntArray {
    val side = width * cell
    val out = IntArray(side * height * cell)
    val seam = softGridSeam(cell)
    val inner = cell - seam
    for (y in 0 until height) for (x in 0 until width) {
        val pixel = src[y * width + x]
        if (pixel ushr 24 == 0) continue
        val shaded = softGridShade(pixel)
        for (dy in 0 until cell) {
            val row = (y * cell + dy) * side + x * cell
            for (dx in 0 until cell) out[row + dx] = if (dx < inner && dy < inner) pixel else shaded
        }
    }
    return out
}
