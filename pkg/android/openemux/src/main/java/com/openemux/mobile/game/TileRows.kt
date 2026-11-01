package com.openemux.mobile.game

/** A pause-sheet action tile's width with its padding, in dp: the 60 dp square and 4 dp on each side. */
const val TILE_SLOT_DP = 68f

/**
 * How many rows [count] action tiles take in a row [widthDp] wide: one wherever they all fit at [TILE_SLOT_DP],
 * else the fewest rows of equal columns that do (6 tiles at 328 dp: 2 rows of 3). Never more rows than tiles.
 */
fun tileRows(count: Int, widthDp: Float): Int {
    if (count <= 1) return 1
    var rows = 1
    while (rows < count && tilesPerRow(count, rows) * TILE_SLOT_DP > widthDp) rows++
    return rows
}

/** The tiles in each of [rows] rows of equal columns: the last row holds what is left. */
fun tilesPerRow(count: Int, rows: Int): Int = (count + rows - 1) / rows
