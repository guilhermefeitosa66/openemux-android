package com.openemux.mobile.launch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.util.Locale

/**
 * One touch area of an overlay, in pixels: [radial] marks an analog stick, whose rect is its range; [next] is the
 * overlay an overlay_next switches to; [lit] is a toggle's "on" image, drawn over its rect only while the engine has
 * that state on. A stick with a [well] (the circle drawn around it) has the knob's travel for its rect, [knob] the
 * image the engine moves with the thumb and [saturate] where along the travel the value peaks. [dpad] makes it an
 * eight-way D-pad area with those up, down, left, right binds, whose rect is the travel of its circle ([knob]), and
 * which is touched at least [reach] times its half-size on each side of its centre (left, top, right, bottom). [image] is
 * drawn over its rect all the time (an app control drawn apart from the page's image, which its collapsed twin shares).
 */
internal class OverlayDesc(
    val name: String, val rect: RectF, val radial: Boolean = false, val next: String? = null, val lit: OverlayLit? = null,
    val well: RectF? = null, val knob: Bitmap? = null, val saturate: Float = 1f,
    val dpad: List<String>? = null, val reach: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    val image: Bitmap? = null,
)

/** The image of a desc lit by an engine state (overlayN_descM_lit_by: fast_forward, turbo, slow_motion or anchor). */
internal class OverlayLit(val state: String, val bitmap: Bitmap)

/** What [SkinRenderer] and [ImportedSkinRenderer] write the same way: the overlays of a skin.cfg and their images. */
internal object OverlayWriter {

    /** The app accent (Oe.Accent) and its light tone (Oe.AccentOnSoft, dark), for a toggle that is on. */
    const val ACCENT = 0xFF7C3AED.toInt()
    const val ACCENT_RING = 0xFFD4C4FF.toInt()

    /** How far a stick's knob travels from the well's centre, as a share of the well's radius. */
    const val KNOB_TRAVEL = 0.65f

    /** The engine state each toggle hotkey lights its button with. */
    val LIT_BY = mapOf(
        "turbo_fire_toggle" to "turbo", "toggle_slowmotion" to "slow_motion", "toggle_fast_forward" to "fast_forward",
        ANCHOR_ACTION to "anchor",
    )

