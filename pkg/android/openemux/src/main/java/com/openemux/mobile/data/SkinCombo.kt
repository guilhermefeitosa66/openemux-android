package com.openemux.mobile.data

import android.view.KeyEvent
import com.openemux.mobile.launch.SkinBinds
import kotlin.math.hypot

/**
 * A combo button the player made in the editor (issue 842): one on-screen button that presses [buttons] (the skin's
 * input names, 2 to [SkinCombos.MAX], in the order its label reads) together. The skin keeps it in its root `combos`
 * and each layout says where it is (docs/skin-format.md).
 */
data class SkinCombo(val id: String, val buttons: List<String>)

/** Whether [this] is a combo button: what the model holds a combo as, a button whose bind joins its inputs with `|`. */
val SkinElement.isCombo: Boolean get() = SkinCombos.SEPARATOR in bind

/**
 * The rules of combo buttons. In memory a combo is a [SkinElementType.BUTTON] element of the layout whose [SkinElement.bind]
 * is its inputs joined with [SEPARATOR] (`"y|b"`, what the overlay engine presses together); [SkinFormat] writes it
 * under the `combos` keys instead of `elements`, so an older app reads the skin without it rather than refusing it.
 */
object SkinCombos {
    const val SEPARATOR = '|'
    const val MIN = 2
    const val MAX = 3

    /** How long the buttons are held, unchanged, for the capture to take them (ms). */
    const val HOLD_MS = 1000L

    /** A combo's look until the player paints it: neutral, so it never reads as one of the console's own buttons. */
    const val BUTTON = 0xFF5A606E.toInt()
    const val RELIEF = 0xFF3B3F48.toInt()
    const val LETTER = 0xFFF2F3F5.toInt()

    /** The face buttons, whose size a new combo takes. */
    private val FACE = setOf("a", "b", "x", "y", "c", "z")

    /**
     * The input names a combo may press: face buttons, shoulders, triggers, C buttons, Select and Start. Not the D-pad,
     * the sticks or their clicks, the quick actions, the keypad or the FDS disk key.
     */
    val ELIGIBLE: Set<String> = setOf(
        "a", "b", "x", "y", "c", "z", "l", "r", "l1", "r1", "l2", "r2", "start", "select", "mode",
        "cUp", "cDown", "cLeft", "cRight", "option1", "option2",
    )

    fun bind(buttons: List<String>): String = buttons.joinToString(SEPARATOR.toString())

    fun buttons(bind: String): List<String> = bind.split(SEPARATOR)

    /** Whether [buttons] make a combo the app can press: 2 to [MAX] different eligible inputs. */
    fun valid(buttons: List<String>): Boolean =
        buttons.size in MIN..MAX && buttons.toSet().size == buttons.size && buttons.all { it in ELIGIBLE }

    /** The combos a skin's layouts hold, each once, in the order they first appear. */
    fun of(layouts: Collection<SkinLayout>): List<SkinCombo> =
        layouts.flatMap { l -> l.elements.filter { it.isCombo } }.distinctBy { it.id }.map { SkinCombo(it.id, buttons(it.bind)) }

    /**
     * The label of a combo pressing [buttons]: each input's name joined with "+", the name being the label the skin
     * gives that input on any of [layouts] (a PlayStation skin's "□"), else the input name upper-cased.
     */
    fun label(buttons: List<String>, layouts: Collection<SkinLayout>): String = buttons.joinToString("+") { b ->
        layouts.asSequence().flatMap { it.elements.asSequence() }
            .firstOrNull { !it.isCombo && it.bind == b && !it.label.isNullOrBlank() }?.label ?: b.replaceFirstChar { it.uppercase() }
    }

    /** [layout]'s controls a combo may press, one per input name, in layout order. */
    fun eligible(layout: SkinLayout): List<SkinElement> =
        layout.elements.filter { it.type == SkinElementType.BUTTON && it.bind in ELIGIBLE }.distinctBy { it.bind }

    /** Whether [layout] offers a combo: two or more different inputs to press. */
    fun offered(layout: SkinLayout): Boolean = eligible(layout).size >= MIN

