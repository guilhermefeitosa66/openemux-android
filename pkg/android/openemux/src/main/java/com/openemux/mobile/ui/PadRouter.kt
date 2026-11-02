package com.openemux.mobile.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** The buttons the screens give a meaning to, beyond moving focus, A and B. */
enum class PadButton { L1, R1, X, Y, SELECT, START }

/** What a pad press does, from the press and the focus alone. */
sealed interface PadDecision {
    /** Not from a pad: the window handles it as before, and the input mode stays as it is. */
    data object NotPad : PadDecision

    /** A pad key the focus system takes as it is: a D-pad move, the center, a key no screen uses. */
    data object Pass : PadDecision

    /** A, sent on as the D-pad center, so the focused item clicks. */
    data object AsCenter : PadDecision

    /** B goes back on its release; its press is taken so nothing else sees it. */
    data class Back(val act: Boolean) : PadDecision

    /** A screen button, run on its release from the screen on top; its press is taken. */
    data class Bus(val button: PadButton, val act: Boolean) : PadDecision

    /** Nothing visible holds focus: this press lands it, visibly, and does nothing else. */
    data object Land : PadDecision

    /** The release of the press that landed focus: taken, so it clicks or moves nothing. */
    data object Swallow : PadDecision
}

private val DPAD = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
)

/** The gamepad's button codes, from A to Mode and the generic 1 to 16 (KeyEvent.isGamepadButton, testable). */
fun isPadButton(code: Int) =
    code in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE ||
        code in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16

/**
 * What the pad press [code] ([action] down or up) does. [fromPad] says the
 * event came from a gamepad or D-pad device; [focusShown] whether an item on
 * screen holds focus in the window that gets it; [landed] the key whose press
 * last landed focus, so its release is taken too. No press is swallowed
 * silently: one that finds nothing to act on lands focus where it shows.
 */
fun decidePadKey(code: Int, action: Int, fromPad: Boolean, focusShown: Boolean, landed: Int): PadDecision {
    if (!fromPad && !isPadButton(code) && code !in DPAD) return PadDecision.NotPad
    val up = action == KeyEvent.ACTION_UP
    if (up && code == landed) return PadDecision.Swallow
    val bus = when (code) {
        KeyEvent.KEYCODE_BUTTON_L1 -> PadButton.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> PadButton.R1
        KeyEvent.KEYCODE_BUTTON_X -> PadButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> PadButton.Y
        KeyEvent.KEYCODE_BUTTON_SELECT -> PadButton.SELECT
        KeyEvent.KEYCODE_BUTTON_START -> PadButton.START
        else -> null
    }
    return when {
        code == KeyEvent.KEYCODE_BUTTON_B -> PadDecision.Back(up)
        bus != null -> PadDecision.Bus(bus, up)
        code != KeyEvent.KEYCODE_BUTTON_A && code !in DPAD -> PadDecision.Pass
        !focusShown && !up -> PadDecision.Land
        code == KeyEvent.KEYCODE_BUTTON_A -> PadDecision.AsCenter
        else -> PadDecision.Pass
    }
}

/** The screen action [button] runs, from one screen's set. */
fun GamepadActions.handler(button: PadButton): (() -> Unit)? = when (button) {
    PadButton.L1 -> l1
    PadButton.R1 -> r1
    PadButton.X -> x
    PadButton.Y -> y
    PadButton.SELECT -> select
    PadButton.START -> start
}

/**
 * The D-pad key the left stick (or, when the stick rests, the hat) points to,
 * or 0 when both rest: a half tilt is the dead zone.
 */
fun stickKey(x: Float, y: Float, hatX: Float, hatY: Float): Int {
    val h = if (kotlin.math.abs(x) > 0.5f) x else hatX
    val v = if (kotlin.math.abs(y) > 0.5f) y else hatY
    return when {
        h < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
        h > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
        v < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
        v > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
        else -> 0
    }
}

/** When a held stick repeats its key: a pause on the first move, then a steady repeat. */
fun stickDelay(sameKey: Boolean): Long = if (sameKey) 90L else 380L

