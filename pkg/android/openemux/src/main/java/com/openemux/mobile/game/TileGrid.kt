package com.openemux.mobile.game

/** A pause-sheet action tile's width with its padding, in dp: the 60 dp square and 4 dp on each side. */
const val TILE_SLOT_DP = 68f

/** The pause sheet's tile columns on a phone upright (issue 858): 5 tiles go 4 + 1, Audio under Fast forward. */
const val PAUSE_TILE_COLUMNS_UPRIGHT = 4

/** The tile columns everywhere else: landscape, the tablet panel and tabletop, where 5 tiles make one row. */
const val PAUSE_TILE_COLUMNS = 5

/** The widest gap between two tile columns, in dp: tiles sit close together, not spread across the row. */
const val TILE_GAP_DP = 8f

/** The gap between two rows of tiles, in dp. */
const val TILE_ROW_GAP_DP = 12f

/** How many tile columns the sheet asks for in [layout]. */
fun tileColumns(layout: PauseLayout): Int = if (layout == PauseLayout.PHONE) PAUSE_TILE_COLUMNS_UPRIGHT else PAUSE_TILE_COLUMNS

/** The tiles' grid: [columns] columns [columnDp] wide, [gapDp] apart. */
data class TileGrid(val columns: Int, val columnDp: Float, val gapDp: Float)

/**
 * The grid of [columns] columns across a row [widthDp] wide: the width shared out equally after gaps of
 * [TILE_GAP_DP]. A column never gets narrower than a tile ([TILE_SLOT_DP]): the gap shrinks first (the
 * landscape half fits 5 tiles in one row that way), and only where even no gap leaves room for fewer
 * tiles than [columns] does the grid take fewer columns, so no tile is ever cut or overlaps another.
 */
fun tileGrid(widthDp: Float, columns: Int): TileGrid {
    val fit = (widthDp / TILE_SLOT_DP).toInt().coerceIn(1, columns.coerceAtLeast(1))
    if (fit == 1) return TileGrid(1, widthDp, 0f)
    val gap = ((widthDp - fit * TILE_SLOT_DP) / (fit - 1)).coerceIn(0f, TILE_GAP_DP)
    return TileGrid(fit, (widthDp - gap * (fit - 1)) / fit, gap)
}

/**
 * Whether a label laid out with lines ending at [lineEnds] breaks a word in two: a line ends between two
 * characters neither of which is a space or a hyphen. Text written without spaces (Chinese, Japanese)
 * breaks anywhere, so a break next to one of its characters is not a split word.
 */
fun splitsAWord(text: String, lineEnds: List<Int>): Boolean = lineEnds.any { end ->
    end in 1 until text.length && joins(text[end - 1]) && joins(text[end])
}

private fun joins(c: Char): Boolean =
    !c.isWhitespace() && c != '-' && c != '‐' && !unspaced(c)

private fun unspaced(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
    else -> c == 'ー' // the katakana long-vowel mark is COMMON, but sits inside katakana words
}
