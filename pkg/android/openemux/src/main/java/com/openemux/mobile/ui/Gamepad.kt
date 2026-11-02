package com.openemux.mobile.ui

import android.content.Intent
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.openemux.mobile.game.Gamepads
import com.openemux.mobile.game.PadInfo
import com.retroarch.R
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight

/** Whether the app is being driven by a gamepad: the focus highlight shows only then. */
object InputMode {
    /** Whether the last input was a pad press rather than a touch. */
    var lastInputPad by mutableStateOf(false)

    /** Whether a controller is connected (or a debug build says one is). */
    var padConnected by mutableStateOf(false)

    /**
     * Whether the gamepad look shows (the focus ring, the hint bar): always in the gamepad layout, otherwise
     * after a pad press until the next touch. Writing it records the last input: a press sets it, a touch clears it.
     */
    var gamepad: Boolean
        get() = gamepadLook(ControllerLayoutPref.value, padConnected, lastInputPad)
        set(value) { lastInputPad = value }

    /** The gamepad layout: a controller connected, or the setting says always. Larger targets and the L1/R1 pills follow it. */
    val padLayout: Boolean get() = padLayoutOn(ControllerLayoutPref.value, padConnected)

    /** Pad presses so far, so a screen can tell focus the player moved from focus the platform put somewhere. */
    var presses = 0L

    /** How many highlighted items hold focus now (0 or 1 in practice). */
    var focused = 0

    /** The restorable item that last held focus and is still on screen: where Right from the tab rail goes back to. */
    var last: FocusRequester? = null
}

/**
 * The family of the pad in use, for the button faces in the hints. One per process: the game
 * runs in a process of its own and fills its copy from the connected devices.
 */
object PadLabels {
    var detected by mutableStateOf(PadFamily.XBOX)
        private set
    var dualSense by mutableStateOf(false)
        private set

    private val tracker = PadFamilyTracker()

    private fun refresh() {
        val (family, dual) = detection(tracker.current)
        detected = family
        dualSense = dual
    }

    private fun padOf(device: InputDevice) = PadId(device.vendorId, device.productId, device.name)

    /** Only real gamepads name the buttons: a keyboard or `adb shell input` never does. */
    private fun isPad(device: InputDevice) = Gamepads.anyGamepad(listOf(PadInfo(device.sources, device.isVirtual)))

    /** Reads the connected pads again (on start, when the listener was not running). */
    fun scan() {
        tracker.reset(
            InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter(::isPad).associate { it.id to padOf(it) },
        )
        refresh()
    }

    /**
     * A key press from a real pad makes it the one that names the buttons. The event's source decides, as it does
     * for the gamepad layout: a pad that reports late has no gamepad source on its device yet.
     */
    fun pressed(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        val device = event.device ?: return
        if (device.isVirtual) return
        tracker.pressed(device.id, padOf(device))
        refresh()
    }

    val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            InputDevice.getDevice(deviceId)?.takeIf(::isPad)?.let { tracker.added(deviceId, padOf(it)); refresh() }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            val device = InputDevice.getDevice(deviceId)
            if (device != null && isPad(device)) tracker.changed(deviceId, padOf(device)) else tracker.removed(deviceId)
            refresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            tracker.removed(deviceId)
            refresh()
        }
    }

    /**
     * Debug builds: `DEBUG_GAMEPAD` with `--ei vendor <id>` (and `--ei product`, `--es name`) says which pad is in
     * use; `--ez clear true` goes back to the devices. The emulator has no pad of its own to read. Returns whether
     * the broadcast was only about the labels, so the caller leaves its connected override alone.
     */
    fun debug(intent: Intent): Boolean {
        tracker.override = when {
            intent.getBooleanExtra("clear", false) -> null
            intent.hasExtra("vendor") -> PadId(intent.getIntExtra("vendor", 0), intent.getIntExtra("product", 0), intent.getStringExtra("name"))
            else -> return false
        }
        refresh()
        return intent.hasExtra("vendor") && !intent.hasExtra("connected") && !intent.hasExtra("clear")
    }
}

/** The buttons a screen gives a meaning to, beyond moving focus, A and B. */
class GamepadActions(
    val l1: (() -> Unit)? = null,
    val r1: (() -> Unit)? = null,
    val x: (() -> Unit)? = null,
    val y: (() -> Unit)? = null,
    val select: (() -> Unit)? = null,
    val start: (() -> Unit)? = null,
    val hints: List<Pair<String, String>> = emptyList(),
    /** False while the screen part these buttons belong to does not hold focus: the ones below answer. */
    val enabled: () -> Boolean = { true },
)

