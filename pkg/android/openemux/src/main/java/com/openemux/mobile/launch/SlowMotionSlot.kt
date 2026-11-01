package com.openemux.mobile.launch

/** A frame in a package skin's mapping units. */
data class SkinBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

/**
 * Where a package skin's slow-motion key goes ([slowMotion]), and where the items it makes room among move to
 * ([moved], by index into the item list).
 */
data class SlowMotionLayout(val slowMotion: SkinBox, val moved: Map<Int, SkinBox>)

/** The package skins' fast-forward inputs, the toggle first: the item the slow-motion key goes right before. */
val PACKAGE_FAST_FORWARD_INPUTS = listOf("toggleFastForward", "fastForward")

/**
 * Where the slow-motion key goes on a package skin whose fast forward is item [ff] of [items] (the frames of the
 * button items, null for the D-pads and sticks, which never share a key's row).
 *
 * The keys in a row with fast forward, edge to edge (the bundled skins' strip: save, load, turbo, fast forward), are
 * spread again over the same span in one more cell, with slow motion in the one right before fast forward, so nothing
 * outside the strip is covered. A fast forward with no neighbours (a lone button on an imported skin) is split in two:
 * slow motion in the left half, or in the top half when the button is taller than wide.
 */
fun slowMotionLayout(items: List<SkinBox?>, ff: Int): SlowMotionLayout {
    val key = items[ff] ?: error("fast forward has no frame")
    // Frames are written by hand or rounded by an export, so "the same row" and "edge to edge" allow a little slack.
    val slack = key.h * 0.02f
    fun sameRow(b: SkinBox) = kotlin.math.abs(b.y - key.y) <= slack && kotlin.math.abs(b.h - key.h) <= slack
    val row = items.withIndex().filter { (_, b) -> b != null && sameRow(b) }.map { it.index }
    val run = mutableListOf(ff)
    while (true) {
        val left = items[run.first()]!!.x
        run.add(0, row.firstOrNull { it !in run && kotlin.math.abs(items[it]!!.right - left) <= slack } ?: break)
    }
    while (true) {
        val right = items[run.last()]!!.right
        run.add(row.firstOrNull { it !in run && kotlin.math.abs(items[it]!!.x - right) <= slack } ?: break)
    }
    if (run.size == 1) {
        return if (key.w >= key.h) {
            val half = key.w / 2f
            SlowMotionLayout(key.copy(w = half), mapOf(ff to key.copy(x = key.x + half, w = half)))
        } else {
            val half = key.h / 2f
            SlowMotionLayout(key.copy(h = half), mapOf(ff to key.copy(y = key.y + half, h = half)))
        }
    }
    val x0 = items[run.first()]!!.x
    val cell = (items[run.last()]!!.right - x0) / (run.size + 1)
    // Slow motion takes fast forward's place in the order; fast forward and whatever follows move one cell right.
    val at = run.indexOf(ff)
    fun box(i: Int) = SkinBox(x0 + cell * i, key.y, cell, key.h)
    return SlowMotionLayout(box(at), run.withIndex().associate { (i, item) -> item to box(if (i < at) i else i + 1) })
}

/**
 * The touch boxes of the slow-motion key and fast forward, [slowTouch] and [ffTouch] (their frames [slow] and [ff] with
 * the skin's extended edges), kept off each other's key. Slow motion sits right before fast forward, edge to edge (left
 * of it, or above it when a tall button was split), so an extended edge reaching across that shared side would let a
 * single press hit both toggles.
 */
fun speedTouches(slow: SkinBox, ff: SkinBox, slowTouch: SkinBox, ffTouch: SkinBox): Pair<SkinBox, SkinBox> {
    val sideBySide = ff.x >= slow.right - slow.w * 0.01f
    return if (sideBySide) {
        val ffLeft = maxOf(ffTouch.x, slow.right)
        slowTouch.copy(w = minOf(slowTouch.right, ff.x) - slowTouch.x) to ffTouch.copy(x = ffLeft, w = ffTouch.right - ffLeft)
    } else {
        val ffTop = maxOf(ffTouch.y, slow.bottom)
        slowTouch.copy(h = minOf(slowTouch.bottom, ff.y) - slowTouch.y) to ffTouch.copy(y = ffTop, h = ffTouch.bottom - ffTop)
    }
}
