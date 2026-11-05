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
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFrameColors
import com.openemux.mobile.data.SkinIconTone
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.ui.Icons
import com.retroarch.R
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws a skin in OpenEmux's own format (docs/skin-format.md, read by data/SkinFormat.kt) as a RetroArch overlay, the
 * same kind of skin.cfg [ImportedSkinRenderer] writes for a package: a portrait and a landscape page, each the form's
 * layout of that orientation fitted to the window (OpenEmuxSkinLayouts.kt decides where everything goes).
 *
 * The page's image is the background and the frame, with the game's screens cut out of it; every element is a desc of
 * its own with its own image, the element's file when the skin has one and its colours, shape and label when not. The
 * D-pad's ball and a stick's knob are the images the engine moves with the thumb.
 */
class OpenEmuxSkinRenderer(context: Context) {

    private val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD

    /**
     * Writes [pkg]'s [layouts] (the form's portrait and landscape, LayoutChoice.Use) for [console] into [outDir], the
     * window [width] x [height] less the [cutout]. [tablet] and [first] order the pages as a package's (packagePages);
     * [hardcore] dims the speed keys and leaves them out.
     */
    fun render(
        pkg: SkinPackage, layouts: LayoutChoice.Use, console: Console,
        width: Int, height: Int, outDir: File, cutout: Int = 0, tablet: Boolean = false, hardcore: Boolean = false, first: String? = null,
    ): File {
        outDir.mkdirs()
        val portraitW = minOf(width, height)
        val portraitH = maxOf(width, height) - cutout
        Log.i(TAG, "drawing ${pkg.skin.name} (${layouts.portrait.id}, ${layouts.landscape.id})")
        val pages = packagePages(null, tablet, first)
        val cfg = StringBuilder("overlays = ${pages.size}\n")
        pages.forEachIndexed { i, page ->
            val id = if (page.portrait) layouts.portrait else layouts.landscape
            val layout = pkg.skin.layouts.getValue(id)
            val w = if (page.portrait) portraitW else portraitH
            val h = if (page.portrait) portraitH else portraitW
            val plan = planPage(console, layout, w, h, hardcore)
            val prefix = "oe-${page.name}"
            val image = "$prefix.png"
            val bitmap = page(pkg, id, layout, plan, w, h)
            val descs = plan.elements.flatMap { descs(pkg, id, layout, layout.elements[it.index], it) }
            OverlayWriter.saveImages(outDir, image, bitmap, descs, prefix)
            bitmap.recycle()
            val viewport = plan.viewport.rect()
            OverlayWriter.write(
                cfg, i, page.name, page.rotate, image, descs, w, h, viewport, grow = 1f, clear = listOf(viewport), gap = plan.gap,
                prefix = prefix,
            )
        }
        return File(outDir, "skin.cfg").apply { writeText(cfg.toString()) }
    }