/** One candidate for where a landing press puts focus: its centre in the window when it shows. */
data class LandingSpot(val shown: Boolean, val initial: Boolean, val lastFocus: Long, val x: Float = 0f, val y: Float = 0f)

/**
 * Where a press that finds nothing focused lands: indexes into [spots], best
 * first, the next one tried when an item turns focus down. The item that held
 * focus last, if it still shows; then, when [anchor] says where that item was
 * on screen, the showing items nearest that place (the user scrolled it away:
 * focus stays where the eye was); then the screen's initial item, if it
 * shows; then the showing ones in reading order; then the initial item and
 * the last focused, wherever they are.
 */
fun rankLanding(spots: List<LandingSpot>, anchor: Pair<Float, Float>? = null): List<Int> {
    val indexed = spots.withIndex()
    val latest = indexed.filter { it.value.lastFocus > 0 }.maxByOrNull { it.value.lastFocus }
    val shown = indexed.filter { it.value.shown }
    val order = mutableListOf<Int>()
    if (latest != null && latest.value.shown) order += latest.index
    if (anchor != null) {
        val (ax, ay) = anchor
        order += shown.sortedBy { (it.value.x - ax).let { d -> d * d } + (it.value.y - ay).let { d -> d * d } }.map { it.index }
    }
    order += shown.filter { it.value.initial }.map { it.index }
    order += shown.sortedWith(compareBy({ it.value.y }, { it.value.x })).map { it.index }
    order += indexed.filter { it.value.initial }.map { it.index }
    if (latest != null) order += latest.index
    return order.distinct()
}

/** An item that can take the gamepad's focus, in one window. */
class PadFocusTarget(var initial: Boolean = false, val requester: FocusRequester = FocusRequester()) {
    var coords: LayoutCoordinates? = null
    var focused = false

    /** When it last took focus (a tick of its scope), 0 if never. */
    var lastFocus = 0L

    /** Its centre in the window while any of it shows there, else null: an item scrolled out of its list does not. */
    fun shownCenter(): Pair<Float, Float>? {
        val c = coords?.takeIf { it.isAttached } ?: return null
        val b = runCatching { c.boundsInWindow() }.getOrNull() ?: return null
        return if (b.width >= 1f && b.height >= 1f) b.center.x to b.center.y else null
    }

    /** Whether any of it shows in its window. */
    fun shown(): Boolean = shownCenter() != null
}

/**
 * The focusable items of one window (the activity's, a sheet's, a dialog's
 * or a popup's), so a press that finds nothing focused there knows where to
 * land, and a window that closes knows where to give focus back.
 */
class PadScope {
    private val targets = mutableListOf<PadFocusTarget>()
    private var tick = 0L

    /** Whether anything in the window holds focus, a control the scope does not know included. */
    var rootHasFocus = false

    /**
     * The window left touch mode with nothing focused, on a pad press: the router lands focus with that press
     * (its press, or only its release when the platform spent the press), and nothing else may take it first.
     */
    var touchExit = false

    /** The window's Compose view, taken out of touch mode before focus lands (A does not leave it on its own). */
    var view: View? = null

    fun add(target: PadFocusTarget) { targets += target }
    fun remove(target: PadFocusTarget) { targets -= target }

    /** Where the item that held focus last was in the window, while it held it. */
    var anchor: Pair<Float, Float>? = null
        private set

    /** Whether focus left since the last gain: the targets that gain it together (an item's own two) share a tick. */
    private var moved = true

    fun focusChanged(target: PadFocusTarget, now: Boolean) {
        target.focused = now
        if (!now) { moved = true; return }
        if (moved) { tick++; moved = false }
        target.lastFocus = tick
        target.shownCenter()?.let { anchor = it }
    }

    /** The focused item moved (its list scrolled to it): the anchor follows it while it shows. */
    fun placed(target: PadFocusTarget) {
        if (target.focused) target.shownCenter()?.let { anchor = it }
    }