/** The screens' actions, the one on top answering; the activity routes the buttons here. */
object GamepadBus {
    private val stack = mutableListOf<GamepadActions>()

    /** The hint bar of the screen on top. */
    var hints by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    fun push(actions: GamepadActions) { stack += actions; refresh() }
    fun pop(actions: GamepadActions) { stack.remove(actions); refresh() }

    /** Shows the hints of the top entry that answers now; called again when one turns on or off. */
    fun refresh() { hints = stack.lastOrNull { it.enabled() }?.hints.orEmpty() }

    /** Runs the top screen's handler for [pick], falling back to the ones below it. */
    fun dispatch(pick: (GamepadActions) -> (() -> Unit)?): Boolean {
        for (actions in stack.asReversed()) {
            if (!actions.enabled()) continue
            pick(actions)?.let { it(); return true }
        }
        return false
    }
}

/** Registers this screen's buttons while it is on screen. */
@Composable
fun GamepadButtons(
    hints: List<Pair<String, String>> = emptyList(),
    l1: (() -> Unit)? = null,
    r1: (() -> Unit)? = null,
    x: (() -> Unit)? = null,
    y: (() -> Unit)? = null,
    select: (() -> Unit)? = null,
    start: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val latest = rememberUpdatedState(listOf(l1, r1, x, y, select, start))
    val on = rememberUpdatedState(enabled)
    LaunchedEffect(enabled) { GamepadBus.refresh() }
    DisposableEffect(Unit) {
        // Which buttons a screen uses is fixed; what they do follows recomposition.
        fun slot(i: Int, used: Boolean): (() -> Unit)? = if (used) ({ latest.value[i]?.invoke() }) else null
        val actions = GamepadActions(
            l1 = slot(0, l1 != null), r1 = slot(1, r1 != null), x = slot(2, x != null),
            y = slot(3, y != null), select = slot(4, select != null), start = slot(5, start != null),
            hints = hints, enabled = { on.value },
        )
        GamepadBus.push(actions)
        onDispose { GamepadBus.pop(actions) }
    }
}


/** The focus ring's stroke, its gap outside the item and the soft halo around it. */
private val FocusRing = 3.dp
private val FocusGap = 3.dp
private val FocusHalo = 6.dp

/**
 * How far the highlight reaches outside its item (gap, ring and halo). A
 * scroll container leaves at least this, plus its items' scale-up, as
 * padding on its scroll axis, or it cuts the ring.
 */
val FocusOutset = 12.dp

/**
 * Focus target sizes, the touch one and the larger one for the gamepad layout, where anything focusable is at
 * least [PadTarget] tall so the ring has a real target. The touch layout keeps its sizes.
 */
val PadTarget = 44.dp
val ChipHeight = 36.dp
val ChipHeightPad = 40.dp
val ToggleSize = DpSize(40.dp, 32.dp)
val ToggleSizePad = DpSize(44.dp, 40.dp)
val SwatchSize = 38.dp
val SwatchSizePad = PadTarget
val PillHeight = 36.dp
val PillHeightPad = 40.dp

/** [touch] in the touch layout, [pad] in the gamepad layout. */
fun <T> padSize(touch: T, pad: T): T = if (InputMode.padLayout) pad else touch

/**
 * A text link in the gamepad layout: at least [PadTarget] tall, its text centred in it, with room on the sides.
 * Goes before the link's own padding, after its click.
 */
fun Modifier.linkTarget(): Modifier =
    if (InputMode.padLayout) heightIn(min = PadTarget).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 6.dp) else this

/** The room a focused item keeps from the edges of a vertical list. */
val FocusMargin = 24.dp

/** How far a list's end fades into the page while more of it can scroll in. */
val EndFade = 44.dp

/** The hint bar's height, its divider included. */
val HintBarHeight = 40.dp

/**
 * What the hint bar covers at the bottom of the screen: it shows in gamepad mode, so nothing outside it.
 * In tabletop it sits on the bottom half, under the deck, and covers nothing on the top one.
 */
val hintBarInset: Dp
    @Composable get() = if (InputMode.gamepad && !LocalTabletop.current) HintBarHeight else 0.dp

/**
 * The focus highlight from the design: a 3dp ring 3dp outside the item with
 * a soft halo, an optional scale-up, and the item drawn over its neighbours.
 * [inset] draws it inside instead, with a lifted background, for rows in a
 * card. [onFocus] tells the item when it shows the highlight. Only shown in
 * gamepad mode.
 */