    /** [buttons] in the order they read on [layout]: left to right, then top to bottom; one it lacks goes last. */
    fun ordered(buttons: Collection<String>, layout: SkinLayout): List<String> {
        val at = eligible(layout).associate { it.bind to it.rect }
        return buttons.distinct().sortedWith(compareBy<String>({ it !in at }, { at[it]?.cx ?: 0f }, { at[it]?.cy ?: 0f }))
    }

    /** The first `combo-<n>` no element of [layouts] uses as its id, nor one of [taken]. */
    fun nextId(layouts: Collection<SkinLayout>, taken: Set<String> = emptySet()): String {
        val used = layouts.flatMap { l -> l.elements.map { it.id } }.toSet() + taken
        return generateSequence(1) { it + 1 }.map { "combo-$it" }.first { it !in used }
    }

    /**
     * The control whose size a new combo on [layout] takes: its widest face button, else its widest eligible button,
     * else none.
     */
    fun template(layout: SkinLayout): SkinElement? {
        val eligible = eligible(layout)
        return (eligible.filter { it.bind in FACE }.takeIf { it.isNotEmpty() } ?: eligible).maxByOrNull { it.rect.width }
    }

    /** A new combo [combo] on [layout] at [rect], in the combo look, its relief as deep for its size as [template]'s. */
    fun element(combo: SkinCombo, label: String, rect: SkinRect, template: SkinElement?): SkinElement {
        val depth = template?.let { it.depth * rect.width / it.rect.width } ?: 0f
        return SkinElement(
            combo.id, SkinElementType.BUTTON, bind(combo.buttons), rect, shape = SkinShape.CIRCLE, label = label,
            colors = mapOf("button" to BUTTON, "relief" to RELIEF, "letter" to LETTER),
            depth = depth.coerceAtMost(rect.height * (1f - SkinElement.MIN_FACE)),
        )
    }

    /** The side of a new combo on [layout]: [template]'s width (a face button), else an eighth of the layout's short side. */
    fun diameter(layout: SkinLayout, template: SkinElement?): Float =
        template?.rect?.width ?: (minOf(layout.size.width, layout.size.height) / 8f)

    /** The share of a combo's side kept free around it from what it must not cover. */
    const val MARGIN = 0.1f

    /**
     * Where a combo [side] units square starts on [layout]: as near as it gets to the centre of the buttons it presses
     * ([buttons]; the layout's centre when it has none of them), inside the layout and clear, by [MARGIN] of its
     * side, of the game screens, the frame and every control. Searched on a grid of a quarter of its side; with no
     * clear spot, at the centre of those buttons, kept inside the layout.
     */
    fun start(layout: SkinLayout, buttons: List<String>, side: Float): SkinRect {
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val s = side.coerceAtMost(minOf(w, h))
        val pressed = layout.elements.filter { !it.isCombo && it.bind in buttons }
        val cx = if (pressed.isEmpty()) w / 2f else pressed.map { it.rect.cx }.average().toFloat()
        val cy = if (pressed.isEmpty()) h / 2f else pressed.map { it.rect.cy }.average().toFloat()
        val controls = layout.elements.map { it.rect }
        // A layout whose game screen fills it (controls over the picture) has no spot off the screen: then one off the
        // controls alone.
        return nearestClear(w, h, s, cx, cy, layout.screens + listOfNotNull(layout.frame?.rect) + controls)
            ?: nearestClear(w, h, s, cx, cy, controls)
            ?: SkinRect((cx - s / 2f).coerceIn(0f, w - s), (cy - s / 2f).coerceIn(0f, h - s), s, s)
    }

    /** The [s]-sided square nearest ([cx], [cy]) inside [w] x [h] clear of [blocked] by [MARGIN], or null when none is. */
    private fun nearestClear(w: Float, h: Float, s: Float, cx: Float, cy: Float, blocked: List<SkinRect>): SkinRect? {
        val m = s * MARGIN
        val step = (s / 4f).coerceAtLeast(1f)
        var best: SkinRect? = null
        var bestDistance = Float.MAX_VALUE
        var y = 0f
        while (y + s <= h) {
            var x = 0f
            while (x + s <= w) {
                val d = hypot(x + s / 2f - cx, y + s / 2f - cy)
                if (d < bestDistance && blocked.none { overlaps(it, x - m, y - m, s + 2 * m) }) {
                    best = SkinRect(x, y, s, s)
                    bestDistance = d
                }
                x += step
            }
            y += step
        }
        return best
    }