    /** Whether an item that shows holds focus; one the scope does not know counts as showing. */
    fun focusShown(): Boolean {
        val held = targets.firstOrNull { it.focused } ?: return rootHasFocus
        return held.shown()
    }

    /** The item that holds focus, else the last one that did. */
    fun current(): PadFocusTarget? = targets.firstOrNull { it.focused } ?: targets.filter { it.lastFocus > 0 }.maxByOrNull { it.lastFocus }

    /**
     * [list] as landing candidates. Only the item that held focus last counts as such: when it is gone
     * (scrolled out of its list and disposed), an older one that still shows must not take its place ahead
     * of the anchor, or a press after a finger scroll lands on the header the user left long ago.
     */
    internal fun spots(list: List<PadFocusTarget>): List<LandingSpot> = list.map { t ->
        val c = t.shownCenter()
        LandingSpot(c != null, t.initial, if (t.lastFocus == tick) t.lastFocus else 0L, c?.first ?: 0f, c?.second ?: 0f)
    }

    /** Puts focus on the first item of [rankLanding] that takes it; false when none does. */
    fun land(): Boolean {
        val list = targets.toList()
        return rankLanding(spots(list), anchor).any { focus(list[it]) }
    }

    /** Puts focus on [target], out of touch mode first: there the items are not focusable, and only a D-pad key leaves it. */
    fun focus(target: PadFocusTarget): Boolean {
        view?.let { v -> if (v.isInTouchMode) runCatching { v.requestFocusFromTouch() } }
        // Leaving touch mode here is not a press to wait for (touchExit), or the root would turn this request down.
        touchExit = false
        return runCatching { target.requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
    }
}

/** The window the composables below belong to: the activity's unless a sheet, dialog or popup provides its own. */
val LocalPadScope = compositionLocalOf { PadScope() }

/**
 * Applies [decidePadKey] in one window: the activity, or a sheet's or
 * dialog's. [back] is that window's back (B), [scope] its focusable items,
 * [bus] what a screen button does there (the screen on top's action).
 */
class PadRouter(
    private val scope: () -> PadScope,
    private val bus: (PadButton) -> Unit = { b -> GamepadBus.dispatch { it.handler(b) } },
    private val back: () -> Unit,
) {
    private var landed = 0
    private var stickKey = 0
    private var stickNext = 0L

    /** A touch hands the screen back to the finger: the highlight goes until the next pad press. */
    fun touch(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) InputMode.gamepad = false
    }

    fun key(event: KeyEvent, dispatch: (KeyEvent) -> Boolean): Boolean {
        val code = event.keyCode
        val fromPad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_DPAD)
        val s = scope()
        val decision = decidePadKey(code, event.action, fromPad, s.focusShown(), landed)
        if (decision == PadDecision.NotPad) { s.touchExit = false; return dispatch(event) }
        InputMode.gamepad = true
        if (event.action == KeyEvent.ACTION_DOWN) { InputMode.presses++; InputMode.gainsAtPress = InputMode.focusGains }
        if (s.touchExit) {
            // The press that took the window out of touch mode only lands focus.
            s.touchExit = false
            if (event.action == KeyEvent.ACTION_DOWN) landed = code
            s.land()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && code != landed) landed = 0
        return when (decision) {
            PadDecision.AsCenter -> dispatch(
                KeyEvent(
                    event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, InputDevice.SOURCE_DPAD,
                ),
            )
            is PadDecision.Back -> { if (decision.act && !hideKeyboard(s.view)) back(); true }
            is PadDecision.Bus -> { if (decision.act) bus(decision.button); true }
            // Nothing the scope knows takes focus: the press goes on, so the focus system lands it itself.
            PadDecision.Land -> { s.touchExit = false; if (s.land()) { landed = code; true } else dispatch(event) }
            PadDecision.Swallow -> { landed = 0; true }
            else -> dispatch(event)
        }
    }

    /** The left stick and the hat move focus like the D-pad, with a key-repeat feel. */
    fun motion(ev: MotionEvent, dispatch: (MotionEvent) -> Boolean, dispatchKey: (KeyEvent) -> Boolean): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) || ev.actionMasked != MotionEvent.ACTION_MOVE) return dispatch(ev)
        val key = stickKey(
            ev.getAxisValue(MotionEvent.AXIS_X), ev.getAxisValue(MotionEvent.AXIS_Y),
            ev.getAxisValue(MotionEvent.AXIS_HAT_X), ev.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        val now = SystemClock.uptimeMillis()
        if (key == 0) { stickKey = 0; return true }
        if (key != stickKey || now >= stickNext) {
            stickNext = now + stickDelay(key == stickKey)
            stickKey = key
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, 0, ev.deviceId, 0, 0, InputDevice.SOURCE_DPAD)
            dispatchKey(down)
            dispatchKey(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
        }
        return true
    }
}

