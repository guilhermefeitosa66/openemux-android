package com.openemux.mobile.launch

import org.json.JSONArray
import org.json.JSONObject

/** A frame in a package skin's mapping units. */
data class SkinBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

/**
 * Where a package skin's slow-motion key (or another key [insertSlot] makes room for) goes ([slowMotion]), and where
 * the items it makes room among move to ([moved], by index into the item list).
 */
data class SlowMotionLayout(val slowMotion: SkinBox, val moved: Map<Int, SkinBox>)

/** The package skins' fast-forward inputs, the toggle first: the item the slow-motion key goes right before. */
val PACKAGE_FAST_FORWARD_INPUTS = listOf("toggleFastForward", "fastForward")

/** A package item's frame ([f], mapping units) as a box. */
fun packageBox(f: JSONObject) =
    SkinBox(f.getDouble("x").toFloat(), f.getDouble("y").toFloat(), f.getDouble("width").toFloat(), f.getDouble("height").toFloat())

/**
 * The index in a package's [items] of its fast-forward key (one input alone), or null: the toggle wins over the hold
 * key when a skin has both, whatever their order in the items.
 */
fun fastForwardItem(items: JSONArray): Int? = PACKAGE_FAST_FORWARD_INPUTS.firstNotNullOfOrNull { input ->
    (0 until items.length()).firstOrNull { i ->
        (items.getJSONObject(i).opt("inputs") as? JSONArray)?.let { it.length() == 1 && it.getString(0) == input } == true
    }
}

/**
 * Where the slow-motion key goes on a package skin whose fast forward is item [ff] of [items] (the frames of the
 * button items, null for the D-pads and sticks, which never share a key's row): right before it ([insertSlot]).
 */
fun slowMotionLayout(items: List<SkinBox?>, ff: Int): SlowMotionLayout =
    insertSlot(items, ff, after = false).let { SlowMotionLayout(it.slowMotion, it.moved) }

/**
 * Where a new key goes beside item [at] of [items] (the frames of the button items, null for the D-pads and sticks,
 * which never share a key's row): right before it, or right [after] it. The new key's frame is [SlowMotionLayout.slowMotion].
 *
 * The keys in a row with [at], edge to edge (the bundled skins' strip: save, load, turbo, fast forward), are spread
 * again over the same span in one more cell, the new key in the cell beside [at], so nothing outside the strip is
 * covered. A key with no neighbours (a lone button on an imported skin) is split in two: the new key in the half on
 * its side (left or right, or top or bottom when the button is taller than wide).
 */
fun insertSlot(items: List<SkinBox?>, at: Int, after: Boolean): SlowMotionLayout {
    val key = items[at] ?: error("the key has no frame")
    // Frames are written by hand or rounded by an export, so "the same row" and "edge to edge" allow a little slack.
    val slack = key.h * 0.02f
    fun sameRow(b: SkinBox) = kotlin.math.abs(b.y - key.y) <= slack && kotlin.math.abs(b.h - key.h) <= slack
    val row = items.withIndex().filter { (_, b) -> b != null && sameRow(b) }.map { it.index }
    val run = mutableListOf(at)
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
            val first = key.copy(w = half)
            val second = key.copy(x = key.x + half, w = half)
            if (after) SlowMotionLayout(second, mapOf(at to first)) else SlowMotionLayout(first, mapOf(at to second))
        } else {
            val half = key.h / 2f
            val first = key.copy(h = half)
            val second = key.copy(y = key.y + half, h = half)
            if (after) SlowMotionLayout(second, mapOf(at to first)) else SlowMotionLayout(first, mapOf(at to second))
        }
    }
    val x0 = items[run.first()]!!.x
    val cell = (items[run.last()]!!.right - x0) / (run.size + 1)
    // The new key takes the cell beside [at]; every key from there on moves one cell right.
    val slot = run.indexOf(at) + if (after) 1 else 0
    fun box(i: Int) = SkinBox(x0 + cell * i, key.y, cell, key.h)
    return SlowMotionLayout(box(slot), run.withIndex().associate { (i, item) -> item to box(if (i < slot) i else i + 1) })
}

/**
 * The speed row of a package skin whose fast forward is item [ff] of [items]: slow motion right before fast forward
 * when [slowMotion], and the anchor key right after it (issue 841). [moved] has the items that move, by index into
 * [items].
 */
data class SpeedRow(val slowMotion: SkinBox?, val anchor: SkinBox, val moved: Map<Int, SkinBox>)

/** Lays out [SpeedRow]: slow motion made room for first ([slowMotionLayout]), then the anchor key after fast forward. */
fun speedRow(items: List<SkinBox?>, ff: Int, slowMotion: Boolean): SpeedRow {
    val slow = if (slowMotion) slowMotionLayout(items, ff) else null
    // Slow motion's slot joins the row as one more item, so the anchor's pass spreads it too.
    val placed = items.mapIndexed { i, b -> slow?.moved?.get(i) ?: b } + listOfNotNull(slow?.slowMotion)
    val anchor = insertSlot(placed, ff, after = true)
    val moved = (slow?.moved.orEmpty() + anchor.moved).filterKeys { it < items.size }
    return SpeedRow(slow?.let { anchor.moved[items.size] ?: it.slowMotion }, anchor.slowMotion, moved)
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