fun Modifier.gamepadFocus(
    corner: Dp = 14.dp,
    scale: Float = 1f,
    inset: Boolean = false,
    onFocus: ((Boolean) -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val shown = focused && InputMode.gamepad
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(120), label = "focus")
    val grow by animateFloatAsState(if (shown) scale else 1f, tween(120), label = "grow")
    if (onFocus != null) {
        val report = rememberUpdatedState<(Boolean) -> Unit>(onFocus)
        LaunchedEffect(shown) { report.value(shown) }
    }
    // An item that leaves while focused takes its count with it.
    DisposableEffect(Unit) { onDispose { if (focused) InputMode.focused -= 1 } }
    // Known to its window, so a press that finds nothing focused can land here.
    val target = remember { PadFocusTarget() }
    val scope = LocalPadScope.current
    this
        // In the gamepad layout a tap moves the focus to the item tapped, so the next press starts there; the
        // touch itself goes on to the item untouched. A drag (a scroll, a fling) leaves the focus where it was.
        // The touch put the window in touch mode, where a clickable takes no focus: the scope takes it out first.
        .then(if (InputMode.padLayout) Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragged = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) dragged = true
                } while (event.changes.any { it.pressed })
                if (!dragged) scope.focus(target)
            }
        } else Modifier)
        .padTarget(target)
        .zIndex(if (shown) 1f else 0f)
        .onFocusChanged {
            val now = it.hasFocus || it.isFocused
            if (now != focused) InputMode.focused += if (now) 1 else -1
            focused = now
        }
        .graphicsLayer { scaleX = grow; scaleY = grow }
        .drawWithContent {
            if (inset && alpha > 0f) {
                drawRoundRect(Oe.Raised.copy(alpha = alpha), cornerRadius = CornerRadius(corner.toPx()))
            }
            drawContent()
            if (alpha > 0f) {
                val ring = (if (inset) 2.5.dp else FocusRing).toPx()
                val off = if (inset) -ring / 2f else FocusGap.toPx() + ring / 2f
                val r = corner.toPx() + off
                if (!inset) {
                    val halo = FocusHalo.toPx()
                    val out = off + ring / 2f + halo / 2f
                    drawRoundRect(
                        Oe.AccentBright.copy(alpha = 0.28f * alpha),
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + out * 2, size.height + out * 2),
                        cornerRadius = CornerRadius(corner.toPx() + out),
                        style = Stroke(halo),
                    )
                }
                drawRoundRect(
                    Oe.AccentText.copy(alpha = alpha),
                    topLeft = Offset(-off, -off),
                    size = Size(size.width + off * 2, size.height + off * 2),
                    cornerRadius = CornerRadius(r.coerceAtLeast(0f)),
                    style = Stroke(ring),
                )
            }
        }
}

/**
 * A control for the finger only: never a focus stop for the D-pad, nor where a pad press lands. For the
 * on-screen back arrows, which B already does: focused, they turned A into "back" against the hint bar.
 */
fun Modifier.touchOnly(): Modifier = focusProperties { canFocus = false }

/** Focus scrolling that answers with [pick] in gamepad mode and leaves touch to the platform's [fallback]. */
@OptIn(ExperimentalFoundationApi::class)
private class FocusScrollSpec(
    private val fallback: BringIntoViewSpec,
    private val pick: (offset: Float, size: Float, containerSize: Float) -> Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (InputMode.gamepad) pick(offset, size, containerSize)
        else fallback.calculateScrollDistance(offset, size, containerSize)
}

/**
 * The scroll containers in [content] keep the gamepad's focused item in the
 * middle; the ends of the list still rest against its padding. For the
 * sideways rows of games. A scrollable nested inside provides its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CenteredFocusScroll(content: @Composable () -> Unit) {
    val outer = LocalBringIntoViewSpec.current
    val spec = remember(outer) { FocusScrollSpec(outer, FocusGeometry::centered) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * The scroll containers in [content] keep the gamepad's focused item
 * [leading] from their start and [trailing] from their end, scrolling only
 * when it leaves that band. A vertical list adds what covers its bottom (the
 * tab bar, [hintBarInset]) to [trailing]; a row nested inside a list provides
 * its own, or it inherits the list's.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarginFocusScroll(leading: Dp = FocusMargin, trailing: Dp = FocusMargin, content: @Composable () -> Unit) {
    val outer = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val start = with(density) { leading.toPx() }
    val end = with(density) { trailing.toPx() }
    val spec = remember(outer, start, end) {
        FocusScrollSpec(outer) { offset, size, container -> FocusGeometry.margin(offset, size, container, start, end) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Keeps gamepad focus in this list when it is pushed past the last item in
 * [direction]. The margin leaves room between the last item and what lies
 * beyond it (the tab bar, the header), and the focus search would otherwise
 * jump there; the last item holds focus instead, as when it touched the edge.
 * In tabletop the bottom half lies past the end going down, so down lets go.
 */
