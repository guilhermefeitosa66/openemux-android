package com.openemux.mobile.data

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

/** What the editor has selected: a whole group (the face buttons) or one control. */
sealed class SkinSelection {
    abstract val ids: List<String>

    data class Group(val group: String, override val ids: List<String>) : SkinSelection()

    data class One(val id: String) : SkinSelection() {
        override val ids: List<String> get() = listOf(id)
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
     * A tap on [id] with [current] selected: the first tap on a grouped control takes its group, a tap on a member of
     * the selected group (or of the selected control's group) takes that one control.
     */
    fun tap(layout: SkinLayout, current: SkinSelection?, id: String): SkinSelection {
        val members = members(layout, id)
        val within = current != null && members.size > 1 && current.ids.all { it in members }
        return if (within) SkinSelection.One(id) else first(layout, id)
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
        if (current is SkinSelection.One) {
            val members = members(layout, current.id)
            if (members.size > 1) return SkinSelection.One(members[Math.floorMod(members.indexOf(current.id) + by, members.size)])
        }
        val units = units(layout)
        val at = units.indexOf(current)
        val next = if (at < 0) (if (by > 0) 0 else units.size - 1) else Math.floorMod(at + by, units.size)
        return units[next]
    }

    /** A group becomes its first control, one grouped control its group; a lone control stays as it is. */
    fun toggle(layout: SkinLayout, current: SkinSelection?): SkinSelection? = when (current) {
        null -> null
        is SkinSelection.Group -> SkinSelection.One(current.ids.first())
        is SkinSelection.One -> first(layout, current.id)
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

fun OpenEmuxSkin.rects(): SkinRects = layouts.mapValues { (_, l) -> l.elements.associate { it.id to it.rect } }

/**
 * One undo step's worth of an editing session: the rects of every layout, their colours ([looks], issue 786) and what
 * was done to their image files ([images]).
 */
data class SkinEditState(val rects: SkinRects, val looks: Map<SkinLayoutId, SkinLook>, val images: SkinImageEdits = emptyMap())

/**
 * One editing session on [saved] (issue 784): the rects of every layout, the one being edited ([layoutId], set from
 * the device's form and rotation), the selection, Snap, and undo/redo. [start] is a draft's rects, [looks] its colours
 * and [images] its image edits; only the layouts and elements [saved] has are taken from them. [existing]: the image
 * files the skin has (SkinSource.files), which tells a colour that drops an image from one that has none to drop.
 */
class SkinEditorModel(
    val saved: OpenEmuxSkin,
    start: SkinRects? = null,
    looks: Map<SkinLayoutId, SkinLook>? = null,
    images: SkinImageEdits = emptyMap(),
    private val existing: Set<String> = emptySet(),
) {
    private val original = saved.rects()
    private val originalLooks = saved.layouts.mapValues { (_, l) -> l.look() }
    private val history = SkinEditHistory(
        SkinEditState(merge(original, start), mergeLooks(originalLooks, looks), images.filterKeys { knownImage(it) }),
    )

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

    private val state: SkinEditState get() = live ?: history.current
    val rects: SkinRects get() = state.rects
    val looks: Map<SkinLayoutId, SkinLook> get() = state.looks
    val images: SkinImageEdits get() = state.images
    val dirty: Boolean get() = rects != original || looks != originalLooks || images.isNotEmpty()
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** The layout being edited with its current rects and colours, or null when the skin has none for this form and rotation. */
    val layout: SkinLayout?
        get() {
            val id = layoutId ?: return null
            val base = saved.layouts[id] ?: return null
            val now = rects.getValue(id)
            return base.copy(elements = base.elements.map { e -> e.copy(rect = now.getValue(e.id)) }).withLook(looks.getValue(id))
        }

    /** Edits [form]'s layout in [orientation]; the selection is dropped. */
    fun show(form: SkinForm, orientation: SkinOrientation) {
        val id = SkinLayoutId.entries.first { it.form == form && it.orientation == orientation }
        val next = id.takeIf { it in saved.layouts }
        if (next != layoutId) {
            cancelGesture()
            selection = null
        }
        layoutId = next
    }

    /** The topmost control under ([x], [y]), within [slop] of its box; null for none. */
    fun hit(x: Float, y: Float, slop: Float = 0f): String? = layout?.elements?.lastOrNull { e ->
        val r = e.rect
        x >= r.x - slop && x <= r.right + slop && y >= r.y - slop && y <= r.bottom + slop
    }?.id

    fun tap(id: String) {
        val l = layout ?: return
        selection = SkinSelect.tap(l, selection, id)
    }

    fun clearSelection() {
        selection = null
    }

    /** Gamepad: the next (+1) or previous (-1) control or group. */
    fun step(by: Int) {
        val l = layout ?: return
        selection = SkinSelect.step(l, selection, by)
    }

    /** Gamepad: a group to its first control and back. */
    fun toggle() {
        val l = layout ?: return
        selection = SkinSelect.toggle(l, selection)
    }

    /** A drag starts on [id]: what it moves becomes the selection. */
    fun beginMove(id: String) {
        val l = layout ?: return
        val sel = SkinSelect.dragged(l, selection, id)
        selection = sel
        gestureFrom = current().filterKeys { it in sel.ids }
    }

    /** The drag is [dx], [dy] from where it began; [free] (Snap off, or a second finger down) skips the snapping. */
    fun moveBy(dx: Float, dy: Float, free: Boolean = false) {
        val from = gestureFrom ?: return
        val id = layoutId ?: return
        val size = saved.layouts.getValue(id).size
        val others = history.current.rects.getValue(id).filterKeys { it !in from }
        val result = SkinSnap.move(SkinSnap.bounds(from.values), others, size, dx, dy, snap && !free)
        guides = result.guides
        live = withLayout(id, from.mapValues { (_, r) -> r.copy(x = r.x + result.dx, y = r.y + result.dy) })
    }

    /** A pinch or the corner handle starts on the selection. */
    fun beginResize() {
        val sel = selection ?: return
        gestureFrom = current().filterKeys { it in sel.ids }
    }

    /**
     * The selection at [factor] times its size when the gesture began, on the nearest 5 % step of its first
     * control's saved size: returns that percentage.
     */
    fun resizeBy(factor: Float): Int? {
        val from = gestureFrom ?: return null
        val ref = selection?.ids?.first() ?: return null
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

    /** The selection's first control's size as a percentage of its saved size; null with nothing selected. */
    fun percent(): Int? {
        val ref = selection?.ids?.first() ?: return null
        val saved = savedRect(ref) ?: return null
        return SkinResize.percent(saved, current()[ref] ?: return null)
    }

    /** The Size tool: one step larger (+1) or smaller (-1). */
    fun sizeStep(by: Int) {
        val ref = selection?.ids?.first() ?: return
        val saved = savedRect(ref) ?: return
        val now = percent() ?: return
        val target = SkinResize.step((SkinResize.step(now.toFloat()) + by * SkinResize.STEP).toFloat())
        resizeTo(current().filterKeys { it in selection!!.ids }, ref, saved, target, live = false)
    }

    /** Moves the selection one grid step per unit of [stepsX], [stepsY]. */
    fun nudge(stepsX: Int, stepsY: Int) {
        val sel = selection ?: return
        val id = layoutId ?: return
        val moved = SkinSnap.nudge(current().filterKeys { it in sel.ids }, saved.layouts.getValue(id).size, stepsX, stepsY)
        history.push(withLayout(id, moved))
    }

    /** The selection back to its saved place and size. */
    fun reset() {
        val sel = selection ?: return
        val id = layoutId ?: return
        history.push(withLayout(id, original.getValue(id).filterKeys { it in sel.ids }))
    }

    /** The whole layout being edited back to how the skin has it. */
    fun resetLayout() {
        val id = layoutId ?: return
        history.push(withLayout(id, original.getValue(id)))
    }

    /**
     * Paints [part] of [target] ([ids]: the controls it paints) in [color], live until [endGesture] (a colour being
     * dragged on the picker is one undo step). A part drawn by an image file of its own loses the image: a colour
     * and an image are one fill, and the image would hide the colour (issue 786).
     */
    fun paint(target: SkinTarget, ids: List<String>, part: SkinPart, color: Int) {
        val id = layoutId ?: return
        val l = layout ?: return
        val base = history.current
        val look = SkinPainter.paint(base.looks.getValue(id), l, target, ids, part, color)
        live = base.copy(looks = base.looks + (id to look), images = dropImages(base.images, SkinPainter.files(id, l, ids, part)))
    }

    /** The icon on a toggled quick action, light or dark: one undo step. */
    fun tone(tone: SkinIconTone) {
        val id = layoutId ?: return
        val l = layout ?: return
        val base = history.current
        history.push(base.copy(looks = base.looks + (id to SkinPainter.tone(base.looks.getValue(id), l, tone))))
        cancelGesture()
    }

    /** The files of [staged] (path in the skin to the staged texture's name) replace those images: one undo step. */
    fun texture(staged: Map<String, String>) {
        val base = history.current
        history.push(base.copy(images = base.images + staged.filterKeys { knownImage(it) }.mapValues { SkinImageEdit.Staged(it.value) }))
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
    }

    fun redo() {
        cancelGesture()
        history.redo()
    }

    /** The rects that differ from the skin's, per layout. */
    fun edits(): SkinRects = rects.mapValues { (layout, now) ->
        now.filter { (id, r) -> original.getValue(layout)[id] != r }
    }.filterValues { it.isNotEmpty() }

    /** The colours of the layouts whose colours changed; [SkinLook.colors] holds only the controls that changed. */
    fun lookEdits(): Map<SkinLayoutId, SkinLook> = looks.filter { (id, look) -> look != originalLooks.getValue(id) }
        .mapValues { (id, look) -> look.copy(colors = look.colors.filter { (e, c) -> originalLooks.getValue(id).colors[e] != c }) }

    /** [json], the skin's skin.json, with the edited rects and colours written in. */
    fun json(json: String): String = SkinEditJson.patch(json, edits(), lookEdits())

    private fun current(): Map<String, SkinRect> = rects.getValue(layoutId!!)

    private fun savedRect(id: String): SkinRect? = layoutId?.let { original.getValue(it)[id] }

    private fun resizeTo(from: Map<String, SkinRect>, ref: String, saved: SkinRect, target: Int, live: Boolean) {
        val id = layoutId ?: return
        val scaled = SkinResize.scale(from, saved, ref, target, this.saved.layouts.getValue(id).size)
        if (live) this.live = withLayout(id, scaled) else history.push(withLayout(id, scaled))
    }

    /** The committed state with [changed] put into layout [id]'s rects. */
    private fun withLayout(id: SkinLayoutId, changed: Map<String, SkinRect>): SkinEditState {
        val now = history.current
        return now.copy(rects = now.rects + (id to now.rects.getValue(id) + changed))
    }

    /** [images] with [files] drawn in colours: a skin's own image is marked gone, a texture staged this session dropped. */
    private fun dropImages(images: SkinImageEdits, files: List<String>): SkinImageEdits {
        val out = images.toMutableMap()
        files.forEach { path -> if (path in existing) out[path] = SkinImageEdit.Removed else out.remove(path) }
        return out
    }

    /** Whether [path] is an image file of a layout and an element (or the background, the frame) [saved] has. */
    private fun knownImage(path: String): Boolean {
        val layoutId = SkinLayoutId.byId(path.substringBefore('/')) ?: return false
        val l = saved.layouts[layoutId] ?: return false
        val file = path.substringAfter('/')
        return file == l.background.imageFile || file == l.frame?.imageFile || l.elements.any { file in it.imageFiles }
    }

    private companion object {
        /** [base] with the rects of [draft] that name a layout and an element [base] has. */
        fun merge(base: SkinRects, draft: SkinRects?): SkinRects = if (draft == null) base else base.mapValues { (layout, rects) ->
            val d = draft[layout].orEmpty()
            rects.mapValues { (id, r) -> d[id] ?: r }
        }

        /** [base] with [draft]'s colours for the layouts and controls [base] has. */
        fun mergeLooks(base: Map<SkinLayoutId, SkinLook>, draft: Map<SkinLayoutId, SkinLook>?): Map<SkinLayoutId, SkinLook> =
            if (draft == null) base else base.mapValues { (layout, look) ->
                val d = draft[layout] ?: return@mapValues look
                SkinLook(d.background, d.frame?.takeIf { look.frame != null } ?: look.frame, look.colors.mapValues { (id, c) -> d.colors[id] ?: c })
            }
    }
}

/** Writes the editor's rects and colours into a skin.json, every other key as it was (issues 784, 786). */
object SkinEditJson {
    /**
     * [json] with each element of [edits] given its rect in whole units: at least 1 wide and tall, inside its layout;
     * and each layout of [looks] given its background, frame and controls' colours (a key left out when unset).
     * Elements and layouts the file does not have are skipped.
     */
    fun patch(json: String, edits: SkinRects, looks: Map<SkinLayoutId, SkinLook> = emptyMap()): String {
        val root = JSONObject(json.removePrefix("\uFEFF"))
        val layouts = root.getJSONObject("layouts")
        for ((layoutId, rects) in edits) {
            val layout = layouts.optJSONObject(layoutId.id) ?: continue
            val size = layout.getJSONObject("size")
            val w = size.getInt("width")
            val h = size.getInt("height")
            val elements = layout.optJSONArray("elements") ?: continue
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val r = rects[e.optString("id")] ?: continue
                val width = r.width.roundToInt().coerceIn(1, w)
                val height = r.height.roundToInt().coerceIn(1, h)
                e.put("x", r.x.roundToInt().coerceIn(0, w - width))
                e.put("y", r.y.roundToInt().coerceIn(0, h - height))
                e.put("width", width)
                e.put("height", height)
            }
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
                val parts = look.colors[e.optString("id")] ?: continue
                val type = SkinElementType.byId(e.optString("type"))
                val colors = JSONObject()
                // The type's order, and only the parts it takes.
                type?.colorParts?.forEach { part -> put(colors, part, parts[part]) }
                if (colors.length() > 0) e.put("colors", colors) else e.remove("colors")
            }
        }
        return root.toString(2) + "\n"
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
}
