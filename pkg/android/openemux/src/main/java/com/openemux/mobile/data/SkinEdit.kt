package com.openemux.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The skin editor's maths (issue 784): pure functions over a layout's rects, no Android, so every rule is unit-tested.
 * Everything here is in the layout's own units (SkinLayout.size); the editor's stage converts to the screen.
 */

private val SkinRect.cx get() = x + width / 2f
private val SkinRect.cy get() = y + height / 2f
private val SkinRect.right get() = x + width
private val SkinRect.bottom get() = y + height

/** Whether ([px], [py]) is inside the rect, or within [slop] of it. */
private fun SkinRect.holds(px: Float, py: Float, slop: Float = 0f) =
    px >= x - slop && px <= right + slop && py >= y - slop && py <= bottom + slop

/** The alignment grid the editor shows and snaps to. */
object SkinGrid {
    /** Grid cells across the layout's short side: 22 px on a 390-wide phone, as in the design. */
    const val CELLS = 18

    /** One grid step in [size]'s units. */
    fun step(size: SkinSize): Float = (minOf(size.width, size.height) / CELLS.toFloat()).coerceAtLeast(1f)
}

/** What a dragged control snapped to: a line on [axis] (X: a vertical line at x = [pos]) and why. */
enum class SnapKind { ALIGN, EDGE, MIRROR, CENTER }

enum class SnapAxis { X, Y }

/** [other]: the control the line belongs to, for [SnapKind.ALIGN], [SnapKind.EDGE] and [SnapKind.MIRROR]. */
data class SnapGuide(val axis: SnapAxis, val pos: Float, val kind: SnapKind, val other: String? = null)

/** How far the selection moves once snapped and clamped, and the lines it snapped to. */
data class SnapResult(val dx: Float, val dy: Float, val guides: List<SnapGuide>)

object SkinSnap {
    /** How close (in grid steps) a target has to be to pull the control onto it. */
    const val PULL = 0.4f

    /** The box around [rects]. */
    fun bounds(rects: Collection<SkinRect>): SkinRect {
        val left = rects.minOf { it.x }
        val top = rects.minOf { it.y }
        return SkinRect(left, top, rects.maxOf { it.right } - left, rects.maxOf { it.bottom } - top)
    }

    /**
     * Moves [moving] (the selection's box) by [dx], [dy] inside [size]. With [snap], the nearest target within the pull
     * on each axis wins: the screen's centre line and the mirror of another control's centre (across only), another
     * control's centre line, then its edges; with none near, the box's centre lands on half a grid step. The box is
     * kept inside the layout; a guide the clamp moved it off is dropped.
     */
    fun move(moving: SkinRect, others: Map<String, SkinRect>, size: SkinSize, dx: Float, dy: Float, snap: Boolean): SnapResult {
        val grid = SkinGrid.step(size)
        var x = moving.x + dx
        var y = moving.y + dy
        var gx: SnapGuide? = null
        var gy: SnapGuide? = null
        if (snap) {
            val sx = axis(x, moving.width, others.mapValues { (_, r) -> r.x to r.width }, size.width, grid, SnapAxis.X)
            val sy = axis(y, moving.height, others.mapValues { (_, r) -> r.y to r.height }, size.height, grid, SnapAxis.Y)
            x = sx.first; gx = sx.second
            y = sy.first; gy = sy.second
        }
        val cx = clamp(x, moving.width, size.width)
        val cy = clamp(y, moving.height, size.height)
        if (cx != x) gx = null
        if (cy != y) gy = null
        return SnapResult(cx - moving.x, cy - moving.y, listOfNotNull(gx, gy))
    }

    /** Moves [rects] together by [steps] grid steps on each axis, kept inside [size]. */
    fun nudge(rects: Map<String, SkinRect>, size: SkinSize, stepsX: Int, stepsY: Int): Map<String, SkinRect> {
        if (rects.isEmpty()) return rects
        val grid = SkinGrid.step(size)
        val box = bounds(rects.values)
        val dx = clamp(box.x + stepsX * grid, box.width, size.width) - box.x
        val dy = clamp(box.y + stepsY * grid, box.height, size.height) - box.y
        return rects.mapValues { (_, r) -> r.copy(x = r.x + dx, y = r.y + dy) }
    }

    /** [start] kept so [length] fits in [extent] (at 0 when it cannot). */
    internal fun clamp(start: Float, length: Float, extent: Int): Float = start.coerceIn(0f, maxOf(0f, extent - length))

    private class Target(val start: Float, val line: Float, val kind: SnapKind, val other: String?)

    /** The snapped start of a [length] span at [start] on one axis, and the guide it snapped to (null for the grid). */
    private fun axis(
        start: Float, length: Float, others: Map<String, Pair<Float, Float>>, extent: Int, grid: Float, axis: SnapAxis,
    ): Pair<Float, SnapGuide?> {
        val targets = buildList {
            if (axis == SnapAxis.X) {
                add(Target(extent / 2f - length / 2f, extent / 2f, SnapKind.CENTER, null))
                others.forEach { (id, o) ->
                    val m = extent - (o.first + o.second / 2f)
                    add(Target(m - length / 2f, m, SnapKind.MIRROR, id))
                }
            }
            others.forEach { (id, o) ->
                val c = o.first + o.second / 2f
                add(Target(c - length / 2f, c, SnapKind.ALIGN, id))
            }
            others.forEach { (id, o) ->
                val end = o.first + o.second
                add(Target(o.first, o.first, SnapKind.EDGE, id))
                add(Target(end, end, SnapKind.EDGE, id))
                add(Target(end - length, end, SnapKind.EDGE, id))
                add(Target(o.first - length, o.first, SnapKind.EDGE, id))
            }
        }
        val pull = grid * PULL
        var best: Target? = null
        for (t in targets) {
            val d = abs(t.start - start)
            if (d < pull && (best == null || d < abs(best.start - start))) best = t
        }
        best?.let { return it.start to SnapGuide(axis, it.line, it.kind, it.other) }
        val half = grid / 2f
        val centre = ((start + length / 2f) / half).roundToInt() * half
        return centre - length / 2f to null
    }
}

/** The editor's size steps: a share of the size saved in the skin. */
object SkinResize {
    const val MIN = 50
    const val MAX = 200
    const val STEP = 5

    /** [now]'s size as a whole percentage of [saved]'s. */
    fun percent(saved: SkinRect, now: SkinRect): Int = (now.width / saved.width * 100f).roundToInt()

    /** [raw] percent on the nearest step, within [MIN] and [MAX]. */
    fun step(raw: Float): Int = ((raw / STEP).roundToInt() * STEP).coerceIn(MIN, MAX)

    /**
     * [rects] (the selection when the resize began) scaled about their box's centre so that [ref] is [target] percent
     * of its [saved] size; the members keep their sizes relative to each other. Never larger than [size], and moved
     * back inside it when the growth pushed it out.
     */
    fun scale(rects: Map<String, SkinRect>, saved: SkinRect, ref: String, target: Int, size: SkinSize): Map<String, SkinRect> {
        val box = SkinSnap.bounds(rects.values)
        var r = saved.width * target / 100f / rects.getValue(ref).width
        r = minOf(r, size.width / box.width, size.height / box.height)
        val cx = box.cx
        val cy = box.cy
        val scaled = rects.mapValues { (_, o) -> SkinRect(cx + (o.x - cx) * r, cy + (o.y - cy) * r, o.width * r, o.height * r) }
        val b = SkinSnap.bounds(scaled.values)
        val dx = SkinSnap.clamp(b.x, b.width, size.width) - b.x
        val dy = SkinSnap.clamp(b.y, b.height, size.height) - b.y
        return scaled.mapValues { (_, o) -> o.copy(x = o.x + dx, y = o.y + dy) }
    }
}

