package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.Skin
import com.openemux.mobile.data.SkinBackground
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFamily
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinFrame
import com.openemux.mobile.data.SkinFrameColors
import com.openemux.mobile.data.SkinKey
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinRow
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.data.SkinSide
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinTemplate
import com.openemux.mobile.data.skin
import kotlin.math.roundToInt

/**
 * The drawn skin (data/Skin.kt's [Console.skin], drawn in game by [SkinRenderer]) as a skin in OpenEmux's own format
 * (issue 798): what "Create copy and edit" makes on a console with no bundled package. Pure data, no Android graphics,
 * so it runs as JVM unit tests; [DrawnSkinArt] paints the few [Art] images it lists.
 *
 * Each layout is the drawn skin laid out for one reference window of its form ([WINDOWS]), with the formulas
 * [SkinRenderer] uses there (the phone's are its own, the tablet's [SkinGeometry]'s), in design units scaled to the
 * layout's pixels. What the format cannot express is left out or given a place of its own: the keypad, which the drawn
 * skin opens over the face buttons, gets its own keys ([keypadUpright], [keypadSideways]); the key that opens it and the
 * hide key are left out (the format has no pages); the gamepad and tabletop pages stay the app's.
 */
object DrawnSkinCopy {
    /** A reference window: its portrait size in pixels and its density (dp per pixel's inverse), for the unit. */
    data class Window(val width: Int, val height: Int, val density: Float, val tablet: Boolean)

    /**
     * Phone: 1080 x 2340 (19.5:9). Tablet: an 800 x 1280 dp tablet at density 2, where the tablet unit's cap applies.
     * Fold: an unfolded Galaxy Z Fold's inner screen, 1812 x 2178 at 2.625, which the drawn skin lays out as a tablet
     * (its smallest width is over 600 dp).
     */
    val WINDOWS = mapOf(
        SkinForm.PHONE to Window(1080, 2340, 2.625f, tablet = false),
        SkinForm.TABLET to Window(1600, 2560, 2f, tablet = true),
        SkinForm.FOLD to Window(1812, 2178, 2.625f, tablet = true),
    )

    /** An image the copy has that colours and shapes cannot draw: [file] in layout [layout], [width] x [height]. */
    sealed interface Art {
        val layout: SkinLayoutId
        val file: String
        val width: Int
        val height: Int

        /** The body in [color], the console's [name] written in [ink] in [nameBox] at [font] px with [spacing]. */
        data class Background(
            override val layout: SkinLayoutId, override val width: Int, override val height: Int,
            val color: Int, val name: String, val nameBox: SkinRect, val font: Float, val spacing: Float, val ink: Int,
        ) : Art {
            override val file = "background.png"
        }

        /** The D-pad's cross in [pad] with its four arrow marks in [mark] (data/Skin.kt's 120-unit grid). */
        data class Dpad(override val layout: SkinLayoutId, override val file: String, override val width: Int, override val height: Int, val pad: Int, val mark: Int) : Art

        /**
         * The D-pad's ball: the drawn skin's ring in [mark], at its size against the ball the format draws ([RING]
         * of the image across).
         */
        data class Ball(override val layout: SkinLayoutId, override val file: String, override val width: Int, override val height: Int, val mark: Int) : Art

        /** The menu key: a round key in [fill] with the three-line glyph in [ink]. */
        data class Menu(override val layout: SkinLayoutId, override val file: String, override val width: Int, override val height: Int, val fill: Int, val ink: Int) : Art
    }

    /** What [convert] gives: the skin and the images to paint into its layout folders. */
    data class Copy(val skin: OpenEmuxSkin, val art: List<Art>)

    /** How wide OpenEmuxSkinRenderer draws a D-pad's ball against its travel. */
    private const val BALL_DRAWN = 0.55f

    /**
     * The ring's share of the ball image: the drawn ring is 14 units of the D-pad's 120 in radius, the format draws the
     * ball [BALL_DRAWN] of the travel (44/60 of the D-pad) across.
     */
    const val RING = 2f * 14f / (120f * OverlayLines.DPAD_TRAVEL * BALL_DRAWN)

    /** The least a keypad key is across, in units: a grid that would go under it takes the other place. */
    const val MIN_KEY = 40f

