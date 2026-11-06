package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.coreBind
import com.openemux.mobile.data.skin
import com.openemux.mobile.ui.Fold

// What OpenEmuxSkinRenderer draws, decided without Android graphics so it runs as JVM unit tests: which of a skin's
// layouts a window uses, where the layout lands in it, and what each element becomes on the overlay page.

/**
 * The form a window is drawn as: a foldable's inner screen (any window that sees a fold, flat or half open), else a
 * tablet ([tablet]: smallest width of 600dp or more, SkinGeometry.tablet), else a phone.
 */
fun skinForm(tablet: Boolean, fold: Fold?): SkinForm = when {
    fold != null -> SkinForm.FOLD
    tablet -> SkinForm.TABLET
    else -> SkinForm.PHONE
}

/** What a skin offers a form: both its layouts, or the first one it lacks. */
sealed interface LayoutChoice {
    data class Use(val portrait: SkinLayoutId, val landscape: SkinLayoutId) : LayoutChoice
    data class Missing(val layout: SkinLayoutId) : LayoutChoice
}

/**
 * The [form]'s upright and sideways layouts of [skin]. The overlay holds both pages at once (the engine turns between
 * them with no redraw), so a skin lacking either is not used for that form.
 */
fun chooseLayouts(skin: OpenEmuxSkin, form: SkinForm): LayoutChoice {
    val ids = SkinOrientation.entries.map { o -> SkinLayoutId.entries.first { it.form == form && it.orientation == o } }
    ids.firstOrNull { it !in skin.layouts }?.let { return LayoutChoice.Missing(it) }
    return LayoutChoice.Use(ids[0], ids[1])
}

/**
 * A layout of [lw] x [lh] fitted into a [w] x [h] page: scaled by [s] keeping its proportions, centred, from
 * ([ox], [oy]).
 */
data class SkinFit(val s: Float, val ox: Float, val oy: Float) {
    fun box(r: SkinRect) = SkinBox(ox + r.x * s, oy + r.y * s, r.width * s, r.height * s)

    companion object {
        fun of(lw: Int, lh: Int, w: Int, h: Int): SkinFit {
            val s = minOf(w.toFloat() / lw, h.toFloat() / lh)
            return SkinFit(s, (w - lw * s) / 2f, (h - lh * s) / 2f)
        }
    }
}

/**
 * One element of a layout as the overlay page has it, in page pixels. [index] is the element's place in the layout.
 * A null bind is drawn and presses nothing (a name nothing maps, or a speed key in hardcore, [dim]).
 */
sealed interface ElementPlan {
    val index: Int
    val box: SkinBox

    /** A button or a keypad key, pressing [bind]. */
    data class Button(override val index: Int, override val box: SkinBox, val bind: String?) : ElementPlan

    /**
     * A quick action on the frame, pressing [bind]; [lit] is the engine state it lights with while on
     * (OverlayWriter.LIT_BY), [icon] the app action whose glyph draws it without an image.
     */
    data class Action(
        override val index: Int, override val box: SkinBox, val bind: String?, val icon: String?, val lit: String?, val dim: Boolean,
    ) : ElementPlan

    /**
     * A D-pad: [travel] is its ball's travel about the centre (the engine's desc rect), touched [reach] times its
     * half-size on each side (left, top, right, bottom), which is the element's whole [box]. [binds] are its up, down,
     * left and right; null for a second D-pad on a console with none, drawn and pressing nothing.
     */
    data class Dpad(
        override val index: Int, override val box: SkinBox, val travel: SkinBox, val reach: FloatArray, val binds: List<String>? = DIRECTIONS,
    ) : ElementPlan {
        companion object {
            val DIRECTIONS = listOf("up", "down", "left", "right")
        }
    }

    /**
     * A stick ([bind]: analog_left or analog_right) in the well [box]: its range is the square [range] (the value
     * peaks at its edge), its knob drawn [knob] across in it.
     */
    data class Stick(override val index: Int, override val box: SkinBox, val bind: String, val range: SkinBox, val knob: Float) : ElementPlan
}

/** A layout as an overlay page: where the game goes ([viewport], both NDS screens' box), its elements, a stick's [gap]. */
data class PagePlan(val fit: SkinFit, val viewport: SkinBox, val elements: List<ElementPlan>, val gap: Float)

/**
 * Lays [layout] out on a [w] x [h] page for [console]; [hardcore] leaves the speed keys out. Buttons and keypad keys
 * press what [coreId] reads them as (data/Skin.kt's coreBind: Gearcoleco's ColecoVision pad), as the drawn skin does.
 */