/**
 * The parts of a layout that are not controls, in [SkinRects] under ids a skin's own (`[a-z0-9-]`) can never take:
 * each game screen and the frame (issue 785), and a control's label once it has a place of its own.
 */
object SkinArea {
    const val FRAME = "@frame"
    private const val SCREEN = "@screen"
    private const val LABEL = "#label"

    fun screen(index: Int) = "$SCREEN$index"

    /** The key of control [id]'s label. */
    fun label(id: String) = "$id$LABEL"

    fun isLabel(key: String) = key.endsWith(LABEL)

    /** The control whose label [key] is, or null for any other key. */
    fun labelOwner(key: String): String? = key.takeIf(::isLabel)?.removeSuffix(LABEL)

    /** The screen [id] names, or null for any other id. */
    fun screenIndex(id: String): Int? = id.takeIf { it.startsWith(SCREEN) }?.removePrefix(SCREEN)?.toIntOrNull()

    fun isArea(id: String) = id.startsWith("@")

    /**
     * What "Screen area" moves in [layout]: its screens, its frame, and the quick actions on the frame (whose centre is
     * inside it), so the bezel and its icons go with the picture.
     */
    fun ids(layout: SkinLayout): List<String> = buildList {
        layout.screens.indices.forEach { add(screen(it)) }
        val frame = layout.frame?.rect ?: return@buildList
        add(FRAME)
        layout.elements.filter { it.type == SkinElementType.ACTION && frame.holds(it.rect.cx, it.rect.cy) }.forEach { add(it.id) }
    }
}

/**
 * What the editor has selected: a whole group (the face buttons), one control, one control's label, the screen area,
 * or one of the two things that are painted and never moved, the screen's frame and the console's body.
 */
sealed class SkinSelection {
    /** The controls it paints. */
    abstract val ids: List<String>

    /** Whether it can be dragged, nudged and resized. */
    open val movable: Boolean get() = true

    data class Group(val group: String, override val ids: List<String>) : SkinSelection()

    data class One(val id: String) : SkinSelection() {
        override val ids: List<String> get() = listOf(id)
    }

    /** The label of control [id], moved and sized on its own. */
    data class Label(val id: String) : SkinSelection() {
        override val ids: List<String> get() = listOf(id)
    }

    /** The game screens with their frame and its quick actions (issue 785, [SkinArea.ids]). */
    data class Screen(override val ids: List<String>) : SkinSelection()

    /** The screen's frame, with the quick actions on it: its colours only. */
    data object Frame : SkinSelection() {
        override val ids: List<String> get() = emptyList()
        override val movable: Boolean get() = false
    }

    /** The console's body, behind everything: its colour and its texture only. */
    data object Body : SkinSelection() {
        override val ids: List<String> get() = emptyList()
        override val movable: Boolean get() = false
    }
}

object SkinSelect {
    /** The controls moved together with [id]: its group's members in layout order, or just [id]. */
    fun members(layout: SkinLayout, id: String): List<String> {
        val group = layout.elements.firstOrNull { it.id == id }?.group ?: return listOf(id)
        return layout.elements.filter { it.group == group }.map { it.id }
    }

    /** [id] as a first selection: its group when it has other members, else itself. */
    fun first(layout: SkinLayout, id: String): SkinSelection {
        val members = members(layout, id)
        if (members.size < 2) return SkinSelection.One(id)
        return SkinSelection.Group(layout.elements.first { it.id == id }.group!!, members)
    }

    /**
     * A tap on [id] with [current] selected, each tap going one step in: the first tap on a grouped control takes its
     * group, the next that one control, the next its label (when it has one), and one more starts over. A lone
     * control goes straight to itself, then its label.
     */
    fun tap(layout: SkinLayout, current: SkinSelection?, id: String): SkinSelection {
        val element = layout.elements.firstOrNull { it.id == id } ?: return first(layout, id)
        val members = members(layout, id)
        return when {
            current is SkinSelection.One && current.id == id -> if (SkinLabels.has(element)) SkinSelection.Label(id) else first(layout, id)
            current is SkinSelection.Label && current.id == id -> first(layout, id)
            // Its group, or another control of it (or that one's label), is selected: this one control.
            current != null && current.movable && current.ids.isNotEmpty() && members.size > 1 && current.ids.all { it in members } -> SkinSelection.One(id)
            else -> first(layout, id)
        }
    }

    /** What a drag on [id] moves: the current selection when [id] is in it, else what a first tap would select. */
    fun dragged(layout: SkinLayout, current: SkinSelection?, id: String): SkinSelection =
        if (current != null && id in current.ids) current else first(layout, id)

    /**
     * The selection after [current] going [by] (+1 next, -1 previous) for a gamepad: one control steps through its
     * group's members, anything else through the layout's groups and lone controls.
     */
    fun step(layout: SkinLayout, current: SkinSelection?, by: Int): SkinSelection? {
        if (layout.elements.isEmpty()) return null
        // A label steps as its control does.
        val one = (current as? SkinSelection.One)?.id ?: (current as? SkinSelection.Label)?.id
        if (one != null) {
            val members = members(layout, one)
            if (members.size > 1) return SkinSelection.One(members[Math.floorMod(members.indexOf(one) + by, members.size)])
        }
        val units = units(layout)
        val at = units.indexOf(if (current is SkinSelection.Label) first(layout, current.id) else current)
        val next = if (at < 0) (if (by > 0) 0 else units.size - 1) else Math.floorMod(at + by, units.size)
        return units[next]
    }

    /** A group becomes its first control, one grouped control its group; a lone control stays as it is. */
    fun toggle(layout: SkinLayout, current: SkinSelection?): SkinSelection? = when (current) {
        null -> null
        is SkinSelection.Group -> SkinSelection.One(current.ids.first())
        is SkinSelection.One -> first(layout, current.id)
        is SkinSelection.Label -> first(layout, current.id)
        is SkinSelection.Screen, SkinSelection.Frame, SkinSelection.Body -> current
    }

    /** The groups and lone controls of [layout], each once, in layout order. */
    fun units(layout: SkinLayout): List<SkinSelection> = layout.elements.map { first(layout, it.id) }.distinct()
}

/** Undo and redo over whole snapshots of what is being edited, at most [cap] steps back. */
class SkinEditHistory<T>(initial: T, private val cap: Int = 100) {
    var current: T = initial
        private set
    private val undos = ArrayDeque<T>()
    private val redos = ArrayDeque<T>()

    val canUndo: Boolean get() = undos.isNotEmpty()
    val canRedo: Boolean get() = redos.isNotEmpty()

    /** [next] becomes the current state; the redo steps are gone. The same state again is no step. */
    fun push(next: T) {
        if (next == current) return
        undos.addLast(current)
        if (undos.size > cap) undos.removeFirst()
        redos.clear()
        current = next
    }

    fun undo(): Boolean {
        val previous = undos.removeLastOrNull() ?: return false
        redos.addLast(current)
        current = previous
        return true
    }

    fun redo(): Boolean {
        val next = redos.removeLastOrNull() ?: return false
        undos.addLast(current)
        current = next
        return true
    }
}

