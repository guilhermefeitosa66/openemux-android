package com.openemux.mobile.ui

// The shelf's geometry, in dp, kept free of Compose so the JVM tests reach it.

/** Padding on each side of the shelf grid and the gap between its cards. */
const val SHELF_SIDE_DP = 20f
const val SHELF_GAP_DP = 12f

/** Below this two cards side by side would squeeze the icon and the name. */
const val SHELF_MIN_CARD_DP = 250f

/**
 * A shelf card's insides: the [box] the icon sits in (no fill of its own, only room), the console
 * [icon] and the Favorites [star] in it, the name's size [nameSp] and the [startPadding] before the box.
 */
data class ShelfCardSpec(val box: Float, val icon: Float, val star: Float, val nameSp: Float, val startPadding: Float)

/** The icon is the card's hero: 64dp on a phone (a whole multiple of the 32 px bitmaps on 2x and 3x screens), 96dp on a tablet. */
fun shelfCardSpec(tablet: Boolean): ShelfCardSpec =
    if (tablet) ShelfCardSpec(112f, 96f, 62f, 24f, 20f) else ShelfCardSpec(76f, 64f, 42f, 20f, 16f)

/** How many cards fit side by side in [widthDp], each at least [SHELF_MIN_CARD_DP] wide. */
fun shelfColumns(widthDp: Float, maxColumns: Int = 2, side: Float = SHELF_SIDE_DP, gap: Float = SHELF_GAP_DP): Int =
    gridColumns(widthDp - 2 * side, SHELF_MIN_CARD_DP, gap, floor = 1).coerceAtMost(maxOf(1, maxColumns))

/**
 * The shelves pinned before the consoles: "ALL" whenever the library has games, then "FAV" when
 * at least one of them is a favorite (issue 655). Neither on an empty library.
 */
fun pinnedShelves(total: Int, favorites: Int): List<String> = when {
    total <= 0 -> emptyList()
    favorites <= 0 -> listOf("ALL")
    else -> listOf("ALL", "FAV")
}

/** One column with both pinned shelves: they share a row, half width each, instead of a row apiece. */
fun pinnedAsPair(columns: Int, pinned: List<String>): Boolean = columns == 1 && pinned.size == 2

/** A card on screen, in any unit, as the grid laid it out. */
data class Slot<K>(val key: K, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * The card a dragged card would swap with: the one under the dragged card's
 * centre ([cx], [cy]). Never the dragged card itself, nor one that cannot move.
 */
fun <K> reorderTarget(cx: Float, cy: Float, dragged: K, slots: List<Slot<K>>, movable: (K) -> Boolean): K? =
    slots.firstOrNull { s ->
        s.key != dragged && movable(s.key) &&
            cx >= s.x && cx < s.x + s.width && cy >= s.y && cy < s.y + s.height
    }?.key

/** The games to pick up again: the [n] last played, newest first; never-played ones never show. */
fun <T> continueList(items: List<T>, lastPlayed: (T) -> Long, n: Int): List<T> =
    items.filter { lastPlayed(it) > 0 }.sortedByDescending(lastPlayed).take(n.coerceAtLeast(0))

/** The [n] games that came in last, by when their file was copied in, newest first. */
fun <T> recentlyAdded(items: List<T>, modified: (T) -> Long, n: Int): List<T> =
    // [modified] reads the file system: once per item, not once per comparison.
    items.map { it to modified(it) }.sortedByDescending { it.second }.take(n.coerceAtLeast(0)).map { it.first }

/** How many games the tablet's recently added row shows. */
const val RECENTLY_ADDED_COUNT = 4

/** How many games the phone's Continue row holds (issue 925): the last played, sideways. */
const val PHONE_CONTINUE_COUNT = 3

/** A phone's continue card, when there is more than one: the next one peeks in at the edge. */
const val PHONE_CONTINUE_CARD_DP = 280f

/**
 * The width of each card in the phone's Continue row, for a shelf [widthDp] wide with [side] padding:
 * one game takes the whole width; more than one are [PHONE_CONTINUE_CARD_DP] wide, or narrower on
 * a small screen, so the next card always shows at the edge.
 */
fun continueCardWidth(widthDp: Float, count: Int, side: Float = SHELF_SIDE_DP): Float {
    val room = maxOf(0f, widthDp - 2 * side)
    return if (count <= 1) room else minOf(PHONE_CONTINUE_CARD_DP, room * 0.86f)
}