    /** The page's own image: the background, the frame, and the game's screens cut out of them. */
    private fun page(pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, plan: PagePlan, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(layout.background.color ?: DEFAULT_BACKGROUND)
        val whole = plan.fit.box(SkinRect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat())).rect()
        pkg.background(id)?.let { draw(canvas, pkg, it, whole) }
        layout.frame?.let { frame ->
            val box = plan.fit.box(frame.rect).rect()
            val drawn = pkg.frame(id)?.let { draw(canvas, pkg, it, box) } == true
            if (!drawn) canvas.drawRect(box, fill(frame.colors.frame ?: DEFAULT_FRAME))
        }
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        layout.screens.forEach { canvas.drawRect(plan.fit.box(it).rect(), clear) }
        return bitmap
    }

    /** The descs [plan] makes of [element]: its images and its touch area. */
    private fun descs(pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, element: SkinElement, plan: ElementPlan): List<OverlayDesc> {
        val files = pkg.images(id, element)
        val box = plan.box.rect()
        return when (plan) {
            is ElementPlan.Button -> {
                val image = image(pkg, files[0], box) ?: shape(element, box)
                listOf(OverlayDesc(plan.bind ?: "nul", box, image = image))
            }
            is ElementPlan.Action -> {
                val frame = layout.frame?.colors ?: SkinFrameColors()
                val art = image(pkg, files[0], box)
                val icon = art ?: glyph(plan.icon, box, frame.icons ?: DEFAULT_ICONS)
                if (plan.dim) return listOf(OverlayDesc("nul", box, image = faded(icon)))
                val lit = plan.lit?.let { state ->
                    OverlayDesc("nul", RectF(box), lit = OverlayLit(state, tile(plan.icon, box, frame, image(pkg, files[0], box))))
                }
                listOfNotNull(OverlayDesc(plan.bind ?: "nul", box, image = icon), lit)
            }
            is ElementPlan.Dpad -> {
                val pad = image(pkg, files[0], box) ?: cross(box, element.colors["pad"] ?: DEFAULT_PAD)
                val travel = plan.travel.rect()
                val ball = image(pkg, files[1], travel) ?: ball(travel, element.colors["ball"] ?: DEFAULT_BALL)
                listOf(
                    OverlayDesc("nul", box, image = pad),
                    OverlayDesc("dpad_area", travel, dpad = listOf("up", "down", "left", "right"), reach = plan.reach, knob = ball),
                )
            }
            is ElementPlan.Stick -> {
                val base = image(pkg, files[0], box) ?: disc(box, element.colors["base"] ?: DEFAULT_BASE)
                val range = plan.range.rect()
                listOf(
                    OverlayDesc("nul", box, image = base),
                    OverlayDesc(plan.bind, range, radial = true, well = box, knob = knob(pkg, files[1], range, plan.knob, element.colors["knob"] ?: DEFAULT_KNOB)),
                )
            }
        }
    }

    /** [file] of the skin drawn over [box] of [canvas]; false when it cannot be read. */
    private fun draw(canvas: Canvas, pkg: SkinPackage, file: String, box: RectF): Boolean {
        val bitmap = decode(pkg, file, box.width(), box.height()) ?: return false
        canvas.drawBitmap(bitmap, null, box, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        bitmap.recycle()
        return true
    }

    /** [file] (null: the skin has none) scaled to [box]'s size, or null when there is none or it cannot be read. */
    private fun image(pkg: SkinPackage, file: String?, box: RectF): Bitmap? {
        file ?: return null
        val decoded = decode(pkg, file, box.width(), box.height()) ?: return null
        val out = blank(box)
        Canvas(out).drawBitmap(decoded, null, RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        decoded.recycle()
        return out
    }

    /**
     * [file] decoded no larger than it needs to be for a [w] x [h] box (a power-of-two subsample of a much larger
     * picture), or null when it cannot be read: the element is then drawn in its colours.
     */
    private fun decode(pkg: SkinPackage, file: String, w: Float, h: Float): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        pkg.source.open(file)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        pkg.source.open(file)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            .also { if (it == null) Log.w(TAG, "$file is not an image: drawn in its colours") }
    } catch (e: Exception) {
        Log.w(TAG, "$file not readable: drawn in its colours", e)
        null
    }

    /** A button or keypad key with no image: its shape in its "button" colour, its label in its "letter" colour. */
    private fun shape(element: SkinElement, box: RectF): Bitmap {
        val out = blank(box)
        val c = Canvas(out)
        val r = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val paint = fill(element.colors["button"] ?: DEFAULT_BUTTON)
        when (element.shape ?: if (element.type == SkinElementType.KEYPAD) SkinShape.RECT else SkinShape.CIRCLE) {
            SkinShape.CIRCLE -> c.drawOval(r, paint)
            SkinShape.PILL -> c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, paint)
            SkinShape.RECT -> minOf(r.width(), r.height()).let { c.drawRoundRect(r, it * 0.16f, it * 0.16f, paint) }
        }
        element.label?.takeIf { it.isNotBlank() }?.let { label ->
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = element.colors["letter"] ?: DEFAULT_LETTER; typeface = inter; textAlign = Paint.Align.CENTER
                textSize = r.height() * 0.42f
            }
            // A long label (START) shrinks to fit the width.
            val width = text.measureText(label)
            if (width > r.width() * 0.8f) text.textSize *= r.width() * 0.8f / width
            c.drawText(label, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text)
        }
        return out
    }

    /** A D-pad with no image: a cross in its "pad" colour, arms a third of its size wide. */
    private fun cross(box: RectF, color: Int): Bitmap {
        val out = blank(box)
        val c = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val paint = fill(color)
        val arm = minOf(w, h) / 3f
        val corner = arm * 0.2f
        c.drawRoundRect(RectF((w - arm) / 2f, 0f, (w + arm) / 2f, h), corner, corner, paint)
        c.drawRoundRect(RectF(0f, (h - arm) / 2f, w, (h + arm) / 2f), corner, corner, paint)
        return out
    }

    /** A D-pad's ball with no image: a circle in its "ball" colour, about half its travel across, in the travel's box. */
    private fun ball(travel: RectF, color: Int): Bitmap {
        val out = blank(travel)
        val d = minOf(out.width, out.height) * 0.55f
        Canvas(out).drawOval(RectF((out.width - d) / 2f, (out.height - d) / 2f, (out.width + d) / 2f, (out.height + d) / 2f), fill(color))
        return out
    }

    /** A stick's base with no image: a circle in its "base" colour. */
    private fun disc(box: RectF, color: Int): Bitmap {
        val out = blank(box)
        val d = minOf(out.width, out.height).toFloat()
        Canvas(out).drawOval(RectF((out.width - d) / 2f, (out.height - d) / 2f, (out.width + d) / 2f, (out.height + d) / 2f), fill(color))
        return out
    }

    /** A stick's knob, [size] across, centred in its [range] square: the skin's image, else a circle in [color]. */
    private fun knob(pkg: SkinPackage, file: String?, range: RectF, size: Float, color: Int): Bitmap {
        val out = blank(range)
        val target = RectF((out.width - size) / 2f, (out.height - size) / 2f, (out.width + size) / 2f, (out.height + size) / 2f)
        val c = Canvas(out)
        val image = file?.let { decode(pkg, it, size, size) }
        if (image != null) {
            c.drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            image.recycle()
        } else {
            c.drawOval(target, fill(color))
        }
        return out
    }

    /** A quick action with no image: its glyph ([icon], an app action as the strip names it) in [color]. */
    private fun glyph(icon: String?, box: RectF, color: Int): Bitmap {
        val out = blank(box)
        drawGlyph(Canvas(out), icon, out.width / 2f, out.height / 2f, minOf(out.width, out.height) * 0.6f, color)
        return out
    }

    /**
     * A quick action's "on" look: a rounded tile in the frame's "active" colour with the skin's [art] over it, or the
     * action's glyph in the "activeIcon" tone.
     */
    private fun tile(icon: String?, box: RectF, colors: SkinFrameColors, art: Bitmap?): Bitmap {
        val out = blank(box)
        val c = Canvas(out)
        val r = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val corner = minOf(r.width(), r.height()) * 0.25f
        c.drawRoundRect(r, corner, corner, fill(colors.active ?: OverlayWriter.ACCENT))
        if (art != null) {
            c.drawBitmap(art, 0f, 0f, null)
            art.recycle()
        } else {
            val tone = if (colors.activeIcon == SkinIconTone.DARK) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            drawGlyph(c, icon, r.centerX(), r.centerY(), minOf(r.width(), r.height()) * 0.6f, tone)
        }
        return out
    }

    /** [image] at a speed key's opacity in hardcore (0.45, like the drawn skins' strip); [image] is recycled. */
    private fun faded(image: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(image, 0f, 0f, Paint().apply { alpha = SPEED_OFF_ALPHA })
        image.recycle()
        return out
    }

    private fun drawGlyph(c: Canvas, icon: String?, cx: Float, cy: Float, size: Float, color: Int) {
        val data = GLYPHS[icon] ?: return
        val path: Path = PathParser.createPathFromPathData(data)
        path.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(cx - size / 2f, cy - size / 2f) })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (icon !in FILLED) {
                style = Paint.Style.STROKE; strokeWidth = 1.8f * size / 24f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }
        })
    }

    private fun blank(box: RectF) =
        Bitmap.createBitmap(box.width().roundToInt().coerceAtLeast(1), box.height().roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun SkinBox.rect() = RectF(x, y, right, bottom)

    companion object {
        private const val TAG = "OpenEmuxSkin"

        // A part left unset, with no image, in the app's Dark skin family.
        private const val DEFAULT_BACKGROUND = 0xFF16161A.toInt()
        private const val DEFAULT_FRAME = 0xFF0E0E11.toInt()
        private const val DEFAULT_ICONS = 0xFFFFFFFF.toInt()
        private const val DEFAULT_BUTTON = 0xFF2E2E35.toInt()
        private const val DEFAULT_LETTER = 0xFFFFFFFF.toInt()
        private const val DEFAULT_PAD = 0xFF2E2E35.toInt()
        private const val DEFAULT_BALL = 0xFF6B6B76.toInt()
        private const val DEFAULT_BASE = 0xFF2E2E35.toInt()
        private const val DEFAULT_KNOB = 0xFF6B6B76.toInt()

        /** The quick actions' glyphs, by the app action that draws them (OpenEmuxBinds), in 24-unit paths. */
        private val GLYPHS = mapOf(
            "save_state" to Icons.SaveState, "load_state" to Icons.LoadState, "turbo_fire_toggle" to "M13 2L4 14h7l-1 8 9-12h-7z",
            "toggle_slowmotion" to Icons.SlowMotion, "toggle_fast_forward" to Icons.FastForward, "screens" to Icons.Screens,
            "menu_toggle" to Icons.Menu, "screenshot" to Icons.Camera, "reset" to Icons.Reset,
        )

        /** The filled glyphs; the rest are stroked, like the strip's turbo bolt. */
        private val FILLED = setOf("save_state", "load_state", "toggle_fast_forward")
    }
}