    /** The height the upright rows want for the clusters at full size (SkinGeometry.portrait's), in units. */
    private const val CONTROLS = 240f

    /** The room kept between the keypad and what is around it, and between its keys, in units. */
    private const val GAP = 12f
    private const val KEY_GAP = 8f

    /** The strip's quick actions as the format names them, with their element ids. */
    private val ACTIONS = mapOf(
        "save_state" to ("save" to "quickSave"), "load_state" to ("load" to "quickLoad"), "turbo_fire_toggle" to ("turbo" to "turbo"),
        "toggle_slowmotion" to ("slow-motion" to "slowMotion"), "toggle_fast_forward" to ("fast-forward" to "toggleFastForward"),
    )

    /** [console]'s drawn skin as a skin of the player's own named [name]: the six layouts and their images. */
    fun convert(console: Console, name: String): Copy {
        val pages = SkinLayoutId.entries.associateWith { Page(console, console.skin, it) }
        return Copy(OpenEmuxSkin(console, name, pages.mapValues { it.value.layout() }), pages.values.flatMap { it.art() })
    }

    /**
     * The format's name for the RetroPad [bind] a drawn control presses on [console], as a button: the bind itself when
     * it is a name the format maps to it, else the console's own name for it; null when the format has none.
     */
    fun buttonName(console: Console, bind: String): String? {
        val names = SkinFormat.bindsFor(SkinElementType.BUTTON)
        if (bind in names && OpenEmuxBinds.button(console, bind) == bind) return bind
        return names.sorted().firstOrNull { OpenEmuxBinds.action(console, it, false) == null && OpenEmuxBinds.button(console, it) == bind }
    }

    /** A format name as an element id: lowercase, a hyphen before each capital (cUp → c-up). */
    fun idOf(name: String) = name.replace(Regex("[A-Z]")) { "-" + it.value.lowercase() }

    /** One layout of the drawn skin, placed in design units ([Part]s) then scaled to its pixels. */
    private class Page(val console: Console, val skin: Skin, val id: SkinLayoutId) {
        val window = WINDOWS.getValue(id.form)
        val portrait = id.orientation == SkinOrientation.PORTRAIT
        val size = if (portrait) SkinSize(window.width, window.height) else SkinSize(window.height, window.width)
        val unit = SkinGeometry.unit(window.width, window.density, window.tablet)
        val w = size.width / unit
        val h = size.height / unit
        val aspect = console.gameAspect
        val keypad = skin.keypad.isNotEmpty()

        /** The drawn page's pieces, in units. */
        lateinit var picture: Box
        lateinit var strip: Box
        lateinit var menu: Box
        var name: Box = Box(0f, 0f, 0f, 0f)
        var nameFont = 11f
        var nameSpacing = 0.18f
        val elements = mutableListOf<Pair<SkinElement, Box>>()
        val clusterBoxes = mutableListOf<Pair<SkinSide, Box>>()

        init {
            when {
                !window.tablet && portrait -> phonePortrait()
                !window.tablet -> phoneLandscape()
                portrait -> tabletPortrait()
                else -> tabletLandscape()
            }
        }

        fun layout(): SkinLayout {
            val all = listOf(
                SkinElement("menu", SkinElementType.BUTTON, "menu", rect(menu), group = "menu", shape = SkinShape.PILL),
            ) + actions() + elements.map { (e, b) -> e.copy(rect = rect(b)) }
            // The NDS's two screens one over the other, as the core shows them in the picture's box.
            val screens = if (console == Console.NDS) listOf(picture.copy(h = picture.h / 2f), picture.copy(y = picture.y + picture.h / 2f, h = picture.h / 2f))
            else listOf(picture)
            return SkinLayout(
                size, screens.map(::rect), SkinBackground(skin.colors.body),
                SkinFrame(rect(strip), SkinFrameColors(frame = skin.colors.strip, icons = skin.colors.icon)),
                unique(all),
            )
        }

