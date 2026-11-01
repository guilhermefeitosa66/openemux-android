package com.openemux.mobile.ui

/** From this row width (dp) the filter pager shows everything: steps, icon, full name, count and caret. */
const val HEADER_FULL_DP = 680f

/** Under this row width (dp) the pager drops its steps and the icon, and the name goes short. */
const val HEADER_NARROW_DP = 480f

/** What the landscape header's filter pager shows at a given width (issue 669). */
data class HeaderBand(
    val steps: Boolean,
    val icon: Boolean,
    val shortName: Boolean,
    val count: Boolean,
    val caret: Boolean,
)

/**
 * The pager's band for a header row [rowWidthDp] wide. The caret marks the label as a menu for touch;
 * in the gamepad layout the shoulders take its place, so it never shows there.
 */
fun headerBand(rowWidthDp: Float, padLayout: Boolean): HeaderBand = when {
    rowWidthDp >= HEADER_FULL_DP -> HeaderBand(steps = true, icon = true, shortName = false, count = true, caret = !padLayout)
    rowWidthDp >= HEADER_NARROW_DP -> HeaderBand(steps = true, icon = true, shortName = false, count = false, caret = false)
    else -> HeaderBand(steps = false, icon = false, shortName = true, count = false, caret = !padLayout)
}

/**
 * The filter [step] places from [current] in [ids], clamped at both ends. An unknown [current]
 * counts as the first filter, as the chips did.
 */
fun stepFilter(ids: List<String>, current: String, step: Int): String =
    ids[(ids.indexOf(current).coerceAtLeast(0) + step).coerceIn(0, ids.lastIndex)]

/**
 * Whether stepping [step] from [current] reaches another filter, rather than stopping at an end. An
 * unknown [current] (its console's last game just removed) always moves, as the shoulders do.
 */
fun canStep(ids: List<String>, current: String, step: Int): Boolean = stepFilter(ids, current, step) != current
