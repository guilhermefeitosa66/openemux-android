package com.openemux.mobile.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The skin editor's appearance tools (issue 786): what can be painted, the colours the game draws for parts left
 * unset, colour maths (hex, HSV, WCAG contrast), the palettes and where a texture lands in its box. Pure Kotlin, no
 * Android, so every rule is unit-tested; the drawing is in launch/OpenEmuxSkinRenderer.kt.
 */

/**
 * One thing the paint panel paints: a colour key of the format (an element's part, [SkinElementType.colorParts], the
 * background's colour, or one of the frame's [SkinFrameColors]).
 */
enum class SkinPart(val id: String) {
    BACKGROUND("background"), FRAME("frame"), ICONS("icons"), ACTIVE("active"),
    BUTTON("button"), RELIEF("relief"), LETTER("letter"), PAD("pad"), BALL("ball"), BASE("base"), KNOB("knob");

    /**
     * Which of an element's [SkinElement.imageFiles] a picture of this part fills; null for a part no picture fills
     * (the relief and the label are drawn in a colour, whatever image the control has).
     */
    val imageIndex: Int?
        get() = when (this) {
            BUTTON, PAD, BASE -> 0
            BALL, KNOB -> 1
            else -> null
        }

    /** Whether a picture can fill it: the parts with an image file of their own. */
    val texturable: Boolean get() = imageIndex != null || this == BACKGROUND || this == FRAME

    companion object {
        fun byId(id: String): SkinPart? = entries.firstOrNull { it.id == id }

        /** The parts [type] can be painted in, in the format's order. */
        fun of(type: SkinElementType): List<SkinPart> = type.colorParts.mapNotNull { byId(it) }

        /** The screen frame's parts, in the panel's order. */
        val FRAME_PARTS = listOf(FRAME, ICONS, ACTIVE)
    }
}

/** What a paint panel is open on: the layout's background, its screen frame, or controls. */
sealed class SkinTarget {
    abstract val parts: List<SkinPart>

    data object Background : SkinTarget() {
        override val parts = listOf(SkinPart.BACKGROUND)
    }

    data object Frame : SkinTarget() {
        override val parts = SkinPart.FRAME_PARTS
    }

    /** Controls of [type] (the selection's first). */
    data class Controls(val type: SkinElementType) : SkinTarget() {
        override val parts: List<SkinPart> get() = SkinPart.of(type)
    }

    companion object {
        /**
         * The target of the selection's Colour or Texture: its controls; a quick action has no colours of its own (it
         * is drawn in the frame's), so it opens the frame.
         */
        fun of(layout: SkinLayout, ids: List<String>): SkinTarget? {
            val first = layout.elements.firstOrNull { it.id == ids.firstOrNull() } ?: return null
            if (first.type == SkinElementType.ACTION) return if (layout.frame != null) Frame else null
            return Controls(first.type)
        }
    }
}

/** The colours the game draws a part in when the skin leaves it unset (the app's Dark skin family). */
object SkinPaintDefaults {
    const val BACKGROUND = 0xFF16161A.toInt()
    const val FRAME = 0xFF0E0E11.toInt()
    const val ICONS = 0xFFFFFFFF.toInt()
    /** A toggled quick action's background: the app's accent. */
    const val ACTIVE = 0xFF7C3AED.toInt()
    const val BUTTON = 0xFF2E2E35.toInt()
    const val LETTER = 0xFFFFFFFF.toInt()

    /** How much darker than its face a control's relief is when the skin does not say: the face's own shade. */
    const val RELIEF_SHADE = 0.42f
    const val PAD = 0xFF2E2E35.toInt()
    const val BALL = 0xFF6B6B76.toInt()
    const val BASE = 0xFF2E2E35.toInt()
    const val KNOB = 0xFF6B6B76.toInt()
    const val LIGHT_ICON = 0xFFFFFFFF.toInt()
    const val DARK_ICON = 0xFF000000.toInt()