        fun art(): List<Art> {
            val out = mutableListOf<Art>(
                Art.Background(id, size.width, size.height, skin.colors.body, skin.name, rect(name), nameFont * unit, nameSpacing, skin.colors.ink),
            )
            val placed = layout().elements
            placed.firstOrNull { it.id == "menu" }?.let { out += Art.Menu(id, "menu.png", px(it.rect.width), px(it.rect.height), skin.colors.menu, skin.colors.menuInk) }
            placed.filter { it.type == SkinElementType.DPAD }.forEach { e ->
                out += Art.Dpad(id, e.imageFiles[0], px(e.rect.width), px(e.rect.height), skin.colors.dpad, skin.colors.mark)
                val ball = (minOf(e.rect.width, e.rect.height) * SkinTemplate.BALL_SHARE).roundToInt().coerceAtLeast(1)
                out += Art.Ball(id, e.imageFiles[1], ball, ball, skin.colors.mark)
            }
            return out
        }

        private fun px(v: Float) = v.roundToInt().coerceAtLeast(1)

        /** [b] in the layout's pixels: whole pixels, at least one across, inside the layout. */
        fun rect(b: Box): SkinRect {
            val x0 = (b.x * unit).roundToInt().coerceIn(0, size.width - 1)
            val y0 = (b.y * unit).roundToInt().coerceIn(0, size.height - 1)
            val x1 = (b.right * unit).roundToInt().coerceIn(x0 + 1, size.width)
            val y1 = (b.bottom * unit).roundToInt().coerceIn(y0 + 1, size.height)
            return SkinRect(x0.toFloat(), y0.toFloat(), (x1 - x0).toFloat(), (y1 - y0).toFloat())
        }

        /** The strip's five slots, each the drawn strip's touch area. */
        private fun actions(): List<SkinElement> {
            val list = stripActions(layoutKey = false)
            val slot = strip.w / list.size
            return list.mapIndexed { i, action ->
                val (eid, bind) = ACTIONS.getValue(action)
                SkinElement(eid, SkinElementType.ACTION, bind, rect(Box(strip.x + slot * i, strip.y, slot, strip.h)), group = "actions")
            }
        }

        /** The ids made unique: a second control with one id gets "-2", and so on. */
        private fun unique(list: List<SkinElement>): List<SkinElement> {
            val seen = mutableMapOf<String, Int>()
            return list.map { e ->
                val n = (seen[e.id] ?: 0) + 1
                seen[e.id] = n
                if (n == 1) e else e.copy(id = "${e.id}-$n")
            }
        }

        // SkinRenderer.Layout.portrait, uncompacted: the layouts are tall enough for its 420-unit panel.
        private fun phonePortrait() {
            val gameH = minOf(390f / aspect, if (skin.family == SkinFamily.DUAL) 466f else 380f)
            val gameW = gameH * aspect
            picture = Box((390f - gameW) / 2f, 0f, gameW, gameH)
            strip = Box(0f, gameH, 390f, 44f)
            val top = gameH + 44f
            val pd = maxOf(h - top, 420f)
            fun at(x: Float, y: Float, bw: Float, bh: Float) = Box(x, top + y, bw, bh)
            shoulders(skin.shouldersLeft, 16f, 141f, top + 16f, 40f)
            shoulders(skin.shouldersRight, 249f, 374f, top + 16f, 40f)
            menu = at(145f, 14f, 48f, 44f)
            val m0 = top + 68f
            var m1 = top + pd - 108f
            val pillsTop = top + pd - 100f
            val upright = if (keypad) keypadUpright(96f, 290f, m0, m1, pillsTop, 1f) else null
            if (upright != null && upright.band) m1 = upright.box.y - GAP
            val k = if (upright != null && upright.band) minOf(1f, (m1 - m0) / CONTROLS) else 1f
            clusters(96f, 290f, rows(m0, m1), k, k)
            upright?.let { keys(it.box) }
            pills(pills().mapIndexed { i, key -> key to Box(195f + (i - (pills().size - 1) / 2f) * 96f - 30f, pillsTop, 60f, 24f) })
            name = at(0f, pd - 38f, 390f, 11f)
        }