    /** A toggle's "on" pill, [w] x [h] pixels: the accent with its light inner ring of [ring] pixels. */
    fun pill(w: Int, h: Int, radius: Float, ring: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fw = bitmap.width.toFloat()
        val fh = bitmap.height.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, fw, fh), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        canvas.drawRoundRect(
            RectF(ring / 2f, ring / 2f, fw - ring / 2f, fh - ring / 2f), radius - ring / 2f, radius - ring / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT_RING; style = Paint.Style.STROKE; strokeWidth = ring },
        )
        return bitmap
    }

    /**
     * Writes the overlay's [bitmap] as [image] in [outDir] (none: the page has no image of its own, or shares one
     * already written), and its descs' lit images, moving images (a stick's knob, a D-pad's circle) and own images
     * next to it, named after [prefix]; a moving image or an own image is recycled once written.
     */
    fun saveImages(outDir: File, image: String?, bitmap: Bitmap?, descs: List<OverlayDesc>, prefix: String = image!!.removeSuffix(".png")) {
        if (image != null && bitmap != null) File(outDir, image).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        descs.forEachIndexed { i, d ->
            d.lit?.let { lit ->
                File(outDir, litImage(prefix, lit)).outputStream().use { lit.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            d.knob?.let { knob ->
                File(outDir, knobImage(prefix, i)).outputStream().use { knob.compress(Bitmap.CompressFormat.PNG, 100, it) }
                knob.recycle()
            }
            d.image?.let { own ->
                File(outDir, ownImage(prefix, i)).outputStream().use { own.compress(Bitmap.CompressFormat.PNG, 100, it) }
                own.recycle()
            }
        }
    }

    private fun litImage(prefix: String, lit: OverlayLit) = "$prefix-lit-${lit.state}.png"

    /** The moving image (a stick's knob, a D-pad's circle) of the desc at [i]. */
    private fun knobImage(prefix: String, i: Int) = "$prefix-knob$i.png"

    /** The image of its own the desc at [i] is drawn with. */
    private fun ownImage(prefix: String, i: Int) = "$prefix-image$i.png"

    /**
     * Overlay [index] of the cfg, [w] x [h] pixels, with the game in [picture]; [other] is the overlay of the other
     * orientation. Touch areas are [grow] times what is drawn, except a stick's range, a lit image and a D-pad's
     * circle, which RetroArch stretches over its desc's range (a D-pad's touch area grows through its reach). A
     * D-pad's or a stick's zone reaches past what is drawn up to the other controls and the [clear] areas, [gap] short of them.
     * [hidden] is where the auto-rotate switch sits, as a fraction of the height: off the fold on a tabletop overlay.
     * A null [image] writes a page with no image of its own; the descs' images are named after [prefix], as
     * [saveImages] wrote them.
     */
    fun write(
        cfg: StringBuilder, index: Int, name: String, other: String, image: String?,
        descs: List<OverlayDesc>, w: Int, h: Int, picture: RectF, grow: Float, clear: List<RectF>, gap: Float,
        hidden: Float = 0.5f, prefix: String = image!!.removeSuffix(".png"),
    ) {
        fun touch(d: OverlayDesc) = touchArea(d, grow)
        val p = "overlay$index"
        cfg.append("${p}_name = \"$name\"\n")
        // Where the engine draws the game, keeping its aspect inside this box; GL counts y from the bottom.
        cfg.append(
            String.format(
                Locale.US, "%s_viewport = \"%.5f,%.5f,%.5f,%.5f\"\n",
                p, picture.left / w, (h - picture.bottom) / h, picture.width() / w, picture.height() / h,
            )
        )
        if (image != null) cfg.append("${p}_overlay = \"$image\"\n")
        cfg.append("${p}_full_screen = true\n")
        cfg.append("${p}_normalized = true\n")
        cfg.append("${p}_range_mod = 1.0\n")
        cfg.append("${p}_alpha_mod = 1.0\n")
        cfg.append("${p}_descs = ${descs.size + 1}\n")
        descs.forEachIndexed { i, d ->
            val area = touch(d)
            cfg.append(
                String.format(
                    Locale.US, "%s_desc%d = \"%s,%.5f,%.5f,%s,%.5f,%.5f\"\n",
                    p, i, bindOf(d.name), area.centerX() / w, area.centerY() / h, if (d.radial) "radial" else "rect",
                    area.width() / 2f / w, area.height() / 2f / h,
                )
            )
            when {
                d.radial -> stick(cfg, p, i, d, prefix, startReach(descs, i, grow, clear, gap, w, h))
                d.dpad != null -> {
                    OverlayLines.dpad(cfg, "${p}_desc$i", d.dpad, startReach(descs, i, grow, clear, gap, w, h))
                    if (d.knob != null) OverlayLines.knob(cfg, "${p}_desc$i", knobImage(prefix, i))
                }
            }
            if (d.image != null) cfg.append("${p}_desc${i}_overlay = \"${ownImage(prefix, i)}\"\n")
            d.next?.let { cfg.append("${p}_desc${i}_next_target = \"$it\"\n") }
            if (d.name == ANCHOR_ACTION) cfg.append("${p}_desc${i}_anchor_toggle = true\n")
            d.lit?.let {
                cfg.append("${p}_desc${i}_overlay = \"${litImage(prefix, it)}\"\n")
                cfg.append("${p}_desc${i}_lit_by = \"${it.state}\"\n")
            }
        }
        // Unreachable switch that lets auto-rotate find the other orientation.
        val n = descs.size
        val y = if (hidden == 0.5f) "0.5" else "%.5f".format(Locale.US, hidden)
        cfg.append("${p}_desc$n = \"overlay_next,0.5,$y,rect,0.00001,0.00001\"\n")
        cfg.append("${p}_desc${n}_next_target = \"$other\"\n")
    }

    /**
     * The bind a desc named [name] is written with: the anchor key presses nothing itself (its `_anchor_toggle` flips
     * the engine's anchor mode), the rest press what they are named.
     */
    internal fun bindOf(name: String) = if (name == ANCHOR_ACTION) "nul" else name

    /**
     * Where a touch on [d] registers: its rect [grow] times, except a stick's range, a lit image, an own image and a
     * D-pad's circle.
     */
    internal fun touchArea(d: OverlayDesc, grow: Float): RectF {
        return scaled(d.rect, if (d.radial || d.lit != null || d.image != null || d.dpad != null) 1f else grow)
    }

    /** [r] [g] times its size, about its centre. */
    private fun scaled(r: RectF, g: Float): RectF {
        val hw = r.width() / 2f * g
        val hh = r.height() / 2f * g
        return RectF(r.centerX() - hw, r.centerY() - hh, r.centerX() + hw, r.centerY() + hh)
    }

    /**
     * All [d] takes of the screen, as touched: a stick's well, a D-pad's rect [OverlayDesc.reach] times its half-size
     * on each side (the least a touch on it starts over), anything else its [touchArea].
     */
    internal fun footprint(d: OverlayDesc, grow: Float): RectF {
        d.well?.let { return it }
        if (d.dpad == null) return touchArea(d, grow)
        val hw = d.rect.width() / 2f
        val hh = d.rect.height() / 2f
        return RectF(
            d.rect.centerX() - hw * d.reach[0], d.rect.centerY() - hh * d.reach[1],
            d.rect.centerX() + hw * d.reach[2], d.rect.centerY() + hh * d.reach[3],
        )
    }

    /**
     * Stick [d] at desc [i], whose rect is its knob's travel: the value saturates where the knob stops (or sooner, at
     * its [OverlayDesc.saturate]; a stick with no well and so no knob's travel, over its whole frame, at 0.65), its
     * knob is the image the engine moves with the thumb, and a touch on it starts as far as [reach] goes.
     */
    private fun stick(cfg: StringBuilder, p: String, i: Int, d: OverlayDesc, prefix: String, reach: FloatArray) {
        val pct = when {
            d.well == null -> "0.65"
            d.saturate == 1f -> "1.0"
            else -> String.format(Locale.US, "%.3f", d.saturate)
        }
        cfg.append("${p}_desc${i}_saturate_pct = $pct\n")
        if (d.knob != null) OverlayLines.knob(cfg, "${p}_desc$i", knobImage(prefix, i))
        OverlayLines.stickZone(cfg, "${p}_desc$i", reach)
    }

    /**
     * Where a touch can start the movement control at [i] of [descs], as its reach (left, top, right, bottom): see
     * [TouchZones]. Its neighbours are the other controls as touched and the [clear] areas. A "nul" desc is an image
     * (an app control's, a panel), no control: the zone may reach over it.
     */
    private fun startReach(
        descs: List<OverlayDesc>, i: Int, grow: Float, clear: List<RectF>, gap: Float, w: Int, h: Int,
    ): FloatArray {
        val d = descs[i]
        val others = descs.filterIndexed { j, o -> j != i && o.name != "nul" }
        val (peers, neighbours) = others.partition { it.dpad != null || it.radial }
        val zone = TouchZones.startZone(
            drawn(d).box(), startFloor(d, grow).box(),
            neighbours.map { footprint(it, grow).box() } + clear.map { it.box() },
            peers.map { startFloor(it, grow).box() },
            gap, w.toFloat(), h.toFloat(),
        )
        return TouchZones.reach(d.rect.box(), zone)
    }

    /**
     * The least movement control [d] is touched over, whatever crowds it: a D-pad's rect and all it takes of the
     * screen, a stick's knob travel and the square inside its circle ([TouchZones.inscribed]).
     */
    private fun startFloor(d: OverlayDesc, grow: Float): RectF {
        if (d.dpad != null) return RectF(d.rect).apply { union(footprint(d, grow)) }
        val square = TouchZones.inscribed((d.well ?: d.rect).box())
        return RectF(square.x, square.y, square.right, square.bottom).apply { if (d.well != null) union(d.rect) }
    }

    /** What movement control [d] shows on the screen: a D-pad's cross around its circle's travel, a stick's well. */
    private fun drawn(d: OverlayDesc): RectF =
        if (d.dpad == null) d.well ?: d.rect else scaled(d.rect, 1f / OverlayLines.DPAD_TRAVEL)

    private fun RectF.box() = SkinBox(left, top, width(), height())
}
