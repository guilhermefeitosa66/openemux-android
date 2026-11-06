package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.SkinBackground
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// A Delta/Manic package made into a skin in OpenEmux's own format (issue 797), decided without Android graphics so it
// runs as JVM unit tests: which layouts the copy gets, where every element goes and where each of its images comes
// from (PackageConverter does the bitmap work), and how a control is lifted off the package's art.

/** A rect in whole pixels: of the package's art, or of a layout (the art's pixels shifted by the layout's offset). */
data class PixelRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right get() = x + w
    val bottom get() = y + h

    /** The part of this rect inside a [width] x [height] space; null when nothing is. */
    fun clamp(width: Int, height: Int): PixelRect? {
        val l = x.coerceIn(0, width)
        val t = y.coerceIn(0, height)
        val r = right.coerceIn(0, width)
        val b = bottom.coerceIn(0, height)
        return PixelRect(l, t, r - l, b - t).takeIf { it.w > 0 && it.h > 0 }
    }

    fun shift(dy: Int) = copy(y = y + dy)

    fun skin() = SkinRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
}

/** Where one image of a converted element comes from. */
sealed interface ImageSource {
    /** The art's [frame] (art pixels), lifted off it and put at ([dx], [dy]) in the image, the rest transparent. */
    data class Art(val frame: PixelRect, val dx: Int, val dy: Int) : ImageSource

    /**
     * The package's own image [entry] (a D-pad's circle, a stick's knob): its centred square [crop] (a share of its
     * shorter side) drawn [fill] of the image across, centred.
     */
    data class Entry(val entry: String, val crop: Float, val fill: Float) : ImageSource

    /** The slow-motion glyph the package renderer adds, in the ink of the art's [model] frame (fast forward's). */
    data class SlowMotion(val model: PixelRect) : ImageSource
}

/** One element of a converted layout and where each of its images ([SkinElement.imageFiles], in order) comes from. */
data class PlannedElement(val element: SkinElement, val sources: List<ImageSource>)

/**
 * One layout of the copy: the representation it is made from ([family] and [orientation], also the key two layouts
 * drawn from the same art share), its art [asset] at [artW] x [artH] pixels, put [offsetY] pixels down in the layout,
 * [translucent] art drawn at the package renderer's 70 %, and the [layout] with its [elements].
 */
data class PlannedLayout(
    val family: String, val orientation: String, val asset: String, val artW: Int, val artH: Int, val offsetY: Int,
    val translucent: Boolean, val layout: SkinLayout, val elements: List<PlannedElement>,
) {
    val art get() = "$family/$orientation"
}

/** A package's copy as planned: [layouts] by id, every one the package's art covers. */
data class PackagePlan(val console: Console, val name: String, val layouts: Map<SkinLayoutId, PlannedLayout>)

/** What lifting a control off the art gave: its pixels ([px], [w] x [h], transparent where the art shows) and the colour around it. */
class LiftedArt(val px: IntArray, val w: Int, val h: Int, val surround: Int, val clean: Boolean)

/**
 * What PackageConverter learnt about one element: the image files it could not make ([missing]: the format then draws
 * them from colours) and the colour of what stands out in its frame ([color], for that fallback).
 */
data class ConvertedElement(val missing: Set<String> = emptySet(), val color: Int? = null)

object PackageConversion {
    /** The package renderer's opacity for a translucent skin's art (0xB3 of 0xFF). */
    const val TRANSLUCENT_ALPHA = 0xB3

    /** The scale a PDF (or an art of unknown size) is drawn at, from the package's mapping units. */
    const val VECTOR_SCALE = 3f

    /** The long side a PDF art is drawn at, at most. */
    const val MAX_VECTOR_SIDE = 3000

    /** How far a border pixel may be from the colour around a control, out of 255, for the border to count as plain. */
    const val EDGE_TOLERANCE = 24

    /** The share of a control's border that must be plain for it to be lifted off the art. */
    const val CLEAN_SHARE = 0.8f

    /** How far a pixel must be from the colour around a control, out of 255, to be part of the control. */
    const val CUT_TOLERANCE = 16