/** The rects of every element, per layout. */
typealias SkinRects = Map<SkinLayoutId, Map<String, SkinRect>>

fun OpenEmuxSkin.rects(): SkinRects = layouts.mapValues { (_, l) -> l.rects() }

/**
 * The rects of [SkinLayout.elements], of each screen and of the frame ([SkinArea]), and of each label that has a place
 * of its own ([SkinElement.labelOffset]): one left on its control's face follows the control and needs none.
 */
fun SkinLayout.rects(): Map<String, SkinRect> = buildMap {
    elements.forEach { put(it.id, it.rect) }
    screens.forEachIndexed { i, r -> put(SkinArea.screen(i), r) }
    frame?.let { put(SkinArea.FRAME, it.rect) }
    elements.filter { it.labelOffset != null && SkinLabels.has(it) }.forEach { put(SkinArea.label(it.id), SkinLabels.box(it)) }
}

/**
 * [this] with the rects of [rects] put back: elements by id (a control resized keeps its proportions,
 * [SkinElement.at]), their labels, screens and frame by their [SkinArea] ids.
 */
fun SkinLayout.withRects(rects: Map<String, SkinRect>): SkinLayout = copy(
    elements = elements.map { e ->
        val placed = rects[e.id]?.let(e::at) ?: e
        rects[SkinArea.label(e.id)]?.let { SkinLabels.placed(placed, it) } ?: placed
    },
    screens = screens.mapIndexed { i, r -> rects[SkinArea.screen(i)] ?: r },
    frame = frame?.let { f -> rects[SkinArea.FRAME]?.let { f.copy(rect = it) } ?: f },
)

/**
 * One undo step's worth of an editing session: the rects of every layout the skin offers (its keys, issue 785), their
 * colours ([looks], issue 786), what was done to their image files ([images]) and the combo buttons the skin has
 * ([combos], issue 842: each combo's place is in [rects] under its id).
 */
data class SkinEditState(
    val rects: SkinRects,
    val looks: Map<SkinLayoutId, SkinLook>,
    val images: SkinImageEdits = emptyMap(),
    val combos: List<SkinCombo> = emptyList(),
)

/**
 * One editing session on [saved] (issues 784, 785 and 786): the rects and colours of every layout, the one being edited
 * ([layoutId], set from the device's form and rotation), the selection, Snap, Try mode, and undo/redo. The layouts the
 * skin offers are the keys of [rects], so switching a form on or off is an undo step like any other.
 *
 * [start] is a draft's rects, [looks] its colours and [images] its image edits; only the layouts and elements the
 * session has are taken from them. [added] are layouts the draft has and [saved] has not, [removed] layouts of [saved]
 * the draft dropped. [existing]: the image files the skin has (SkinSource.files), which tells a colour that drops an
 * image from one that has none to drop. [combos] are a draft's combo buttons (null: the skin's) and [comboElements]
 * the combos a draft placed on layouts of [saved] that has none of them.
 */
