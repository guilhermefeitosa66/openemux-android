package com.openemux.mobile.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.PathParser
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.ui.Icons
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a controller skin in the package format of the iOS emulators Delta
 * and Manic EMU (a .deltaskin or .manicskin: a zip with info.json and PDF or
 * PNG art) into a RetroArch overlay, the same kind of skin.cfg
 * [SkinRenderer] writes for the drawn skins. The app's own skins for the
 * twelve best-known consoles ship in this format (assets/skins, built by
 * tools/skins).
 *
 * The device's portrait and landscape representations become one overlay
 * each; the art is scaled from the skin's mappingSize to the screen, the
 * game's screens are cut out of it and become the overlay's viewport, and
 * every item becomes a touch area bound to a RetroPad button or a hotkey.
 */
class ImportedSkinRenderer {

    /** What a drawOverlay laid out: the touch areas, the game's box, and what a stick's zone keeps clear of. */
    private class Drawn(val descs: List<OverlayDesc>, val viewport: RectF, val clear: List<RectF>, val gap: Float)

    /** Where the representation lands on screen: mapping units times [s], from ([ox], [oy]). */
    private class Placement(val s: Float, val ox: Float, val oy: Float) {
        fun rect(x: Double, y: Double, w: Double, h: Double) =
            RectF(ox + x.toFloat() * s, oy + y.toFloat() * s, ox + (x + w).toFloat() * s, oy + (y + h).toFloat() * s)

        fun rect(b: SkinBox) = rect(b.x.toDouble(), b.y.toDouble(), b.w.toDouble(), b.h.toDouble())
    }

    /**
     * A glyph lifted off the art: the [w] x [h] box at ([left], [top]), its pixels [px] as read, how much of each is
     * glyph for the lit pill ([alpha], 0 to 1, the faint shading left out), how much of each the glyph covers ([cover],
     * its difference from the colour around it over the glyph's full one, anti-aliased edges included), the colour
     * around it ([bg]), the glyph's own colour ([ink]) and its height in pixels ([height]).
     */
    private class Lifted(
        val left: Int, val top: Int, val w: Int, val h: Int, val px: IntArray, val alpha: FloatArray, val cover: FloatArray,
        val bg: Int, val ink: Int, val height: Int,
    )

    /**
     * [tablet] picks the skin's iPad art over its iPhone art; [hardcore] leaves fast forward and slow motion out
     * (drawn dimmed, with no touch area).
     * [first] (a page's name) is the overlay RetroArch opens on: a skin redrawn in game keeps the one shown.
     * [ndsLayouts] (the saved portrait and landscape NDS layouts) draws one page per layout and orientation when the
     * package has the art for them (the app's own DS skin: each representation's "layouts", tools/skins), named and
     * linked as the drawn skin's (ndsFlatSpecs); [wroteNdsLayouts] says whether it did.
     */
    fun render(
        skinFile: File, console: Console, width: Int, height: Int, outDir: File,
        cutout: Int = 0, tablet: Boolean = false, hardcore: Boolean = false, first: String? = null,
        ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null,
    ): File {
        this.console = console
        this.hardcore = hardcore
        outDir.mkdirs()
        val portraitW = minOf(width, height)
        val portraitH = maxOf(width, height) - cutout
        ZipFile(skinFile).use { zip ->
            val info = SkinStore.packageInfo(zip)
            Log.i(TAG, "importing ${info.optString("name")} (${info.optString("identifier")})")
            val reps = info.getJSONObject("representations")
            val own = mapOf("portrait" to representation(reps, "portrait", tablet), "landscape" to representation(reps, "landscape", tablet))
            val nds = ndsLayouts?.takeIf { console == Console.NDS && own.values.all(::hasNdsLayouts) }
            wroteNdsLayouts = nds != null
            val pages = packagePages(nds, tablet, first)

            val cfg = StringBuilder("overlays = ${pages.size}\n")
            pages.forEachIndexed { i, page ->
                val base = own.getValue(if (page.portrait) "portrait" else "landscape")
                val rep = page.layout?.let { l -> base.optJSONObject("layouts")?.optJSONObject(l) } ?: base
                val w = if (page.portrait) portraitW else portraitH
                val h = if (page.portrait) portraitH else portraitW
                // Pages with the same art (top only and bottom only) name the same image: the engine loads it once.
                // A file name of its own: an asset in a folder of the zip ("art/portrait.pdf") would name a folder outDir lacks.
                val art = assetName(rep.getJSONObject("assets")).substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
                val image = "${if (page.portrait) "portrait" else "landscape"}-$art.png"
                val drawn = drawOverlay(zip, rep, page.portrait, w, h, outDir, image, page.screensNext, page.name.replace('@', '-'))
                // The skin's own extended edges already widen the touch areas.
                OverlayWriter.write(
                    cfg, i, page.name, page.rotate, image, drawn.descs, w, h, drawn.viewport, grow = 1f, clear = drawn.clear, gap = drawn.gap,
                    prefix = page.name.replace('@', '-'),
                )
            }
            return File(outDir, "skin.cfg").apply { writeText(cfg.toString()) }
        }
    }

