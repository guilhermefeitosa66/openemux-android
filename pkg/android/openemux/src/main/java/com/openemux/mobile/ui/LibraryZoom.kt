package com.openemux.mobile.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// The library's stepped zoom on a phone held upright (issue 868): the cartridge and cover views go from
// one to five columns, the list view through four row sizes. Plain values, so the decisions run as JVM
// unit tests; the gesture and the grid that read them are in LibraryScreens.kt.

/** The fewest and the most columns a zoomed grid shows. */
const val ZOOM_MIN_COLUMNS = 1
const val ZOOM_MAX_COLUMNS = 5

/** The cartridge view's columns until the user zooms: a phone's two, as before. */
const val ZOOM_DEFAULT_CARTS = 2

/** The cover view's columns until the user zooms: a phone's three, as before. */
const val ZOOM_DEFAULT_COVERS = 3

/** How far the fingers spread (or close) from where they started before a pinch takes its one step. */
const val PINCH_BIGGER = 1.25f
const val PINCH_SMALLER = 0.8f

/**
 * A list row's size: its art's box, how many lines the title takes, the title's and the console's text
 * size, and the gap between the art and the text. A row is the art plus 8dp above and below.
 */
enum class ListSize(val artDp: Int, val titleLines: Int, val titleSp: Int, val subtitleSp: Int, val textGapDp: Int) {
    Compact(40, 1, 15, 13, 12),
    Normal(64, 1, 15, 13, 14),
    Large(96, 2, 15, 13, 16),
    ExtraLarge(128, 2, 17, 14, 16);

    /** The row's height with its padding. */
    val rowDp get() = artDp + 2 * LIST_ROW_PADDING_DP

    /** The next size, bigger or smaller; the ends stay where they are. */
    fun step(bigger: Boolean): ListSize = entries[(ordinal + if (bigger) 1 else -1).coerceIn(0, entries.size - 1)]
}

/** A list row's padding above and below its art. */
const val LIST_ROW_PADDING_DP = 8

/** One step of the columns, [bigger] items meaning fewer of them; held between [ZOOM_MIN_COLUMNS] and [ZOOM_MAX_COLUMNS]. */
fun stepColumns(columns: Int, bigger: Boolean): Int =
    (columns + if (bigger) -1 else 1).coerceIn(ZOOM_MIN_COLUMNS, ZOOM_MAX_COLUMNS)

/** What a pinch whose fingers are now [spread] times as far apart as when it started asks for: true bigger, false smaller, null nothing yet. */
fun pinchStep(spread: Float): Boolean? = when {
    spread >= PINCH_BIGGER -> true
    spread <= PINCH_SMALLER -> false
    else -> null
}

/**
 * Whether the library zooms in steps: a phone held upright only. Landscape (a short window), a tablet or an open
 * fold (wider than Compact) and tabletop keep their automatic columns.
 */
fun zoomApplies(layout: WindowLayout, tabletop: Boolean): Boolean =
    layout.width == SizeClass.Compact && !layout.short && layout.heightDp > layout.widthDp && !tabletop

/** A zoomed grid's spacing and its caption: [captionSp] null when the tiles are too narrow to read one. */
data class ZoomGrid(val gapDp: Float, val rowGapDp: Float, val captionSp: Int?)

/** The spacing and the caption of a grid of [columns] cartridges ([carts]) or covers. */
fun zoomGrid(columns: Int, carts: Boolean): ZoomGrid {
    val tight = columns >= 4
    val gap = when {
        !tight -> if (carts) GridSpec.Carts.gap else GridSpec.Covers.gap
        carts -> 10f
        else -> 8f
    }
    val caption = when {
        columns >= ZOOM_MAX_COLUMNS -> null
        tight -> 11
        carts -> 13
        else -> 12
    }
    return ZoomGrid(gap, if (tight) 12f else 18f, caption)
}

/** The step each view of the library keeps, stored with the app's other preferences. */
object LibraryZoomPref {
    private const val CARTS = "library_zoom_carts"
    private const val COVERS = "library_zoom_covers"
    private const val LIST = "library_zoom_list"

    var carts by mutableIntStateOf(ZOOM_DEFAULT_CARTS)
        private set
    var covers by mutableIntStateOf(ZOOM_DEFAULT_COVERS)
        private set
    var list by mutableStateOf(ListSize.Normal)
        private set

    /** Stored columns, or [default] for none or one out of range. */
    fun parseColumns(stored: Int, default: Int): Int = if (stored in ZOOM_MIN_COLUMNS..ZOOM_MAX_COLUMNS) stored else default

    /** A stored row size, or [ListSize.Normal] for none or one this version does not know. */
    fun parseList(stored: String?): ListSize = ListSize.entries.firstOrNull { it.name == stored } ?: ListSize.Normal

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        carts = parseColumns(p.getInt(CARTS, ZOOM_DEFAULT_CARTS), ZOOM_DEFAULT_CARTS)
        covers = parseColumns(p.getInt(COVERS, ZOOM_DEFAULT_COVERS), ZOOM_DEFAULT_COVERS)
        list = parseList(p.getString(LIST, null))
    }

    fun setCarts(context: Context, columns: Int) {
        carts = parseColumns(columns, carts)
        prefs(context).edit().putInt(CARTS, carts).apply()
    }

    fun setCovers(context: Context, columns: Int) {
        covers = parseColumns(columns, covers)
        prefs(context).edit().putInt(COVERS, covers).apply()
    }

    fun setList(context: Context, size: ListSize) {
        list = size
        prefs(context).edit().putString(LIST, size.name).apply()
    }
}

/** An item on screen, in the grid's own pixels: [top] measured from the grid's top edge. */
data class ZoomSlot(val index: Int, val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The item a pinch at ([x], [y]) is over, to keep it in place across the step: the one under that point, else
 * on the same row the one nearest across, else the nearest row's. Null with nothing on screen.
 */
fun anchorItem(slots: List<ZoomSlot>, x: Float, y: Float): ZoomSlot? {
    fun across(s: ZoomSlot) = kotlin.math.abs(s.left + s.width / 2f - x)
    fun down(s: ZoomSlot) = kotlin.math.abs(s.top + s.height / 2f - y)
    val row = slots.filter { y >= it.top && y < it.top + it.height }
    return if (row.isNotEmpty()) row.minBy(::across) else slots.minByOrNull(::down)
}