    private fun overlaps(r: SkinRect, x: Float, y: Float, side: Float) =
        r.x < x + side && x < r.x + r.width && r.y < y + side && y < r.y + r.height

    private val SkinRect.cx get() = x + width / 2f
    private val SkinRect.cy get() = y + height / 2f
}

/**
 * A button's outline on one layout: a circle, a pill, a rounded square, or a right triangle whose square corner points
 * at [corner] of its rect, to tuck into the gap between two round buttons. A combo's (issue 842; a circle until the
 * player picks another), or the one the player gave any other button (issue 891). It is the control's, not one
 * layout's: every layout of the skin draws the control in the same outline ([unify]).
 */
data class ComboOutline(val shape: SkinShape, val corner: SkinCorner? = null) {
    companion object {
        val CIRCLE = ComboOutline(SkinShape.CIRCLE)
        val PILL = ComboOutline(SkinShape.PILL)

        /** The rounded square (issue 891): the skin format's `rect`, its corners rounded as the renderer rounds them. */
        val SQUARE = ComboOutline(SkinShape.RECT)

        /** The outlines the Shape tool offers, in its order: circle, pill, rounded square, then the triangle at each corner. */
        val ALL: List<ComboOutline> = listOf(CIRCLE, PILL, SQUARE) + SkinCorner.entries.map { ComboOutline(SkinShape.TRIANGLE, it) }

        /** A pill is this many times as wide as the circle it was, and [PILL_TALL] times as tall. */
        const val PILL_WIDE = 1.6f
        const val PILL_TALL = 0.8f

        /**
         * The outline a layout's `shape` and `corner` keys name: a circle when either is missing or unknown (an outline
         * a newer app adds, or a triangle with no corner), so the skin is read both ways.
         */
        fun read(shape: String?, corner: String?): ComboOutline = when (SkinShape.byId(shape.orEmpty())) {
            SkinShape.PILL -> PILL
            SkinShape.RECT -> SQUARE
            SkinShape.TRIANGLE -> SkinCorner.byId(corner)?.let { ComboOutline(SkinShape.TRIANGLE, it) } ?: CIRCLE
            else -> CIRCLE
        }

        /** The outline a [shape] of a [type] draws: no shape is the type's default (a key's rounded square, else a circle). */
        fun of(shape: SkinShape?, corner: SkinCorner?, type: SkinElementType): ComboOutline =
            when (shape ?: if (type == SkinElementType.KEYPAD) SkinShape.RECT else SkinShape.CIRCLE) {
                SkinShape.PILL -> PILL
                SkinShape.RECT -> SQUARE
                SkinShape.TRIANGLE -> corner?.let { ComboOutline(SkinShape.TRIANGLE, it) } ?: CIRCLE
                SkinShape.CIRCLE -> CIRCLE
            }

        /** [e]'s outline. */
        fun of(e: SkinElement): ComboOutline = of(e.shape, e.corner, e.type)

        /**
         * Whether the Shape tool takes [e] (issue 891): a button or a keypad key, a combo too; never the D-pad, a stick or
         * a quick action on the frame.
         */
        fun shapeable(e: SkinElement): Boolean = e.type == SkinElementType.BUTTON || e.type == SkinElementType.KEYPAD

        /** The outline the player gave [e]: a combo's always, another button's only when it is not the skin's own. */
        fun chosen(e: SkinElement): ComboOutline? = if (e.isCombo || e.own != null) of(e) else null

        /**
         * [e] drawn as [outline] (issue 891), its rect left as it is: a triangle is never turned, as a combo's, so the
         * overlay's touch on its diagonal meets what is drawn. Null brings back the skin's own outline and turn. A combo
         * only takes the outline (its own is always one of these).
         */
        fun drawn(e: SkinElement, outline: ComboOutline?): SkinElement {
            if (e.isCombo) return if (outline == null) e else e.copy(shape = outline.shape, corner = outline.corner)
            if (outline == null) {
                val own = e.own ?: return e
                return e.copy(shape = own.shape, corner = null, rotation = own.rotation, own = null)
            }
            val own = e.own ?: SkinOwnOutline(e.shape, e.rotation, e.rect.width, e.rect.height)
            val rotation = if (outline.shape == SkinShape.TRIANGLE) 0f else own.rotation
            return e.copy(shape = outline.shape, corner = outline.corner, rotation = rotation, own = own)
        }

        /**
         * [layouts] with each button and key in one outline on all of them (issue 891): an outline is the control's, not
         * one layout's. A skin saved while each layout kept its own (a combo's since issue 842, a button's in the first
         * builds of issue 891) gives every layout the outline the player picked on most of them, the first layout's in
         * [SkinLayoutId] order on a tie; a layout where the control is in its default (the skin's own outline, a
         * combo's circle) never wins over one the player picked. Each control keeps its centre and its size there.
         */
        fun unify(layouts: Map<SkinLayoutId, SkinLayout>): Map<SkinLayoutId, SkinLayout> {
            val picked = LinkedHashMap<String, MutableList<ComboOutline>>()
            for (id in SkinLayoutId.entries) {
                for (e in layouts[id]?.elements.orEmpty()) {
                    val o = chosen(e)?.takeIf { !(e.isCombo && it == CIRCLE) } ?: continue
                    picked.getOrPut(e.id) { mutableListOf() } += o
                }
            }
            val winners = picked.mapValues { (_, os) ->
                val count = os.groupingBy { it }.eachCount()
                val most = count.values.max()
                os.first { count.getValue(it) == most }
            }
            if (winners.isEmpty()) return layouts
            return layouts.mapValues { (_, l) ->
                l.copy(
                    elements = l.elements.map { e ->
                        val o = winners[e.id]?.takeIf { shapeable(e) && chosen(e) != it } ?: return@map e
                        val after = drawn(e, o)
                        after.at(reshape(e.rect, of(e), of(after), l.size))
                    },
                )
            }
        }

        /** How large [rect] drawn as [o] is: a pill [PILL_TALL] of its height's circle, anything else its width. */
        fun side(rect: SkinRect, o: ComboOutline): Float = if (o.shape == SkinShape.PILL) rect.height / PILL_TALL else rect.width

        /**
         * [rect] (drawn as [from]) drawn as [to], about the same centre: a circle, a rounded square and a triangle are as
         * wide as tall, a pill [PILL_WIDE] times as wide and [PILL_TALL] times as tall as the circle it was; with a
         * [size], moved back inside the layout (and never larger than it).
         */
        fun reshape(rect: SkinRect, from: ComboOutline, to: ComboOutline, size: SkinSize? = null): SkinRect {
            val side = side(rect, from)
            val w = if (to.shape == SkinShape.PILL) side * PILL_WIDE else side
            val h = if (to.shape == SkinShape.PILL) side * PILL_TALL else side
            return centred(rect, w, h, size)
        }

        /**
         * [rect] (drawn as [from]) back in the skin's own outline [to], whose rect was [own] (issue 891): [own]'s
         * proportions at the size [rect] has, about the same centre; with a [size], kept inside the layout.
         */
        fun restore(rect: SkinRect, from: ComboOutline, own: SkinRect, to: ComboOutline, size: SkinSize? = null): SkinRect {
            val k = side(rect, from) / side(own, to)
            return centred(rect, own.width * k, own.height * k, size)
        }

        /** A [w] x [h] rect about [rect]'s centre; with a [size], never larger than it and moved back inside it. */
        private fun centred(rect: SkinRect, w0: Float, h0: Float, size: SkinSize?): SkinRect {
            var w = w0
            var h = h0
            if (size != null) {
                val k = minOf(1f, size.width / w, size.height / h)
                w *= k
                h *= k
            }
            val x = rect.x + rect.width / 2f - w / 2f
            val y = rect.y + rect.height / 2f - h / 2f
            if (size == null) return SkinRect(x, y, w, h)
            return SkinRect(x.coerceIn(0f, size.width - w), y.coerceIn(0f, size.height - h), w, h)
        }

        /** Whether ([px], [py]) is inside [e]'s triangle (within [slop]); every other outline takes its whole rect. */
        fun holds(e: SkinElement, px: Float, py: Float, slop: Float = 0f): Boolean {
            val corner = e.corner?.takeIf { e.shape == SkinShape.TRIANGLE } ?: return true
            val hw = e.rect.width / 2f
            val hh = e.rect.height / 2f
            // How far past the long edge, in half-sizes: the edge is where the corner's two signs balance.
            val dx = (px - e.rect.x - hw) / hw * corner.sx
            val dy = (py - e.rect.y - hh) / hh * corner.sy
            return dx + dy >= -slop / minOf(hw, hh) * 1.4143f
        }
    }
}