    /** The format's background colour of a copy: transparent, as the room around the package's art. */
    const val TRANSPARENT = 0x00000000

    /** The format's usual ids (docs/skin-format.md) of the names that have one other than themselves. */
    private val IDS = mapOf(
        "quickSave" to "save", "quickLoad" to "load", "toggleFastForward" to "fast-forward", "fastForward" to "fast-forward",
        "slowMotion" to "slow-motion", "reset" to "restart", "analogStick" to "stick", "rightAnalogStick" to "stick2",
        SkinFormat.DPAD_BIND to "dpad",
    )

    /** The groups the editor moves elements with. */
    private val GROUPS = mapOf(
        "a" to "face", "b" to "face", "c" to "face", "x" to "face", "y" to "face", "z" to "face",
        "l" to "shoulders-left", "l1" to "shoulders-left", "l2" to "shoulders-left", "l3" to "shoulders-left",
        "r" to "shoulders-right", "r1" to "shoulders-right", "r2" to "shoulders-right", "r3" to "shoulders-right",
        "start" to "center", "select" to "center", "mode" to "center",
        "cUp" to "c-buttons", "cDown" to "c-buttons", "cLeft" to "c-buttons", "cRight" to "c-buttons",
    )

    private val DIRECTIONS = listOf("up", "down", "left", "right")

    /**
     * The copy of the package [info] (its info.json) for [console], named [name]: one layout per form and orientation
     * the package covers ([SkinStore.packageForms]: Phone from its iPhone art, Tablet and Fold from its iPad art).
     * [artSize] gives an art entry's size in pixels, null when it has none of its own (a PDF): it is then drawn at
     * [VECTOR_SCALE].
     */
    fun plan(info: JSONObject, console: Console, name: String, artSize: (String) -> Pair<Int, Int>?): PackagePlan {
        val reps = info.getJSONObject("representations")
        val forms = SkinStore.packageForms(info)
        if (forms.isEmpty()) throw IllegalArgumentException("the package has no art for both orientations")
        val planned = mutableMapOf<String, PlannedLayout>()
        val layouts = SkinLayoutId.entries.filter { it.form in forms }.associateWith { id ->
            val family = if (id.form == SkinForm.PHONE) "iphone" else "ipad"
            val orientation = id.orientation.id
            planned.getOrPut("$family/$orientation") {
                layout(representation(reps, family, orientation), console, family, orientation, artSize)
            }
        }
        return PackagePlan(console, name, layouts)
    }

    /** The [family]'s art in [orientation], edge-to-edge before the classic one (the package renderer's order). */
    private fun representation(reps: JSONObject, family: String, orientation: String): JSONObject =
        listOf("edgeToEdge", "standard").firstNotNullOf { reps.optJSONObject(family)?.optJSONObject(it)?.optJSONObject(orientation) }

    /** The art entry a representation draws: resizable (a PDF) when there is one, the largest PNG otherwise. */
    fun assetName(assets: JSONObject): String {
        val byKey = assets.keys().asSequence().associateBy({ it.lowercase(Locale.US) }, { assets.getString(it) })
        return listOf("resizable", "large", "medium", "small").firstNotNullOf { byKey[it] }
    }