    fun of(part: SkinPart): Int = when (part) {
        SkinPart.BACKGROUND -> BACKGROUND
        SkinPart.FRAME -> FRAME
        SkinPart.ICONS -> ICONS
        SkinPart.ACTIVE -> ACTIVE
        SkinPart.BUTTON -> BUTTON
        SkinPart.RELIEF -> relief(BUTTON)
        SkinPart.LETTER -> LETTER
        SkinPart.PAD -> PAD
        SkinPart.BALL -> BALL
        SkinPart.BASE -> BASE
        SkinPart.KNOB -> KNOB
    }

    /** The icon's colour on a toggled quick action. */
    fun icon(tone: SkinIconTone?): Int = if (tone == SkinIconTone.DARK) DARK_ICON else LIGHT_ICON

    /** The relief under a face painted [face], when the skin sets none: the same hue, darker. */
    fun relief(face: Int): Int = SkinColorMath.darken(face, RELIEF_SHADE)

    /** [e]'s face colour: a button's or a key's "button", the D-pad's "pad". */
    fun face(e: SkinElement): Int =
        if (e.type == SkinElementType.DPAD) e.colors["pad"] ?: PAD else e.colors["button"] ?: BUTTON

    /** The colour [e]'s relief is drawn in: the one set, else its face's, darker. */
    fun relief(e: SkinElement): Int = e.colors["relief"] ?: relief(face(e))

    /** The colour [e]'s label is drawn in: the one set, else white on a button, the ball's on a D-pad (its arrows). */
    fun letter(e: SkinElement): Int =
        e.colors["letter"] ?: if (e.type == SkinElementType.DPAD) e.colors["ball"] ?: BALL else LETTER

    /** Laid over a pressed button whose face is light (issue 891): 30 % black. */
    const val PRESS_DARK = 0x4D000000

    /** Laid over a pressed button whose face is dark: 35 % white. */
    const val PRESS_LIGHT = 0x59FFFFFF

    /**
     * The tint a pressed button is drawn with (issue 891), from its drawing's [pixels] (ARGB): darker over a light face,
     * lighter over a dark one, so it reads on both. The face is what [SkinColorMath.meanLuminance] makes of them.
     */
    fun pressTint(pixels: IntArray): Int = if (SkinColorMath.meanLuminance(pixels) > 0.5) PRESS_DARK else PRESS_LIGHT
}

/** Colour maths: `#RRGGBB`, HSV, and WCAG 2 contrast. Colours are ARGB ints. */
object SkinColorMath {
    /** The least contrast a letter or an icon needs on its fill: WCAG 2's ratio for large text and graphics. */
    const val MIN_CONTRAST = 3.0

    private val HEX = Regex("#?([0-9A-Fa-f]{6})")

    /** `#RRGGBB` (the `#` optional, either case, spaces around ignored) as an opaque colour; null when it is not one. */
    fun parseHex(text: String): Int? {
        val m = HEX.matchEntire(text.trim()) ?: return null
        return (0xFF000000L or m.groupValues[1].toLong(16)).toInt()
    }

