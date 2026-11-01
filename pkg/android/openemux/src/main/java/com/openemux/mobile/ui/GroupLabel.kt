package com.openemux.mobile.ui

/**
 * The console labels over the landscape row of a mixed page, as plain math
 * (in pixels, along the row from its leading edge). The row holds only
 * games, group after group; each group's label stands over its first game,
 * sticks at the row's start once that game scrolls past it, and is pushed
 * out by the next group's label.
 */
object GroupLabel {
    /** The row index where each group begins, given the groups' sizes in order. */
    fun starts(sizes: List<Int>): IntArray {
        val out = IntArray(sizes.size)
        var at = 0
        sizes.forEachIndexed { i, n -> out[i] = at; at += n }
        return out
    }

    /** The group the item at [index] belongs to: the last one beginning at or before it. */
    fun groupAt(index: Int, starts: IntArray): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= index) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * Where a group's label sits: over its first game ([groupStartX], null once
     * that game has left the row), never before [edge]; and when the next
     * group's label is in view at [nextLabelX], at least [gap] before it, even
     * if that takes it past the edge.
     */
    fun stickyX(groupStartX: Int?, nextLabelX: Int?, labelWidth: Int, edge: Int, gap: Int): Int {
        val resting = maxOf(edge, groupStartX ?: edge)
        return if (nextLabelX == null) resting else minOf(resting, nextLabelX - gap - labelWidth)
    }
}
