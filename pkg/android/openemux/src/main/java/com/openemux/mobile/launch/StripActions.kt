package com.openemux.mobile.launch

/**
 * The drawn skin's quick actions, in strip order: save, load, turbo, slow motion, fast forward, then the NDS screen
 * layout key when [layoutKey] (the skin has a next layout and the strip carries it).
 */
fun stripActions(layoutKey: Boolean): List<String> =
    listOf("save_state", "load_state", "turbo_fire_toggle", "toggle_slowmotion", "toggle_fast_forward") +
        listOfNotNull("screens".takeIf { layoutKey })

/** The strip's speed toggles: RetroAchievements hardcore draws them off, with no touch area and no "on" look. */
val STRIP_SPEED_ACTIONS = setOf("toggle_slowmotion", "toggle_fast_forward")

/** The alpha (of 255) a speed key is drawn at while off in hardcore: 0.45, like the pause sheet's disabled tiles. */
const val SPEED_OFF_ALPHA = 0x73

/** Whether [action] is drawn off and does nothing on the strip: a speed toggle in [hardcore]. */
fun stripActionOff(action: String, hardcore: Boolean) = hardcore && action in STRIP_SPEED_ACTIONS

/** The stroked strip glyphs (the rest are filled): turbo's bolt, slow motion's dashed ring, the NDS screens. */
fun stripActionStroked(action: String) = action == "turbo_fire_toggle" || action == "screens" || action == "toggle_slowmotion"

/** Phone landscape: the picture's width, in design units, between the two control sides of a [wd]-unit-wide skin. */
fun phoneLandscapeGameW(wd: Float, aspect: Float) = minOf(wd - 2f * 170f, 354f * aspect)

/** The smallest width a strip slot gets, room for the lit pill around the glyph (46 wide at most, the slot less 8). */
const val STRIP_SLOT_FLOOR = 40f

/**
 * Phone landscape: the strip's width under a [gameW]-unit-wide picture, [keys] slots: the picture's, or wider, centred
 * on it, when a narrow picture (the NDS screens one over the other) would squeeze the slots under [STRIP_SLOT_FLOOR].
 */
fun phoneLandscapeStripW(gameW: Float, keys: Int) = maxOf(gameW, keys * STRIP_SLOT_FLOOR)

/**
 * The speed binds a RetroAchievements hardcore session unbinds ("nul"): fast forward, which the engine still allows
 * there, and slow motion, which it refuses already, so no gamepad binding reaches either.
 */
val HARDCORE_UNBOUND_KEYS = listOf(
    "input_toggle_fast_forward", "input_hold_fast_forward", "input_toggle_slowmotion", "input_hold_slowmotion",
)
