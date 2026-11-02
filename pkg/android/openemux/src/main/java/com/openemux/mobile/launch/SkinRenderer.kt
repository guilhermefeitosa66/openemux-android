package com.openemux.mobile.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.WindowInsets
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.NDS_SCREEN_GAPS
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.Skin
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinColors
import com.openemux.mobile.data.SkinFamily
import com.openemux.mobile.data.SkinKey
import com.openemux.mobile.data.SkinRow
import com.openemux.mobile.data.SkinSide
import com.openemux.mobile.data.forCore
import com.openemux.mobile.data.skin
import com.openemux.mobile.ui.Fold
import com.openemux.mobile.ui.Icons
import com.retroarch.R
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The window a skin is drawn for: its short and long sides and the camera
 * cutout's depth. A rotation keeps it (the skin holds both orientations);
 * unfolding a Fold, split screen or a free-form resize changes it.
 */
data class SkinSize(val short: Int, val long: Int, val cutout: Int) {
    /** A few px either way is inset jitter, not a new window. */
    fun differs(other: SkinSize?, tolerance: Int = 8): Boolean = other == null ||
        abs(short - other.short) > tolerance || abs(long - other.long) > tolerance || abs(cutout - other.cutout) > tolerance

    override fun toString() = "${short}x${long}x$cutout"

    companion object {
        /**
         * The cutout is the deeper of the two short-edge insets: top or bottom in portrait (upside down puts
         * it at the bottom), left or right in landscape. Any of the four rotations then gives the same size.
         */
        @RequiresApi(30)
        fun of(metrics: WindowMetrics): SkinSize {
            val bounds = metrics.bounds
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            val cutout = if (bounds.height() >= bounds.width()) maxOf(inset.top, inset.bottom) else maxOf(inset.left, inset.right)
            return SkinSize(minOf(bounds.width(), bounds.height()), maxOf(bounds.width(), bounds.height()), cutout)
        }

        /** Exactly three numbers: a malformed piece is not skipped over. */
        fun parse(text: String?): SkinSize? = text?.split('x')?.takeIf { it.size == 3 }
            ?.map { it.toIntOrNull() ?: return null }?.let { (s, l, c) -> SkinSize(s, l, c) }
    }
}

/**
 * Draws the console's controller skin (data/Skin.kt, the design's skin
 * boards) at the screen's exact size and writes it as a RetroArch overlay:
 * one image per orientation plus the touch areas, each bound to a RetroPad
 * button, an analog stick or a hotkey.
 */
class SkinRenderer(private val context: Context) {

    private val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
    private val grotesk: Typeface = ResourcesCompat.getFont(context, R.font.grotesk_bold) ?: Typeface.DEFAULT_BOLD