@Composable
fun Modifier.holdFocusAtEnd(direction: FocusDirection): Modifier {
    val tabletop = LocalTabletop.current
    return focusProperties {
        onExit = { if (holdsFocusAtEnd(direction, requestedFocusDirection, InputMode.gamepad, tabletop)) cancelFocusChange() }
    }.focusGroup()
}

/** Whether a list held in [held] keeps focus pushed in [requested]: in tabletop, down reaches the deck and the tabs. */
fun holdsFocusAtEnd(held: FocusDirection, requested: FocusDirection, gamepad: Boolean, tabletop: Boolean) =
    gamepad && requested == held && !(tabletop && held == FocusDirection.Down)

/**
 * While [closed], the gamepad's D-pad never moves focus into this group: it
 * stays where it was. Touch is unaffected. The library uses it to keep focus
 * on the games while selecting, off the controls that the face buttons cover.
 */
fun Modifier.gamepadFence(closed: Boolean): Modifier =
    focusProperties {
        onEnter = { if (blocksGamepadMove(closed, requestedFocusDirection, InputMode.gamepad)) cancelFocusChange() }
    }.focusGroup()

/** While [held], the gamepad's D-pad never moves focus out of this group. */
fun Modifier.holdGamepadFocus(held: Boolean): Modifier =
    focusProperties {
        onExit = { if (blocksGamepadMove(held, requestedFocusDirection, InputMode.gamepad)) cancelFocusChange() }
    }.focusGroup()

/**
 * Whether a fence or a hold that is [on] stops a move in [requested]: only the
 * D-pad's, so a requestFocus from code (which arrives as Enter) still goes through.
 */
fun blocksGamepadMove(on: Boolean, requested: FocusDirection, gamepad: Boolean) =
    on && gamepad && requested != FocusDirection.Enter && requested != FocusDirection.Exit

/**
 * Lays its content out at its natural width and, when that is wider than the
 * room it has, shrinks it to fit rather than pushing the ends off screen.
 */
internal fun Modifier.shrinkToFit(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val room = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val scale = if (placeable.width > room) room.toFloat() / placeable.width else 1f
    val width = minOf(placeable.width, room).coerceAtLeast(constraints.minWidth)
    layout(width, placeable.height) {
        val x = (width - placeable.width) / 2
        if (scale < 1f) placeable.placeWithLayer(x, 0) { scaleX = scale; scaleY = scale } else placeable.place(x, 0)
    }
}

/**
 * A row that scrolls sideways under the gamepad without cutting the first
 * and last items' ring: it reaches [bleed] into its parent's side padding
 * and pads its content by as much inside the scroll, so the content lines up
 * with the rest of the screen while the ring has room at both ends. With
 * [fadeEnd], its end fades into the page while more can scroll in.
 */
@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    bleed: Dp = 20.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    fadeEnd: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val page = Oe.Bg
    // With the fade, a focused item stops short of it instead of sitting under it.
    MarginFocusScroll(leading = bleed, trailing = if (fadeEnd) maxOf(bleed, EndFade) else bleed) {
        Row(
            modifier.bleed(bleed).fillMaxWidth()
                .then(if (fadeEnd) Modifier.drawWithContent {
                    drawContent()
                    if (scroll.canScrollForward) {
                        val fade = minOf(EndFade.toPx(), size.width)
                        drawRect(
                            Brush.horizontalGradient(listOf(page.copy(alpha = 0f), page), startX = size.width - fade, endX = size.width),
                            topLeft = Offset(size.width - fade, 0f), size = Size(fade, size.height),
                        )
                    }
                } else Modifier)
                .horizontalScroll(scroll).padding(horizontal = bleed),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}

/** Widens the layout by [x] on each side, past its parent's padding. */
internal fun Modifier.bleed(x: Dp): Modifier = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedWidth) (x * 2).roundToPx() else 0
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Remembers this item as the screen's focused one in [saved] and, when the
 * screen comes back in gamepad mode, focuses it again; [initial] marks the
 * item that takes focus when nothing was saved, or when the screen sets
 * [saved] back to null (the focused item is gone).
 */
