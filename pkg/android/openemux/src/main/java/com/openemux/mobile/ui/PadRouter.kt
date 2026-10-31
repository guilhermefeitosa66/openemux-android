package com.openemux.mobile.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
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

/** One candidate for where a landing press puts focus. */
data class LandingSpot(val shown: Boolean, val initial: Boolean, val lastFocus: Long, val top: Float, val left: Float)

/**
 * Where a press that finds nothing focused lands, as an index into [spots]
 * (-1 for none): the item that held focus last, if it still shows; else the
 * screen's initial item, if it shows; else the first one showing, in reading
 * order; else the initial item or the last focused, wherever they are.
 */
fun pickLanding(spots: List<LandingSpot>): Int {
    val shown = spots.withIndex().filter { it.value.shown }
    shown.filter { it.value.lastFocus > 0 }.maxByOrNull { it.value.lastFocus }?.let { return it.index }
    shown.firstOrNull { it.value.initial }?.let { return it.index }
    shown.minWithOrNull(compareBy({ it.value.top }, { it.value.left }))?.let { return it.index }
    spots.withIndex().firstOrNull { it.value.initial }?.let { return it.index }
    return spots.withIndex().filter { it.value.lastFocus > 0 }.maxByOrNull { it.value.lastFocus }?.index ?: -1
}

/** An item that can take the gamepad's focus, in one window. */
class PadTarget(var initial: Boolean = false, val requester: FocusRequester = FocusRequester()) {
    var coords: LayoutCoordinates? = null
    var focused = false

    /** When it last took focus (a tick of its scope), 0 if never. */
    var lastFocus = 0L

    /** Whether any of it shows in its window: an item scrolled out of its list does not. */
    fun shown(): Boolean {
        val c = coords?.takeIf { it.isAttached } ?: return false
        val b = runCatching { c.boundsInWindow() }.getOrNull() ?: return false
        return b.width >= 1f && b.height >= 1f
    }
}

/**
 * The focusable items of one window (the activity's, a sheet's, a dialog's
 * or a popup's), so a press that finds nothing focused there knows where to
 * land, and a window that closes knows where to give focus back.
 */
class PadScope {
    private val targets = mutableListOf<PadTarget>()
    private var tick = 0L

    /** Whether anything in the window holds focus, a control the scope does not know included. */
    var rootHasFocus = false

    fun add(target: PadTarget) { targets += target }
    fun remove(target: PadTarget) { targets -= target }

    fun focusChanged(target: PadTarget, now: Boolean) {
        target.focused = now
        if (now) target.lastFocus = ++tick
    }

    /** Whether an item that shows holds focus; one the scope does not know counts as showing. */
    fun focusShown(): Boolean {
        val held = targets.firstOrNull { it.focused } ?: return rootHasFocus
        return held.shown()
    }

    /** The item that holds focus, else the last one that did. */
    fun current(): PadTarget? = targets.firstOrNull { it.focused } ?: targets.filter { it.lastFocus > 0 }.maxByOrNull { it.lastFocus }

    /** Puts focus on the item [pickLanding] chooses; false when there is none. */
    fun land(): Boolean {
        val list = targets.toList()
        val spots = list.map { t ->
            val shown = t.shown()
            val pos = if (shown) t.coords?.let { c -> runCatching { c.boundsInWindow() }.getOrNull() } else null
            LandingSpot(shown, t.initial, t.lastFocus, pos?.top ?: 0f, pos?.left ?: 0f)
        }
        val i = pickLanding(spots)
        if (i < 0) return false
        return runCatching { list[i].requester.requestFocus() }.isSuccess
    }
}

/** The window the composables below belong to: the activity's unless a sheet, dialog or popup provides its own. */
val LocalPadScope = compositionLocalOf { PadScope() }

/**
 * Applies [decidePadKey] in one window: the activity, or a sheet's or
 * dialog's. [back] is that window's back (B), [scope] its focusable items.
 */
class PadRouter(private val scope: () -> PadScope, private val back: () -> Unit) {
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
        val decision = decidePadKey(code, event.action, fromPad, scope().focusShown(), landed)
        if (decision == PadDecision.NotPad) return dispatch(event)
        InputMode.gamepad = true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && code != landed) landed = 0
        return when (decision) {
            PadDecision.AsCenter -> dispatch(
                KeyEvent(
                    event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, InputDevice.SOURCE_DPAD,
                ),
            )
            is PadDecision.Back -> { if (decision.act) back(); true }
            is PadDecision.Bus -> { if (decision.act) GamepadBus.dispatch { it.handler(decision.button) }; true }
            PadDecision.Land -> { landed = code; scope().land(); true }
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

/** A dialog window's callback with the pad's handling in front; everything else goes to [original]. */
private class PadCallback(private val original: Window.Callback, private val router: PadRouter) : Window.Callback by original {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = router.key(event) { original.dispatchKeyEvent(it) }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean { router.touch(event); return original.dispatchTouchEvent(event) }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        router.motion(event, { original.dispatchGenericMotionEvent(it) }, ::dispatchKeyEvent)
}

/** Registers this item with its window's [PadScope], as one a landing press can put focus on. */
internal fun Modifier.padTarget(target: PadTarget, attach: Boolean = true): Modifier = composed {
    val scope = LocalPadScope.current
    DisposableEffect(scope, target) {
        scope.add(target)
        onDispose { scope.remove(target); target.coords = null }
    }
    (if (attach) focusRequester(target.requester) else this)
        .onPlaced { target.coords = it }
        .onFocusChanged { scope.focusChanged(target, it.isFocused || it.hasFocus) }
}

/** Tells [scope] whether anything under this root holds focus. */
fun Modifier.padRoot(scope: PadScope): Modifier = onFocusChanged { scope.rootHasFocus = it.hasFocus }

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
        val window = (view.parent as? DialogWindowProvider)?.window
        val original = window?.callback
        val back = (original as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
        val wrapped = if (window != null && original != null && original !is PadCallback) {
            PadCallback(original, PadRouter({ scope }) { back?.onBackPressed() }).also { window.callback = it }
        } else null
        val from = opener.current()
        onDispose {
            if (wrapped != null && window?.callback === wrapped) window.callback = original
            giveFocusBack(from)
        }
    }
    PadFocusIn(scope)
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

/** In gamepad mode, focus goes back to [target] once the window over it is gone. */
private fun giveFocusBack(target: PadTarget?) {
    if (target == null || !InputMode.gamepad) return
    Handler(Looper.getMainLooper()).post { if (InputMode.gamepad) runCatching { target.requester.requestFocus() } }
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
    val router = remember(scope) { PadRouter({ scope }) { back.value() } }
    DisposableEffect(scope) {
        val from = opener.current()
        onDispose { giveFocusBack(from) }
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