        // SkinRenderer.Layout.landscape.
        private fun phoneLandscape() {
            val gameW = phoneLandscapeGameW(w, aspect)
            val gameH = gameW / aspect
            val left = (w - gameW) / 2f
            val top = (390f - gameH - 36f) / 2f
            picture = Box(left, top, gameW, gameH)
            val stripW = phoneLandscapeStripW(gameW, stripActions(false).size)
            strip = Box(left + (gameW - stripW) / 2f, top + gameH, stripW, 36f)
            val side = left
            val lx = side / 2f
            val rx = w - side / 2f
            shoulders(skin.shouldersLeft, 12f, side - 12f, 12f, 36f)
            shoulders(skin.shouldersRight, w - side + 12f, w - 12f, 12f, 36f)
            menu = Box(lx - 48f, 54f, 44f, 36f)
            val rows = mapOf(SkinRow.CENTER to 196f, SkinRow.UPPER to 142f, SkinRow.LOWER to 262f)
            clusters(lx, rx, rows, 0.82f, 0.85f, if (keypad) faceUp(rows) else rows)
            if (keypad) keypadSideways(rx, side, 390f - GAP)
            sidePills(lx, rx, 330f, 52f, 20f, 80f)
            name = Box(w - side, 58f, side, 10f)
            nameFont = 10f
            nameSpacing = 0.16f
        }

        // SkinRenderer.Layout.tabletPortrait.
        private fun tabletPortrait() {
            val g = SkinGeometry.portrait(w, h, aspect)
            frame(g)
            var rows = mapOf(SkinRow.UPPER to g.rows.upper, SkinRow.CENTER to g.rows.center, SkinRow.LOWER to g.rows.lower)
            var k = g.k
            if (keypad) {
                val m0 = g.menu.bottom + GAP
                val upright = keypadUpright(g.lx, g.rx, m0, g.pillsTop - GAP, g.pillsTop, g.k)
                if (upright != null && upright.band) {
                    // Under the clusters: they keep the room above it, at their scale for that room.
                    val m1 = upright.box.y - GAP
                    rows = rows(m0, m1)
                    k = minOf(k, (m1 - m0) / CONTROLS)
                }
                clusters(g.lx, g.rx, rows, k, k)
                upright?.let { keys(it.box) }
            } else {
                clusters(g.lx, g.rx, rows, k, k)
            }
            pills(pills().mapIndexed { i, key -> key to Box(g.picture.cx + (i - (pills().size - 1) / 2f) * 96f - 30f, g.pillsTop, 60f, 24f) })
            name = g.name
        }

        // SkinRenderer.Layout.tabletLandscape (no NDS layout key: the strip has no screens key in a copy).
        private fun tabletLandscape() {
            val g = SkinGeometry.landscape(w, h, aspect, footprint(), depth(), layoutKey = false)
            frame(g)
            val rows = mapOf(SkinRow.UPPER to g.rows.upper, SkinRow.CENTER to g.rows.center, SkinRow.LOWER to g.rows.lower)
            clusters(g.lx, g.rx, rows, g.k, g.k, if (keypad) faceUp(rows) else rows)
            if (keypad) keypadSideways(g.rx, g.lx * 2f, h - 16f)
            sidePills(g.lx, g.rx, g.pillsTop, 60f, 24f, 84f)
            name = g.name
            nameFont = 10f
            nameSpacing = 0.16f
        }

        /** The picture, the strip, the shoulders and the menu key where a tablet frame [g] puts them. */
        private fun frame(g: TabletFrame) {
            picture = g.picture
            strip = g.strip
            menu = g.menu
            shoulders(skin.shouldersLeft, g.shouldersLeft.x, g.shouldersLeft.right, g.shouldersLeft.y, g.shouldersLeft.h)
            shoulders(skin.shouldersRight, g.shouldersRight.x, g.shouldersRight.right, g.shouldersRight.y, g.shouldersRight.h)
        }

        /** The phone's and the tablet's upright rows between [m0] and [m1]. */
        private fun rows(m0: Float, m1: Float) =
            mapOf(SkinRow.UPPER to m0 + (m1 - m0) * 0.27f, SkinRow.CENTER to (m0 + m1) / 2f, SkinRow.LOWER to m0 + (m1 - m0) * 0.75f)

        /** Sideways with a keypad: every row of the right side up to the upper one, the keypad under them. */
        private fun faceUp(rows: Map<SkinRow, Float>) = rows + (SkinRow.CENTER to rows.getValue(SkinRow.UPPER))

        /** The pills the copy draws: the drawn skin's, less the key that opens the keypad. */
        private fun pills(): List<SkinKey> = skin.pills.filter { it.bind != null && it.bind != KEYPAD_BIND }