fun Modifier.restorableFocus(key: String, saved: MutableState<String?>, initial: Boolean = false): Modifier = composed {
    val requester = remember { FocusRequester() }
    // Derived, so a focus move (non-null to non-null) recomposes nothing, and read only by
    // the initial item, so the others do not recompose when it flips.
    val forgotten = remember(saved) { derivedStateOf { saved.value == null } }
    val screens = LocalNavFocus.current
    // The pad presses when the item came in: only focus no press has moved since the screen opened is the
    // platform's to take over, not focus after a later press (A on a filter chip, the press that wakes the pad).
    val openedAt = remember { InputMode.presses }
    // Also when the gamepad wakes up with nothing focused, or when the screen forgets
    // its focused item (it was removed), so focus is never missing.
    LaunchedEffect(InputMode.gamepad, initial && forgotten.value) {
        if (!InputMode.gamepad) return@LaunchedEffect
        if (saved.value == key) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        } else if (saved.value == null && initial) {
            // Let a screen or page transition hand its focus over first.
            delay(150)
            if (takesInitialFocus(InputMode.focused, screens?.held, InputMode.presses != openedAt)) runCatching { requester.requestFocus() }
        }
    }
    DisposableEffect(requester) { onDispose { if (InputMode.last === requester) InputMode.last = null } }
    // The screen's initial item is where a press lands when nothing on screen holds focus.
    val target = remember(requester) { PadFocusTarget(initial, requester) }
    target.initial = initial
    this.focusRequester(requester).padTarget(target, attach = false).onFocusChanged {
        if (it.isFocused || it.hasFocus) {
            saved.value = key
            InputMode.last = requester
        }
    }
}

/** Whether focus is somewhere inside one part of the tree, written by [tracksFocus]. */
class FocusFlag { var held = false }

/** Keeps [flag] up to date with whether anything below holds focus. */
fun Modifier.tracksFocus(flag: FocusFlag): Modifier = onFocusChanged { flag.held = it.hasFocus }

/**
 * Whether focus is in the NavHost's screens (not the tabs, the rail or the deck); null outside the NavHost's
 * composition (the deck). A sheet or dialog opened from a screen inherits it, so there it reads the screens' focus.
 */
val LocalNavFocus = compositionLocalOf<FocusFlag?> { null }

/**
 * Whether a screen's initial item takes the gamepad's focus as the screen opens: when nothing holds
 * focus, and also when the focus in the screens was put there by the platform, with no press since
 * the screen opened ([pressed] false). When A opens a screen, the item it pressed leaves with the old
 * one, Compose clears focus and the platform enters the window again on the first item at the top
 * left: the new screen's back arrow or search, against the hint bar (issue 701). Focus on the tabs or
 * the rail stays there, and so does focus the player has moved.
 */
fun takesInitialFocus(focused: Int, inScreens: Boolean?, pressed: Boolean): Boolean =
    focused == 0 || (inScreens == true && !pressed)

/** Where the rows of a screen remember the one that held focus; null outside such a screen. */
val LocalRowFocus = compositionLocalOf<MutableState<String?>?> { null }

/**
 * The rows in [content] (ListRow, SwitchRow, SettingTile) remember which one
 * held focus, and focus it again when the screen comes back in gamepad mode.
 */
@Composable
fun RememberRowFocus(content: @Composable () -> Unit) {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalRowFocus provides saved, content = content)
}

/** A row restorable under its screen's [RememberRowFocus], by [key]. */
fun Modifier.rowFocus(key: String): Modifier = composed {
    val saved = LocalRowFocus.current
    if (saved == null) this else restorableFocus(key, saved)
}

/** The gamepad hint bar: badge + label pairs, only in gamepad mode. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AnimatedVisibility(InputMode.gamepad, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
        Column(Modifier.fillMaxWidth().background(Oe.Bg.copy(alpha = 0.94f))) {
            HorizontalDivider(color = Oe.Surface2, thickness = 1.dp)
            // A screen with many hints on a narrow phone shrinks them all rather than lose the last one.
            Box(Modifier.fillMaxWidth().height(HintBarHeight - 1.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Row(
                Modifier.shrinkToFit(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                hints.forEach { (badge, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PadBadge(badge)
                        Text(label, style = Type.Caption.copy(fontSize = 12.sp, color = Oe.Muted), maxLines = 1, softWrap = false)
                    }
                }
            } }
        }
    }
}

/** The faces the pad in use (or the forced family) prints for [token], as one badge shows them. */
fun padFaces(token: String): List<Face> {
    val pref = ButtonLabelsPref.value
    return badgeFaces(faces(token, effectiveFamily(pref, PadLabels.detected), effectiveDualSense(pref, PadLabels.dualSense)))
}