/** B with the keyboard up closes the keyboard first, as the system back does; true when it did. */
private fun hideKeyboard(view: View?): Boolean {
    if (view == null) return false
    val shown = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    if (!shown) return false
    val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return false
    imm.hideSoftInputFromWindow(view.windowToken, 0)
    return true
}

/** A dialog window's callback with the pad's handling in front; everything else goes to [original]. */
private class PadCallback(private val original: Window.Callback, private val router: PadRouter) : Window.Callback by original {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = router.key(event) { original.dispatchKeyEvent(it) }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean { router.touch(event); return original.dispatchTouchEvent(event) }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        router.motion(event, { original.dispatchGenericMotionEvent(it) }, ::dispatchKeyEvent)
}

/** Registers this item with its window's [PadScope], as one a landing press can put focus on. */
internal fun Modifier.padTarget(target: PadFocusTarget, attach: Boolean = true): Modifier = composed {
    val scope = LocalPadScope.current
    DisposableEffect(scope, target) {
        scope.add(target)
        onDispose { scope.remove(target); target.coords = null }
    }
    (if (attach) focusRequester(target.requester) else this)
        .onPlaced { target.coords = it; scope.placed(target) }
        .onFocusChanged { scope.focusChanged(target, it.isFocused || it.hasFocus) }
}

/**
 * A single-line field keeps the D-pad's up and down for its cursor, and the
 * pad could never leave it: in gamepad mode they move focus out instead.
 */
fun Modifier.leaveFieldVertically(): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { e ->
        val dir = when (e.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> null
        }
        if (dir == null || !InputMode.gamepad) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown) focus.moveFocus(dir)
        true
    }
}

/**
 * A screen of plain text has nothing for the D-pad to move to: the text
 * takes focus, and up and down scroll it by most of a screen, so the pad can
 * read it all. B still goes back.
 */
fun Modifier.padScrollsText(scroll: ScrollState): Modifier = composed {
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }
    // The text itself holds focus, so it always shows while it scrolls (a link above it would scroll away).
    val target = remember { PadFocusTarget(initial = true) }
    LaunchedEffect(InputMode.gamepad) {
        if (!InputMode.gamepad) return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requester.requestFocus() }
    }
    padTarget(target).focusable().onSizeChanged { height = it.height }.onPreviewKeyEvent { e ->
        val step = textScrollStep(e.key == Key.DirectionDown, e.key == Key.DirectionUp, height, scroll.value, scroll.maxValue)
        if (step == null || !InputMode.gamepad) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(step) }
        true
    }
}

/**
 * How far a D-pad press scrolls a text of [height] px, at [value] of [max]:
 * two thirds of it down or up; null when the key is neither, or the text is
 * already at that end (up at the top lets focus go to what lies above).
 */
fun textScrollStep(down: Boolean, up: Boolean, height: Int, value: Int, max: Int): Float? = when {
    down && value < max -> height * 2f / 3f
    up && value > 0 -> -height * 2f / 3f
    else -> null
}