    /**
     * [cutout] is the camera cutout's depth along the long edge: the engine's
     * window, and so the overlay, only covers the screen past it.
     * [hardcore] greys out the strip's fast forward and slow motion and takes their touch areas away:
     * the engine still fast-forwards in RetroAchievements hardcore, the app does not offer either speed.
     * [density] picks the shape: a window 600dp or more across its short side draws the tablet skin (SkinGeometry).
     * [first] is the overlay RetroArch opens on, when the skin has one of that name:
     * a skin redrawn in game keeps the one that was shown.
     * [fold] (seen at launch, in a [width] x [height] window) adds the tabletop overlays when it runs across the long
     * side, opening on [ndsTabletop] for NDS; the activity switches to them by name, and reads their band and the NDS
     * screen gaps from [TABLETOP_FILE]. With [under], a package skin's skin.cfg (ImportedSkinRenderer), only those
     * tabletop overlays are drawn, after the package's own: it has no tabletop art, and opening flat brings it back.
     *
     * Every page has its twins (OverlayName): the gamepad layout, with the app controls only, and each with the app
     * controls collapsed to the key that shows them again. A package skin gets the gamepad ones only, its art being
     * its author's; with [under] on NDS, [ndsLayouts] says the package drew one page per layout (ImportedSkinRenderer),
     * and the gamepad pages follow them. [gamepad] opens the drawn skin on the gamepad layout when [first] names no page.
     */
    fun render(
        console: Console, width: Int, height: Int, outDir: File, cutout: Int = 0,
        ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null, hardcore: Boolean = false, coreId: String? = null,
        density: Float, first: String? = null, fold: Fold? = null, ndsTabletop: ScreenLayout? = null, under: File? = null,
        gamepad: Boolean = false,
    ): File {
        outDir.mkdirs()
        val skin = console.skin.forCore(coreId)
        val portraitW = minOf(width, height)
        val portraitH = maxOf(width, height) - cutout
        val tablet = SkinGeometry.tablet(width, height, density)
        val unit = SkinGeometry.unit(portraitW, density, tablet)

        val band = SkinGeometry.tabletopBandFor(fold, width, height, density, cutout)
        val wide = portraitW / density >= SkinGeometry.TABLETOP_WIDE
        val base = under?.readText()
        val flat = base?.let(::overlayNames).orEmpty()
        val packaged = base != null
        val specs = if (!packaged) {
            withAppModes(overlaySpecs(console.gameAspect, skin.keypad.isNotEmpty(), ndsLayouts, band != null, ndsTabletop, tablet)).let { all ->
                // With a gamepad, the named page's gamepad twin.
                val opening = first?.takeIf { f -> all.any { it.name == f } }?.let { f -> if (gamepad) OverlayName.map(f) { it.withGamepad(true) } else f }
                    ?: if (gamepad) OverlayName.map(all.first().name) { it.withGamepad(true) } else null
                if (opening != null) all.sortedBy { if (it.name == opening) 0 else 1 } else all
            }
        } else {
            // A package skin opened on its own [first] already (ImportedSkinRenderer): these go after.
            val nds = console == Console.NDS
            val tabletop = if (band != null) packageTabletopSpecs(console.gameAspect, nds, ndsTabletop, ndsLayouts?.second, tablet) else emptyList()
            packageSpecs(console.gameAspect, nds, tabletop, ndsLayouts, tablet)
        }
        val gaps = mutableMapOf<String, Int>()
        val cfg = StringBuilder()
        // A page's collapsed and gamepad twins draw the same art as the page (the app controls are images of their
        // own): the engine loads an image named twice once.
        val written = mutableSetOf<String>()
        specs.forEachIndexed { n, spec ->
            val i = flat.size + n
            val w = if (spec.portrait) portraitW else portraitH
            val h = if (spec.portrait) portraitH else portraitW
            // The gamepad layout draws no virtual buttons, so no page image, unless its keypad is open.
            val art = !spec.gamepad || spec.keypadOpen
            val prefix = spec.name.replace('@', '-')
            val image = if (art) OverlayName.map(spec.name) { it.touch }.replace('@', '-') + ".png" else null
            val fresh = image != null && written.add(image)
            val bitmap = if (fresh) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) else Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val toggle = OverlayName.map(spec.name) { it.copy(keypad = !spec.keypadOpen) }
            // A package skin's own pages have no hide key, nor do the touch pages drawn under them.
            val hide = OverlayName.map(spec.name) { it.withCollapsed(true) }.takeIf { spec.gamepad || !packaged }
            val show = OverlayName.map(spec.name) { it.withCollapsed(false) }
            val layout = Layout(
                Canvas(bitmap), skin, unit, spec.aspect, spec.keypadOpen, toggle, spec.layoutNext, hardcore, spec.layout,
                spec.collapsed, hide, show,
            )
            val descs = when {
                !art -> layout.pad(w, h, band.takeIf { spec.tabletop }, spec.portrait, keypad = skin.keypad.isNotEmpty() && !packaged)
                spec.tabletop && band != null -> layout.tabletop(w, h, band.startPx / unit, band.endPx / unit, wide)
                tablet && spec.portrait -> layout.tabletPortrait(w, h)
                tablet -> layout.tabletLandscape(w, h)
                spec.portrait -> layout.portrait(h)
                else -> layout.landscape(w)
            }
            OverlayWriter.saveImages(outDir, image.takeIf { fresh }, bitmap.takeIf { fresh }, descs, prefix)
            bitmap.recycle()
            // Touch areas a little larger than what is drawn: thumbs are wide. A stick's zone keeps clear of the game
            // and, on a tabletop overlay, of the fold's band.
            val clear = if (spec.tabletop && band != null) {
                listOf(layout.picture, RectF(0f, band.startPx, w.toFloat(), band.endPx))
            } else {
                listOf(layout.picture)
            }
            OverlayWriter.write(
                cfg, i, spec.name, spec.rotate, image, descs, w, h, layout.picture, grow = TOUCH_GROW,
                clear = clear, gap = 2f * unit, hidden = if (spec.tabletop) 0.002f else 0.5f, prefix = prefix,
            )
            if (spec.layout != null && spec.tabletop) gaps[spec.name] = layout.gap
        }
        File(outDir, TABLETOP_FILE).delete()
        if (band != null) {
            // In the launch window's dp, as the activity measures the fold it sees.
            val props = StringBuilder()
            props.append(String.format(Locale.US, "band_start_dp=%.2f\nband_end_dp=%.2f\n", (band.startPx + cutout) / density, (band.endPx + cutout) / density))
            props.append("overlays=${(flat + specs.map { it.name }).joinToString(",")}\n")
            gaps.forEach { (name, gap) -> props.append("gap.$name=$gap\n") }
            // A package with one page per layout sets the core from the page it lands on, as the drawn skin does.
            if (base != null && console == Console.NDS && ndsLayouts == null) props.append("flat_layout=$PACKAGE_NDS_LAYOUT\n")
            File(outDir, TABLETOP_FILE).writeText(props.toString())
        }
        val file = File(outDir, "skin.cfg")
        file.writeText(base?.let { withOverlays(it, specs.size, cfg.toString()) } ?: "overlays = ${specs.size}\n$cfg")
        return file
    }

    /** The launcher and the game draw the skin for a window the same way. */
    fun render(
        console: Console, size: SkinSize, outDir: File,
        ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null, hardcore: Boolean = false, coreId: String? = null,
        density: Float, first: String? = null, fold: Fold? = null, ndsTabletop: ScreenLayout? = null, under: File? = null,
        gamepad: Boolean = false,
    ): File = render(console, size.short, size.long, outDir, size.cutout, ndsLayouts, hardcore, coreId, density, first, fold, ndsTabletop, under, gamepad)

    /**
     * One orientation, drawn in the design's units (its phone frame is 390
     * wide in portrait and 390 tall in landscape): [unit] pixels each.
     */
    private inner class Layout(
        /** The page's image; an app control swaps in a canvas of its own while it is drawn (appLayer). */
        private var c: Canvas,
        /** The console's skin with the launched core's binds. */
        private val skin: Skin,
        private val unit: Float, private val aspect: Float,
        /** Whether the keypad takes the face buttons' place; [toggle] is the overlay its key switches to. */
        private val keypadOpen: Boolean, private val toggle: String,
        /** The overlay of the next NDS screen layout, for the strip's layout key. */
        private val layoutNext: String?,
        /** RetroAchievements hardcore: the strip's fast forward and slow motion are drawn off and do nothing. */
        private val hardcore: Boolean,
        /** The NDS layout this overlay shows, named on the tablet's layout key. */
        private val screenLayout: ScreenLayout?,
        /** The app controls are collapsed: only the key that shows them, where the menu key is, at [showNext]. */
        private val collapsed: Boolean,
        /** The collapsed twin the hide key switches to; null: no hide key (a package skin's pages). */
        private val hideNext: String?,
        private val showNext: String,
    ) {
        private val colors: SkinColors = skin.colors
        private val descs = mutableListOf<OverlayDesc>()

        /** The game's box, in pixels, as the overlay's viewport. */
        var picture = RectF()
            private set

        /** The NDS screen gap (desmume_screens_gap) a tabletop overlay's layout asks of the core. */
        var gap = 0
            private set

        /** Maps design units to pixels; the portrait panel squeezes through it when short. */
        private var ox = 0f
        private var oy = 0f
        private var f = 1f
        private fun x(v: Float) = (ox + v * f) * unit
        private fun y(v: Float) = (oy + v * f) * unit
        private fun s(v: Float) = v * f * unit
        private fun rect(l: Float, t: Float, w: Float, h: Float) = RectF(x(l), y(t), x(l + w), y(t + h))

        fun portrait(height: Int): List<OverlayDesc> {
            val hd = height / unit
            // The picture on top, at full width up to the design's cap: taller ones narrow, centred.
            val gameH = minOf(390f / aspect, if (skin.family == SkinFamily.DUAL) 466f else 380f)
            val gameW = gameH * aspect
            picture = RectF((390f - gameW) / 2f * unit, 0f, (390f + gameW) / 2f * unit, gameH * unit)
            fill(RectF(0f, gameH * unit, 390f * unit, height.toFloat()), colors.body)
            strip(RectF(0f, gameH * unit, 390f * unit, (gameH + 44f) * unit), 22f, opaque = true)

            // The design's panel under the strip is 420 tall; under a tall picture (two screens, a vertical one) it compacts.
            val top = gameH + 44f
            val room = hd - top
            if (room < 420f) return compact(top, room)
            f = minOf(1f, room / 420f)
            val pd = maxOf(room, 420f)
            ox = 195f * (1f - f)
            oy = top

            shoulderRow(skin.shouldersLeft, 16f, 141f, 16f, 40f, 15f)
            shoulderRow(skin.shouldersRight, 249f, 374f, 16f, 40f, 15f)
            menu(rect(145f, 14f, 48f, 44f), rect(197f, 14f, 48f, 44f))

            val m0 = 68f
            val m1 = pd - 108f
            clusters(96f, 290f, rows = mapOf(SkinRow.CENTER to (m0 + m1) / 2f, SkinRow.UPPER to m0 + (m1 - m0) * 0.27f, SkinRow.LOWER to m0 + (m1 - m0) * 0.75f), k = 1f, dpadK = 1f)
            if (keypadOpen) keypad(290f, (m0 + m1) / 2f, 180f, minOf(320f, m1 - m0 + 40f))

            val pills = skin.pills.filter { it.bind != null }
            pills.forEachIndexed { i, key -> pill(key, 195f + (i - (pills.size - 1) / 2f) * 96f, pd - 100f, 60f, 24f, 11f) }
            label(skin.name, 0f, pd - 38f, 390f, 11f, 0.18f)
            return descs
        }

        /** One row of shoulders, keys and menu on top, the controls under it, no name. */
        private fun compact(top: Float, room: Float): List<OverlayDesc> {
            f = minOf(1f, room / 200f)
            val pd = maxOf(room, 200f)
            ox = 195f * (1f - f)
            oy = top
            val pills = skin.pills.filter { it.bind != null }
            val shoulders = if (pills.isEmpty()) 125f else 76f
            shoulderRow(skin.shouldersLeft, 16f, 16f + shoulders, 8f, 32f, 13f)
            shoulderRow(skin.shouldersRight, 374f - shoulders, 374f, 8f, 32f, 13f)
            menu(rect(147f, 6f, 46f, 36f), rect(197f, 6f, 46f, 36f))
            val half = (pills.size + 1) / 2
            listOf(pills.take(half) to (22f + shoulders to 141f), pills.drop(half) to (249f to 368f - shoulders)).forEach { (keys, span) ->
                val w = (span.second - span.first - 6f * (keys.size - 1)) / keys.size.coerceAtLeast(1)
                keys.forEachIndexed { i, key -> pill(key, span.first + i * (w + 6f) + w / 2f, 13f, w, 22f, 0f) }
            }
            val band = pd - 56f
            val cy = 48f + band / 2f
            val k = minOf(1f, band / 180f)
            clusters(96f, 290f, rows = mapOf(SkinRow.CENTER to cy, SkinRow.UPPER to cy - band * 0.24f, SkinRow.LOWER to cy + band * 0.24f), k = k, dpadK = k)
            if (keypadOpen) keypad(290f, cy, 180f, band)
            return descs
        }

        fun landscape(width: Int): List<OverlayDesc> {
            val wd = width / unit
            // The picture centred between the control sides, with the strip under it.
            val gameW = phoneLandscapeGameW(wd, aspect)
            val gameH = gameW / aspect
            val left = (wd - gameW) / 2f
            val top = (390f - gameH - 36f) / 2f
            picture = RectF(left * unit, top * unit, (left + gameW) * unit, (top + gameH) * unit)
            // The overlay sits over the game: the picture's box stays clear.
            fill(RectF(0f, 0f, wd * unit, 390f * unit), colors.body)
            c.drawRect(picture, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            val stripW = phoneLandscapeStripW(gameW, stripActions(layoutNext != null).size)
            val stripX = left + (gameW - stripW) / 2f
            strip(RectF(stripX * unit, (top + gameH) * unit, (stripX + stripW) * unit, (top + gameH + 36f) * unit), 20f, opaque = true)

            val side = left
            val lx = side / 2f
            val rx = wd - side / 2f
            shoulderRow(skin.shouldersLeft, 12f, side - 12f, 12f, 36f, 13f)
            shoulderRow(skin.shouldersRight, wd - side + 12f, wd - 12f, 12f, 36f, 13f)
            menu(rect(lx - 48f, 54f, 44f, 36f), rect(lx + 4f, 54f, 44f, 36f))

            clusters(lx, rx, rows = mapOf(SkinRow.CENTER to 196f, SkinRow.UPPER to 142f, SkinRow.LOWER to 262f), k = 0.82f, dpadK = 0.85f)
            if (keypadOpen) keypad(rx, 200f, side - 16f, 230f)

            val pills = skin.pills.filter { it.bind != null }
            if (pills.size == 1) pill(pills[0], rx, 330f, 52f, 20f, 10f)
            else {
                val half = (pills.size + 1) / 2
                val l = pills.take(half)
                val r = pills.drop(half)
                l.forEachIndexed { i, key -> pill(key, lx + (i - (l.size - 1) / 2f) * 80f, 330f, 52f, 20f, 10f) }
                r.forEachIndexed { i, key -> pill(key, rx + (i - (r.size - 1) / 2f) * 80f, 330f, 52f, 20f, 10f) }
            }
            label(skin.name, wd - side, 58f, side, 10f, 0.16f)
            return descs
        }

        /** Tablet portrait (TabJogoRetrato): the picture across the top, the strip, then L · menu · R and the controls. */
        fun tabletPortrait(width: Int, height: Int): List<OverlayDesc> {
            val g = SkinGeometry.portrait(width / unit, height / unit, aspect)
            picture = px(g.picture)
            fill(RectF(0f, picture.bottom, width.toFloat(), height.toFloat()), colors.body)
            strip(px(g.strip), 22f, opaque = true)
            frame(g)
            if (keypadOpen) keypad(g.rx, g.rows.center, 180f, minOf(320f, g.pillsTop - g.menu.bottom - 30f))
            val pills = skin.pills.filter { it.bind != null }
            pills.forEachIndexed { i, key -> pill(key, g.picture.cx + (i - (pills.size - 1) / 2f) * 96f, g.pillsTop, 60f, 24f, 11f) }
            label(skin.name, g.name.x, g.name.y, g.name.w, 11f, 0.18f)
            return descs
        }

        /**
         * Tablet landscape (TabJogoGBA, TabJogoNDS): the picture centred between two side columns, the strip under it,
         * the NDS layout key over it rather than in the strip.
         */
        fun tabletLandscape(width: Int, height: Int): List<OverlayDesc> {
            val wd = width / unit
            val g = SkinGeometry.landscape(wd, height / unit, aspect, footprint(), depth(), layoutKey = layoutNext != null)
            picture = px(g.picture)
            fill(RectF(0f, 0f, width.toFloat(), height.toFloat()), colors.body)
            c.drawRect(picture, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            strip(px(g.strip), 22f, opaque = true, screens = false)
            g.layoutKey?.let { layoutKey(it) }
            frame(g)
            val side = g.lx * 2f
            if (keypadOpen) keypad(g.rx, g.rows.center, side - 16f, minOf(280f, g.pillsTop - g.menu.bottom - 24f))
            val pills = skin.pills.filter { it.bind != null }
            if (pills.size == 1) pill(pills[0], g.rx, g.pillsTop, 60f, 24f, 10f)
            else {
                val half = (pills.size + 1) / 2
                val l = pills.take(half)
                val r = pills.drop(half)
                l.forEachIndexed { i, key -> pill(key, g.lx + (i - (l.size - 1) / 2f) * 84f, g.pillsTop, 60f, 24f, 10f) }
                r.forEachIndexed { i, key -> pill(key, g.rx + (i - (r.size - 1) / 2f) * 84f, g.pillsTop, 60f, 24f, 10f) }
            }
            label(skin.name, g.name.x, g.name.y, g.name.w, 10f, 0.16f)
            return descs
        }

        /**
         * Tabletop (DobFoldJogo, DobFlipJogo, DobFoldNDS), the band from [start] to [end] units down: the picture in the
         * top half, the controls in the bottom one, and the band background only, its colour a shade under the body's.
         */
        fun tabletop(width: Int, height: Int, start: Float, end: Float, wide: Boolean): List<OverlayDesc> {
            val wd = width / unit
            val hd = height / unit
            val nds = screenLayout != null
            val g = if (nds) SkinGeometry.tabletopNds(wd, hd, end, footprint()) else SkinGeometry.tabletop(wd, hd, start, end, aspect, wide)
            val top = Box(0f, 0f, wd, start)
            val view = when (screenLayout?.id) {
                null -> g.picture
                // Side by side: both screens in the top half, as wide as it allows.
                "top", "side" -> SkinGeometry.fitIn(top, aspect, 14f)
                "bottom" -> SkinGeometry.fitIn(g.picture, aspect)
                else -> SkinGeometry.ndsHalvesFit(start, end, g.picture, NDS_SCREEN_GAPS)?.also { gap = it.gap }?.viewport
                    // No gap covers the band: both screens in the top half rather than one on the fold.
                    ?: SkinGeometry.fitIn(top, 256f / 384f, 14f)
            }
            picture = px(view)
            fill(RectF(0f, 0f, width.toFloat(), height.toFloat()), colors.body)
            c.drawRect(picture, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            strip(px(g.strip), 22f, opaque = true)
            frame(g)
            if (keypadOpen) keypad(g.rx, g.rows.center, if (nds) g.lx * 2f - 16f else 180f, minOf(280f, g.pillsTop - g.menu.bottom - 24f))
            val pills = skin.pills.filter { it.bind != null }
            if (nds) {
                val half = (pills.size + 1) / 2
                pills.take(half).forEachIndexed { i, key -> pill(key, g.lx + i * 72f, g.pillsTop, 60f, 24f, 0f) }
                pills.drop(half).forEachIndexed { i, key -> pill(key, g.rx - i * 72f, g.pillsTop, 60f, 24f, 0f) }
            } else {
                pills.forEachIndexed { i, key -> pill(key, wd / 2f + (i - (pills.size - 1) / 2f) * 96f, g.pillsTop, 60f, 24f, 11f) }
            }
            label(skin.name, g.name.x, g.name.y, g.name.w, if (nds) 10f else 11f, 0.16f)
            // Last, over anything that ran into it: the band is background only.
            fill(RectF(0f, start * unit, width.toFloat(), end * unit), shade(colors.body))
            // As touched: a D-pad through its reach, a stick over its well.
            val grown = descs.partition { d ->
                val area = OverlayWriter.footprint(d, TOUCH_GROW)
                area.top < end * unit && area.bottom > start * unit
            }
            if (grown.first.isNotEmpty()) Log.w("SkinRenderer", "tabletop: ${grown.first.map { it.name }} reach the fold, left out")
            return grown.second
        }

        /** [color] a little darker: the fold's band (#4F4296 → #3E3479 on the boards). */
        private fun shade(color: Int): Int {
            fun ch(shift: Int) = (((color shr shift) and 0xFF) * 0.785f).toInt() shl shift
            return (color and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
        }

        /** The shoulders, the menu key and the clusters, where [g] puts them. */
        private fun frame(g: TabletFrame) {
            shoulderRow(skin.shouldersLeft, g.shouldersLeft.x, g.shouldersLeft.right, g.shouldersLeft.y, g.shouldersLeft.h, 15f)
            shoulderRow(skin.shouldersRight, g.shouldersRight.x, g.shouldersRight.right, g.shouldersRight.y, g.shouldersRight.h, 15f)
            menu(px(g.menu), px(g.hide))
            clusters(g.lx, g.rx, rows = mapOf(SkinRow.UPPER to g.rows.upper, SkinRow.CENTER to g.rows.center, SkinRow.LOWER to g.rows.lower), k = g.k, dpadK = g.k)
        }

        /** The clusters drawn: the keypad takes the right side's place while it is open. */
        private fun shown() = skin.clusters.filter { !(keypadOpen && it.side == SkinSide.RIGHT) }

        /** The widest cluster at full size, in units: what a tablet's side column must hold. */
        private fun footprint(): Float = shown().maxOfOrNull { cl ->
            when (cl) {
                is SkinCluster.DPad -> cl.size
                is SkinCluster.Stick -> cl.size
                is SkinCluster.Face -> cl.slots.maxOf { it.dx + it.w / 2f } - cl.slots.minOf { it.dx - it.w / 2f }
            }
        } ?: SkinGeometry.SIDE

        /** How far the lowest cluster reaches below the centre row at full size, in units: the pills go under it. */
        private fun depth(): Float = shown().maxOfOrNull { cl ->
            val row = when (cl.row) {
                SkinRow.UPPER -> SkinGeometry.UPPER_ROW
                SkinRow.CENTER -> 0f
                SkinRow.LOWER -> SkinGeometry.LOWER_ROW
            }
            row + when (cl) {
                is SkinCluster.DPad -> cl.size / 2f
                is SkinCluster.Stick -> cl.size / 2f
                is SkinCluster.Face -> cl.slots.maxOf { it.dy + it.h / 2f }
            }
        } ?: 0f

        /** The NDS layout key over the picture: the screens icon and the layout's name; it steps to the next layout. */
        private fun layoutKey(b: Box) {
            if (collapsed) return
            val box = px(b)
            appLayer(box) { drawLayoutKey(b, box) }
            descs += OverlayDesc("overlay_next", box, next = layoutNext)
        }

        private fun drawLayoutKey(b: Box, box: RectF) {
            c.drawRoundRect(box, box.height() / 2f, box.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.pill })
            val icon = s(16f)
            c.drawPath(stripIcon("screens", box.left + s(20f), box.centerY(), icon), stripPaint("screens", icon, colors.menuInk))
            val name = screenLayout?.let { context.getString(it.label).uppercase(context.resources.configuration.locales[0]) }.orEmpty()
            label(name, b.x + 30f, b.y + b.h / 2f - 5.5f, b.w - 36f, 10f, 0.06f, alpha = 0xFF, color = colors.menuInk)
        }

        private fun px(b: Box) = RectF(b.x * unit, b.y * unit, b.right * unit, b.bottom * unit)

        private fun clusters(lx: Float, rx: Float, rows: Map<SkinRow, Float>, k: Float, dpadK: Float) {
            for (cl in shown()) {
                val cx = if (cl.side == SkinSide.LEFT) lx else rx
                val cy = rows.getValue(cl.row)
                when (cl) {
                    is SkinCluster.DPad -> dpad(cx, cy, cl.size * dpadK, cl.binds)
                    is SkinCluster.Stick -> stick(cl, cx, cy, cl.size * k)
                    is SkinCluster.Face -> cl.slots.zip(cl.buttons).forEach { (slot, b) ->
                        val w = slot.w * k
                        val h = slot.h * k
                        val box = rect(cx + slot.dx * k - w / 2f, cy + slot.dy * k - h / 2f, w, h)
                        val radius = minOf(box.width(), box.height()) / 2f
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = b.bg
                            setShadowLayer(s(6f), 0f, s(5f), 0x66000000)
                        }
                        c.drawRoundRect(box, radius, radius, paint)
                        val size = maxOf(11f, minOf(slot.w, slot.h) * k * (if (b.label.length > 2) 0.22f else 0.34f))
                        text(b.label, box, b.fg, s(size), grotesk)
                        b.bind?.let { descs += OverlayDesc(it, box) }
                    }
                }
            }
        }

        private fun dpad(cx: Float, cy: Float, size: Float, binds: List<String>) {
            val box = rect(cx - size / 2f, cy - size / 2f, size, size)
            val u = box.width() / 120f
            fun at(px: Float, py: Float) = Pair(box.left + px * u, box.top + py * u)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.dpad }
            c.drawRoundRect(RectF(box.left + 40 * u, box.top + 2 * u, box.left + 80 * u, box.top + 118 * u), 9 * u, 9 * u, paint)
            c.drawRoundRect(RectF(box.left + 2 * u, box.top + 40 * u, box.left + 118 * u, box.top + 80 * u), 9 * u, 9 * u, paint)
            paint.color = colors.mark
            for (tri in listOf(
                listOf(60f to 14f, 51f to 26f, 69f to 26f), listOf(60f to 106f, 51f to 94f, 69f to 94f),
                listOf(14f to 60f, 26f to 51f, 26f to 69f), listOf(106f to 60f, 94f to 51f, 94f to 69f),
            )) {
                val path = Path()
                tri.forEachIndexed { i, (px, py) ->
                    val (ax, ay) = at(px, py)
                    if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
                }
                path.close()
                c.drawPath(path, paint)
            }
            // The circle on the centre is not painted into the skin: it is an image of its own, the size of its
            // travel (the engine stretches it over the range), which the engine moves with the thumb and
            // recentres on release. Its edge stops at the arm tips.
            val travel = DPAD_TRAVEL_UNITS * u
            val side = Math.round(2f * travel).coerceAtLeast(1)
            val knob = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val k = side / 2f
            Canvas(knob).apply {
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (colors.mark and 0x00FFFFFF) or (0xB3 shl 24)
                    setShadowLayer(1.5f * u, 0f, 1.5f * u, 0x40000000)
                }
                drawCircle(k, k, 14 * u, ring)
                ring.apply { clearShadowLayer(); style = Paint.Style.STROKE; strokeWidth = 2 * u; color = colors.mark }
                drawCircle(k, k, 13 * u, ring)
            }
            val range = RectF(box.centerX() - travel, box.centerY() - travel, box.centerX() + travel, box.centerY() + travel)
            descs += OverlayDesc("dpad_area", range, dpad = binds, reach = FloatArray(4) { DPAD_REACH }, knob = knob)
        }

        private fun stick(cl: SkinCluster.Stick, cx: Float, cy: Float, size: Float) {
            val box = rect(cx - size / 2f, cy - size / 2f, size, size)
            val r = box.width() / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cl.color ?: colors.stick }
            c.drawCircle(box.centerX(), box.centerY(), r, paint)
            // The well's inner shadow, along its top.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.color = 0x40000000
            c.drawCircle(box.centerX(), box.centerY() + r * 0.03f, r * 0.94f, paint)
            // The knob is not painted into the skin: it is an image of its own, the size of its travel
            // (the engine stretches it over the range), which the engine moves with the thumb.
            val travel = r * OverlayWriter.KNOB_TRAVEL
            val side = Math.round(2f * travel).coerceAtLeast(1)
            val knob = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val k = side / 2f
            val kr = r * 0.5f
            Canvas(knob).apply {
                drawCircle(k, k, kr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = cl.knob ?: colors.knob
                    // Inside the margin the travel leaves around the knob, so the image's edge stays clear.
                    setShadowLayer(r * 0.09f, 0f, r * 0.05f, 0x73000000)
                })
                // On the knob, which would hide it on the well.
                if (cl.label.isNotEmpty()) text(this, cl.label, RectF(0f, 0f, side.toFloat(), side.toFloat()), 0xFF3B2F7A.toInt(), s(13f), grotesk)
            }
            val range = RectF(box.centerX() - travel, box.centerY() - travel, box.centerX() + travel, box.centerY() + travel)
            descs += OverlayDesc(cl.bind, range, radial = true, well = box, knob = knob)
        }

        private fun shoulderRow(keys: List<SkinKey>, x0: Float, x1: Float, top: Float, h: Float, font: Float) {
            val shown = keys.filter { it.bind != null }
            if (shown.isEmpty()) return
            val w = (x1 - x0 - 8f * (shown.size - 1)) / shown.size
            shown.forEachIndexed { i, key ->
                val box = rect(x0 + i * (w + 8f), top, w, h)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.shoulder }
                c.drawRoundRect(box, s(12f), s(12f), paint)
                text(key.label, box, colors.shoulderInk, s(font), inter)
                descs += OverlayDesc(key.bind!!, box)
            }
        }

        /**
         * The menu key at [box] and the hide key beside it at [hide], each an image of its own; collapsed, the show
         * key alone, in the hide key's place: the eye never moves.
         */
        private fun menu(box: RectF, hide: RectF) {
            if (collapsed) return showKey(hide)
            appLayer(box) { roundKey(box); menuGlyph(box, s(20f)) }
            descs += OverlayDesc("menu_toggle", box)
            if (hideNext == null) return
            appLayer(hide) { roundKey(hide); eye(hide, s(20f), slashed = true) }
            descs += OverlayDesc("overlay_next", hide, next = hideNext)
        }

        /** The key that shows the app controls again: faint, and touched over at least [MIN_TOUCH] units. */
        private fun showKey(box: RectF) {
            appLayer(box, alpha = if (padStyle) PAD_SHOW_ALPHA else SHOW_ALPHA) { roundKey(box); eye(box, s(20f), slashed = false) }
            val half = maxOf(box.width(), box.height(), MIN_TOUCH * unit) / 2f
            descs += OverlayDesc("overlay_next", RectF(box.centerX() - half, box.centerY() - half, box.centerX() + half, box.centerY() + half), next = showNext)
        }

        /**
         * The gamepad layout's look: black panels and faint keys, so the controls stay out of the game's way. Touch
         * layouts keep the skin's own colours.
         */
        private var padStyle = false
        private val keyFill get() = if (padStyle) PAD_KEY_FILL else colors.menu
        private val keyInk get() = if (padStyle) PAD_INK else colors.menuInk

        private fun roundKey(box: RectF) {
            val r = minOf(box.width(), box.height()) / 2f
            c.drawRoundRect(box, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = keyFill })
        }

        /** Three lines, [size] across. */
        private fun menuGlyph(box: RectF, size: Float) {
            val u = size / 24f
            val left = box.centerX() - 12f * u
            val top = box.centerY() - 12f * u
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f * u; color = keyInk; strokeCap = Paint.Cap.ROUND
            }
            for (ly in floatArrayOf(7f, 12f, 17f)) c.drawLine(left + 5 * u, top + ly * u, left + 19 * u, top + ly * u, stroke)
        }

        /** An eye, [size] across, stroked like the turbo bolt; [slashed] crosses it out (hide). */
        private fun eye(box: RectF, size: Float, slashed: Boolean) {
            val path = PathParser.createPathFromPathData(if (slashed) EYE_OFF else EYE)
            path.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(box.centerX() - size / 2f, box.centerY() - size / 2f) })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = keyInk; style = Paint.Style.STROKE; strokeWidth = 1.8f * size / 24f
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            })
        }

        /** A keypad's three by three keys, [size] across. */
        private fun keypadGlyph(box: RectF, size: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = keyInk }
            for (r in -1..1) for (k in -1..1) c.drawCircle(box.centerX() + k * size / 3f, box.centerY() + r * size / 3f, size / 10f, paint)
        }

        /**
         * Draws [draw] (an app control over [box]) into an image of its own, a "nul" desc placed before the descs it
         * adds, so their lit images go over it: the page's own image stays free of it, and the page's collapsed twin
         * shares that image. [alpha] fades the whole control.
         */
        private fun appLayer(box: RectF, alpha: Int = 0xFF, draw: () -> Unit) {
            val left = floor(box.left)
            val top = floor(box.top)
            val w = (ceil(box.right) - left).toInt().coerceAtLeast(1)
            val h = (ceil(box.bottom) - top).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val page = c
            val mark = descs.size
            c = Canvas(bitmap).apply { translate(-left, -top) }
            try {
                if (alpha != 0xFF) c.saveLayerAlpha(null, alpha)
                draw()
                if (alpha != 0xFF) c.restore()
            } finally {
                c = page
            }
            descs.add(mark, OverlayDesc("nul", RectF(left, top, left + w, top + h), image = bitmap))
        }

        /** A plain [color] behind the gamepad layout's keys: a few pixels the engine stretches over [box]. */
        private fun panel(box: RectF, color: Int) {
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            descs += OverlayDesc("nul", box, image = bitmap)
        }

        /**
         * The gamepad layout (SkinGeometry.padLandscape, padPortrait, padTabletop with the fold's [band]): no virtual
         * buttons and no page image, the panels and the keys images of their own; [keypad] adds the keypad key.
         * Collapsed, the show key alone, the picture fit to the screen.
         */
        fun pad(width: Int, height: Int, band: TabletopBand?, portrait: Boolean, keypad: Boolean): List<OverlayDesc> {
            val wd = width / unit
            val hd = height / unit
            val keys = SkinGeometry.padKeys(screens = layoutNext != null, keypad = keypad)
            val g = when {
                band != null -> SkinGeometry.padTabletop(
                    wd, hd, band.startPx / unit, band.endPx / unit, aspect, keys, collapsed, screenLayout?.id, NDS_SCREEN_GAPS,
                ).also { gap = it.gap }
                portrait -> SkinGeometry.padPortrait(wd, hd, aspect, keys, collapsed)
                else -> SkinGeometry.padLandscape(wd, hd, aspect, keys, collapsed)
            }
            picture = px(g.picture)
            padStyle = true
            if (collapsed) {
                showKey(px(g.hide))
                return descs
            }
            // Black, like the bars around the picture: no grey band beside the game.
            g.panels.forEach { panel(px(it), PAD_PANEL) }
            g.shade?.let { panel(px(it), PAD_PANEL) }
            val icon = s(22f)
            for ((key, slot) in g.keys) {
                val box = px(slot)
                when (key) {
                    SkinGeometry.PAD_MENU -> {
                        appLayer(box) { roundKey(box); menuGlyph(box, icon) }
                        descs += OverlayDesc("menu_toggle", box)
                    }
                    SkinGeometry.PAD_HIDE -> {
                        appLayer(box) { roundKey(box); eye(box, icon, slashed = true) }
                        descs += OverlayDesc("overlay_next", box, next = hideNext)
                    }
                    SkinGeometry.PAD_KEYPAD -> {
                        appLayer(box) { roundKey(box); keypadGlyph(box, icon) }
                        descs += OverlayDesc("overlay_next", box, next = toggle)
                    }
                    else -> action(key, box, icon)
                }
            }
            return descs
        }

        /** A quick action (the strip's icons) on its own in [box]: its icon, its touch area and its "on" look. */
        private fun action(action: String, box: RectF, icon: Float) {
            val off = stripActionOff(action, hardcore)
            // A faint round key like the others; hardcore's disabled speed keys fainter still.
            val color = if (off) (PAD_INK and 0x00FFFFFF) or (0x30 shl 24) else PAD_INK
            appLayer(box) { roundKey(box); c.drawPath(stripIcon(action, box.centerX(), box.centerY(), icon), stripPaint(action, icon, color)) }
            if (off) return
            descs += if (action == SkinGeometry.PAD_SCREENS) OverlayDesc("overlay_next", box, next = layoutNext) else OverlayDesc(action, box)
            OverlayWriter.LIT_BY[action]?.let { descs += litPill(action, it, box.centerX(), box.centerY(), box.width(), icon, alpha = PAD_LIT_ALPHA) }
        }

        private fun pill(key: SkinKey, cx: Float, top: Float, w: Float, h: Float, font: Float) {
            val box = rect(cx - w / 2f, top, w, h)
            val keypad = key.bind == KEYPAD_BIND
            // The keypad key is an app control: an image of its own, with its name under it, and gone when collapsed.
            if (keypad) {
                if (collapsed) return
                val area = if (font == 0f) box else rect(cx - maxOf(w, 80f) / 2f, top, maxOf(w, 80f), h + 5f + font * 1.6f)
                appLayer(area) { drawPill(key, box, cx, top, w, h, font, keypad = true) }
                descs += OverlayDesc("overlay_next", box, next = toggle)
                return
            }
            drawPill(key, box, cx, top, w, h, font, keypad = false)
            descs += OverlayDesc(key.bind!!, box)
        }

        private fun drawPill(key: SkinKey, box: RectF, cx: Float, top: Float, w: Float, h: Float, font: Float, keypad: Boolean) {
            // The keypad key stays lit while the keypad is open.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (keypad && keypadOpen) colors.ink else colors.pill }
            c.drawRoundRect(box, s(12f), s(12f), paint)
            val text = key.labelRes?.let { context.getString(it).uppercase(context.resources.configuration.locales[0]) } ?: key.label
            // A [font] of 0 writes the label inside the key, for the compact panel.
            if (font == 0f) label(text, cx - w / 2f, top + h / 2f - 5.5f, w, 10f, 0.06f, alpha = 0xFF, color = colors.menuInk)
            else label(text, cx - 40f, top + h + 5f, 80f, font, 0.08f, alpha = 0xFF)
        }

        /** The keypad's keys in a grid of [w] by [h] around ([cx], [cy]), on the face buttons' side. */
        private fun keypad(cx: Float, cy: Float, w: Float, h: Float) {
            val rows = skin.keypad
            val cols = rows.maxOf { it.size }
            val gap = 8f
            val key = minOf((w - gap * (cols - 1)) / cols, (h - gap * (rows.size - 1)) / rows.size)
            val kw = minOf((w - gap * (cols - 1)) / cols, key * 1.3f)
            val top = cy - (rows.size * key + gap * (rows.size - 1)) / 2f
            rows.forEachIndexed { r, row ->
                val left = cx - (row.size * kw + gap * (row.size - 1)) / 2f
                row.forEachIndexed { i, k ->
                    val box = rect(left + i * (kw + gap), top + r * (key + gap), kw, key)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = colors.pill
                        setShadowLayer(s(3f), 0f, s(2f), 0x4D000000)
                    }
                    c.drawRoundRect(box, s(10f), s(10f), paint)
                    text(k.label, box, colors.menuInk, s(minOf(18f, key * 0.4f)), grotesk)
                    k.bind?.let { descs += OverlayDesc(it, box) }
                }
            }
        }

        /**
         * The quick actions: save, load, turbo, slow motion, fast forward, in that order, then the NDS screen layout
         * unless [screens] is off.
         */
        private fun strip(box: RectF, icon: Float, opaque: Boolean, screens: Boolean = true) {
            if (!collapsed) appLayer(box) { drawStrip(box, icon, opaque, screens) }
        }

        private fun drawStrip(box: RectF, icon: Float, opaque: Boolean, screens: Boolean) {
            fill(box, if (opaque) colors.strip else (colors.strip and 0x00FFFFFF) or (0xCC shl 24))
            val actions = stripActions(layoutKey = layoutNext != null && screens)
            val slot = box.width() / actions.size
            val size = s(icon)
            actions.forEachIndexed { i, action ->
                val cx = box.left + slot * (i + 0.5f)
                val cy = box.centerY()
                val off = stripActionOff(action, hardcore)
                // At 0.45, like the pause sheet's disabled tiles.
                val color = if (off) (colors.icon and 0x00FFFFFF) or (SPEED_OFF_ALPHA shl 24) else colors.icon
                c.drawPath(stripIcon(action, cx, cy, size), stripPaint(action, size, color))
                val area = RectF(box.left + slot * i, box.top, box.left + slot * (i + 1), box.bottom)
                if (off) return@forEachIndexed
                descs += if (action == "screens") OverlayDesc("overlay_next", area, next = layoutNext) else OverlayDesc(action, area)
                OverlayWriter.LIT_BY[action]?.let { descs += litPill(action, it, cx, cy, slot, size) }
            }
        }

        private fun stripIcon(action: String, cx: Float, cy: Float, size: Float): Path {
            val path = PathParser.createPathFromPathData(
                when (action) {
                    "save_state" -> Icons.SaveState
                    "load_state" -> Icons.LoadState
                    "turbo_fire_toggle" -> "M13 2L4 14h7l-1 8 9-12h-7z"
                    "toggle_slowmotion" -> Icons.SlowMotion
                    // Two screens, one over the other.
                    "screens" -> "M6 3h12a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM6 13h12a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z"
                    else -> "M3 6v12l9-6zM12 6v12l9-6z"
                }
            )
            path.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(cx - size / 2f, cy - size / 2f) })
            return path
        }

        private fun stripPaint(action: String, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (stripActionStroked(action)) {
                style = Paint.Style.STROKE; strokeWidth = 1.8f * size / 24f; strokeJoin = Paint.Join.ROUND
                // The slow-motion ring is dashes: round caps, as the design draws them.
                if (action == "toggle_slowmotion") strokeCap = Paint.Cap.ROUND
            }
        }

        /**
         * A toggle's "on" look, shown by the engine while [state] is on: the app accent's pill, 46 x 30 (no wider than
         * the slot less 8), its light inner ring, and the icon over it in white. A "nul" desc, so it presses nothing.
         */
        private fun litPill(action: String, state: String, cx: Float, cy: Float, slot: Float, size: Float, alpha: Int = 0xFF): OverlayDesc {
            val pill = OverlayWriter.pill(minOf(s(46f), slot - s(8f)).toInt(), s(30f).toInt(), s(10f), s(1.5f))
            val w = pill.width
            val h = pill.height
            Canvas(pill).drawPath(stripIcon(action, w / 2f, h / 2f, size), stripPaint(action, size, 0xFFFFFFFF.toInt()))
            // The gamepad layout's lit pill is translucent too, so "on" does not pull the eye off the game.
            val bitmap = if (alpha == 0xFF) pill else Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(pill, 0f, 0f, Paint().apply { this.alpha = alpha })
            }
            val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            return OverlayDesc("nul", rect, lit = OverlayLit(state, bitmap))
        }

        private fun label(text: String, left: Float, top: Float, w: Float, font: Float, spacing: Float, alpha: Int = 0xB3, color: Int = colors.ink) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = (color and 0x00FFFFFF) or (alpha shl 24); typeface = inter; textSize = s(font)
                textAlign = Paint.Align.CENTER; letterSpacing = spacing
            }
            // Long names shrink to fit a narrow side.
            val room = s(w) * 0.9f
            val measured = paint.measureText(text)
            if (measured > room) paint.textSize *= room / measured
            c.drawText(text, x(left + w / 2f), y(top) - paint.ascent(), paint)
        }

        private fun text(label: String, box: RectF, color: Int, size: Float, face: Typeface) = text(c, label, box, color, size, face)

        private fun text(on: Canvas, label: String, box: RectF, color: Int, size: Float, face: Typeface) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; typeface = face; textSize = size; textAlign = Paint.Align.CENTER
            }
            on.drawText(label, box.centerX(), box.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
        }

        private fun fill(box: RectF, color: Int) {
            c.drawRect(box, Paint().apply { this.color = color })
        }
    }

    companion object {
        /** The show key's alpha: faint, as the desktop bar's show button. */
        private const val SHOW_ALPHA = 0x80

        /** The gamepad layout: black panels, keys a faint white disc with a 45% white glyph, show at 35%, lit at 70%. */
        private const val PAD_PANEL = 0xFF000000.toInt()
        private const val PAD_KEY_FILL = 0x12FFFFFF
        private const val PAD_INK = 0x73FFFFFF
        private const val PAD_SHOW_ALPHA = 0xB3
        private const val PAD_LIT_ALPHA = 0xB3

        /** The least a key is touched over, in units (dp on a phone). */
        private const val MIN_TOUCH = 48f

        /** Feather's eye and eye-off (MIT), on a 24 grid. */
        private const val EYE = "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
        private const val EYE_OFF = "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" +
            "M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24M1 1l22 22"

        /** Beside skin.cfg when the skin has tabletop overlays: their band, their names and the NDS screen gaps. */
        const val TABLETOP_FILE = "tabletop.properties"

        /** How much larger than what is drawn a control's touch area is: thumbs are wide. */
        private const val TOUCH_GROW = SkinGeometry.TOUCH_GROW

        /** How far the D-pad's circle travels from the centre, in the D-pad's 120-unit grid. */
        private const val DPAD_TRAVEL_UNITS = OverlayLines.DPAD_TRAVEL * 60f

        /**
         * The D-pad's touch area over its travel: as far as the old 3x3 cells reached, each a third of the 120-unit
         * grid grown [TOUCH_GROW] times about its own centre, so the outer ones ended at 40 + 20 * grow from the middle.
         */
        private const val DPAD_REACH = (40f + 20f * TOUCH_GROW) / DPAD_TRAVEL_UNITS
    }
}