    private fun layout(
        rep: JSONObject, console: Console, family: String, orientation: String, artSize: (String) -> Pair<Int, Int>?,
    ): PlannedLayout {
        val size = rep.getJSONObject("mappingSize")
        val mw = size.getDouble("width").toFloat()
        val mh = size.getDouble("height").toFloat()
        if (mw <= 0f || mh <= 0f) throw IllegalArgumentException("$family $orientation has no mapping size")
        val asset = assetName(rep.getJSONObject("assets"))
        val own = artSize(asset)
        val s = own?.let { it.first / mw } ?: min(VECTOR_SCALE, MAX_VECTOR_SIDE / maxOf(mw, mh))
        val artW = own?.first ?: (mw * s).roundToInt()
        // The package renderer stretches the art over the mapping size: an art of another shape is drawn that way too.
        val artH = (mh * s).roundToInt()
        fun px(v: Double) = (v * s).roundToInt()
        fun rect(f: JSONObject) = PixelRect(px(f.getDouble("x")), px(f.getDouble("y")),
            px(f.getDouble("x") + f.getDouble("width")) - px(f.getDouble("x")), px(f.getDouble("y") + f.getDouble("height")) - px(f.getDouble("y")))

        // The screens the game goes in (the DS has two, top first); the ones with filters are blurred backdrops.
        val frames = rep.optJSONArray("screens")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.has("filters") && it.has("outputFrame") }.map { it.getJSONObject("outputFrame") }
        }.orEmpty().ifEmpty { listOfNotNull(rep.optJSONObject("gameScreenFrame")) }
        val portrait = orientation == SkinOrientation.PORTRAIT.id
        // Only the controls (Delta's own skins): upright, the game goes above the art, as wide as it; sideways, the
        // controls lie over the whole game.
        val offsetY = if (frames.isEmpty() && portrait) (artW / console.gameAspect).roundToInt() else 0
        val layoutW = artW
        val layoutH = artH + offsetY
        val screens = if (frames.isEmpty()) {
            listOf(PixelRect(0, 0, layoutW, if (portrait) offsetY else layoutH))
        } else {
            frames.map { rect(it).clamp(artW, artH) ?: throw IllegalArgumentException("a screen of $family $orientation is outside its art") }
                .sortedBy { it.y }.map { it.shift(offsetY) }
        }

        val items = rep.optJSONArray("items") ?: JSONArray()
        val boxes = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            item.optJSONObject("frame")?.takeIf { item.opt("inputs") is JSONArray }?.let(::packageBox)
        }
        val ff = fastForwardItem(items)
        val slow = ff?.let { slowMotionLayout(boxes, it) }
        val defaultEdges = rep.optJSONObject("extendedEdges")
        val elements = mutableListOf<PlannedElement>()
        val ids = mutableSetOf<String>()
        fun id(bind: String): String {
            val base = IDS[bind] ?: kebab(bind)
            return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in ids }.also { ids += it }
        }

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val frameJson = item.optJSONObject("frame") ?: continue
            val frame = rect(frameJson).clamp(artW, artH) ?: continue
            val edges = item.optJSONObject("extendedEdges") ?: defaultEdges
            fun edge(k: String) = edges?.optDouble(k, 0.0)?.let(::px) ?: 0
            when (val inputs = item.opt("inputs")) {
                is JSONArray -> {
                    val bind = (0 until inputs.length()).map { inputs.optString(it) }.firstOrNull { it in SkinFormat.BUTTON_BINDS } ?: continue
                    if (bind == ImportedSkinRenderer.SCREENS_INPUT && console != Console.NDS) continue
                    // A key slow motion made room among takes its narrower cell, its art centred in it.
                    val cell = slow?.moved?.get(i)?.let { rect(it, s).clamp(artW, artH) } ?: frame
                    if (i == ff && slow != null) {
                        rect(slow.slowMotion, s).clamp(artW, artH)?.let { slot ->
                            elements += action("slowMotion", slot, offsetY, id("slowMotion"), ImageSource.SlowMotion(frame))
                        }
                    }
                    if (bind in SkinFormat.ACTION_BINDS) {
                        elements += action(bind, cell, offsetY, id(bind), ImageSource.Art(frame, (cell.w - frame.w) / 2, (cell.h - frame.h) / 2))
                    } else {
                        // A button keeps the package's extended edges (unless it moved): its rect is where a touch counts.
                        val touch = if (cell != frame) cell else {
                            PixelRect(frame.x - edge("left"), frame.y - edge("top"), frame.w + edge("left") + edge("right"), frame.h + edge("top") + edge("bottom"))
                                .clamp(artW, artH)!!
                        }
                        val e = SkinElement(id(bind), SkinElementType.BUTTON, bind, touch.shift(offsetY).skin(), GROUPS[bind])
                        val dx = cell.x + (cell.w - frame.w) / 2 - touch.x
                        val dy = cell.y + (cell.h - frame.h) / 2 - touch.y
                        elements += PlannedElement(e, listOf(ImageSource.Art(frame, dx, dy)))
                    }
                }
                is JSONObject -> elements += directional(inputs, item, frame, offsetY, s, ::id)
            }
        }
        val layout = SkinLayout(
            SkinSize(layoutW, layoutH), screens.map { it.skin() }, SkinBackground(TRANSPARENT),
            elements = elements.map { it.element },
        )
        return PlannedLayout(family, orientation, asset, artW, artH, offsetY, rep.optBoolean("translucent", false), layout, elements)
    }

    private fun action(bind: String, cell: PixelRect, offsetY: Int, id: String, source: ImageSource) =
        PlannedElement(SkinElement(id, SkinElementType.ACTION, bind, cell.shift(offsetY).skin(), "actions"), listOf(source))

    /**
     * A directional item: a D-pad (plain up, down, left, right), a stick (analog names, a thumbstick, or directions
     * the format cannot all take: the package renderer's radial fallback), or, for four buttons on one item (an N64
     * skin's C buttons), four buttons over the item's arms.
     */
    private fun directional(
        inputs: JSONObject, item: JSONObject, frame: PixelRect, offsetY: Int, s: Float, id: (String) -> String,
    ): List<PlannedElement> {
        val names = DIRECTIONS.map { inputs.optString(it) }
        val art = ImageSource.Art(frame, 0, 0)
        val rect = frame.shift(offsetY).skin()
        fun stick(bind: String): PlannedElement {
            val knob = item.optJSONObject("thumbstick")?.optString("name")?.takeIf { it.isNotEmpty() }
            val r = minOf(frame.w, frame.h) / 2f
            // The knob is drawn half the well across; the package draws it at its own size, so it fills that much of the image.
            val kw = item.optJSONObject("thumbstick")?.optDouble("width", 0.0)?.toFloat()?.takeIf { it > 0f }
            val fill = kw?.let { (it * s / r).coerceAtMost(1f) } ?: 1f
            val knobSource = knob?.let { ImageSource.Entry(it, 1f, fill) }
            val e = SkinElement(id(bind), SkinElementType.STICK, bind, rect, "sticks")
            return PlannedElement(e, listOfNotNull(art, knobSource))
        }
        if (names.any { it.startsWith("rightAnalogStick") || it.startsWith("cStick") }) return listOf(stick("rightAnalogStick"))
        if (names.any { it.startsWith("analogStick") } || item.has("thumbstick")) return listOf(stick("analogStick"))
        if (names == DIRECTIONS) {
            // The package draws its circle over the D-pad's travel; the format draws the ball over 55 % of it.
            val ball = item.optString("knob").takeIf { it.isNotEmpty() }?.let { ImageSource.Entry(it, BALL_OF_TRAVEL, 1f) }
            val e = SkinElement(id(SkinFormat.DPAD_BIND), SkinElementType.DPAD, SkinFormat.DPAD_BIND, rect, "dpad")
            return listOf(PlannedElement(e, listOfNotNull(art, ball)))
        }
        if (names.all { it in SkinFormat.BUTTON_BINDS && it !in SkinFormat.ACTION_BINDS }) {
            val tw = frame.w / 3
            val th = frame.h / 3
            val arms = listOf(
                PixelRect(frame.x + tw, frame.y, frame.w - 2 * tw, th), PixelRect(frame.x + tw, frame.bottom - th, frame.w - 2 * tw, th),
                PixelRect(frame.x, frame.y + th, tw, frame.h - 2 * th), PixelRect(frame.right - tw, frame.y + th, tw, frame.h - 2 * th),
            )
            return names.zip(arms).filter { (_, arm) -> arm.w > 0 && arm.h > 0 }.map { (bind, arm) ->
                PlannedElement(SkinElement(id(bind), SkinElementType.BUTTON, bind, arm.shift(offsetY).skin(), GROUPS[bind]), listOf(ImageSource.Art(arm, 0, 0)))
            }
        }
        return listOf(stick("analogStick"))
    }

    /** The D-pad's ball across, as a share of its travel (the one OpenEmuxSkinRenderer draws it at). */
    const val BALL_OF_TRAVEL = OpenEmuxSkinRenderer.BALL_SHARE

    private fun rect(b: SkinBox, s: Float): PixelRect {
        val l = (b.x * s).roundToInt()
        val t = (b.y * s).roundToInt()
        return PixelRect(l, t, (b.right * s).roundToInt() - l, (b.bottom * s).roundToInt() - t)
    }

    /** An input name as an element id: camelCase to lowercase words with single hyphens (cUp: c-up). */
    fun kebab(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "key" }

    /** The colour every pixel of [px] (a [w]-wide ARGB art) is drawn with at [TRANSLUCENT_ALPHA]: in place. */
    fun fade(px: IntArray) {
        for (i in px.indices) {
            val c = px[i]
            px[i] = ((((c ushr 24) * TRANSLUCENT_ALPHA + 127) / 255) shl 24) or (c and 0xFFFFFF)
        }
    }

    /** How far apart two ARGB colours are, out of 255: the larger of their alphas' and their premultiplied channels' differences. */
    fun distance(a: Int, b: Int): Int {
        val aa = a ushr 24
        val ba = b ushr 24
        // Per pixel of every control: no array of shifts to allocate on each call.
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) * aa - ((b shr shift) and 0xFF) * ba) / 255
        return maxOf(abs(aa - ba), channel(16), channel(8), channel(0))
    }

    /** The median colour of [colors], channel by channel (alpha included). */
    fun median(colors: IntArray): Int {
        fun channel(shift: Int) = colors.map { (it ushr shift) and 0xFF }.sorted()[colors.size / 2]
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * The control in [frame] of the art [px] ([w] x [h] ARGB), lifted off it: the pixels that differ from the colour
     * around it (the median of the frame's border) as they are, the others transparent. It is clean when at least
     * [CLEAN_SHARE] of the border is that colour: a control on a plain surface. [frame] lies inside the art.
     */
    fun lift(px: IntArray, w: Int, frame: PixelRect): LiftedArt {
        fun at(x: Int, y: Int) = px[y * w + x]
        val border = buildList {
            for (x in frame.x until frame.right) { add(at(x, frame.y)); if (frame.h > 1) add(at(x, frame.bottom - 1)) }
            for (y in frame.y + 1 until frame.bottom - 1) { add(at(frame.x, y)); if (frame.w > 1) add(at(frame.right - 1, y)) }
        }.toIntArray()
        val surround = median(border)
        val clean = border.count { distance(it, surround) <= EDGE_TOLERANCE } >= border.size * CLEAN_SHARE
        val out = IntArray(frame.w * frame.h)
        for (y in 0 until frame.h) for (x in 0 until frame.w) {
            val c = at(frame.x + x, frame.y + y)
            if (distance(c, surround) > CUT_TOLERANCE) out[y * frame.w + x] = c
        }
        return LiftedArt(out, frame.w, frame.h, surround, clean)
    }

    /** Takes [lifted] (cut at [frame]) off the art [px] ([w] wide): each of its pixels painted the colour around it. */
    fun erase(px: IntArray, w: Int, frame: PixelRect, lifted: LiftedArt) {
        for (y in 0 until frame.h) for (x in 0 until frame.w) {
            if (lifted.px[y * frame.w + x] != 0) px[(frame.y + y) * w + frame.x + x] = lifted.surround
        }
    }

    /** [lifted] put at ([dx], [dy]) in an [iw] x [ih] image, the rest transparent: what sticks out is cut off. */
    fun place(lifted: LiftedArt, iw: Int, ih: Int, dx: Int, dy: Int): IntArray {
        val out = IntArray(iw * ih)
        for (y in 0 until lifted.h) for (x in 0 until lifted.w) {
            val tx = x + dx
            val ty = y + dy
            if (tx in 0 until iw && ty in 0 until ih) out[ty * iw + tx] = lifted.px[y * lifted.w + x]
        }
        return out
    }

    /**
     * The glyph of a lifted key: its ink (the pixel furthest from the colour around it) and its height in pixels (the
     * rows where it stands out at least half as much), null when nothing stands out.
     */
    fun glyph(lifted: LiftedArt): Pair<Int, Int>? {
        val diff = lifted.px.map { if (it == 0) 0 else distance(it, lifted.surround) }
        val most = diff.maxOrNull()?.takeIf { it > 0 } ?: return null
        val rows = (0 until lifted.h).filter { y -> (0 until lifted.w).any { x -> diff[y * lifted.w + x] * 2 >= most } }
        return lifted.px[diff.indexOf(most)] to rows.last() - rows.first() + 1
    }

    /**
     * The slow-motion glyph's size in its [w] x [h] slot, as the package renderer draws it: twice fast forward's glyph
     * height ([model], null when it has none: 60 % of the slot), within 90 % of the slot.
     */
    fun slowMotionSize(model: Int?, w: Int, h: Int): Float = minOf(model?.times(2f) ?: (h * 0.6f), w * 0.9f, h * 0.9f)

    /** The colour of what stands out in [frame] of the art [px] ([w] wide), for an element drawn from colours: the median of its inner pixels. */
    fun fill(px: IntArray, w: Int, frame: PixelRect): Int {
        val inner = PixelRect(frame.x + frame.w / 4, frame.y + frame.h / 4, maxOf(1, frame.w / 2), maxOf(1, frame.h / 2))
        return median(IntArray(inner.w * inner.h) { i -> px[(inner.y + i / inner.w) * w + inner.x + i % inner.w] } ) or (0xFF shl 24)
    }

    /** White or black, whichever reads on [color]. */
    fun ink(color: Int): Int {
        val luma = 0.299 * ((color shr 16) and 0xFF) + 0.587 * ((color shr 8) and 0xFF) + 0.114 * (color and 0xFF)
        return if (luma > 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** The letter or word a button drawn from colours shows: its bind's, the hardware's own in English. */
    fun label(bind: String): String? = when {
        bind in setOf("start", "select", "mode") -> bind.uppercase(Locale.US)
        bind.startsWith("c") && bind.length > 2 -> "C"
        bind.length <= 2 -> bind.uppercase(Locale.US)
        else -> null
    }

    /**
     * [plan]'s skin with what the conversion learnt ([results], by layout and element index): an element whose main
     * image could not be made is drawn from colours instead (its [ConvertedElement.color], a label and a shape).
     */
    fun finish(plan: PackagePlan, results: Map<SkinLayoutId, List<ConvertedElement>>): OpenEmuxSkin {
        val layouts = plan.layouts.mapValues { (id, planned) ->
            val done = results[id].orEmpty()
            planned.layout.copy(elements = planned.elements.mapIndexed { i, p ->
                val result = done.getOrNull(i) ?: ConvertedElement()
                val e = p.element
                if (e.imageFiles.first() !in result.missing) return@mapIndexed e
                val color = result.color ?: return@mapIndexed e
                when (e.type) {
                    SkinElementType.BUTTON, SkinElementType.KEYPAD -> e.copy(
                        colors = mapOf("button" to color, "letter" to ink(color)), label = label(e.bind),
                        shape = if (e.rect.width > e.rect.height * 1.4f) SkinShape.PILL else SkinShape.CIRCLE,
                    )
                    SkinElementType.DPAD -> e.copy(colors = mapOf("pad" to color))
                    SkinElementType.STICK -> e.copy(colors = mapOf("base" to color))
                    SkinElementType.ACTION -> e
                }
            })
        }
        return OpenEmuxSkin(plan.console, plan.name, layouts)
    }

    /** The size of each image file of [layout], as the format and the templates have it (SkinTemplate). */
    fun sizes(layout: SkinLayout): Map<String, Pair<Int, Int>> = SkinTemplate.images(layout).associate { it.file to (it.width to it.height) }
}
