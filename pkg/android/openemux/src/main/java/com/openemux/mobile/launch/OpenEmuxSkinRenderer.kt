package com.openemux.mobile.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.SkinArt
import com.openemux.mobile.data.SkinColorMath
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFrameColors
import com.openemux.mobile.data.SkinFill
import com.openemux.mobile.data.SkinLabelKind
import com.openemux.mobile.data.SkinLabels
import com.openemux.mobile.data.SkinPaintDefaults
import com.openemux.mobile.data.SkinShapes
import com.openemux.mobile.data.SkinTextureFit
import com.openemux.mobile.data.SkinTextureFrame
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.data.SkinTemplate
import com.openemux.mobile.data.TemplateImage
import com.openemux.mobile.data.TemplateImageKind
import com.openemux.mobile.ui.Icons
import com.retroarch.R
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws a skin in OpenEmux's own format (docs/skin-format.md, read by data/SkinFormat.kt) as a RetroArch overlay, the
 * same kind of skin.cfg [ImportedSkinRenderer] writes for a package: a portrait and a landscape page, each the form's
 * layout of that orientation fitted to the window (OpenEmuxSkinLayouts.kt decides where everything goes).
 *
 * The page's image is the background and the frame, with the game's screens cut out of it, and the labels printed
 * beside their controls; every element is a desc of its own with its own image, the element's file when the skin has
 * one and its colours, shape and label when not. The D-pad's ball and a stick's knob are the images the engine moves
 * with the thumb.
 *
 * A control drawn from its colours is a face over a relief ([SkinElement.depth] of it showing under the face), shaded
 * from its one colour the way the bundled art is (tools/skins), so a control repainted in the editor still sits among
 * the ones left as they were.
 */
class OpenEmuxSkinRenderer(context: Context) {

    /** The labels' typeface: the one the bundled art is lettered in (tools/skins: Space Grotesk, bold). */
    private val lettering: Typeface = ResourcesCompat.getFont(context, R.font.grotesk_bold) ?: Typeface.DEFAULT_BOLD