    private var console: Console = Console.GBA
    private var hardcore = false

    /** Whether the last [render] drew one page per NDS layout. */
    var wroteNdsLayouts = false
        private set

    /** Whether a representation has the art of every NDS layout but its own (one over the other): tools/skins'. */
    private fun hasNdsLayouts(rep: JSONObject) =
        rep.optJSONObject("layouts")?.let { l -> NDS_ART_LAYOUTS.all { l.optJSONObject(it) != null } } == true

    /** The device's own art first (iPad on a tablet, iPhone on a phone), edge-to-edge before the classic one. */
    private fun representation(reps: JSONObject, orient: String, tablet: Boolean): JSONObject =
        (if (tablet) listOf("ipad", "iphone") else listOf("iphone", "ipad")).firstNotNullOfOrNull { family ->
            listOf("edgeToEdge", "standard").firstNotNullOfOrNull { reps.optJSONObject(family)?.optJSONObject(it)?.optJSONObject(orient) }
        } ?: error("no $orient representation")

    /**
     * [screensNext] is the page the NDS screens key steps to (none: the key is left out); [prefix] names the page's
     * own images.
     */
    private fun drawOverlay(
        zip: ZipFile, rep: JSONObject, portrait: Boolean, w: Int, h: Int, outDir: File, image: String,
        screensNext: String? = null, prefix: String = image.removeSuffix(".png"),
    ): Drawn {
        val size = rep.getJSONObject("mappingSize")
        val mw = size.getDouble("width").toFloat()
        val mh = size.getDouble("height").toFloat()
        // The screens the game goes in (the DS has two, one over the other); the ones with filters are
        // blurred backdrops RetroArch cannot draw.
        val frames = rep.optJSONArray("screens")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.has("filters") && it.has("outputFrame") }.map { it.getJSONObject("outputFrame") }
        }.orEmpty().ifEmpty { listOfNotNull(rep.optJSONObject("gameScreenFrame")) }
        val screen = frames.takeIf { it.isNotEmpty() }?.let { fs ->
            val l = fs.minOf { it.getDouble("x") }
            val t = fs.minOf { it.getDouble("y") }
            JSONObject().put("x", l).put("y", t)
                .put("width", fs.maxOf { it.getDouble("x") + it.getDouble("width") } - l)
                .put("height", fs.maxOf { it.getDouble("y") + it.getDouble("height") } - t)
        }

        val place: Placement
        val viewport: RectF
        if (screen != null) {
            // A whole-device skin: fit it, centred, and the game goes where it says.
            val s = minOf(w / mw, h / mh)
            place = Placement(s, (w - mw * s) / 2f, (h - mh * s) / 2f)
            viewport = place.rect(screen.getDouble("x"), screen.getDouble("y"), screen.getDouble("width"), screen.getDouble("height"))
        } else if (portrait) {
            // Only the controls (Delta's own skins): full width at the bottom, the game in the room above.
            val s = w / mw
            place = Placement(s, 0f, h - mh * s)
            viewport = RectF(0f, 0f, w.toFloat(), h - mh * s)
        } else {
            // Landscape controls laid over a full-screen game.
            val s = minOf(w / mw, h / mh)
            place = Placement(s, (w - mw * s) / 2f, (h - mh * s) / 2f)
            viewport = RectF(0f, 0f, w.toFloat(), h.toFloat())
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val art = place.rect(0.0, 0.0, mw.toDouble(), mh.toDouble())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Translucent skins sit over the game, so the picture shows through them.
        if (rep.optBoolean("translucent", false)) paint.alpha = 0xB3
        canvas.drawBitmap(rasterize(zip, assetName(rep.getJSONObject("assets")), art), null, art, paint)
        if (screen != null) canvas.drawRect(viewport, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })

        val defaultEdges = rep.optJSONObject("extendedEdges")
        val descs = mutableListOf<OverlayDesc>()
        val items = rep.optJSONArray("items") ?: JSONArray()
        val boxes = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            item.getJSONObject("frame").takeIf { item.get("inputs") is JSONArray }?.let { box(it) }
        }
        // Slow motion goes right before fast forward, in a slot made among the keys beside it.
        // The toggle wins over the hold key when a skin has both, whatever their order in the items.
        val ff = PACKAGE_FAST_FORWARD_INPUTS.firstNotNullOfOrNull { input ->
            (0 until items.length()).firstOrNull { i ->
                (items.getJSONObject(i).get("inputs") as? JSONArray)?.let { it.length() == 1 && it.getString(0) == input } == true
            }
        }
        val slow = ff?.let { slowMotionLayout(boxes, it) }
        if (ff != null && slow != null) respace(bitmap, slow, boxes, ff, place)
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val f = slow?.moved?.get(i) ?: box(item.getJSONObject("frame"))
            val e = item.optJSONObject("extendedEdges") ?: defaultEdges
            fun edge(k: String) = e?.optDouble(k, 0.0)?.toFloat() ?: 0f
            val frame = place.rect(f)
            // Fast forward's and slow motion's touch areas stop at the side they share, so one press never hits both.
            val speed = slow?.takeIf { i == ff }?.let { speedTouches(it.slowMotion, f, extended(it.slowMotion, ::edge), extended(f, ::edge)) }
            val touch = place.rect(speed?.second ?: extended(f, ::edge))
            if (speed != null && slow != null && !hardcore) {
                descs += OverlayDesc(SLOW_MOTION, place.rect(speed.first))
                OverlayWriter.LIT_BY[SLOW_MOTION]?.let { descs += litPill(bitmap, it, place.rect(slow.slowMotion), place.s) }
            }
            when (val inputs = item.get("inputs")) {
                is JSONObject -> {
                    val stick = item.optJSONObject("thumbstick")?.let { knob(zip, it, frame, place, paint) }
                    // knob is the app's own key (tools/skins): the D-pad's circle, when the package has its image.
                    // An unreadable circle leaves the D-pad without one, as a stick's unreadable knob does.
                    val circle = {
                        item.optString("knob").takeIf { it.isNotEmpty() }?.let { name ->
                            try {
                                findEntry(zip, name)?.let { entry -> zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } }
                            } catch (e: Exception) {
                                Log.w(TAG, "knob '$name' not readable", e)
                                null
                            }
                        }
                    }
                    directional(inputs, frame, touch, item.has("thumbstick"), stick, circle, descs)
                }
                is JSONArray -> {
                    // The app's own NDS screens key (tools/skins): a step to the next layout's page.
                    if (inputs.length() == 1 && inputs.getString(0) == SCREENS_INPUT) {
                        screensNext?.let { descs += OverlayDesc("overlay_next", touch, next = it) }
                        continue
                    }
                    val binds = (0 until inputs.length()).mapNotNull { bind(inputs.getString(it)) }
                    if (binds.isNotEmpty()) descs += OverlayDesc(binds.joinToString("|"), touch)
                    OverlayWriter.LIT_BY[binds.singleOrNull()]?.let { state ->
                        descs += litPill(bitmap, state, frame, place.s)
                    }
                }
            }
        }
        OverlayWriter.saveImages(outDir, image, bitmap, descs, prefix)
        // A whole-device skin keeps the game's picture clear of the sticks' zones; one over the game cannot.
        return Drawn(descs, viewport, listOfNotNull(viewport.takeIf { screen != null }), 2f * place.s)
    }

    /**
     * A stick's knob ([thumbstick], the item's knob image at its own size in mapping units) as an image the size
     * of the knob's travel, centred on the stick's [frame], or null when the image cannot be read.
     */
    private fun knob(zip: ZipFile, thumbstick: JSONObject, frame: RectF, place: Placement, paint: Paint): OverlayDesc? {
        val name = thumbstick.optString("name").ifEmpty { return null }
        val r = minOf(frame.width(), frame.height()) / 2f
        // A knob with no size given is half the well's.
        val kw = thumbstick.optDouble("width", 0.0).toFloat().let { if (it > 0f) it * place.s else r }
        val kh = thumbstick.optDouble("height", 0.0).toFloat().let { if (it > 0f) it * place.s else r }
        // The engine stretches the image over the stick's range: the range is the travel, unless the knob is
        // larger, and then the value still peaks at the travel. The app's own skins export the knob at the travel's size.
        val travel = r * OverlayWriter.KNOB_TRAVEL
        val half = maxOf(kw, kh) / 2f
        val range = if (half > travel * 1.02f) half else travel
        val side = Math.round(2f * range).coerceAtLeast(1)
        val target = RectF((side - kw) / 2f, (side - kh) / 2f, (side + kw) / 2f, (side + kh) / 2f)
        val image = try {
            rasterize(zip, name, target)
        } catch (e: Exception) {
            Log.w(TAG, "thumbstick '$name' not readable", e)
            return null
        }
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(image, null, target, paint)
        image.recycle()
        val cx = frame.centerX()
        val cy = frame.centerY()
        return OverlayDesc("", RectF(cx - range, cy - range, cx + range, cy + range), radial = true, well = frame, knob = bitmap, saturate = travel / range)
    }

    /**
     * A toggle's "on" look over its item's [frame] in the drawn [art], [s] pixels per mapping unit: the accent pill of
     * the drawn skins' strip (46 x 30 at 80 % of the frame's height, no wider than the frame less 8 units), with the
     * skin's own glyph lifted off the art and drawn over it in white, so it works for any skin, not only the bundled ones.
     */
    private fun litPill(art: Bitmap, state: String, frame: RectF, s: Float): OverlayDesc {
        val h = frame.height() * 0.8f
        val box = pillBox(frame, s)
        val bitmap = OverlayWriter.pill(box.width(), box.height(), h / 3f, h / 20f)
        glyph(art, box.left, box.top, box.width(), box.height())?.let { Canvas(bitmap).drawBitmap(it, 0f, 0f, null) }
        return OverlayDesc("nul", RectF(box), lit = OverlayLit(state, bitmap))
    }

    /**
     * Makes room for slow motion on the [art]: the glyphs of the keys [layout] moves are lifted off and drawn again at
     * their new places, in the skin's own colour, and slow motion's ring is drawn in its slot in fast forward's colour.
     * In hardcore both speed keys are drawn at 0.45, like the drawn skins' strip.
     */
    private fun respace(art: Bitmap, layout: SlowMotionLayout, boxes: List<SkinBox?>, ff: Int, place: Placement) {
        // Every glyph comes off before any goes back, so none is drawn over one still to be lifted.
        val lifted = layout.moved.keys.associateWith { i -> pillBox(place.rect(boxes[i]!!), place.s).let { lift(art, it.left, it.top, it.width(), it.height()) } }
        lifted.values.forEach { it?.let { l -> erase(art, l) } }
        val canvas = Canvas(art)
        val dim = if (hardcore) DISABLED_ALPHA else 1f
        lifted.forEach { (i, l) ->
            l ?: return@forEach
            val to = place.rect(layout.moved.getValue(i))
            val image = Bitmap.createBitmap(IntArray(l.px.size) { p -> color(l.ink, l.cover[p] * if (i == ff) dim else 1f) }, l.w, l.h, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(image, (to.centerX() - l.w / 2f).roundToInt().toFloat(), (to.centerY() - l.h / 2f).roundToInt().toFloat(), null)
            image.recycle()
        }
        val slot = place.rect(layout.slowMotion)
        val model = lifted[ff]
        // Fast forward's two triangles fill half the 24-unit icon's height; the ring is drawn at that icon's size.
        val size = minOf((model?.height?.times(2f)) ?: (slot.height() * 0.6f), slot.width() * 0.9f, slot.height() * 0.9f)
        val ink = model?.ink ?: 0xFFFFFFFF.toInt()
        canvas.drawPath(slowMotionPath(slot.centerX(), slot.centerY(), size), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(ink, dim)
            style = Paint.Style.STROKE; strokeWidth = 1.8f * size / 24f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        })
    }

    private fun slowMotionPath(cx: Float, cy: Float, size: Float): Path = PathParser.createPathFromPathData(Icons.SlowMotion).apply {
        transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(cx - size / 2f, cy - size / 2f) })
    }

    /** [ink] with its alpha times [share]. */
    private fun color(ink: Int, share: Float) = (((ink ushr 24) * share).roundToInt() shl 24) or (ink and 0xFFFFFF)

    /**
     * The glyph of [l] taken off [art]: every pixel of its box that differs from the colour around it gets that colour,
     * so not even the anti-aliased edges are left behind.
     */
    private fun erase(art: Bitmap, l: Lifted) {
        val x0 = maxOf(0, l.left)
        val y0 = maxOf(0, l.top)
        val x1 = minOf(art.width, l.left + l.w)
        val y1 = minOf(art.height, l.top + l.h)
        if (x1 <= x0 || y1 <= y0) return
        val out = IntArray(l.px.size) { i -> if (l.cover[i] > ERASE_MIN_COVER) l.bg else l.px[i] }
        art.setPixels(out, (y0 - l.top) * l.w + (x0 - l.left), l.w, x0, y0, x1 - x0, y1 - y0)
    }

    /** The box a toggle's pill takes over its item's [frame] (see [litPill]), whole pixels. */
    private fun pillBox(frame: RectF, s: Float): Rect {
        val h = frame.height() * 0.8f
        val w = minOf(h * 46f / 30f, frame.width() - 8f * s).toInt().coerceAtLeast(1)
        val ph = h.toInt().coerceAtLeast(1)
        val left = (frame.centerX() - w / 2f).roundToInt()
        val top = (frame.centerY() - ph / 2f).roundToInt()
        return Rect(left, top, left + w, top + ph)
    }

    /**
     * The glyph in the [w] x [h] box of [art] at ([left], [top]): whatever differs from the color around it (the median
     * of the box's edge), as white with that difference for alpha; null when nothing stands out.
     */
    private fun glyph(art: Bitmap, left: Int, top: Int, w: Int, h: Int): Bitmap? = lift(art, left, top, w, h)?.let { l ->
        Bitmap.createBitmap(IntArray(l.px.size) { i -> (l.alpha[i] * 0xFF).toInt() shl 24 or 0xFFFFFF }, w, h, Bitmap.Config.ARGB_8888)
    }

    /** What [glyph] reads: the glyph in the box with what it takes to erase it and draw it again; null when nothing stands out. */
    private fun lift(art: Bitmap, left: Int, top: Int, w: Int, h: Int): Lifted? {
        val px = IntArray(w * h)
        // Only the part of the box inside the art is read; the rest stays transparent.
        val x0 = maxOf(0, left)
        val y0 = maxOf(0, top)
        val x1 = minOf(art.width, left + w)
        val y1 = minOf(art.height, top + h)
        if (x1 > x0 && y1 > y0) art.getPixels(px, (y0 - top) * w + (x0 - left), w, x0, y0, x1 - x0, y1 - y0)
        val edge = (0 until w).flatMap { listOf(px[it], px[(h - 1) * w + it]) } + (0 until h).flatMap { listOf(px[it * w], px[it * w + w - 1]) }
        fun median(shift: Int) = edge.map { (it ushr shift) and 0xFF }.sorted()[edge.size / 2]
        val bg = intArrayOf(median(16), median(8), median(0))
        val diff = IntArray(px.size) { i ->
            val c = px[i]
            val d = maxOf(abs(((c shr 16) and 0xFF) - bg[0]), abs(((c shr 8) and 0xFF) - bg[1]), abs((c and 0xFF) - bg[2]))
            d * ((c ushr 24) and 0xFF) / 0xFF
        }
        val most = diff.maxOrNull() ?: 0
        if (most < GLYPH_MIN_DIFF) return null
        // The faintest fifth is the art's own shading; the glyph's anti-aliased edge ramps up to its full colour.
        val alpha = FloatArray(px.size) { i -> ((diff[i] - most * 0.2f) / (most * 0.5f)).coerceIn(0f, 1f) }
        val cover = FloatArray(px.size) { i -> diff[i].toFloat() / most }
        val rows = (0 until h).filter { y -> (0 until w).any { x -> cover[y * w + x] >= 0.5f } }
        val bgColor = (median(24) shl 24) or (bg[0] shl 16) or (bg[1] shl 8) or bg[2]
        return Lifted(left, top, w, h, px, alpha, cover, bgColor, px[diff.indexOf(most)], rows.last() - rows.first() + 1)
    }

    /**
     * A D-pad as one eight-way area, or a stick as an analog stick. The D-pad's rect is its circle's travel
     * about the centre of the [drawn] frame (the share of the half-size the drawn skins give it); the item's
     * whole [box] is where a touch starts, and that finger then steers it from anywhere. [circle] reads the
     * D-pad's circle, only once the item turns out to be a D-pad. A stick with a [knob] image is laid out as the
     * drawn skins' are; without one, as before, over its frame.
     */
    private fun directional(
        inputs: JSONObject, drawn: RectF, box: RectF, thumbstick: Boolean, knob: OverlayDesc?, circle: () -> Bitmap?,
        descs: MutableList<OverlayDesc>,
    ) {
        fun stick(name: String) =
            knob?.let { OverlayDesc(name, it.rect, radial = true, well = it.well, knob = it.knob, saturate = it.saturate) }
                ?: OverlayDesc(name, box, radial = true)
        val names = listOf("up", "down", "left", "right").map { inputs.optString(it) }
        if (names.any { it.startsWith("rightAnalogStick") || it.startsWith("cStick") }) {
            descs += stick(RADIAL_RIGHT)
            return
        }
        if (names.any { it.startsWith("analogStick") }) {
            descs += stick(RADIAL)
            return
        }
        val up = bind(inputs.optString("up"))
        val down = bind(inputs.optString("down"))
        val left = bind(inputs.optString("left"))
        val right = bind(inputs.optString("right"))
        if (thumbstick || up == null || down == null || left == null || right == null) {
            descs += stick(RADIAL)
            return
        }
        // A frame with no area (a malformed skin) would give the D-pad no travel: the touch box stands in.
        val frame = drawn.takeIf { it.width() > 0f && it.height() > 0f } ?: box
        // Neither has any: there is nothing to touch, and the reach below would divide by zero.
        if (frame.width() <= 0f || frame.height() <= 0f) return
        val tx = frame.width() / 2f * OverlayLines.DPAD_TRAVEL
        val ty = frame.height() / 2f * OverlayLines.DPAD_TRAVEL
        val travel = RectF(frame.centerX() - tx, frame.centerY() - ty, frame.centerX() + tx, frame.centerY() + ty)
        // The start area is the item's box, which need not be centred on the frame: each side reaches its own
        // edge, so a lopsided box does not spill over its neighbours on the near side. A box that stops short of
        // the centre (negative extended edges) reaches nothing on that side rather than a negative, inverted reach.
        val reach = floatArrayOf(
            (frame.centerX() - box.left) / tx, (frame.centerY() - box.top) / ty,
            (box.right - frame.centerX()) / tx, (box.bottom - frame.centerY()) / ty,
        ).map { it.coerceAtLeast(0f) }.toFloatArray()
        descs += OverlayDesc("dpad_area", travel, dpad = listOf(up, down, left, right), reach = reach, knob = circle())
    }

    /**
     * The skin's input name as a RetroArch overlay bind, or null for one left out. The skin names the
     * console's own buttons; the RetroPad has them where data/Skin.kt puts them for that console.
     */
    private fun bind(input: String): String? =
        SkinBinds.bind(console, input, hardcore)
            // Hardcore leaves the speed keys out on purpose: only a name nothing knows is worth a warning.
            ?: null.also { if (SkinBinds.bind(console, input, false) == null) Log.w(TAG, "input '$input' not mapped") }

    /** [b] grown by the item's extended edges ([edge] of "left", "top", "right", "bottom"), in mapping units. */
    private fun extended(b: SkinBox, edge: (String) -> Float) =
        SkinBox(b.x - edge("left"), b.y - edge("top"), b.w + edge("left") + edge("right"), b.h + edge("top") + edge("bottom"))

    private fun box(f: JSONObject) =
        SkinBox(f.getDouble("x").toFloat(), f.getDouble("y").toFloat(), f.getDouble("width").toFloat(), f.getDouble("height").toFloat())

    /** resizable (a PDF) when there is one, the largest PNG otherwise; the keys' case varies between skins. */
    private fun assetName(assets: JSONObject): String {
        val byKey = assets.keys().asSequence().associateBy({ it.lowercase(Locale.US) }, { assets.getString(it) })
        return listOf("resizable", "large", "medium", "small").firstNotNullOf { byKey[it] }
    }

    private fun findEntry(zip: ZipFile, name: String) =
        zip.entries().asSequence().firstOrNull { !it.name.startsWith("__MACOSX") && (it.name == name || it.name.endsWith("/$name")) }

    private fun entry(zip: ZipFile, name: String) = findEntry(zip, name) ?: error("no $name in the skin")

    private fun rasterize(zip: ZipFile, name: String, target: RectF): Bitmap {
        val entry = entry(zip, name)
        val tw = target.width().toInt().coerceAtLeast(1)
        val th = target.height().toInt().coerceAtLeast(1)
        if (!name.endsWith(".pdf", ignoreCase = true)) {
            return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
        }
        // PdfRenderer reads from a file descriptor: the page goes through a temporary copy.
        val tmp = File.createTempFile("skin", ".pdf")
        try {
            zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    pdf.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                        val m = Matrix().apply { setScale(tw.toFloat() / page.width, th.toFloat() / page.height) }
                        page.render(bitmap, Rect(0, 0, tw, th), m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "ImportedSkin"
        private const val RADIAL = "analog_left"
        private const val RADIAL_RIGHT = "analog_right"
        private const val SLOW_MOTION = "toggle_slowmotion"

        /** A speed key's share of opacity in hardcore: 0.45, like the drawn skins' strip and the pause sheet's disabled tiles. */
        private const val DISABLED_ALPHA = SPEED_OFF_ALPHA / 255f

        /** The least difference from its surroundings a toggle's glyph has, out of 255 per channel. */
        private const val GLYPH_MIN_DIFF = 48

        /** How much of a pixel a lifted glyph covers before erasing it paints the colour around it there: noise stays. */
        private const val ERASE_MIN_COVER = 0.06f

        /** The NDS screens key's input: the app's own name (tools/skins), which other emulators ignore. */
        const val SCREENS_INPUT = "screens"

        /** The NDS layouts a package draws apart from its representations' own art, one over the other. */
        private val NDS_ART_LAYOUTS = listOf("top", "bottom", "side")

        /**
         * The skin the user put in files/skins/<console> for [console], if any: the first by name of the .deltaskin and
         * .manicskin files and the folders in OpenEmux's own format (with a skin.json, [isOpenEmuxSkin]).
         */
        fun imported(base: File, console: Console): File? = SkinStore(base).legacyPick(console)

        /** Whether [file] is a skin folder in OpenEmux's own format (docs/skin-format.md): one holding a skin.json. */
        fun isOpenEmuxSkin(file: File) = SkinStore.isOwn(file)

        /**
         * Whether [file], a package the app ships, is in OpenEmux's own format (docs/skin-format.md): a .zip, as the
         * consoles issue 770 added ship; the others are .deltaskin and .manicskin.
         */
        fun isOpenEmuxPackage(file: File) = file.name.endsWith(".zip", ignoreCase = true)

        /** The package [names] (an assets folder's listing) holds for [console], if any. */
        fun bundledName(names: Array<String>?, console: Console): String? =
            names?.firstOrNull { it.contains('.') && it.substringBeforeLast('.') == console.id }

        /**
         * The assets folder and file name of the package [console] plays in [variant]: [variant]'s own, else the
         * console's own colors' when [variant] ships none for it. [list] lists an assets folder.
         */
        fun bundledEntry(list: (String) -> Array<String>?, console: Console, variant: SkinVariant): Pair<String, String>? =
            listOf(variant, SkinVariant.CONSOLE).firstNotNullOfOrNull { v -> bundledName(list(v.assetDir), console)?.let { v.assetDir to it } }

        /**
         * The package the app ships for [console] in [variant]'s assets folder (the console's own when that variant ships
         * none for it), copied out (to a cache folder of its own per variant) so it can be read as a zip.
         */
        fun bundled(context: Context, console: Console, variant: SkinVariant = SkinVariant.CONSOLE): File? {
            val (dir, name) = bundledEntry({ context.assets.list(it) }, console, variant) ?: return null
            val out = File(context.cacheDir, "$dir/$name")
            out.parentFile?.mkdirs()
            // Copied once per install or update of the app, stamped with its time (an exact match, so a clock that
            // moved does not keep an old copy), through a temporary file of its own renamed into place: the launcher
            // and the game's redraws run in two processes and both look here.
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            if (!out.isFile || out.lastModified() != installed) {
                val tmp = File.createTempFile(name, ".tmp", out.parentFile)
                try {
                    context.assets.open("$dir/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.setLastModified(installed)
                    if (!tmp.renameTo(out) && !out.isFile) return null
                } finally {
                    tmp.delete()
                }
            }
            return out
        }
    }
}