/**
 * The capture panel's hold (issue 842): the buttons pressed now, held unchanged for [SkinCombos.HOLD_MS], make the combo.
 * Pressing other buttons starts the wait over; more than [SkinCombos.MAX] never makes one.
 */
class ComboCapture {
    var pressed: Set<String> = emptySet()
        private set
    private var since = 0L

    /** The buttons the hold took, once it has. */
    var done: Set<String>? = null
        private set

    val tooMany: Boolean get() = pressed.size > SkinCombos.MAX

    /** The buttons held at [now] (ms) are [buttons]: a change restarts the wait. */
    fun press(buttons: Set<String>, now: Long) {
        if (done != null || buttons == pressed) return
        pressed = buttons
        since = now
    }

    /** How far the hold is at [now], 0 to 1; reaching 1 with 2 to [SkinCombos.MAX] buttons takes them. */
    fun progress(now: Long): Float {
        done?.let { return 1f }
        if (pressed.size !in SkinCombos.MIN..SkinCombos.MAX) return 0f
        val p = ((now - since).toFloat() / SkinCombos.HOLD_MS).coerceIn(0f, 1f)
        if (p >= 1f) done = pressed
        return p
    }
}

/** What a tap does in "Pick by tapping". */
enum class ComboPickResult { ADDED, REMOVED, TOO_MANY, NOT_A_BUTTON }