    /**
     * Writes [pkg]'s [layouts] (the form's portrait and landscape, LayoutChoice.Use) for [console], played on [coreId],
     * into [outDir], the window [width] x [height] less the [cutout]. [first] is the page that opens (ownPages), with
     * the layouts' keypad pages when both have one (keypadPages); [hardcore] dims the speed keys and leaves them out.
     * [ndsLayouts] (the saved portrait and landscape NDS layouts, on the core that follows the page shown) draws one page
     * per screen layout and orientation when both layouts have screen-layout pages (ndsPages), named and linked as a
     * package's with one art per layout, the [tablet]'s cycle; [wroteNdsLayouts] says whether it did.
     */
    fun render(
        pkg: SkinPackage, layouts: LayoutChoice.Use, console: Console,
        width: Int, height: Int, outDir: File, cutout: Int = 0, hardcore: Boolean = false, first: String? = null,
        coreId: String? = null, ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null, tablet: Boolean = false,
    ): File {
        outDir.mkdirs()
        val portraitW = minOf(width, height)
        val portraitH = maxOf(width, height) - cutout
        val nds = ndsLayouts?.takeIf { console == Console.NDS && ndsPages(pkg.skin, layouts) }
        wroteNdsLayouts = nds != null
        val keypad = nds == null && keypadPages(pkg.skin, layouts)
        val kind = if (nds != null) ", screen-layout pages" else if (keypad) ", keypad pages" else ""
        Log.i(TAG, "drawing ${pkg.skin.name} (${layouts.portrait.id}, ${layouts.landscape.id}$kind)")
        val pages = ownPages(keypad, first, nds, tablet)
        val cfg = StringBuilder("overlays = ${pages.size}\n")
        pages.forEachIndexed { i, page ->
            val id = if (page.portrait) layouts.portrait else layouts.landscape
            val base = pkg.skin.layouts.getValue(id)
            // A screen-layout page is drawn as a layout of its own, its images in its folder.
            val layout = page.screen?.let(base::screenPage) ?: base
            val w = if (page.portrait) portraitW else portraitH
            val h = if (page.portrait) portraitH else portraitW
            val plan = planPage(console, layout, w, h, hardcore, coreId, page.keypad, page.toggle, page.screensNext)
            val prefix = "oe-${page.name.replace('@', '-')}"
            val image = "$prefix.png"
            val bitmap = page(pkg, id, layout, plan, w, h, page.keypad, page.screen)
            val elements = layout.elements(page.keypad)
            val descs = plan.elements.flatMap { descs(pkg, id, layout, elements[it.index], it, page.keypad, page.screen) }
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

    /** Whether the last [render] drew one page per NDS screen layout. */
    var wroteNdsLayouts = false
        private set

    /**
     * One image of [pkg]'s template (issue 787, data/SkinTemplate.kt) as PNG bytes at its exact size: the skin's own
     * file as it is when it is a PNG of that size, re-encoded (a WebP, issue 806) or scaled to it when not, and drawn the way the game draws the element
     * without an image (its colours, shape, label or glyph) when the skin has none or it cannot be read.
     */
    fun templateImage(pkg: SkinPackage, id: SkinLayoutId, image: TemplateImage): ByteArray {
        val path = pkg.image(id, image.path)
        val own = path?.let { p -> runCatching { pkg.source.open(p)?.use { it.readBytes() } }.getOrNull() }
        if (own != null && SkinTemplate.pngSize(own) == image.width to image.height) return own
        val box = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val bitmap = path?.let { image(pkg, it, box) } ?: placeholder(pkg, id, image, box)
        return try {
            java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The skin editor's stage (issue 784): layout [id]'s background and frame at [scale] times its units, the game's
     * screens cut out, as the game draws them.
     */
    fun previewPage(pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, scale: Float): Bitmap {
        val w = (layout.size.width * scale).roundToInt().coerceAtLeast(1)
        val h = (layout.size.height * scale).roundToInt().coerceAtLeast(1)
        // The frame is drawn on its own (previewFrame) and the screens over the page, so both follow the screen area
        // as it moves (issue 785).
        val plan = planPage(pkg.skin.console, layout.copy(elements = emptyList(), frame = null), w, h, false)
        return page(pkg, id, layout.copy(elements = emptyList(), frame = null, screens = emptyList()), plan, w, h, false)
    }

    /**
     * The editor's frame of [layout] alone (issue 785), at [scale] times its units: its image or its colour, the game's
     * screens cut out of it. Null for a layout without a frame.
     */
    fun previewFrame(pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, scale: Float): Bitmap? {
        val frame = layout.frame ?: return null
        val w = (frame.rect.width * scale).roundToInt().coerceAtLeast(1)
        val h = (frame.rect.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val box = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val drawn = pkg.frame(id)?.let { draw(canvas, pkg, it, box) } == true
        if (!drawn) drawFrame(canvas, frame, box, scale)
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        layout.screens.forEach { r ->
            canvas.drawRect(RectF((r.x - frame.rect.x) * scale, (r.y - frame.rect.y) * scale, (r.x + r.width - frame.rect.x) * scale, (r.y + r.height - frame.rect.y) * scale), clear)
        }
        return bitmap
    }

    /**
     * Layout [id] of [pkg] as the game shows it at rest, no larger than [max] px on its longer side, the game's
     * screens dark: the skin list's miniature of the skin on this device.
     */
    fun thumbnail(pkg: SkinPackage, id: SkinLayoutId, max: Int): Bitmap {
        val layout = pkg.skin.layouts.getValue(id)
        val scale = minOf(1f, max / maxOf(layout.size.width, layout.size.height).toFloat())
        val w = (layout.size.width * scale).roundToInt().coerceAtLeast(1)
        val h = (layout.size.height * scale).roundToInt().coerceAtLeast(1)
        val plan = planPage(pkg.skin.console, layout, w, h, false)
        val out = createBitmap(w, h)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(SCREEN)
        page(pkg, id, layout, plan, w, h, false).let { canvas.drawBitmap(it, 0f, 0f, null); it.recycle() }
        for (element in plan.elements) {
            for (desc in descs(pkg, id, layout, layout.elements[element.index], element, false)) {
                for (art in listOfNotNull(desc.image, desc.knob)) {
                    canvas.drawBitmap(art, null, desc.rect, paint)
                    art.recycle()
                }
                desc.lit?.bitmap?.recycle()
            }
        }
        return out
    }

    /**
     * [element] of layout [id] drawn alone on a [w] x [h] bitmap the way the game draws it at rest: its image or its
     * colours, a D-pad with its ball, a stick with its knob (issue 784). A button's or a key's label the app draws is
     * left out: the editor draws it as a piece of its own ([previewLabel]), so it moves with the finger.
     */
    fun previewElement(pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, element: SkinElement, w: Int, h: Int): Bitmap {
        // Its relief and its corners at the bitmap's scale too.
        val alone = element.at(SkinRect(0f, 0f, w.toFloat(), h.toFloat()))
        val page = SkinLayout(com.openemux.mobile.data.SkinSize(w, h), listOf(alone.rect), layout.background, layout.frame, listOf(alone))
        val plan = planPage(pkg.skin.console, page, w, h, false).elements.single()
        val out = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (desc in descs(pkg, id, layout, alone, plan, false, labels = false)) {
            for (art in listOfNotNull(desc.image, desc.knob)) {
                canvas.drawBitmap(art, null, desc.rect, paint)
                art.recycle()
            }
            desc.lit?.bitmap?.recycle()
        }
        return out
    }

    /**
     * A texture (issue 786): [source] filling a [w] x [h] image of [kind] as [frame] places it, clipped to the shape
     * the game draws the part in (a button's or a key's face, the D-pad's cross, a disc for a ball, a stick's base
     * and its knob; the whole box for the background and the frame). On a button, a key or the D-pad ([element]) it is
     * the face alone, where the face is in the control ([SkinArt.FACE]): the relief under it and the label over it
     * stay the app's to draw, in their own colours.
     */
    fun texture(source: Bitmap, kind: TemplateImageKind, element: SkinElement?, w: Int, h: Int, frame: SkinTextureFrame): Bitmap {
        val box = RectF(0f, 0f, w.coerceAtLeast(1).toFloat(), h.coerceAtLeast(1).toFloat())
        val out = blank(box)
        val c = Canvas(out)
        // Opaque white: only the outline matters, a translucent colour must not fade the texture.
        val white = fill(0xFFFFFFFF.toInt())
        val mask = when (kind) {
            TemplateImageKind.BUTTON, TemplateImageKind.KEYPAD -> element?.at(box.skin())?.let { e -> blank(box).also { drawFace(Canvas(it), e, faceOf(e, box)) { white } } }
            TemplateImageKind.DPAD -> element?.at(box.skin())?.let { e -> blank(box).also { drawCross(Canvas(it), faceOf(e, box), 0f, white) } }
            TemplateImageKind.DPAD_BALL, TemplateImageKind.STICK_BASE, TemplateImageKind.STICK_KNOB -> disc(box, 0xFFFFFFFF.toInt())
            else -> null
        }
        mask?.let { c.drawBitmap(it, 0f, 0f, null); it.recycle() }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (mask != null) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val placed = SkinTextureFit.place(source.width, source.height, box.width(), box.height(), frame)
        val rects = if (frame.fill == SkinFill.REPEAT) SkinTextureFit.tiles(placed, box.width(), box.height()) else listOf(placed)
        val draw = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun drawOn(canvas: Canvas) = rects.forEach { r -> canvas.drawBitmap(source, null, RectF(r.x, r.y, r.x + r.width, r.y + r.height), draw) }
        if (mask != null) {
            // The tiles drawn on a picture of their own, then kept only where the mask is (SRC_IN).
            val pic = blank(box)
            drawOn(Canvas(pic))
            c.drawBitmap(pic, 0f, 0f, paint)
            pic.recycle()
        } else {
            drawOn(c)
        }
        return out
    }

    /**
     * [element]'s label alone, as the editor shows and moves it: on a bitmap [scale] px per unit of the layout, the
     * label's centre in its middle. The bitmap is wider than the label's box by [LABEL_ROOM] of the type size on every
     * side, since the box is only about as wide as the lettering ([SkinLabels.box]). Null when it has no label.
     */
    fun previewLabel(element: SkinElement, scale: Float): Bitmap? {
        if (!SkinLabels.has(element)) return null
        val box = SkinLabels.box(element)
        val room = box.height * LABEL_ROOM
        val w = ((box.width + 2f * room) * scale).roundToInt().coerceIn(1, 1024)
        val h = ((box.height + 2f * room) * scale).roundToInt().coerceIn(1, 1024)
        val out = createBitmap(w, h)
        drawLabel(Canvas(out), element, w / 2f, h / 2f, scale)
        return out
    }

    /** [bitmap] as PNG bytes. */
    fun png(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** [image] with no file of its own, as the game draws it. */
    private fun placeholder(pkg: SkinPackage, id: SkinLayoutId, image: TemplateImage, box: RectF): Bitmap {
        val base = pkg.skin.layouts.getValue(id)
        // A screen-layout page's image in the page's colours.
        val layout = image.screenPage?.let(base::screenPage) ?: base
        val e = image.element
        return when (image.kind) {
            TemplateImageKind.BACKGROUND -> blank(box).also { it.eraseColor(layout.background(image.keypadPage).color ?: DEFAULT_BACKGROUND) }
            TemplateImageKind.FRAME -> blank(box).also { b -> layout.frame?.let { drawFrame(Canvas(b), it, box, 1f) } ?: b.eraseColor(DEFAULT_FRAME) }
            // What the element's image is to hold (SkinElement.art): the app draws the rest over the image it gets back.
            TemplateImageKind.BUTTON, TemplateImageKind.KEYPAD -> blank(box).also { b ->
                val c = Canvas(b)
                drawControl(c, e!!, box, relief = e.art != SkinArt.FACE)
                if (e.art == SkinArt.FULL && SkinLabels.has(e)) drawLabelIn(c, e, box)
            }
            TemplateImageKind.ACTION -> glyph(OpenEmuxBinds.action(pkg.skin.console, e!!.bind, false)?.icon, box, layout.frame?.colors?.icons ?: DEFAULT_ICONS)
            TemplateImageKind.DPAD -> blank(box).also { b ->
                val c = Canvas(b)
                drawPad(c, e!!, box, relief = e.art != SkinArt.FACE)
                if (e.art == SkinArt.FULL) drawArrows(c, e, box)
            }
            TemplateImageKind.DPAD_BALL -> disc(box, e!!.colors["ball"] ?: DEFAULT_BALL)
            TemplateImageKind.STICK_BASE -> disc(box, e!!.colors["base"] ?: DEFAULT_BASE)
            TemplateImageKind.STICK_KNOB -> disc(box, e!!.colors["knob"] ?: DEFAULT_KNOB)
        }
    }

    /**
     * The page's own image: the background ([keypad]: the keypad page's), the frame, and the game's screens cut out of
     * them. On [screen], a screen-layout page ([layout] is its screenPage), its background and frame are in its folder.
     */
    private fun page(
        pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, plan: PagePlan, w: Int, h: Int, keypad: Boolean, screen: String? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(layout.background(keypad).color ?: DEFAULT_BACKGROUND)
        val whole = plan.fit.box(SkinRect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat())).rect()
        (if (screen != null) pkg.screenBackground(id, screen) else pkg.background(id, keypad))?.let { draw(canvas, pkg, it, whole) }
        layout.frame?.let { frame ->
            val box = plan.fit.box(frame.rect).rect()
            val drawn = (if (screen != null) pkg.screenFrame(id, screen) else pkg.frame(id))?.let { draw(canvas, pkg, it, box) } == true
            if (!drawn) drawFrame(canvas, frame, box, plan.fit.s)
        }
        // A label printed beside its control (START under its key) does not fit in the control's own image: it is
        // part of the page. One that only reaches past the control's edge is drawn here and in the control's image,
        // which covers its own share of it.
        for (e in layout.elements(keypad)) {
            if (!SkinLabels.has(e) || SkinLabels.box(e).within(e.rect)) continue
            val file = if (screen != null) pkg.screenImages(id, screen, e)[0] else pkg.images(id, e, keypad)[0]
            if (!SkinLabels.drawn(e, file != null)) continue
            val at = SkinLabels.centre(e)
            drawLabel(canvas, e, plan.fit.ox + at.x * plan.fit.s, plan.fit.oy + at.y * plan.fit.s, plan.fit.s)
        }
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        layout.screens.forEach { canvas.drawRect(plan.fit.box(it).rect(), clear) }
        return bitmap
    }

    /** [frame] from its colour over [box], its corners rounded by its radius at [scale] px per unit. */
    private fun drawFrame(canvas: Canvas, frame: com.openemux.mobile.data.SkinFrame, box: RectF, scale: Float) {
        val corner = (frame.radius * scale).coerceAtMost(minOf(box.width(), box.height()) / 2f)
        canvas.drawRoundRect(box, corner, corner, fill(frame.colors.frame ?: DEFAULT_FRAME))
    }

    /**
     * The descs [plan] makes of [element] (on the [keypad] page, the [screen]-layout page or the controls): its images
     * and its touch area.
     */
    private fun descs(
        pkg: SkinPackage, id: SkinLayoutId, layout: SkinLayout, element: SkinElement, plan: ElementPlan, keypad: Boolean,
        screen: String? = null, labels: Boolean = true,
    ): List<OverlayDesc> {
        val files = if (screen != null) pkg.screenImages(id, screen, element) else pkg.images(id, element, keypad)
        val box = plan.box.rect()
        return when (plan) {
            is ElementPlan.Button -> listOf(OverlayDesc(plan.bind ?: "nul", box, image = control(pkg, files[0], element, box, labels)))
            is ElementPlan.Toggle ->
                listOf(OverlayDesc(if (plan.next != null) "overlay_next" else "nul", box, next = plan.next, image = control(pkg, files[0], element, box, labels)))
            is ElementPlan.Action -> {
                val frame = layout.frame?.colors ?: SkinFrameColors()
                val art = image(pkg, files[0], box)
                val icon = art ?: glyph(plan.icon, box, frame.icons ?: DEFAULT_ICONS)
                if (plan.dim) return listOf(OverlayDesc("nul", box, image = faded(icon)))
                val lit = plan.lit?.let { state ->
                    OverlayDesc("nul", RectF(box), lit = OverlayLit(state, tile(plan.icon, box, frame, art?.copy(Bitmap.Config.ARGB_8888, false))))
                }
                listOfNotNull(OverlayDesc(plan.bind ?: "nul", box, next = plan.next, image = icon), lit)
            }
            is ElementPlan.Dpad -> {
                val pad = pad(pkg, files[0], element, box)
                val travel = plan.travel.rect()
                val ball = ball(pkg, files[1], travel, element.colors["ball"] ?: DEFAULT_BALL)
                // A second D-pad on a console with none is drawn, ball at rest, and presses nothing.
                val area = plan.binds?.let { OverlayDesc("dpad_area", travel, dpad = it, reach = plan.reach, knob = ball) }
                    ?: OverlayDesc("nul", travel, image = ball)
                listOf(OverlayDesc("nul", box, image = pad), area)
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
        (pkg.source as? PreviewSkinSource)?.bitmap(file)?.let { return it.copy(Bitmap.Config.ARGB_8888, false) }
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

    /**
     * A button or a keypad key on a [box]-sized bitmap: its image ([file]) when the skin has one, with what the image
     * leaves to the app ([SkinElement.art]: the relief under a face, the label over a key), else all of it from its
     * colours. Without [label], the label the app would draw is left out.
     */
    private fun control(pkg: SkinPackage, file: String?, element: SkinElement, box: RectF, label: Boolean = true): Bitmap {
        val out = blank(box)
        val c = Canvas(out)
        val r = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val art = file?.let { decode(pkg, it, r.width(), r.height()) }
        if (art == null) {
            drawControl(c, element, r)
        } else {
            if (element.art == SkinArt.FACE) drawControl(c, element, r, face = false)
            c.drawBitmap(art, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            art.recycle()
        }
        if (label && SkinLabels.has(element) && SkinLabels.drawn(element, art != null)) drawLabelIn(c, element, r)
        return out
    }

    /** The D-pad on a [box]-sized bitmap, as [control] draws a button: its image, or its cross, and its four arrows. */
    private fun pad(pkg: SkinPackage, file: String?, element: SkinElement, box: RectF): Bitmap {
        val out = blank(box)
        val c = Canvas(out)
        val r = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val art = file?.let { decode(pkg, it, r.width(), r.height()) }
        if (art == null) {
            drawPad(c, element, r)
        } else {
            if (element.art == SkinArt.FACE) drawPad(c, element, r, face = false)
            c.drawBitmap(art, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            art.recycle()
        }
        if (SkinLabels.drawn(element, art != null)) drawArrows(c, element, r)
        return out
    }

    /** How far down [r] the face of [e] ends: [r] stands for the element's whole rect, relief included. */
    private fun faceOf(e: SkinElement, r: RectF) = RectF(r.left, r.top, r.right, r.top + r.height() * e.face.height / e.rect.height)

    /**
     * A button or a key from its colours over [r] (the element's rect): its [relief], the face's own shape straight
     * under it, and its [face], lit from above like the bundled art when it has a relief (flat when it has none, as a
     * skin without one has always been drawn).
     */
    private fun drawControl(c: Canvas, e: SkinElement, r: RectF, relief: Boolean = true, face: Boolean = true) {
        val top = faceOf(e, r)
        val depth = r.height() - top.height()
        if (relief && depth > 0f) {
            val paint = fill(SkinPaintDefaults.relief(e))
            drawFace(c, e, RectF(top.left, top.top + depth, top.right, top.bottom + depth)) { paint }
        }
        if (!face) return
        val color = SkinPaintDefaults.face(e)
        drawFace(c, e, top) { own ->
            fill(color).apply {
                if (e.depth > 0f) shader = when {
                    e.type == SkinElementType.KEYPAD ->
                        LinearGradient(0f, own.top, 0f, own.bottom, SkinColorMath.lighten(color, 0.1f), color, Shader.TileMode.CLAMP)
                    shapeOf(e) == SkinShape.CIRCLE -> RadialGradient(
                        own.left + own.width() * 0.38f, own.top + own.height() * 0.3f, hypot(own.width() * 0.62f, own.height() * 0.7f),
                        intArrayOf(SkinColorMath.lighten(color, 0.22f), color, SkinColorMath.darken(color, 0.14f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                    )
                    else -> LinearGradient(0f, own.top, 0f, own.bottom, SkinColorMath.lighten(color, 0.12f), SkinColorMath.darken(color, 0.08f), Shader.TileMode.CLAMP)
                }
            }
        }
    }

    private fun shapeOf(e: SkinElement) = e.shape ?: if (e.type == SkinElementType.KEYPAD) SkinShape.RECT else SkinShape.CIRCLE

    /**
     * [e]'s outline in [top] (the box its face takes on the canvas), turned as [e] is about the box's centre; [paint]
     * gets the outline's own box, before the turn, to shade it.
     */
    private fun drawFace(c: Canvas, e: SkinElement, top: RectF, paint: (RectF) -> Paint) {
        val (w, h) = SkinShapes.unturned(top.width(), top.height(), e.rotation)
        val own = RectF(top.centerX() - w / 2f, top.centerY() - h / 2f, top.centerX() + w / 2f, top.centerY() + h / 2f)
        val k = top.width() / e.rect.width
        c.withRotation(e.rotation, top.centerX(), top.centerY()) {
            when (shapeOf(e)) {
                SkinShape.CIRCLE -> drawOval(own, paint(own))
                SkinShape.PILL -> (minOf(w, h) / 2f).let { drawRoundRect(own, it, it, paint(own)) }
                SkinShape.RECT -> (e.radius?.times(k) ?: (minOf(w, h) * 0.16f)).coerceAtMost(minOf(w, h) / 2f).let { drawRoundRect(own, it, it, paint(own)) }
            }
        }
    }

    /** [e]'s label on a canvas where [r] stands for the element's rect. */
    private fun drawLabelIn(c: Canvas, e: SkinElement, r: RectF) {
        val k = r.width() / e.rect.width
        val at = SkinLabels.centre(e)
        drawLabel(c, e, r.left + (at.x - e.rect.x) * k, r.top + (at.y - e.rect.y) * k, k)
    }

    /**
     * [e]'s label centred on ([cx], [cy]) at [k] px per unit of the layout, in its "letter" colour: typed in the art's
     * typeface, or the glyph the console prints there (the PlayStation's shapes, an arrow, the menu's lines). With no
     * size of its own a typed one takes a share of the face and shrinks to fit its width; one left on a turned key is
     * turned with it.
     */
    private fun drawLabel(c: Canvas, e: SkinElement, cx: Float, cy: Float, k: Float) {
        val label = e.label?.takeIf { it.isNotBlank() } ?: return
        val color = SkinPaintDefaults.letter(e)
        val kind = SkinLabels.kind(label)
        c.withSave { drawLabel(this, e, label, kind, color, cx, cy, k) }
    }

    /** [drawLabel] on a canvas it may turn: the caller puts it back as it was. */
    private fun drawLabel(c: Canvas, e: SkinElement, label: String, kind: SkinLabelKind, color: Int, cx: Float, cy: Float, k: Float) {
        if (e.labelOffset == null) c.rotate(e.rotation, cx, cy)
        if (kind == SkinLabelKind.TEXT) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; typeface = lettering; textAlign = Paint.Align.CENTER; letterSpacing = SkinLabels.spacing(e)
            }
            val own = e.labelSize
            if (own != null) {
                text.textSize = own * k
            } else {
                val (w, h) = e.face.let { SkinShapes.unturned(it.width, it.height, e.rotation) }
                text.textSize = h * k * 0.42f
                val wide = text.measureText(label)
                if (wide > w * k * SkinLabels.FIT) text.textSize *= w * k * SkinLabels.FIT / wide
            }
            c.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text)
        } else {
            val size = SkinLabels.size(e) * k
            val (path, turn, stroke) = when (kind) {
                SkinLabelKind.ARROW -> Triple(ARROW, SkinLabels.ARROWS.getValue(label), 0f)
                SkinLabelKind.MENU -> Triple(MENU, 0f, 2f)
                else -> Triple(SYMBOLS.getValue(label), 0f, 2.2f)
            }
            c.rotate(turn, cx, cy)
            drawPath(c, path, cx, cy, size, color, stroke)
        }
    }

    /** A 24-unit [path] drawn [size] across, centred on ([cx], [cy]): filled, or with [stroke] units of line. */
    private fun drawPath(c: Canvas, data: String, cx: Float, cy: Float, size: Float, color: Int, stroke: Float) {
        val path: Path = PathParser.createPathFromPathData(data)
        path.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(cx - size / 2f, cy - size / 2f) })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (stroke > 0f) {
                style = Paint.Style.STROKE; strokeWidth = stroke * size / 24f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }
        })
    }

    /**
     * The D-pad from its colours over [r] (the element's rect): its [relief], the cross straight under itself, and its
     * [face] in its "pad" colour.
     */
    private fun drawPad(c: Canvas, e: SkinElement, r: RectF, relief: Boolean = true, face: Boolean = true) {
        val top = faceOf(e, r)
        val depth = r.height() - top.height()
        if (relief && depth > 0f) drawCross(c, top, depth, fill(SkinPaintDefaults.relief(e)))
        if (face) drawCross(c, top, 0f, fill(SkinPaintDefaults.face(e)))
    }

    /**
     * A cross in [top] (the box the D-pad's face takes), [down] px lower: arms [ARM] of its size wide, as the bundled
     * art draws them.
     */
    private fun drawCross(c: Canvas, top: RectF, down: Float, paint: Paint) {
        val size = minOf(top.width(), top.height())
        val arm = size * ARM
        val corner = arm * 0.22f
        val cx = top.centerX()
        val cy = top.centerY() + down
        c.drawRoundRect(RectF(cx - arm / 2f, cy - size / 2f, cx + arm / 2f, cy + size / 2f), corner, corner, paint)
        c.drawRoundRect(RectF(cx - size / 2f, cy - arm / 2f, cx + size / 2f, cy + arm / 2f), corner, corner, paint)
    }

    /** The D-pad's four arrows, one near the tip of each arm, in its "letter" colour, over [r] (the element's rect). */
    private fun drawArrows(c: Canvas, e: SkinElement, r: RectF) {
        val top = faceOf(e, r)
        val size = minOf(top.width(), top.height())
        val arm = size * ARM
        val reach = size / 2f - arm * 0.55f
        val color = SkinPaintDefaults.letter(e)
        for ((dx, dy, turn) in listOf(Triple(0f, -1f, 0f), Triple(0f, 1f, 180f), Triple(-1f, 0f, 270f), Triple(1f, 0f, 90f))) {
            val x = top.centerX() + dx * reach
            val y = top.centerY() + dy * reach
            c.withRotation(turn, x, y) { drawPath(this, ARROW, x, y, arm * 0.36f, color, 0f) }
        }
    }

    /**
     * A D-pad's ball, about half its travel across, centred in the travel's box (the engine stretches the image over
     * it): the skin's image, else a circle in its "ball" colour.
     */
    private fun ball(pkg: SkinPackage, file: String?, travel: RectF, color: Int): Bitmap {
        val out = blank(travel)
        val d = minOf(out.width, out.height) * BALL_SHARE
        val target = RectF((out.width - d) / 2f, (out.height - d) / 2f, (out.width + d) / 2f, (out.height + d) / 2f)
        val image = file?.let { decode(pkg, it, d, d) }
        if (image != null) {
            Canvas(out).drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            image.recycle()
        } else {
            Canvas(out).drawOval(target, fill(color))
        }
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
        c.drawRoundRect(r, corner, corner, fill(colors.active ?: SkinPaintDefaults.ACTIVE))
        if (art != null) {
            c.drawBitmap(art, 0f, 0f, null)
            art.recycle()
        } else {
            val tone = SkinPaintDefaults.icon(colors.activeIcon)
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

    private fun RectF.skin() = SkinRect(left, top, width(), height())

    companion object {
        private const val TAG = "OpenEmuxSkin"

        /** The D-pad's ball across, as a share of its travel's box. */
        internal const val BALL_SHARE = 0.55f

        /** A D-pad's arm across, as a share of the pad (tools/skins: the engine's cross). */
        private const val ARM = 0.34f

        /** The room [previewLabel] leaves around a label's box, as a share of its type size. */
        internal const val LABEL_ROOM = 0.75f

        /** The game's screen in a miniature of the skin. */
        private const val SCREEN = 0xFF050507.toInt()

        /** The glyphs a label may be (data/SkinLabels.kt), as the bundled art draws them: 24-unit paths. */
        private const val ARROW = "M12 5l7.5 12h-15z"
        private const val MENU = "M5 7h14M5 12h14M5 17h14"
        private val SYMBOLS = mapOf(
            SkinLabels.TRIANGLE to "M12 5l7 12H5z", SkinLabels.CIRCLE to "M5.5 12a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0",
            SkinLabels.CROSS to "M6.5 6.5l11 11M17.5 6.5l-11 11", SkinLabels.SQUARE to "M6 6h12v12H6z",
        )

        // A part left unset, with no image, in the app's Dark skin family (data/SkinPaint.kt, which the editor's
        // contrast check reads too).
        private const val DEFAULT_BACKGROUND = SkinPaintDefaults.BACKGROUND
        private const val DEFAULT_FRAME = SkinPaintDefaults.FRAME
        private const val DEFAULT_ICONS = SkinPaintDefaults.ICONS
        private const val DEFAULT_BALL = SkinPaintDefaults.BALL
        private const val DEFAULT_BASE = SkinPaintDefaults.BASE
        private const val DEFAULT_KNOB = SkinPaintDefaults.KNOB

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