        /**
         * Sideways: one pill under the right side's clusters, else half on each side ([SkinRenderer]'s); with a
         * keypad, which takes the right side's foot, all on the left.
         */
        private fun sidePills(lx: Float, rx: Float, top: Float, pw: Float, ph: Float, step: Float) {
            val list = pills()
            val sides = when {
                keypad -> listOf(list to lx)
                list.size == 1 -> listOf(list to rx)
                else -> ((list.size + 1) / 2).let { half -> listOf(list.take(half) to lx, list.drop(half) to rx) }
            }
            pills(sides.flatMap { (keys, cx) -> keys.mapIndexed { i, key -> key to Box(cx + (i - (keys.size - 1) / 2f) * step - pw / 2f, top, pw, ph) } })
        }

        private fun pills(placed: List<Pair<SkinKey, Box>>) {
            for ((key, box) in placed) button(key.label, key.bind!!, box, "center", SkinShape.PILL, skin.colors.pill, skin.colors.menuInk)
        }

        private fun shoulders(keys: List<SkinKey>, x0: Float, x1: Float, top: Float, bh: Float) {
            val shown = keys.filter { it.bind != null }
            if (shown.isEmpty()) return
            val bw = (x1 - x0 - 8f * (shown.size - 1)) / shown.size
            val group = if (x0 < w / 2f) "shoulders-left" else "shoulders-right"
            shown.forEachIndexed { i, key ->
                button(key.label, key.bind!!, Box(x0 + i * (bw + 8f), top, bw, bh), group, SkinShape.RECT, skin.colors.shoulder, skin.colors.shoulderInk)
            }
        }

        /** A button pressing the drawn [bind] by the format's name for it; left out when the format has none. */
        private fun button(label: String, bind: String, box: Box, group: String, shape: SkinShape, fill: Int, ink: Int) {
            val name = buttonName(console, bind) ?: return
            elements += SkinElement(
                idOf(name), SkinElementType.BUTTON, name, SkinRect(0f, 0f, 1f, 1f), group, shape, label.ifEmpty { null },
                mapOf("button" to fill, "letter" to ink),
            ) to box
        }

        /**
         * SkinRenderer.Layout.clusters: each cluster at its side's centre and its row ([right]'s on the right side), [k]
         * its scale.
         */
        private fun clusters(lx: Float, rx: Float, rows: Map<SkinRow, Float>, k: Float, dpadK: Float, right: Map<SkinRow, Float> = rows) {
            var dpads = 0
            var sticks = 0
            for (cl in skin.clusters) {
                val cx = if (cl.side == SkinSide.LEFT) lx else rx
                val cy = (if (cl.side == SkinSide.LEFT) rows else right).getValue(cl.row)
                when (cl) {
                    is SkinCluster.DPad -> {
                        val s = cl.size * dpadK
                        val box = Box(cx - s / 2f, cy - s / 2f, s, s)
                        val bind = if (dpads++ == 0) "dpad" else SkinBinds.DPAD2
                        val c = skin.colors
                        elements += SkinElement(bind, SkinElementType.DPAD, bind, SkinRect(0f, 0f, 1f, 1f), "dpad", colors = mapOf("pad" to c.dpad, "ball" to c.mark)) to box
                        clusterBoxes += cl.side to box
                    }
                    is SkinCluster.Stick -> {
                        val s = cl.size * k
                        val box = Box(cx - s / 2f, cy - s / 2f, s, s)
                        val right = cl.bind == "analog_right"
                        val eid = if (sticks++ == 0) "stick" else "stick2"
                        val colors = mapOf("base" to (cl.color ?: skin.colors.stick), "knob" to (cl.knob ?: skin.colors.knob))
                        elements += SkinElement(eid, SkinElementType.STICK, if (right) "rightAnalogStick" else "analogStick", SkinRect(0f, 0f, 1f, 1f), "sticks", colors = colors) to box
                        clusterBoxes += cl.side to box
                    }
                    is SkinCluster.Face -> cl.slots.zip(cl.buttons).forEach { (slot, b) ->
                        val bw = slot.w * k
                        val bh = slot.h * k
                        val box = Box(cx + slot.dx * k - bw / 2f, cy + slot.dy * k - bh / 2f, bw, bh)
                        clusterBoxes += cl.side to box
                        b.bind?.let { button(b.label, it, box, "face", if (slot.w == slot.h) SkinShape.CIRCLE else SkinShape.PILL, b.bg, b.fg) }
                    }
                }
            }
        }