/** "Pick by tapping" (issue 842): the buttons tapped so far, at most [SkinCombos.MAX], each tap toggling one. */
class ComboPick {
    var picked: List<String> = emptyList()
        private set

    val ready: Boolean get() = picked.size >= SkinCombos.MIN

    /** A tap on a control bound to [bind]; [eligible] whether a combo may press it. */
    fun tap(bind: String, eligible: Boolean): ComboPickResult = when {
        !eligible -> ComboPickResult.NOT_A_BUTTON
        bind in picked -> ComboPickResult.REMOVED.also { picked = picked - bind }
        picked.size >= SkinCombos.MAX -> ComboPickResult.TOO_MANY
        else -> ComboPickResult.ADDED.also { picked = picked + bind }
    }
}

/**
 * A controller's buttons as a skin's input names, for the capture (issue 842): Android's gamepad keys on the RetroPad
 * as RetroArch places them (the bottom button is B, the right one A, the left one Y, the top one X), then the console's
 * own name for that RetroPad button ([SkinBinds.CONSOLE_BINDS] read backwards). Inputs a
 * controller key does not press as such (the N64's C buttons, on the right stick) are picked on the skin instead.
 */
object PadCombo {
    private val PAD = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to "b", KeyEvent.KEYCODE_BUTTON_B to "a", KeyEvent.KEYCODE_BUTTON_X to "y",
        KeyEvent.KEYCODE_BUTTON_Y to "x", KeyEvent.KEYCODE_BUTTON_L1 to "l", KeyEvent.KEYCODE_BUTTON_R1 to "r",
        KeyEvent.KEYCODE_BUTTON_L2 to "l2", KeyEvent.KEYCODE_BUTTON_R2 to "r2",
        KeyEvent.KEYCODE_BUTTON_START to "start", KeyEvent.KEYCODE_BUTTON_SELECT to "select",
    )

    /** Whether [keycode] is a controller button the capture reads. */
    fun reads(keycode: Int): Boolean = keycode in PAD

    /**
     * [keycode] as one of [inputs] (the layout's eligible input names) on [console]: the input that presses its
     * RetroPad button there, or null when none does.
     */
    fun input(console: Console, keycode: Int, inputs: Collection<String>): String? {
        val pad = PAD[keycode] ?: return null
        return inputs.firstOrNull { SkinBinds.bind(console, it, false) == pad }
    }
}