/**
 * One hint badge: the face the pad in use prints on the button at [token]'s position. The confirm
 * position (the bottom button, token "A") keeps the accent fill whatever its face reads.
 */
@Composable
fun PadBadge(token: String) {
    val shown = padFaces(token)
    val confirm = token == "A"
    val color = if (confirm) Color.White else Oe.Text
    val text = (shown.singleOrNull() as? Face.Text)?.text
    Box(
        Modifier.height(22.dp).widthIn(min = 22.dp).background(if (confirm) Oe.AccentBright else Oe.Raised, RoundedCornerShape(11.dp))
            .padding(horizontal = if (shown.size > 1 || (text?.length ?: 1) > 1) 7.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = Type.Tag.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        if (text != null) Text(text, style = style)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { face ->
                when (face) {
                    is Face.Glyph -> PadGlyph(face.kind, color, 11.dp)
                    is Face.Text -> Text(face.text, style = style)
                }
            }
        }
    }
}

/** A drawn button face, stroked in [color]: never a font glyph, whose coverage varies by device. */
@Composable
fun PadGlyph(kind: GlyphKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(
        when (kind) {
            GlyphKind.CROSS -> R.string.pad_glyph_cross
            GlyphKind.CIRCLE -> R.string.pad_glyph_circle
            GlyphKind.SQUARE -> R.string.pad_glyph_square
            GlyphKind.TRIANGLE -> R.string.pad_glyph_triangle
            GlyphKind.MENU -> R.string.pad_glyph_menu
            GlyphKind.VIEW -> R.string.pad_glyph_view
        },
    )
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val u = this.size.width / 11f
        val stroke = Stroke(width = (if (kind == GlyphKind.CROSS) 1.8f else 1.6f) * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke.width, StrokeCap.Round)
        when (kind) {
            GlyphKind.CROSS -> { line(1.5f, 1.5f, 9.5f, 9.5f); line(9.5f, 1.5f, 1.5f, 9.5f) }
            GlyphKind.CIRCLE -> drawCircle(color, radius = 4.5f * u, center = Offset(5.5f * u, 5.5f * u), style = stroke)
            GlyphKind.SQUARE -> drawRect(color, Offset(1.3f * u, 1.3f * u), Size(8.4f * u, 8.4f * u), style = stroke)
            GlyphKind.TRIANGLE -> drawPath(
                Path().apply { moveTo(5.5f * u, 1f * u); lineTo(10f * u, 9.5f * u); lineTo(1f * u, 9.5f * u); close() }, color, style = stroke,
            )
            GlyphKind.MENU -> { line(1.5f, 2.5f, 9.5f, 2.5f); line(1.5f, 5.5f, 9.5f, 5.5f); line(1.5f, 8.5f, 9.5f, 8.5f) }
            GlyphKind.VIEW -> {
                drawRect(color, Offset(4f * u, 1f * u), Size(6f * u, 6f * u), style = stroke)
                drawRect(color, Offset(1f * u, 4f * u), Size(6f * u, 6f * u), style = stroke)
            }
        }
    }
}

/**
 * The four face buttons of a [family] as a small diamond, for the "Button labels" choices:
 * top, left, right and bottom, the bottom one in the accent. Decorative; the card's text says it.
 */
@Composable
fun FaceDiamond(family: PadFamily) {
    Box(Modifier.size(44.dp).clearAndSetSemantics {}) {
        listOf("Y" to Offset(14.5f, 0f), "X" to Offset(0f, 14.5f), "B" to Offset(29f, 14.5f), "A" to Offset(14.5f, 29f)).forEach { (token, at) ->
            val confirm = token == "A"
            val color = if (confirm) Color.White else Oe.Text
            Box(
                Modifier.offset(at.x.dp, at.y.dp).size(15.dp).background(if (confirm) Oe.AccentBright else Oe.Raised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (val face = faces(token, family, false).single()) {
                    is Face.Glyph -> PadGlyph(face.kind, color, 8.dp)
                    is Face.Text -> Text(face.text, style = Type.Tag.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.sp))
                }
            }
        }
    }
}