fun planPage(console: Console, layout: SkinLayout, w: Int, h: Int, hardcore: Boolean, coreId: String? = null): PagePlan {
    val fit = SkinFit.of(layout.size.width, layout.size.height, w, h)
    val screens = layout.screens.map(fit::box)
    val left = screens.minOf { it.x }
    val top = screens.minOf { it.y }
    val viewport = SkinBox(left, top, screens.maxOf { it.right } - left, screens.maxOf { it.bottom } - top)
    val elements = layout.elements.mapIndexed { i, e ->
        val box = fit.box(e.rect)
        when (e.type) {
            SkinElementType.BUTTON -> {
                val action = OpenEmuxBinds.action(console, e.bind, hardcore)
                if (action != null) ElementPlan.Action(i, box, action.bind, action.icon, action.lit, action.dim)
                else ElementPlan.Button(i, box, OpenEmuxBinds.button(console, e.bind)?.let { coreBind(coreId, it) })
            }
            SkinElementType.KEYPAD -> ElementPlan.Button(i, box, OpenEmuxBinds.keypad(console, e.bind)?.let { coreBind(coreId, it) })
            SkinElementType.ACTION -> {
                val action = OpenEmuxBinds.action(console, e.bind, hardcore)!!
                ElementPlan.Action(i, box, action.bind, action.icon, action.lit, action.dim)
            }
            SkinElementType.DPAD -> dpad(i, box, OpenEmuxBinds.dpad(console, e.bind))
            SkinElementType.STICK -> stick(i, box, if (e.bind == "rightAnalogStick") "analog_right" else "analog_left")
        }
    }
    return PagePlan(fit, viewport, elements, 2f * fit.s)
}

/** A D-pad over [box]: its ball travels [OverlayLines.DPAD_TRAVEL] of the half-size, and the whole box starts a touch. */
private fun dpad(i: Int, box: SkinBox, binds: List<String>?): ElementPlan.Dpad {
    val tx = box.w / 2f * OverlayLines.DPAD_TRAVEL
    val ty = box.h / 2f * OverlayLines.DPAD_TRAVEL
    val cx = box.x + box.w / 2f
    val cy = box.y + box.h / 2f
    val reach = 1f / OverlayLines.DPAD_TRAVEL
    return ElementPlan.Dpad(i, box, SkinBox(cx - tx, cy - ty, 2f * tx, 2f * ty), floatArrayOf(reach, reach, reach, reach), binds)
}

/**
 * A stick in the well [box]: the knob, half the well across, travels [OverlayWriter.KNOB_TRAVEL] of the well's radius,
 * and that travel is the range (the knob's half never passes it), where the value peaks.
 */
private fun stick(i: Int, box: SkinBox, bind: String): ElementPlan.Stick {
    val r = minOf(box.w, box.h) / 2f
    val range = r * OverlayWriter.KNOB_TRAVEL
    val cx = box.x + box.w / 2f
    val cy = box.y + box.h / 2f
    return ElementPlan.Stick(i, box, bind, SkinBox(cx - range, cy - range, 2f * range, 2f * range), knob = r)
}

/** The OpenEmux skin format's input names (docs/skin-format.md) as RetroArch overlay binds. */
object OpenEmuxBinds {
    /** The speed keys, which RetroAchievements hardcore draws dimmed and leaves out. */
    val SPEED = setOf("fastForward", "toggleFastForward", "slowMotion")

    /** A quick action's bind, its glyph (an app action, as the drawn skins' strip names them) and the state it lights with. */
    data class Action(val bind: String?, val icon: String?, val lit: String?, val dim: Boolean)

    /** The glyph each action name is drawn with when it has no image. */
    private val ICONS = mapOf(
        "quickSave" to "save_state", "quickLoad" to "load_state", "turbo" to "turbo_fire_toggle",
        "slowMotion" to "toggle_slowmotion", "fastForward" to "toggle_fast_forward", "toggleFastForward" to "toggle_fast_forward",
        "screens" to "screens", "menu" to "menu_toggle", "screenshot" to "screenshot", "restart" to "reset", "reset" to "reset",
    )

    /**
     * [name] as a quick action (any of the format's action names, on a button or an action element), or null for a
     * name that is not one. "screens" presses nothing: the skin has no page per NDS layout to step to.
     */
    fun action(console: Console, name: String, hardcore: Boolean): Action? {
        val icon = ICONS[name] ?: return null
        val dim = hardcore && name in SPEED
        val bind = when {
            dim || name == "screens" -> null
            name == "slowMotion" -> "toggle_slowmotion"
            else -> SkinBinds.bind(console, name, hardcore)
        }
        return Action(bind, icon, bind?.let { OverlayWriter.LIT_BY[it] }, dim)
    }

    /** A D-pad's directions on [console]: [name] dpad's, or dpad2's (the console's second pad, null when it has none). */
    fun dpad(console: Console, name: String): List<String>? =
        if (name == SkinBinds.DPAD2) SkinBinds.SECOND_DPAD[console] else ElementPlan.Dpad.DIRECTIONS

    /** A button's [name] on [console] (a quick action's name is [action]'s), or null for one nothing maps. */
    fun button(console: Console, name: String): String? = SkinBinds.bind(console, name, hardcore = false)

    /**
     * A keypad key, as the drawn skins' keypad (Console.skin's) presses it: a digit is the console's key with that
     * label (ColecoVision's are joypad buttons), else typed on the keyboard's number pad; the keys beside the 0
     * ([name] keypadStar, keypadHash) are the console's own, none when it has no keypad.
     */
    fun keypad(console: Console, name: String): String? {
        val keys = console.skin.keypad
        name.removePrefix("keypad").toIntOrNull()?.let { digit ->
            return keys.flatten().firstOrNull { it.label == "$digit" }?.bind ?: "retrok_num$digit"
        }
        val row = keys.lastOrNull()?.takeIf { it.size == 3 } ?: return null
        return when (name) {
            "keypadStar" -> row[0].bind
            "keypadHash" -> row[2].bind
            else -> null
        }
    }
}