        /** Where the upright keypad goes: [box], a three by four grid between the clusters, or a [band] of two rows of six. */
        class Upright(val box: Box, val band: Boolean)

        /**
         * Upright, the keypad in the column between the clusters (centred at [lx] and [rx] at scale [k], their rows
         * from [m0] to [m1]) when it holds keys [MIN_KEY] across; else two rows of six across the width over
         * [pillsTop], the clusters taking the room above it.
         */
        private fun keypadUpright(lx: Float, rx: Float, m0: Float, m1: Float, pillsTop: Float, k: Float): Upright {
            // Where the clusters reach, from their sides' centres.
            val half = footprint() * k / 2f
            val column = Box(lx + half + GAP, m0, rx - half - GAP - (lx + half + GAP), m1 - m0)
            if (column.w >= 3f * MIN_KEY + 2f * KEY_GAP) return Upright(column, band = false)
            val keyH = minOf(48f, (column.h - GAP) / 4f)
            val bandH = 2f * keyH + KEY_GAP
            return Upright(Box(16f, pillsTop - GAP - bandH, w - 32f, bandH), band = true)
        }

        /** Sideways, the keypad under the face buttons on the right side ([side] wide, centred at [rx]), down to [bottom]. */
        private fun keypadSideways(rx: Float, side: Float, bottom: Float) {
            val top = clusterBoxes.filter { it.first == SkinSide.RIGHT }.maxOfOrNull { it.second.bottom }?.plus(GAP) ?: (bottom / 2f)
            keys(Box(rx - (side - 16f) / 2f, top, side - 16f, bottom - top))
        }

        /**
         * The keypad's keys in [area]: three by four (the drawn keypad's rows) when the area is taller than wide, else
         * two rows of six; keys at most 1.3 times as wide as tall, centred.
         */
        private fun keys(area: Box) {
            val rows = if (area.h >= area.w * 0.6f) skin.keypad else skin.keypad.flatten().chunked(6)
            val cols = rows.maxOf { it.size }
            val keyH = minOf((area.h - KEY_GAP * (rows.size - 1)) / rows.size, (area.w - KEY_GAP * (cols - 1)) / cols, 56f)
            val keyW = minOf((area.w - KEY_GAP * (cols - 1)) / cols, keyH * 1.3f)
            val top = area.y + (area.h - (rows.size * keyH + KEY_GAP * (rows.size - 1))) / 2f
            val last = skin.keypad.last()
            rows.forEachIndexed { r, row ->
                val left = area.cx - (row.size * keyW + KEY_GAP * (row.size - 1)) / 2f
                row.forEachIndexed { i, key ->
                    val bind = when {
                        key.label.toIntOrNull() != null -> "keypad${key.label}"
                        last.size == 3 && key === last[0] -> "keypadStar"
                        else -> "keypadHash"
                    }
                    val eid = "key-" + bind.removePrefix("keypad").lowercase()
                    elements += SkinElement(
                        eid, SkinElementType.KEYPAD, bind, SkinRect(0f, 0f, 1f, 1f), "keypad", SkinShape.RECT, key.label,
                        mapOf("button" to skin.colors.pill, "letter" to skin.colors.menuInk),
                    ) to Box(left + i * (keyW + KEY_GAP), top + r * (keyH + KEY_GAP), keyW, keyH)
                }
            }
        }

        /** SkinRenderer.Layout.footprint, the keypad closed: the widest cluster at full size, in units. */
        private fun footprint(): Float = skin.clusters.maxOfOrNull { cl ->
            when (cl) {
                is SkinCluster.DPad -> cl.size
                is SkinCluster.Stick -> cl.size
                is SkinCluster.Face -> cl.slots.maxOf { it.dx + it.w / 2f } - cl.slots.minOf { it.dx - it.w / 2f }
            }
        } ?: SkinGeometry.SIDE

        /** SkinRenderer.Layout.depth, the keypad closed. */
        private fun depth(): Float = skin.clusters.maxOfOrNull { cl ->
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
    }
}