/** Tells [scope] whether anything under this root holds focus. */
fun Modifier.padRoot(scope: PadScope): Modifier = composed {
    val view = LocalView.current
    scope.view = view
    // A touch puts the window in touch mode, where Compose's clickables cannot hold focus: it is dropped. The
    // next D-pad press was then spent by the platform on leaving touch mode, and Compose put focus on the
    // window's first item, wherever that was. Leaving touch mode is noted here, before that happens; the
    // root turns down Compose's entry below, so the press reaches the router, which lands focus itself.
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnTouchModeChangeListener { inTouch ->
            scope.touchExit = !inTouch && !scope.focusShown()
        }
        view.viewTreeObserver.addOnTouchModeChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnTouchModeChangeListener(listener) }
    }
    focusProperties { onEnter = { if (scope.touchExit) cancelFocusChange() } }
        .focusGroup()
        .onFocusChanged { scope.rootHasFocus = it.hasFocus }
}

/**
 * Puts the pad's handling on the dialog window this is composed in (a
 * bottom sheet's or an alert dialog's): A clicks, B goes back like the
 * system back, the screen buttons and the stick work, a touch there turns the
 * highlight off. Opened from the pad, the window takes focus on its first
 * item; closed, it gives focus back to the item that opened it. [scope] is
 * the window's, [opener] the window's under it.
 */
@Composable
fun PadDialogWindow(scope: PadScope, opener: PadScope) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = dialogWindow(view)
        val original = window?.callback
        val back = (original as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
        val wrapped = if (window != null && original != null && original !is PadCallback) {
            PadCallback(original, PadRouter({ scope }) { back?.onBackPressed() }).also { window.callback = it }
        } else null
        val from = opener.current()
        onDispose {
            if (wrapped != null && window?.callback === wrapped) window.callback = original
            giveFocusBack(opener, from)
        }
    }
    PadFocusIn(scope)
}

/**
 * The dialog window [view] is in: an alert dialog's layout says it, a bottom
 * sheet's does not, but its dialog owns the view tree's back dispatcher.
 */
private fun dialogWindow(view: View): Window? {
    var v: Any? = view.parent
    while (v is View) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent
    }
    return (view.findViewTreeOnBackPressedDispatcherOwner() as? android.app.Dialog)?.window
}

/** Opened from the pad, a window puts focus on its first item once it is laid out. */
@Composable
private fun PadFocusIn(scope: PadScope) {
    LaunchedEffect(scope) {
        if (!InputMode.gamepad) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!scope.focusShown()) scope.land()
    }
}

/** In gamepad mode, focus goes back to [target], in [scope]'s window, once the window over it is gone. */
private fun giveFocusBack(scope: PadScope, target: PadFocusTarget?) {
    if (target == null || !InputMode.gamepad) return
    Handler(Looper.getMainLooper()).post { if (InputMode.gamepad) scope.focus(target) }
}

/**
 * The pad's handling for a popup (the detail screen's menu), which is a
 * window of its own with no dialog behind it: the keys are caught here
 * instead, on their way to the item that holds focus. The popup always
 * takes focus on its first item when it opens, so they always arrive. B and
 * a screen button close it ([onBack]); A clicks; a touch turns the highlight
 * off. Focus goes back to the item that opened it.
 */
fun Modifier.padPopup(scope: PadScope, opener: PadScope, onBack: () -> Unit): Modifier = composed {
    val view = LocalView.current
    val back = rememberUpdatedState(onBack)
    // A screen button closes the popup too: the screen's own actions (L1/R1, X, Y) must not run under it.
    val router = remember(scope) { PadRouter({ scope }, { back.value() }) { back.value() } }
    DisposableEffect(scope) {
        val from = opener.current()
        onDispose { giveFocusBack(opener, from) }
    }
    LaunchedEffect(scope) {
        withFrameNanos { }
        withFrameNanos { }
        if (!scope.focusShown()) scope.land()
    }
    this
        .padRoot(scope)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press) InputMode.gamepad = false
                }
            }
        }
        .onPreviewKeyEvent { router.key(it.nativeKeyEvent) { e -> e !== it.nativeKeyEvent && view.dispatchKeyEvent(e) } }
}