class SkinEditorModel(
    val saved: OpenEmuxSkin,
    start: SkinRects? = null,
    added: Map<SkinLayoutId, SkinLayout> = emptyMap(),
    removed: Set<SkinLayoutId> = emptySet(),
    looks: Map<SkinLayoutId, SkinLook>? = null,
    images: SkinImageEdits = emptyMap(),
    private val existing: Set<String> = emptySet(),
    combos: List<SkinCombo>? = null,
    comboElements: Map<SkinLayoutId, List<SkinElement>> = emptyMap(),
) {
    private val original = saved.rects()
    private val originalLooks = saved.layouts.mapValues { (_, l) -> l.look() }
    private val originalCombos = saved.combos

    /** Layouts this session made (a form switched on) or a draft brought: what [base] falls back to. */
    private val derived = added.toMutableMap()

    /**
     * Combo buttons placed on a layout of [saved] or [derived] that has none of them: by a draft, or by this session
     * (each at its starting spot), kept after a delete so undo and redo find them. Which combos the skin has, and their
     * inputs, is the state's [SkinEditState.combos].
     */
    private val placedCombos = comboElements.mapValues { it.value }.toMutableMap()

    /** Combo labels made so far, by buttons: [present] runs on every read of a layout. */
    private val labels = HashMap<List<String>, String>()
    private val history = run {
        val ids = (saved.layouts.keys - removed) + added.keys
        val startCombos = combos ?: originalCombos
        SkinEditState(
            ids.associateWith { id ->
                val d = start?.get(id).orEmpty()
                val b = present(base(id), startCombos)
                // The draft's rects for what the skin has, and the labels the draft gave a place of their own.
                b.rects().mapValues { (k, r) -> d[k] ?: r } +
                    d.filterKeys { k -> SkinArea.labelOwner(k)?.let { owner -> b.elements.any { it.id == owner && SkinLabels.has(it) } } == true }
            },
            ids.associateWith { id -> mergeLook(base(id).look(), looks?.get(id)) },
            images.filterKeys { it.substringBefore('/') in ids.map(SkinLayoutId::id) && knownImage(it) },
            startCombos,
        ).let { SkinEditHistory(it) }
    }

    /** What a drag, a pinch or a colour being picked has done, before it becomes one undo step. */
    private var live: SkinEditState? = null
    private var gestureFrom: Map<String, SkinRect>? = null

    var layoutId: SkinLayoutId? = null
        private set
    var selection: SkinSelection? = null
        private set
    var snap: Boolean = true
    var guides: List<SnapGuide> = emptyList()
        private set

    /** Try mode (issue 785): presses light controls instead of selecting them. */
    var trying: Boolean = false
        private set

    /** The control held down in Try mode, and the last few pressed (oldest first). */
    var pressed: String? = null
        private set
    var recent: List<String> = emptyList()
        private set

    private val state: SkinEditState get() = live ?: history.current
    val rects: SkinRects get() = state.rects
    val looks: Map<SkinLayoutId, SkinLook> get() = state.looks
    val images: SkinImageEdits get() = state.images

    /** The combo buttons the skin has now (issue 842). */
    val combos: List<SkinCombo> get() = state.combos
    val dirty: Boolean get() = rects != original || looks != originalLooks || images.isNotEmpty() || combos != originalCombos
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** The layout being edited with its current rects and colours, or null when the skin has none for this form and rotation. */
    val layout: SkinLayout?
        get() = layoutId?.let(::layoutOf)

    /** Layout [id] with its current rects and colours, or null when the skin does not offer it now. */
    fun layoutOf(id: SkinLayoutId): SkinLayout? {
        val now = rects[id] ?: return null
        return present(base(id), combos).withRects(now).withLook(looks.getValue(id))
    }

    /** Edits [form]'s layout in [orientation]; the selection is dropped. */
    fun show(form: SkinForm, orientation: SkinOrientation) {
        val id = SkinForms.id(form, orientation)
        val next = id.takeIf { it in rects }
        if (next != layoutId) {
            cancelGesture()
            selection = null
            pressed = null
            // The presses Try mode remembers name controls of the layout left.
            recent = emptyList()
        }
        layoutId = next
    }

    /** Whether the skin offers [form] now: it has at least one of its layouts (what the form's badge shows). */
    fun offered(form: SkinForm): Boolean = SkinForms.layoutsOf(form).any { it in rects }

    /** Whether layout [id] differs from how the skin has it (or is new, or gone): its rects, its colours or its images. */
    fun changed(id: SkinLayoutId): Boolean =
        rects[id] != original[id] || looks[id] != originalLooks[id] || images.keys.any { it.substringBefore('/') == id.id } ||
            layoutOf(id)?.elements?.filter { it.isCombo }?.map { it.id to it.bind }?.toSet() !=
            saved.layouts[id]?.elements?.filter { it.isCombo }?.map { it.id to it.bind }?.toSet()

    /**
     * The Layouts panel's switch for [form] (issue 785). On: each of its layouts the skin lacks is made from the closest
     * one it has ([SkinForms]), or comes back as the skin had it. Off: its layouts leave the skin. The form being
     * edited cannot be switched off. Returns whether anything changed.
     */
    fun setForm(form: SkinForm, on: Boolean): Boolean {
        if (form == layoutId?.form) return false
        val ids = SkinForms.layoutsOf(form)
        val now = history.current
        val next = if (on) {
            val grown = now.rects.toMutableMap()
            val looks = now.looks.toMutableMap()
            for (id in ids.filter { it !in now.rects }) {
                if (id !in saved.layouts && id !in derived) {
                    val source = SkinForms.closest(id, grown.keys) ?: return false
                    // Made from the source as it is when first switched on this session: its rects and its colours
                    // (not its images). Kept in [derived] afterwards, so undo and redo find the same layout.
                    val layouts = grown.keys.associateWith { present(base(it), now.combos).withRects(grown.getValue(it)).withLook(looks.getValue(it)) }
                    derived[id] = SkinForms.derive(layouts.getValue(source), SkinForms.size(id, layouts))
                }
                // A combo made after the layout was, or one the skin's own layout lacks, starts at a spot of its own.
                now.combos.filter { c -> base(id).elements.none { it.id == c.id } }.forEach { c -> place(id, c, present(base(id), now.combos)) }
                grown[id] = present(base(id), now.combos).rects()
                looks[id] = base(id).look()
            }
            now.copy(rects = grown, looks = looks)
        } else {
            val gone = ids.toSet()
            // The layouts' image edits go with them: what they staged is not written for a layout the skin drops.
            now.copy(rects = now.rects - gone, looks = now.looks - gone, images = now.images.filterKeys { p -> gone.none { p.substringBefore('/') == it.id } })
        }
        if (next == now) return false
        cancelGesture()
        history.push(next)
        return true
    }

    /** The topmost control under ([x], [y]), within [slop] of its box or of its label's (a word printed beside a key
     * is the key too); null for none. With the screen area selected, a touch inside it is on the screen area. */
    fun hit(x: Float, y: Float, slop: Float = 0f): String? {
        val l = layout ?: return null
        (selection as? SkinSelection.Screen)?.let { sel ->
            val box = SkinSnap.bounds(sel.ids.mapNotNull { current()[it] })
            if (box.holds(x, y)) return sel.ids.first()
        }
        return l.elements.lastOrNull { it.rect.holds(x, y, slop) || (SkinLabels.has(it) && SkinLabels.box(it).holds(x, y, slop)) }?.id
    }

    /**
     * Whether control [id] stays where the skin has it: a quick action on a layout with a frame is part of the frame,
     * painted with it and never moved.
     */
    fun fixed(id: String): Boolean {
        val l = layout ?: return false
        return l.frame != null && l.elements.any { it.id == id && it.type == SkinElementType.ACTION }
    }

    fun tap(id: String) {
        val l = layout ?: return
        if (selection is SkinSelection.Screen && id in selection!!.ids) return
        selection = if (fixed(id)) SkinSelection.Frame else SkinSelect.tap(controls(l), selection, id)
    }

    /**
     * [l] as far as selecting goes: without the quick actions that stay on its frame ([fixed]), so none of them is ever
     * selected to move, on its own or with a group it shares with other controls.
     */
    private fun controls(l: SkinLayout): SkinLayout =
        if (l.frame == null) l else l.copy(elements = l.elements.filter { it.type != SkinElementType.ACTION })

    /**
     * A tap at ([x], [y]) (the layout's units), within [slop] of a control: the control under it ([tap]); else the
     * frame around the screen; else, with nothing selected, the console's body. A tap on the body or on the game's
     * screen with something selected lets go of it.
     */
    fun tapAt(x: Float, y: Float, slop: Float = 0f) {
        val l = layout ?: return
        hit(x, y, slop)?.let { return tap(it) }
        val onScreen = l.screens.any { it.holds(x, y) }
        selection = when {
            !onScreen && l.frame?.rect?.holds(x, y) == true -> SkinSelection.Frame
            onScreen || selection != null -> null
            x >= 0f && y >= 0f && x <= l.size.width && y <= l.size.height -> SkinSelection.Body
            else -> null
        }
    }

    /** More › Background and Screen frame: the body, or the frame (when the layout has one), selected. */
    fun selectBody() {
        if (layout == null) return
        tryMode(false)
        selection = SkinSelection.Body
    }

    fun selectFrame() {
        if (layout?.frame == null) return
        tryMode(false)
        selection = SkinSelection.Frame
    }

    /**
     * The selection's box in the layout: its controls with the labels that move with them, one label, the frame, or
     * (the body) the whole layout. Null with nothing selected.
     */
    fun box(): SkinRect? {
        val l = layout ?: return null
        return when (selection) {
            null -> null
            SkinSelection.Body -> SkinRect(0f, 0f, l.size.width.toFloat(), l.size.height.toFloat())
            SkinSelection.Frame -> l.frame?.rect
            else -> moving().values.takeIf { it.isNotEmpty() }?.let { SkinSnap.bounds(it) }
        }
    }

    /** More › Screen area: the screens, frame and quick actions selected together ([SkinArea.ids]). */
    fun selectScreen() {
        val l = layout ?: return
        if (l.screens.isEmpty()) return
        tryMode(false)
        selection = SkinSelection.Screen(SkinArea.ids(l))
    }

    /** Try mode on or off: the selection and any gesture are dropped, nothing is lit. */
    fun tryMode(on: Boolean) {
        cancelGesture()
        selection = null
        trying = on
        pressed = null
        if (!on) recent = emptyList()
    }

    /** Try mode: [id] is pressed, lit until [release], and joins [recent]. */
    fun press(id: String) {
        if (!trying || layout?.elements?.any { it.id == id } != true) return
        pressed = id
        recent = SkinTry.remember(recent, id)
    }

    fun release() {
        pressed = null
    }

    /** Try mode with a gamepad: the next (+1) or previous (-1) control is pressed. */
    fun tryStep(by: Int) {
        val elements = layout?.elements?.takeIf { it.isNotEmpty() } ?: return
        val at = elements.indexOfFirst { it.id == (pressed ?: recent.lastOrNull()) }
        val next = if (at < 0) (if (by > 0) 0 else elements.size - 1) else Math.floorMod(at + by, elements.size)
        press(elements[next].id)
    }

    fun clearSelection() {
        selection = null
    }

    /**
     * Gamepad: the next (+1) or previous (-1) control or group. The quick actions on a frame are one stop, the frame,
     * where the first of them is in the layout; a layout with nothing to stop at keeps its selection.
     */
    fun step(by: Int) {
        val l = layout ?: return
        val controls = controls(l)
        // One control of a group (or its label) steps to the group's next control.
        val one = (selection as? SkinSelection.One)?.id ?: (selection as? SkinSelection.Label)?.id
        if (one != null && SkinSelect.members(controls, one).size > 1) {
            selection = SkinSelect.step(controls, selection, by)
            return
        }
        val stops = l.elements.map { if (fixed(it.id)) SkinSelection.Frame else SkinSelect.first(controls, it.id) }.distinct()
        if (stops.isEmpty()) return
        val at = stops.indexOf((selection as? SkinSelection.Label)?.let { SkinSelect.first(controls, it.id) } ?: selection)
        selection = stops[if (at < 0) (if (by > 0) 0 else stops.size - 1) else Math.floorMod(at + by, stops.size)]
    }

    /** Gamepad: a group to its first control and back. */
    fun toggle() {
        val l = layout ?: return
        selection = SkinSelect.toggle(controls(l), selection)
    }

    /** A drag starts on [id]: what it moves becomes the selection. False for a control that stays put ([fixed]). */
    fun beginMove(id: String): Boolean {
        val l = layout ?: return false
        if (fixed(id)) return false
        selection = SkinSelect.dragged(controls(l), selection, id)
        gestureFrom = moving()
        return true
    }

    /** The drag is [dx], [dy] from where it began; [free] (Snap off, or a second finger down) skips the snapping. */
    fun moveBy(dx: Float, dy: Float, free: Boolean = false) {
        val from = gestureFrom ?: return
        val id = layoutId ?: return
        val size = base(id).size
        // Controls snap to controls only: the screens and the frame are not targets (as before issue 785).
        val others = history.current.rects.getValue(id).filterKeys { it !in from && !SkinArea.isArea(it) && !SkinArea.isLabel(it) }
        val result = SkinSnap.move(SkinSnap.bounds(from.values), others, size, dx, dy, snap && !free)
        guides = result.guides
        live = withLayout(id, from.mapValues { (_, r) -> r.copy(x = r.x + result.dx, y = r.y + result.dy) }, label = selection is SkinSelection.Label)
    }

    /** A pinch or the corner handle starts on the selection. False when it has no size to change (the frame, the body). */
    fun beginResize(): Boolean {
        if (selection?.movable != true) return false
        gestureFrom = moving().takeIf { it.isNotEmpty() } ?: return false
        return true
    }

    /**
     * The selection at [factor] times its size when the gesture began, on the nearest 5 % step of its first
     * control's saved size: returns that percentage.
     */
    fun resizeBy(factor: Float): Int? {
        val from = gestureFrom ?: return null
        val ref = ref() ?: return null
        val saved = savedRect(ref) ?: return null
        val target = SkinResize.step(SkinResize.percent(saved, from.getValue(ref)) * factor)
        resizeTo(from, ref, saved, target, live = true)
        return percent()
    }

    /** The drag, pinch or colour being picked ends: what it did is one undo step. */
    fun endGesture() {
        live?.let { history.push(it) }
        cancelGesture()
    }

    /** The drag or pinch is abandoned (the editor lost the window): nothing it did is kept. */
    fun cancelGesture() {
        live = null
        gestureFrom = null
        guides = emptyList()
    }

    /**
     * The selection's first control's (or its label's) size as a percentage of its saved size; null with nothing
     * selected, or something that has no size to change.
     */
    fun percent(): Int? {
        val ref = ref() ?: return null
        val saved = savedRect(ref) ?: return null
        return SkinResize.percent(saved, moving()[ref] ?: return null)
    }

    /** The Size tool: one step larger (+1) or smaller (-1). */
    fun sizeStep(by: Int) {
        val ref = ref() ?: return
        val saved = savedRect(ref) ?: return
        val now = percent() ?: return
        val target = SkinResize.step((SkinResize.step(now.toFloat()) + by * SkinResize.STEP).toFloat())
        resizeTo(moving(), ref, saved, target, live = false)
    }

    /** Moves the selection one grid step per unit of [stepsX], [stepsY]. */
    fun nudge(stepsX: Int, stepsY: Int) {
        val id = layoutId ?: return
        val from = moving().takeIf { it.isNotEmpty() } ?: return
        history.push(withLayout(id, SkinSnap.nudge(from, base(id).size, stepsX, stepsY), label = selection is SkinSelection.Label))
    }

    /**
     * The selection back to its saved place and size; a label the skin leaves on its control's face goes back there
     * (its control keeps drawing it: an image the move replaced comes back with undo, not with this).
     */
    fun reset() {
        val id = layoutId ?: return
        val keys = moving().keys.takeIf { it.isNotEmpty() } ?: return
        val saved = present(base(id), combos).rects()
        history.push(withLayout(id, saved.filterKeys { it in keys }, gone = keys - saved.keys))
    }

    /** The whole layout being edited back to how the skin has it. */
    fun resetLayout() {
        val id = layoutId ?: return
        val saved = present(base(id), combos).rects()
        history.push(withLayout(id, saved, gone = current().keys - saved.keys))
    }

    /**
     * Paints [part] of [target] ([ids]: the controls it paints) in [color], live until [endGesture] (a colour being
     * dragged on the picker is one undo step). A part drawn by an image file of its own loses the image: a colour
     * and an image are one fill, and the image would hide the colour (issue 786).
     *
     * A colour is the skin's, not one layout's: it goes to every layout the skin offers (the same controls by id,
     * the body, the frame), so the other rotation and the other forms are not left in the colours the skin had.
     * Where a control is, how large it is and a texture on it stay each layout's own: in the other layouts the colour
     * leaves a texture in place ([textured]), and replaces only the art the skin came with.
     */
    fun paint(target: SkinTarget, ids: List<String>, part: SkinPart, color: Int) {
        val id = layoutId ?: return
        if (layout == null) return
        val base = history.current
        var looks = base.looks
        var images = base.images
        for (other in base.rects.keys) {
            val l = layoutIn(base, other)
            looks = looks + (other to SkinPainter.paint(base.looks.getValue(other), l, target, ids, part, color))
            val files = SkinPainter.files(other, l, ids, part)
            images = dropImages(images, if (other == id) files else files.filter { !textured(base, l, it) })
        }
        live = base.copy(looks = looks, images = images)
    }

    /** The icon on a toggled quick action, light or dark, in every layout with a frame, as a colour is: one undo step. */
    fun tone(tone: SkinIconTone) {
        if (layout == null) return
        val base = history.current
        history.push(base.copy(looks = base.looks.mapValues { (other, look) -> SkinPainter.tone(look, layoutIn(base, other), tone) }))
        cancelGesture()
    }

    /** Layout [id] as [state] has it: its rects and its colours. */
    private fun layoutIn(state: SkinEditState, id: SkinLayoutId): SkinLayout =
        present(base(id), state.combos).withRects(state.rects.getValue(id)).withLook(state.looks.getValue(id))

    /**
     * Whether the image at [path] of [layout] is a picture the player put there, which a colour picked in another
     * layout leaves alone: a texture placed in this session, or the face of a button, a key or the D-pad
     * ([SkinArt.FACE], what a texture saved earlier is). A texture saved earlier on the body, a ball or a stick cannot
     * be told from the art the skin came with, and gives way to the colour like it.
     */
    private fun textured(state: SkinEditState, layout: SkinLayout, path: String): Boolean =
        state.images[path] is SkinImageEdit.Staged ||
            layout.elements.any { it.art == SkinArt.FACE && it.imageFiles[0] == path.substringAfter('/') }

    /**
     * The files of [staged] (path in the skin to the staged texture's name) replace those images: one undo step. A
     * texture on a button, a key or the D-pad is its face alone ([SkinArt.FACE]): the relief and the label stay the
     * app's to draw, so they are still painted and moved afterwards.
     */
    fun texture(staged: Map<String, String>) {
        val base = history.current
        val known = staged.filterKeys { knownImage(it) }
        val looks = base.looks.mapValues { (id, look) ->
            val faces = base(id).elements.filter { it.type.raised && "${id.id}/${it.imageFiles[0]}" in known }.associate { it.id to SkinArt.FACE }
            if (faces.isEmpty()) look else look.copy(art = look.art + faces)
        }
        history.push(base.copy(looks = looks, images = base.images + known.mapValues { SkinImageEdit.Staged(it.value) }))
        cancelGesture()
    }

    /** [part] of [ids] goes back to its colour: its image (a texture, or the skin's own) is dropped. One undo step. */
    fun removeTexture(target: SkinTarget, ids: List<String>, part: SkinPart) {
        val id = layoutId ?: return
        val l = layout ?: return
        if (target is SkinTarget.Controls && part.imageIndex == null) return
        val base = history.current
        history.push(base.copy(images = dropImages(base.images, SkinPainter.files(id, l, ids, part))))
        cancelGesture()
    }

    /** Whether the skin draws [path] (in the skin) as an image, after the edits. */
    fun hasImage(path: String): Boolean = when (images[path]) {
        SkinImageEdit.Removed -> false
        is SkinImageEdit.Staged -> true
        null -> path in existing
    }

    /** Whether any file drawing [part] of [ids] is an image (so a colour there replaces it). */
    fun textured(ids: List<String>, part: SkinPart): Boolean {
        val id = layoutId ?: return false
        val l = layout ?: return false
        return SkinPainter.files(id, l, ids, part).any { hasImage(it) }
    }

    fun undo() {
        cancelGesture()
        history.undo()
        dropGone()
    }

    fun redo() {
        cancelGesture()
        history.redo()
        dropGone()
    }

    /** A selection of controls the layout no longer has (a combo whose adding was undone) is let go. */
    private fun dropGone() {
        val sel = selection ?: return
        if (sel is SkinSelection.Screen || sel == SkinSelection.Frame || sel == SkinSelection.Body) return
        val ids = layout?.elements?.map { it.id }?.toSet().orEmpty()
        if (!sel.ids.all { it in ids }) selection = null
    }

    /** The rects that differ from the skin's, per layout the skin had and still offers. */
    fun edits(): SkinRects = rects.filterKeys { it in original }.mapValues { (layout, now) ->
        now.filter { (id, r) -> original.getValue(layout)[id] != r }
    }.filterValues { it.isNotEmpty() }

    /**
     * The colours of the layouts the skin had, still offers and whose colours changed; [SkinLook.colors] holds only the
     * controls that changed. A layout switched on is written whole by [addedLayouts], colours included.
     */
    fun lookEdits(): Map<SkinLayoutId, SkinLook> = looks.filter { (id, look) -> id in originalLooks && look != originalLooks.getValue(id) }
        .mapValues { (id, look) ->
            val was = originalLooks.getValue(id)
            look.copy(colors = look.colors.filter { (e, c) -> was.colors[e] != c }, art = look.art.filter { (e, a) -> was.art[e] != a })
        }

    /** The layouts offered now that the skin had not, with their current rects and colours. */
    fun addedLayouts(): Map<SkinLayoutId, SkinLayout> =
        rects.keys.filter { it !in original }.associateWith { layoutOf(it)!! }

    /** The layouts of the skin no longer offered. */
    fun removedLayouts(): Set<SkinLayoutId> = original.keys - rects.keys

    /** [json], the skin's skin.json, with the edited rects and colours written in and the layouts switched on or off. */
    fun json(json: String): String = SkinEditJson.patch(
        json, edits(), lookEdits(), removedLayouts(), addedLayouts(),
        combos = combos, comboPlacements = rects.keys.associateWith { id -> layoutOf(id)!!.elements.filter { it.isCombo } },
        keepCombos = saved.leftOutCombos,
    )

    /** The layout's controls a combo may press (issue 842); a combo needs two of them. */
    fun comboInputs(): List<SkinElement> = layout?.let(SkinCombos::eligible).orEmpty()

    /** The combo selected on its own, or null. */
    val selectedCombo: SkinCombo?
        get() = (selection as? SkinSelection.One)?.id?.let { id -> combos.firstOrNull { it.id == id } }

    /**
     * A combo pressing [buttons] (issue 842), in the order they read on the layout being edited, added to every layout
     * the skin offers at its starting spot there ([SkinCombos.start]) and selected: one undo step. Null, and nothing
     * done, for buttons that make no combo.
     */
    fun addCombo(buttons: Collection<String>): SkinCombo? {
        val l = layout ?: return null
        val ordered = SkinCombos.ordered(buttons, l)
        if (!SkinCombos.valid(ordered)) return null
        tryMode(false)
        val now = history.current
        val combo = SkinCombo(SkinCombos.nextId((saved.layouts.keys + derived.keys).map(::base), saved.leftOutCombos), ordered)
        var rects = now.rects
        for (id in now.rects.keys) {
            val rect = place(id, combo, layoutIn(now, id))
            rects = rects + (id to rects.getValue(id) + (combo.id to rect))
        }
        history.push(now.copy(rects = rects, combos = now.combos + combo))
        selection = SkinSelection.One(combo.id)
        return combo
    }

    /** Combo [id] presses [buttons] now (the Buttons tool): one undo step; nothing for buttons that make no combo. */
    fun recordCombo(id: String, buttons: Collection<String>): Boolean {
        val l = layout ?: return false
        val ordered = SkinCombos.ordered(buttons, l)
        val now = history.current
        if (!SkinCombos.valid(ordered) || now.combos.none { it.id == id }) return false
        cancelGesture()
        history.push(now.copy(combos = now.combos.map { if (it.id == id) it.copy(buttons = ordered) else it }))
        return true
    }

    /** Combo [id] leaves every layout of the skin: one undo step, the selection dropped. */
    fun deleteCombo(id: String) {
        val now = history.current
        if (now.combos.none { it.id == id }) return
        cancelGesture()
        selection = null
        history.push(
            now.copy(
                combos = now.combos.filter { it.id != id },
                rects = now.rects.mapValues { (_, r) -> r - id - SkinArea.label(id) },
            ),
        )
    }

    /**
     * Places [combo] on layout [id] ([shown]: that layout as it is now) at its starting spot, in the combo look and the
     * size of its face buttons, unless the layout already has it: returns where it is.
     */
    private fun place(id: SkinLayoutId, combo: SkinCombo, shown: SkinLayout): SkinRect {
        base(id).elements.firstOrNull { it.id == combo.id }?.let { return it.rect }
        val template = SkinCombos.template(shown)
        val rect = SkinCombos.start(shown, combo.buttons, SkinCombos.diameter(shown, template))
        placedCombos[id] = placedCombos[id].orEmpty() + SkinCombos.element(combo, labelOf(combo.buttons), rect, template)
        return rect
    }

    /** The label of a combo pressing [buttons]: the names the skin's layouts give them. */
    private fun labelOf(buttons: List<String>): String =
        labels.getOrPut(buttons) { SkinCombos.label(buttons, saved.layouts.values + derived.values) }

    /**
     * [layout] with only the combos of [combos], each pressing its inputs and labelled after them, after the layout's
     * other controls in the order [combos] has them.
     */
    private fun present(layout: SkinLayout, combos: List<SkinCombo>): SkinLayout {
        if (layout.elements.none { it.isCombo }) return layout
        val placed = layout.elements.filter { it.isCombo }.associateBy { it.id }
        val shown = combos.mapNotNull { c -> placed[c.id]?.copy(bind = SkinCombos.bind(c.buttons), label = labelOf(c.buttons)) }
        return layout.copy(elements = layout.elements.filter { !it.isCombo } + shown)
    }

    private fun base(id: SkinLayoutId): SkinLayout {
        val layout = saved.layouts[id] ?: derived.getValue(id)
        val placed = placedCombos[id]?.filter { p -> layout.elements.none { it.id == p.id } }.orEmpty()
        return if (placed.isEmpty()) layout else layout.copy(elements = layout.elements + placed)
    }

    private fun current(): Map<String, SkinRect> = rects.getValue(layoutId!!)

    /** [key]'s rect in the skin; a label the skin leaves on its control's face is where that puts it. */
    private fun savedRect(key: String): SkinRect? {
        val saved = layoutId?.let(::base) ?: return null
        return saved.rects()[key] ?: SkinArea.labelOwner(key)?.let { owner -> saved.elements.firstOrNull { it.id == owner } }?.let(SkinLabels::box)
    }

    /** The key the selection's size is read from: its first control, or the label it is. */
    private fun ref(): String? = when (val sel = selection) {
        null -> null
        is SkinSelection.Label -> SkinArea.label(sel.id)
        else -> sel.ids.firstOrNull()?.takeIf { sel.movable }
    }

    /**
     * What the selection moves, by key: its controls and the labels that have a place of their own (a label on its
     * control's face follows it anyway); for a label, the label alone, from where it is drawn now. Empty for the frame
     * and the body, which never move.
     */
    private fun moving(): Map<String, SkinRect> {
        val now = current()
        return when (val sel = selection) {
            null, SkinSelection.Frame, SkinSelection.Body -> emptyMap()
            is SkinSelection.Label -> {
                val key = SkinArea.label(sel.id)
                val box = now[key] ?: layout?.elements?.firstOrNull { it.id == sel.id }?.let(SkinLabels::box)
                box?.let { mapOf(key to it) }.orEmpty()
            }
            else -> now.filterKeys { it in sel.ids || SkinArea.labelOwner(it) in sel.ids }
        }
    }

    private fun resizeTo(from: Map<String, SkinRect>, ref: String, saved: SkinRect, target: Int, live: Boolean) {
        val id = layoutId ?: return
        val scaled = SkinResize.scale(from, saved, ref, target, base(id).size)
        val state = withLayout(id, scaled, label = selection is SkinSelection.Label)
        if (live) this.live = state else history.push(state)
    }

    /**
     * The committed state with [changed] put into layout [id]'s rects and the keys of [gone] taken out. With [label]
     * (a label moved, nudged or sized on its own), a control whose image holds its label ([SkinArt.FULL]) loses that
     * image: it is drawn from its colours, with the label where it now is. Putting things back (Reset) takes no image
     * away.
     */
    private fun withLayout(
        id: SkinLayoutId, changed: Map<String, SkinRect>, gone: Set<String> = emptySet(), label: Boolean = false,
    ): SkinEditState {
        val now = history.current
        val owners = if (label) changed.keys.mapNotNull(SkinArea::labelOwner) else emptyList()
        val images = if (owners.isEmpty()) now.images else
            dropImages(now.images, SkinPainter.files(id, base(id).withLook(now.looks.getValue(id)), owners, SkinPart.LETTER))
        return now.copy(rects = now.rects + (id to (now.rects.getValue(id) - gone) + changed), images = images)
    }

    /** [images] with [files] drawn in colours: a skin's own image is marked gone, a texture staged this session dropped. */
    private fun dropImages(images: SkinImageEdits, files: List<String>): SkinImageEdits {
        val out = images.toMutableMap()
        files.forEach { path -> if (path in existing) out[path] = SkinImageEdit.Removed else out.remove(path) }
        return out
    }

    /**
     * Whether [path] is an image file of a layout and an element (or the background, the frame) the session has: one
     * of [saved]'s, or one switched on ([derived]).
     */
    private fun knownImage(path: String): Boolean {
        val layoutId = SkinLayoutId.byId(path.substringBefore('/')) ?: return false
        val l = saved.layouts[layoutId] ?: derived[layoutId] ?: return false
        val file = path.substringAfter('/')
        return file == l.background.imageFile || file == l.frame?.imageFile || l.elements.any { file in it.imageFiles }
    }

    companion object {
        /**
         * A session on [saved] that resumes [draft], its unsaved skin.json read back (null: none), with the draft's
         * image edits [images]; [existing] as for the constructor.
         */
        fun fromDraft(
            saved: OpenEmuxSkin, draft: OpenEmuxSkin?, images: SkinImageEdits = emptyMap(), existing: Set<String> = emptySet(),
        ): SkinEditorModel = if (draft == null) SkinEditorModel(saved, existing = existing) else
            SkinEditorModel(
                saved, draft.rects(),
                added = draft.layouts.filterKeys { it !in saved.layouts },
                removed = saved.layouts.keys - draft.layouts.keys,
                looks = draft.layouts.mapValues { it.value.look() },
                images = images,
                existing = existing,
                combos = draft.combos,
                // The draft's combos on the skin's own layouts that lack them (the draft's added layouts hold theirs).
                comboElements = draft.layouts.filterKeys { it in saved.layouts }.mapValues { (id, l) ->
                    l.elements.filter { e -> e.isCombo && saved.layouts.getValue(id).elements.none { it.id == e.id } }
                }.filterValues { it.isNotEmpty() },
            )

        /** [base] with [draft]'s colours for the controls [base] has (null: [base] as it is). */
        private fun mergeLook(base: SkinLook, draft: SkinLook?): SkinLook = if (draft == null) base else
            SkinLook(
                draft.background, draft.frame?.takeIf { base.frame != null } ?: base.frame,
                base.colors.mapValues { (id, c) -> draft.colors[id] ?: c }, base.art.mapValues { (id, a) -> draft.art[id] ?: a },
            )
    }
}