    /** [argb] as `#RRGGBB` (its alpha left out). */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** [argb] as hue (0 until 360), saturation and value (0 to 1). */
    fun toHsv(argb: Int): FloatArray {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hi = max(r, max(g, b))
        val d = hi - min(r, min(g, b))
        val h = when {
            d == 0f -> 0f
            hi == r -> 60f * (((g - b) / d).mod(6f))
            hi == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf(h, if (hi == 0f) 0f else d / hi, hi)
    }

    /** The opaque colour of hue [h] (degrees, any value: it wraps), saturation [s] and value [v] (clamped to 0..1). */
    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hue = h.mod(360f)
        val sat = s.coerceIn(0f, 1f)
        val value = v.coerceIn(0f, 1f)
        val c = value * sat
        val x = c * (1f - abs((hue / 60f).mod(2f) - 1f))
        val (r, g, b) = when (floor(hue / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = value - c
        fun byte(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }

    /** [a] moved [t] (0 to 1) of the way to [b], channel by channel; the alpha is [a]'s. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = a shr shift and 0xFF
            return (from + ((b shr shift and 0xFF) - from) * k).roundToInt().coerceIn(0, 255)
        }
        return (a and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** [argb] moved [t] of the way to white, or to black: how the consoles' art shades a control from its one colour. */
    fun lighten(argb: Int, t: Float): Int = mix(argb, 0xFFFFFF, t)

    fun darken(argb: Int, t: Float): Int = mix(argb, 0x000000, t)

    /** WCAG 2's relative luminance of [argb] (its alpha ignored), 0 for black to 1 for white. */
    /**
     * The relative luminance of [pixels] (ARGB) as seen: each pixel's [luminance] weighed by how opaque it is; 0 for
     * nothing opaque.
     */
    fun meanLuminance(pixels: IntArray): Double {
        var sum = 0.0
        var weight = 0.0
        for (p in pixels) {
            val a = (p ushr 24) / 255.0
            if (a == 0.0) continue
            sum += luminance(p) * a
            weight += a
        }
        return if (weight == 0.0) 0.0 else sum / weight
    }

    fun luminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    /** WCAG 2's contrast ratio of two colours: 1 (the same) to 21 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}

/** A named set of colours for the preset row; [id] also names its string (`editor_palette_<id>`). */
data class SkinPalette(val id: String, val colors: List<Int>)

/** The palettes the editor ships (from the design): add one here and a name for it in every locale. */
object SkinPalettes {
    private fun of(id: String, vararg hex: String) = SkinPalette(id, hex.map { SkinColorMath.parseHex(it)!! })

    val BASIC = of("basic", "#F472B6", "#EF4444", "#F97316", "#FACC15", "#22C55E", "#3B82F6", "#8B5CF6")

    val ALL = listOf(
        BASIC,
        of("dracula", "#282A36", "#44475A", "#BD93F9", "#FF79C6", "#8BE9FD", "#50FA7B", "#FFB86C"),
        of("pastel", "#FBCFE8", "#FECACA", "#FED7AA", "#FEF08A", "#BBF7D0", "#BFDBFE", "#DDD6FE"),
        of("nord", "#2E3440", "#4C566A", "#88C0D0", "#81A1C1", "#A3BE8C", "#EBCB8B", "#BF616A"),
        of("gruvbox", "#282828", "#504945", "#CC241D", "#D79921", "#98971A", "#458588", "#B16286"),
        of("neon", "#0D0221", "#FF2A6D", "#05D9E8", "#D1F7FF", "#F9C80E", "#7B2FF7", "#01FF89"),
        of("earth", "#3E2C23", "#6B4F3A", "#A47148", "#D4A373", "#E9EDC9", "#CCD5AE", "#606C38"),
        of("green-screen", "#0F380F", "#306230", "#8BAC0F", "#9BBC0F"),
    )

    /** The recent colours kept. */
    const val RECENT_MAX = 8

    /** The palette called [id]; Basic for one this app does not have (or none saved yet). */
    fun byId(id: String?): SkinPalette = ALL.firstOrNull { it.id == id } ?: BASIC

    /** [recent] after [color] was applied: it first, no repeats, at most [RECENT_MAX]. */
    fun recent(recent: List<Int>, color: Int): List<Int> = (listOf(color) + recent.filter { it != color }).take(RECENT_MAX)

    /** Recent colours as saved: `#RRGGBB` joined by commas. */
    fun recentText(recent: List<Int>): String = recent.joinToString(",") { SkinColorMath.hex(it) }

    /** [text] as saved by [recentText]; anything that is not a colour is skipped. */
    fun recentFrom(text: String?): List<Int> =
        text.orEmpty().split(',').mapNotNull { SkinColorMath.parseHex(it) }.distinct().take(RECENT_MAX)
}

/** How a picture fills its part. */
enum class SkinFill { STRETCH, FIT, REPEAT }

/**
 * A texture's placement in its part's box: [fill], [zoom] (1 to [SkinTextureFit.MAX_ZOOM]) about the box's centre, and
 * an offset ([dx], [dy]) in shares of the box (-1 to 1).
 */
data class SkinTextureFrame(val fill: SkinFill = SkinFill.STRETCH, val zoom: Float = 1f, val dx: Float = 0f, val dy: Float = 0f) {
    /** The same frame moved by [x], [y] (shares of the box) and zoomed by [factor], within the limits. */
    fun moved(x: Float, y: Float, factor: Float = 1f): SkinTextureFrame = copy(
        zoom = (zoom * factor).coerceIn(SkinTextureFit.MIN_ZOOM, SkinTextureFit.MAX_ZOOM),
        dx = (dx + x).coerceIn(-1f, 1f),
        dy = (dy + y).coerceIn(-1f, 1f),
    )
}

object SkinTextureFit {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    /** A repeated picture's tile, as a share of the box's shorter side. */
    const val TILE_SHARE = 0.5f

    /**
     * Where a [srcW] x [srcH] picture is drawn in a [w] x [h] box: Stretch covers the box, Fit is the whole picture
     * centred inside it, Repeat is one tile (the first of the grid that covers the box from it). Then zoomed about the
     * box's centre and moved by the offset.
     */
    fun place(srcW: Int, srcH: Int, w: Float, h: Float, frame: SkinTextureFrame): SkinRect {
        val sw = srcW.coerceAtLeast(1).toFloat()
        val sh = srcH.coerceAtLeast(1).toFloat()
        val (bw, bh) = when (frame.fill) {
            SkinFill.STRETCH -> w to h
            SkinFill.FIT -> min(w / sw, h / sh).let { sw * it to sh * it }
            SkinFill.REPEAT -> (min(w, h) * TILE_SHARE / max(sw, sh)).let { sw * it to sh * it }
        }
        val zoom = frame.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val iw = bw * zoom
        val ih = bh * zoom
        return SkinRect((w - iw) / 2f + frame.dx.coerceIn(-1f, 1f) * w, (h - ih) / 2f + frame.dy.coerceIn(-1f, 1f) * h, iw, ih)
    }

    /**
     * The tiles of a Repeat [tile] that cover a [w] x [h] box: the tile's grid, from the column and row left and above
     * the box's corner.
     */
    fun tiles(tile: SkinRect, w: Float, h: Float): List<SkinRect> {
        val x0 = tile.x - (floor(tile.x / tile.width) + 1f) * tile.width
        val y0 = tile.y - (floor(tile.y / tile.height) + 1f) * tile.height
        return buildList {
            var y = y0
            while (y < h) {
                var x = x0
                while (x < w) {
                    if (x + tile.width > 0f && y + tile.height > 0f) add(SkinRect(x, y, tile.width, tile.height))
                    x += tile.width
                }
                y += tile.height
            }
        }
    }

    /**
     * How much to subsample a [srcW] x [srcH] picture when decoding it for a [w] x [h] part: the largest power of two
     * that keeps it at least twice the part's size (zooming in stays sharp), so a photo never loads whole.
     */
    fun sample(srcW: Int, srcH: Int, w: Int, h: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= w * 2 && srcH / (s * 2) >= h * 2) s *= 2
        return s
    }
}

/**
 * A layout's colours as the editor edits them: the background's, the frame's and each control's parts; and, with
 * them, what each control's image holds ([art]), which a texture changes, and the outline the player gave a control
 * there ([shapes]): every combo button's (issue 842), and any other button drawn in an outline of the player's (issue
 * 891; one it does not name is drawn as the skin draws it).
 */
data class SkinLook(
    val background: Int? = null,
    val frame: SkinFrameColors? = null,
    val colors: Map<String, Map<String, Int>> = emptyMap(),
    val art: Map<String, SkinArt> = emptyMap(),
    val shapes: Map<String, ComboOutline> = emptyMap(),
)

/** [this] layout's colours. */
fun SkinLayout.look(): SkinLook = SkinLook(
    background.color, frame?.colors, elements.associate { it.id to it.colors }, elements.associate { it.id to it.art },
    elements.mapNotNull { e -> ComboOutline.chosen(e)?.let { e.id to it } }.toMap(),
)

/**
 * [this] layout painted in [look]: a control [look] does not name keeps its colours (and a combo its outline); a button
 * [look] gives no outline of the player's is drawn in the skin's own (issue 891).
 */
fun SkinLayout.withLook(look: SkinLook): SkinLayout = copy(
    background = background.copy(color = look.background),
    frame = frame?.let { f -> f.copy(colors = look.frame ?: f.colors) },
    elements = elements.map { e ->
        val painted = e.copy(colors = look.colors[e.id] ?: e.colors, art = look.art[e.id] ?: e.art)
        if (ComboOutline.shapeable(painted)) ComboOutline.drawn(painted, look.shapes[e.id]) else painted
    },
)

/** What the editor did to one of a layout's image files: the image is gone (colours drawn instead), or replaced. */
sealed class SkinImageEdit {
    data object Removed : SkinImageEdit()

    /** Replaced by the texture staged as [name] (data/SkinStore.kt `stageTexture`). */
    data class Staged(val name: String) : SkinImageEdit()
}

/** The editor's image edits, by path in the skin (`phone-portrait/a.png`). */
typealias SkinImageEdits = Map<String, SkinImageEdit>

object SkinPainter {
    /** [look] with [part] of [target] ([ids]: the controls, for [SkinTarget.Controls]) set to [color]. */
    fun paint(look: SkinLook, layout: SkinLayout, target: SkinTarget, ids: List<String>, part: SkinPart, color: Int): SkinLook = when (part) {
        SkinPart.BACKGROUND -> look.copy(background = color)
        SkinPart.FRAME, SkinPart.ICONS, SkinPart.ACTIVE -> {
            if (layout.frame == null) look else {
                val f = look.frame ?: SkinFrameColors()
                look.copy(frame = when (part) {
                    SkinPart.FRAME -> f.copy(frame = color)
                    SkinPart.ICONS -> f.copy(icons = color)
                    else -> f.copy(active = color)
                })
            }
        }
        else -> {
            if (target !is SkinTarget.Controls) look else {
                val colors = look.colors.toMutableMap()
                layout.elements.filter { it.id in ids && part.id in it.type.colorParts }.forEach { e ->
                    colors[e.id] = (colors[e.id] ?: e.colors) + (part.id to color)
                }
                look.copy(colors = colors)
            }
        }
    }

    /** [look] with the frame's icon on a toggled quick action [tone]; nothing for a layout with no frame. */
    fun tone(look: SkinLook, layout: SkinLayout, tone: SkinIconTone): SkinLook =
        if (layout.frame == null) look else look.copy(frame = (look.frame ?: SkinFrameColors()).copy(activeIcon = tone))

    /**
     * The image files (paths in the skin, under [layoutId]'s folder) that draw [part] of [ids] in [layout], which a
     * colour there replaces: an element part's own file, the background's, the frame's; for Icons, every quick
     * action's (an image hides the glyph's colour). A control's relief and label are in its first file when its image
     * holds them ([SkinElement.art]): the whole control for the label, all but a face alone for the relief. Active
     * has none.
     */
    fun files(layoutId: SkinLayoutId, layout: SkinLayout, ids: List<String>, part: SkinPart): List<String> {
        fun controls() = layout.elements.filter { it.id in ids && part.id in it.type.colorParts }
        val names = when (part) {
            SkinPart.BACKGROUND -> listOf(layout.background.imageFile)
            SkinPart.FRAME -> listOfNotNull(layout.frame?.imageFile)
            SkinPart.ICONS -> layout.elements.filter { it.type == SkinElementType.ACTION }.map { it.imageFiles[0] }
            SkinPart.ACTIVE -> emptyList()
            SkinPart.RELIEF -> controls().filter { it.art != SkinArt.FACE }.map { it.imageFiles[0] }
            SkinPart.LETTER -> controls().filter { it.art == SkinArt.FULL }.map { it.imageFiles[0] }
            else -> controls().mapNotNull { e -> part.imageIndex?.let { e.imageFiles.getOrNull(it) } }
        }
        return names.map { "${layoutId.id}/$it" }
    }

    /** The controls among [ids] that have [part] to paint, in layout order. */
    fun painted(layout: SkinLayout, ids: List<String>, part: SkinPart): List<String> =
        layout.elements.filter { it.id in ids && part.id in it.type.colorParts }.map { it.id }

    /**
     * The parts worth offering for [target] on [ids]: a control's Relief only when one of them has a relief to show,
     * its Label only when one has a label (a D-pad always has its arrows).
     */
    fun parts(layout: SkinLayout, target: SkinTarget, ids: List<String>): List<SkinPart> {
        if (target !is SkinTarget.Controls) return target.parts
        val controls = layout.elements.filter { it.id in ids }
        return target.parts.filter { part ->
            when (part) {
                SkinPart.RELIEF -> controls.any { it.type.raised && it.depth > 0f }
                SkinPart.LETTER -> controls.any { it.type == SkinElementType.DPAD || SkinLabels.has(it) }
                else -> true
            }
        }
    }

    /**
     * The colour [part] of [ids]'s first control (or of the background, the frame) is drawn in: the one set, else the
     * renderer's default (a relief's follows its face, a D-pad's arrows its ball).
     */
    fun colorOf(layout: SkinLayout, ids: List<String>, part: SkinPart): Int {
        val f = layout.frame?.colors
        val control = layout.elements.firstOrNull { it.id in ids && part.id in it.type.colorParts }
        val set = when (part) {
            SkinPart.BACKGROUND -> layout.background.color
            SkinPart.FRAME -> f?.frame
            SkinPart.ICONS -> f?.icons
            SkinPart.ACTIVE -> f?.active
            SkinPart.RELIEF -> control?.let(SkinPaintDefaults::relief)
            SkinPart.LETTER -> control?.let(SkinPaintDefaults::letter)
            else -> control?.colors?.get(part.id)
        }
        return set ?: SkinPaintDefaults.of(part)
    }
}

/** A colour pair the game would draw too close to each other. */
enum class SkinContrast { LETTER, ICONS, ACTIVE_ICON }

object SkinContrastCheck {
    /**
     * The low-contrast pairs among what [target] paints in [layout] (already painted): a labelled control's letter on
     * its button (for [ids]), the quick actions' icons on the frame, the icon on a toggled action's background.
     */
    fun issues(layout: SkinLayout, target: SkinTarget, ids: List<String>): Set<SkinContrast> = buildSet {
        fun low(a: Int, b: Int) = SkinColorMath.contrast(a, b) < SkinColorMath.MIN_CONTRAST
        when (target) {
            is SkinTarget.Controls -> {
                // A label printed beside its control is read on the console's body, not on the button.
                val body = layout.background.color ?: SkinPaintDefaults.BACKGROUND
                val labelled = layout.elements.filter { it.id in ids && SkinLabels.has(it) }
                if (labelled.any { low(if (SkinLabels.beside(it)) body else SkinPaintDefaults.face(it), SkinPaintDefaults.letter(it)) }) {
                    add(SkinContrast.LETTER)
                }
            }
            SkinTarget.Frame -> {
                val f = layout.frame?.colors ?: return@buildSet
                if (low(f.frame ?: SkinPaintDefaults.FRAME, f.icons ?: SkinPaintDefaults.ICONS)) add(SkinContrast.ICONS)
                if (low(f.active ?: SkinPaintDefaults.ACTIVE, SkinPaintDefaults.icon(f.activeIcon))) add(SkinContrast.ACTIVE_ICON)
            }
            SkinTarget.Background -> Unit
        }
    }
}

/**
 * A skin as the editor shows it (issue 786): [base] with [images] applied, a removed image gone and a staged texture
 * read from [staged] (null: the texture is gone, and the part is drawn in its colours). An edit names an image the
 * way the model does (`<name>.png`) and stands for the file the skin keeps it in, PNG or WebP ([SkinFormat.modelName]):
 * a staged texture is a PNG, and takes a WebP's place too.
 */
class EditedSkinSource(private val base: SkinSource, private val images: SkinImageEdits, private val staged: (String) -> java.io.File?) : SkinSource {
    override val files: Set<String> = base.files.filter { images[SkinFormat.modelName(it)] == null }.toSet() +
        images.filter { (_, e) -> e is SkinImageEdit.Staged }.keys

    override fun open(path: String): java.io.InputStream? = when (val e = images[SkinFormat.modelName(path)]) {
        SkinImageEdit.Removed -> null
        is SkinImageEdit.Staged -> staged(e.name)?.takeIf { path.endsWith(SkinFormat.PNG) && it.isFile }?.inputStream()
        null -> base.open(path)
    }
}
