package com.openemux.mobile.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Where a control's label is and how large, and the shape a turned control is drawn as: pure geometry in a layout's
 * units, shared by the editor (which selects and moves a label) and the renderer (which draws it). No Android.
 */

/** What a label is drawn as: typed, or one of the glyphs the consoles print on their buttons. */
enum class SkinLabelKind { TEXT, SYMBOL, ARROW, MENU }

object SkinLabels {
    /** The PlayStation's shapes. */
    const val TRIANGLE = "△"
    const val CIRCLE = "○"
    const val CROSS = "✕"
    const val SQUARE = "□"
    val SYMBOLS = setOf(TRIANGLE, CIRCLE, CROSS, SQUARE)

    /** The N64's C buttons: an arrow, and how far it is turned from pointing up (degrees, clockwise). */
    val ARROWS = mapOf("▲" to 0f, "▶" to 90f, "▼" to 180f, "◀" to 270f)

    /** The menu key's three lines. */
    const val MENU = "☰"

    /** A letter's width as a share of its size, on average, in the labels' typeface (Space Grotesk Bold). */
    private const val LETTER = 0.62f

    /** The room a typed label may take of its face's width when it has no size of its own: a long word shrinks to it. */
    const val FIT = 0.8f

    /** Letter spacing in ems: a word printed beside its key is set wider than a letter on one, as the consoles do. */
    const val SPACING = 0.04f
    const val SPACING_BESIDE = 0.14f

    fun kind(label: String): SkinLabelKind = when (label) {
        in SYMBOLS -> SkinLabelKind.SYMBOL
        in ARROWS -> SkinLabelKind.ARROW
        MENU -> SkinLabelKind.MENU
        else -> SkinLabelKind.TEXT
    }

    /**
     * Whether [e] has a label of its own, which the editor selects, moves and paints: a button's or a key's. A D-pad's
     * arrows are painted with it and never move; a stick's letter is part of its knob.
     */
    fun has(e: SkinElement): Boolean =
        (e.type == SkinElementType.BUTTON || e.type == SkinElementType.KEYPAD) && !e.label.isNullOrBlank()

    /** Whether the app draws [e]'s label (a D-pad's arrows): it has no image ([hasImage]), or one that leaves the label out. */
    fun drawn(e: SkinElement, hasImage: Boolean): Boolean = !hasImage || e.art != SkinArt.FULL

    /** The centre of [e]'s label: where [SkinElement.labelOffset] puts it, else the middle of its face. */
    fun centre(e: SkinElement): SkinPoint {
        val o = e.labelOffset
        if (o != null) return SkinPoint(e.rect.x + e.rect.width / 2f + o.x, e.rect.y + e.rect.height / 2f + o.y)
        val f = e.face
        return SkinPoint(f.x + f.width / 2f, f.y + f.height / 2f)
    }

    /** Whether [e]'s label is printed beside it: its centre is outside the control. */
    fun beside(e: SkinElement): Boolean = centre(e).let { !e.rect.contains(it.x, it.y) }

    fun spacing(e: SkinElement): Float = if (beside(e)) SPACING_BESIDE else SPACING

    /**
     * The type size of [e]'s label: its own, else a share of its face by what the label is, a typed one shrunk until it
     * fits the face's width.
     */
    fun size(e: SkinElement): Float {
        e.labelSize?.let { return it }
        val label = e.label.orEmpty()
        val f = e.face
        val (w, h) = SkinShapes.unturned(f.width, f.height, e.rotation)
        return when (kind(label)) {
            SkinLabelKind.SYMBOL -> min(w, h) * 0.52f
            SkinLabelKind.ARROW -> min(w, h) * 0.46f
            SkinLabelKind.MENU -> min(w, h) * 0.53f
            SkinLabelKind.TEXT -> {
                val size = h * 0.42f
                val wide = width(label, size, SPACING)
                if (wide > w * FIT) size * w * FIT / wide else size
            }
        }
    }

    /** About how wide [label] is typed at [size] with [spacing] ems between letters; a glyph is as wide as it is tall. */
    fun width(label: String, size: Float, spacing: Float): Float =
        if (kind(label) != SkinLabelKind.TEXT) size else label.length * size * (LETTER + spacing)

    /** [e]'s label as a box in the layout: what the editor outlines, hits and moves. Close to the drawn label, not exact. */
    fun box(e: SkinElement): SkinRect {
        val size = size(e)
        val w = width(e.label.orEmpty(), size, spacing(e))
        val c = centre(e)
        return SkinRect(c.x - w / 2f, c.y - size / 2f, w, size)
    }

    /** [e] with its label where [box] is: its centre from the control's, its height the type size. */
    fun placed(e: SkinElement, box: SkinRect): SkinElement = e.copy(
        labelSize = box.height,
        labelOffset = SkinPoint(box.x + box.width / 2f - (e.rect.x + e.rect.width / 2f), box.y + box.height / 2f - (e.rect.y + e.rect.height / 2f)),
    )
}

object SkinShapes {
    /** The steepest a control may be turned: past it the box around the turned shape no longer tells its size. */
    const val MAX_TURN = 40f

    /**
     * The size of the shape that, turned [degrees], fills a [w] x [h] box (the box an element's rect is): the engine's
     * `art` the other way round. A turn too steep to tell, or a box no shape fills, gives the box itself.
     */
    fun unturned(w: Float, h: Float, degrees: Float): Pair<Float, Float> {
        if (degrees == 0f) return w to h
        val t = Math.toRadians(degrees.toDouble())
        val c = abs(cos(t))
        val s = abs(sin(t))
        val d = c * c - s * s
        if (d < 0.1) return w to h
        val sw = (w * c - h * s) / d
        val sh = (h * c - w * s) / d
        return if (sw <= 0.0 || sh <= 0.0) w to h else sw.toFloat() to sh.toFloat()
    }
}