/** Writes the editor's rects and colours into a skin.json, every other key as it was (issues 784, 786). */
object SkinEditJson {
    /**
     * [json] with each element, screen and frame of [edits] given its rect in whole units: at least 1 wide and tall,
     * inside its layout; and each layout of [looks] given its background, frame and controls' colours (a key left out
     * when unset). Elements and layouts the file does not have are skipped. The layouts of [removed] go, those of
     * [added] are written in, and a `forms` list follows the layouts left (issue 785).
     */
    fun patch(
        json: String,
        edits: SkinRects,
        looks: Map<SkinLayoutId, SkinLook> = emptyMap(),
        removed: Set<SkinLayoutId> = emptySet(),
        added: Map<SkinLayoutId, SkinLayout> = emptyMap(),
        combos: List<SkinCombo>? = null,
        comboPlacements: Map<SkinLayoutId, List<SkinElement>> = emptyMap(),
        keepCombos: Set<String> = emptySet(),
    ): String {
        val root = JSONObject(json.removePrefix("\uFEFF"))
        val layouts = root.getJSONObject("layouts")
        for ((layoutId, rects) in edits) {
            val layout = layouts.optJSONObject(layoutId.id) ?: continue
            val size = layout.getJSONObject("size")
            val w = size.getInt("width")
            val h = size.getInt("height")
            fun put(target: JSONObject, r: SkinRect) = putRect(target, r, w, h)
            layout.optJSONArray("elements")?.let { elements ->
                for (i in 0 until elements.length()) {
                    val e = elements.getJSONObject(i)
                    val id = e.optString("id")
                    rects[id]?.let { r ->
                        // What is measured in the layout's units follows the control's width (SkinElement.at).
                        val k = e.optDouble("width").takeIf { it > 0.0 }?.let { r.width / it } ?: 1.0
                        put(e, r)
                        // Only a control resized: one that only moved keeps these as the file has them, to the digit.
                        if (kotlin.math.abs(k - 1.0) > 1e-4) {
                            SCALED.forEach { key -> if (e.has(key)) e.put(key, tenth(e.getDouble(key) * k)) }
                            e.optJSONObject("labelOffset")?.let { o -> o.put("x", tenth(o.optDouble("x", 0.0) * k)).put("y", tenth(o.optDouble("y", 0.0) * k)) }
                        }
                    }
                    rects[SkinArea.label(id)]?.let { box ->
                        // The label's centre from the control's, as the editor has both; its height is the type size.
                        val at = rects[id] ?: SkinRect(e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("width").toFloat(), e.getDouble("height").toFloat())
                        e.put("labelSize", tenth(box.height.toDouble()))
                        e.put("labelOffset", JSONObject()
                            .put("x", tenth((box.x + box.width / 2f - (at.x + at.width / 2f)).toDouble()))
                            .put("y", tenth((box.y + box.height / 2f - (at.y + at.height / 2f)).toDouble())))
                    }
                }
            }
            layout.optJSONArray("screens")?.let { screens ->
                for (i in 0 until screens.length()) rects[SkinArea.screen(i)]?.let { put(screens.getJSONObject(i), it) }
            }
            rects[SkinArea.FRAME]?.let { r -> layout.optJSONObject("frame")?.let { put(it, r) } }
        }
        removed.forEach { layouts.remove(it.id) }
        added.forEach { (id, layout) -> layouts.put(id.id, JSONObject(SkinFormat.layoutText(layout))) }
        if (root.has("forms")) {
            val ids = layouts.keys().asSequence().mapNotNull(SkinLayoutId::byId).toSet()
            root.put("forms", JSONArray(SkinForm.entries.filter { f -> ids.any { it.form == f } }.map { it.id }))
        }
        for ((layoutId, look) in looks) {
            val layout = layouts.optJSONObject(layoutId.id) ?: continue
            val bg = layout.optJSONObject("background") ?: JSONObject()
            put(bg, "color", look.background)
            if (bg.length() > 0) layout.put("background", bg) else layout.remove("background")
            val frame = layout.optJSONObject("frame")
            if (frame != null && look.frame != null) {
                val f = look.frame
                val colors = JSONObject()
                put(colors, "frame", f.frame)
                put(colors, "icons", f.icons)
                put(colors, "active", f.active)
                f.activeIcon?.let { colors.put("activeIcon", it.id) }
                if (colors.length() > 0) frame.put("colors", colors) else frame.remove("colors")
            }
            val elements = layout.optJSONArray("elements") ?: continue
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                look.art[e.optString("id")]?.let { art -> if (art == SkinArt.FULL) e.remove("art") else e.put("art", art.id) }
                val parts = look.colors[e.optString("id")] ?: continue
                val type = SkinElementType.byId(e.optString("type"))
                val colors = JSONObject()
                // The type's order, and only the parts it takes.
                type?.colorParts?.forEach { part -> put(colors, part, parts[part]) }
                if (colors.length() > 0) e.put("colors", colors) else e.remove("colors")
            }
        }
        if (combos != null) writeCombos(root, layouts, combos, comboPlacements, keepCombos)
        return root.toString(2) + "\n"
    }

    /**
     * The skin's combo buttons (issue 842) written over whatever the file had: the root `combos` and each layout's
     * (its places from [placements], in whole units inside the layout as any rect the editor writes); a key with none
     * left goes.
     */
    private fun writeCombos(
        root: JSONObject, layouts: JSONObject, combos: List<SkinCombo>, placements: Map<SkinLayoutId, List<SkinElement>>, keep: Set<String>,
    ) {
        // What a newer app wrote and this one left out on reading ([keep]: SkinFormat's "left out, not refused") stays
        // as it was: those root entries and their places.
        val ours = combos.map { it.id }.toSet()
        val foreign = root.optJSONArray("combos").objects().filter { it.optString("id") in keep && it.optString("id") !in ours }
        val foreignIds = foreign.map { it.optString("id") }.toSet()
        val rootArray = SkinFormat.combosText(combos)?.let { JSONArray(it) } ?: JSONArray()
        foreign.forEach { rootArray.put(it) }
        if (rootArray.length() > 0) root.put("combos", rootArray) else root.remove("combos")
        for (key in layouts.keys().asSequence().toList()) {
            val layout = layouts.optJSONObject(key) ?: continue
            val kept = layout.optJSONArray("combos").objects().filter { it.optString("id") in foreignIds }
            val placed = SkinLayoutId.byId(key)?.let { placements[it] }.orEmpty()
            val size = layout.getJSONObject("size")
            val w = size.getInt("width")
            val h = size.getInt("height")
            val array = SkinFormat.layoutCombosText(placed)?.let { JSONArray(it) } ?: JSONArray()
            for (i in 0 until array.length()) {
                val c = array.getJSONObject(i)
                putRect(c, SkinRect(c.getDouble("x").toFloat(), c.getDouble("y").toFloat(), c.getDouble("width").toFloat(), c.getDouble("height").toFloat()), w, h)
                SCALED.forEach { k -> if (c.has(k)) c.put(k, tenth(c.getDouble(k))) }
                c.optJSONObject("labelOffset")?.let { o -> o.put("x", tenth(o.getDouble("x"))).put("y", tenth(o.getDouble("y"))) }
            }
            kept.forEach { array.put(it) }
            if (array.length() > 0) layout.put("combos", array) else layout.remove("combos")
        }
    }

    /** The objects of [this] array (none when it is null). */
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    /** [r] into [target] in whole units: at least 1 wide and tall, inside a [w] x [h] layout. */
    private fun putRect(target: JSONObject, r: SkinRect, w: Int, h: Int) {
        val width = r.width.roundToInt().coerceIn(1, w)
        val height = r.height.roundToInt().coerceIn(1, h)
        target.put("x", r.x.roundToInt().coerceIn(0, w - width))
        target.put("y", r.y.roundToInt().coerceIn(0, h - height))
        target.put("width", width)
        target.put("height", height)
    }

    /** [argb] as the format writes a colour: `#RRGGBB`, or `#RRGGBBAA` when it is not opaque. */
    fun color(argb: Int): String {
        val rgb = SkinColorMath.hex(argb)
        val alpha = argb ushr 24
        return if (alpha == 0xFF) rgb else rgb + "%02X".format(alpha)
    }

    private fun put(obj: JSONObject, key: String, argb: Int?) {
        if (argb == null) obj.remove(key) else obj.put(key, color(argb))
    }

    /** The keys of an element measured in the layout's units, which follow its width when it is resized. */
    private val SCALED = listOf("radius", "depth", "labelSize")

    /** [v] to a tenth of a unit, a whole number written as one. */
    private fun tenth(v: Double): Number = (Math.round(v * 10.0) / 10.0).let { if (it == Math.rint(it)) it.toInt() else it }
}
