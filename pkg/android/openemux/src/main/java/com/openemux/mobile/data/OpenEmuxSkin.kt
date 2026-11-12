package com.openemux.mobile.data

/**
 * A controller skin in OpenEmux's own format (docs/skin-format.md): what [SkinFormat] reads from a skin.json and
 * writes back. Pure data, no Android graphics: the renderer, the editor and the templates build on it.
 *
 * The layouts present are the ones the skin offers; [forms] follows from them.
 */
data class OpenEmuxSkin(
    val console: Console,
    val name: String,
    val layouts: Map<SkinLayoutId, SkinLayout>,
    val format: Int = SkinFormat.VERSION,
    /**
     * The ids of the combos skin.json has that this app left out on reading (issue 842: a newer app's, it cannot press):
     * the editor keeps them in the file and gives its own combos other ids.
     */
    val leftOutCombos: Set<String> = emptySet(),
) {
    /** The device forms the skin has at least one layout for, in [SkinForm] order. */
    val forms: List<SkinForm> get() = SkinForm.entries.filter { form -> layouts.keys.any { it.form == form } }

    /** The combo buttons the player made (issue 842), each once, as its layouts place them. */
    val combos: List<SkinCombo> get() = SkinCombos.of(layouts.values)
}

/** The kinds of device a skin can be made for: each has an upright and a sideways layout. */
enum class SkinForm(val id: String) { PHONE("phone"), TABLET("tablet"), FOLD("fold") }

enum class SkinOrientation(val id: String) { PORTRAIT("portrait"), LANDSCAPE("landscape") }

/** The six layouts a skin can offer; [id] is also the name of the layout's folder of images. */
enum class SkinLayoutId(val form: SkinForm, val orientation: SkinOrientation) {
    PHONE_PORTRAIT(SkinForm.PHONE, SkinOrientation.PORTRAIT),
    PHONE_LANDSCAPE(SkinForm.PHONE, SkinOrientation.LANDSCAPE),
    TABLET_PORTRAIT(SkinForm.TABLET, SkinOrientation.PORTRAIT),
    TABLET_LANDSCAPE(SkinForm.TABLET, SkinOrientation.LANDSCAPE),
    FOLD_PORTRAIT(SkinForm.FOLD, SkinOrientation.PORTRAIT),
    FOLD_LANDSCAPE(SkinForm.FOLD, SkinOrientation.LANDSCAPE),
    ;

    val id: String get() = "${form.id}-${orientation.id}"

    companion object {
        fun byId(id: String): SkinLayoutId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What an element is, the colour parts it can be painted in ([colorParts]) and the suffixes of its image files after
 * its base name ([imageSuffixes]: a D-pad has the pad and its ball, a stick its base and its knob).
 */
enum class SkinElementType(val id: String, val colorParts: List<String>, val imageSuffixes: List<String>) {
    BUTTON("button", listOf("button", "relief", "letter"), listOf("")),
    /** Its "letter" is its four arrows. */
    DPAD("dpad", listOf("pad", "ball", "relief", "letter"), listOf("", "-ball")),
    STICK("stick", listOf("base", "knob"), listOf("-base", "-knob")),
    /** A quick action on the screen's frame: drawn in the frame's colours, so it has none of its own. */
    ACTION("action", emptyList(), listOf("")),
    KEYPAD("keypad", listOf("button", "relief", "letter"), listOf("")),
    ;

    /** Whether it is drawn as a face over a relief, with a label: a button, a key, the D-pad. */
    val raised: Boolean get() = "relief" in colorParts

    companion object {
        fun byId(id: String): SkinElementType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The outline an element is drawn with when it has no image. [TRIANGLE] is a right triangle in its rect whose square
 * corner is the element's [SkinElement.corner]: a combo button's (issue 842), or a button the player gave that outline
 * (issue 891), never a skin's own.
 */
enum class SkinShape(val id: String) {
    CIRCLE("circle"), PILL("pill"), RECT("rect"), TRIANGLE("triangle");

    companion object {
        fun byId(id: String): SkinShape? = entries.firstOrNull { it.id == id }
    }
}

/** The corner of its rect a triangular combo button's square corner points at (issue 842). */
enum class SkinCorner(val id: String) {
    BOTTOM_LEFT("bottom-left"), BOTTOM_RIGHT("bottom-right"), TOP_LEFT("top-left"), TOP_RIGHT("top-right");

    /** Which way the corner is from the rect's centre: -1 or 1 on each axis, y down. */
    val sx: Float get() = if (this == BOTTOM_LEFT || this == TOP_LEFT) -1f else 1f
    val sy: Float get() = if (this == TOP_LEFT || this == TOP_RIGHT) -1f else 1f

    companion object {
        fun byId(id: String?): SkinCorner? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The outline a skin gives a control the player drew in another (issue 891): its [shape] (null: the type's default),
 * its [rotation], and the [width] x [height] it had when the player's outline was chosen, whose proportions "Original"
 * brings back.
 */
data class SkinOwnOutline(val shape: SkinShape?, val rotation: Float, val width: Float, val height: Float)

/** Whether a quick action's icon is light or dark on its "active" background. */
enum class SkinIconTone(val id: String) {
    LIGHT("light"), DARK("dark");

    companion object {
        fun byId(id: String): SkinIconTone? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What an element's image holds, so what is left for the app to draw (docs/skin-format.md): the whole control
 * ([FULL], the default), the control without its label ([KEY]: the app draws the label, where [SkinElement.labelOffset]
 * puts it), or its face alone ([FACE], what a texture picked in the editor is: the app draws the relief under it and
 * the label over it).
 */
enum class SkinArt(val id: String) {
    FULL("full"), KEY("key"), FACE("face");

    companion object {
        fun byId(id: String): SkinArt? = entries.firstOrNull { it.id == id }
    }
}

/** A layout's pixel space: every rect in the layout is in these units, and its images at this scale. */
data class SkinSize(val width: Int, val height: Int)

/** A point, or an offset, in a layout's units. */
data class SkinPoint(val x: Float, val y: Float)

data class SkinRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    /** Whether the rect lies wholly inside [size]. */
    fun inside(size: SkinSize) = x >= 0f && y >= 0f && x + width <= size.width && y + height <= size.height

    /** Whether ([px], [py]) is in the rect, its edges included. */
    fun contains(px: Float, py: Float) = px >= x && px <= x + width && py >= y && py <= y + height

    /** Whether the rect lies wholly inside [other]. */
    fun within(other: SkinRect) = x >= other.x && y >= other.y && x + width <= other.x + other.width && y + height <= other.y + other.height
}

/** [color] is ARGB, null for the renderer's default; [image] replaces "background" as the image's base name. */
data class SkinBackground(val color: Int? = null, val image: String? = null) {
    val imageFile: String get() = "${image ?: "background"}.png"
}

/** The frame's colours (ARGB, null for the default): the bezel, the quick actions' icons, a toggled action's look. */
data class SkinFrameColors(
    val frame: Int? = null,
    val icons: Int? = null,
    val active: Int? = null,
    val activeIcon: SkinIconTone? = null,
)

/**
 * The bezel around the game screen, where the quick actions sit; [image] replaces "frame" as its base name, [radius]
 * rounds its corners when it is drawn from its colour.
 */
data class SkinFrame(val rect: SkinRect, val colors: SkinFrameColors = SkinFrameColors(), val image: String? = null, val radius: Float = 0f) {
    val imageFile: String get() = "${image ?: "frame"}.png"
}

/**
 * One control. [bind] is its input name (docs/skin-format.md; "dpad" for a D-pad, "dpad2" for a second one), [group] what the editor moves it
 * with, [colors] its colour parts (ARGB, keys from [SkinElementType.colorParts]), [shape] and [label] what it is drawn
 * as without an image, [image] a base name for its image files other than [id].
 *
 * [rect] is the whole control: its face and, under it, [depth] units of relief (0: flat). [rotation] turns the face
 * (degrees, clockwise) inside [rect], which is then the box around the turned shape; [radius] rounds a "rect" shape's
 * corners. [labelSize] is the label's type size and [labelOffset] where its centre is from [rect]'s centre (null: on
 * the face's centre); [art] says how much of all this the element's image already holds.
 */
data class SkinElement(
    val id: String,
    val type: SkinElementType,
    val bind: String,
    val rect: SkinRect,
    val group: String? = null,
    val shape: SkinShape? = null,
    val label: String? = null,
    val colors: Map<String, Int> = emptyMap(),
    val image: String? = null,
    val radius: Float? = null,
    val rotation: Float = 0f,
    val depth: Float = 0f,
    val labelSize: Float? = null,
    val labelOffset: SkinPoint? = null,
    val art: SkinArt = SkinArt.FULL,
    /** Where a [SkinShape.TRIANGLE] points (issue 842); null for every other shape. */
    val corner: SkinCorner? = null,
    /**
     * What the skin itself draws this control as, while the player draws it in another outline (issue 891): [shape],
     * [corner] and [rotation] are then the player's. Null: the skin's own.
     */
    val own: SkinOwnOutline? = null,
) {
    /** The element's image files, one per [SkinElementType.imageSuffixes] entry, in that order. */
    val imageFiles: List<String> get() = type.imageSuffixes.map { "${image ?: id}$it.png" }

    /** The face: [rect] without the relief under it. */
    val face: SkinRect get() = rect.copy(height = (rect.height - depth).coerceAtLeast(rect.height * MIN_FACE))

    /**
     * [this] in [to]: what is measured in the layout's units ([depth], [radius], the label's size and place) follows
     * the width, so a control resized keeps its proportions. A combo button follows the height: a circle made a pill
     * (issue 842) gets wider, not deeper.
     */
    fun at(to: SkinRect): SkinElement {
        val k = if (isCombo) to.height / rect.height else to.width / rect.width
        if (k == 1f) return copy(rect = to)
        return copy(
            rect = to, radius = radius?.times(k), depth = depth * k, labelSize = labelSize?.times(k),
            labelOffset = labelOffset?.let { SkinPoint(it.x * k, it.y * k) },
        )
    }

    companion object {
        /** The least of a control's height its face keeps, whatever [depth] says. */
        const val MIN_FACE = 0.25f
    }
}

/**
 * One layout: its pixel space, the game's one or two screens, the background, the frame and the controls; [keypad] its
 * keypad page, when the skin has one; [screenLayouts] its NDS screen-layout pages by id ([SkinScreenPage.IDS] order),
 * empty when it has none.
 */
data class SkinLayout(
    val size: SkinSize,
    val screens: List<SkinRect>,
    val background: SkinBackground = SkinBackground(),
    val frame: SkinFrame? = null,
    val elements: List<SkinElement> = emptyList(),
    val keypad: SkinKeypadPage? = null,
    val screenLayouts: Map<String, SkinScreenPage> = emptyMap(),
) {
    /** The controls of the page shown: the layout's own, or its keypad page's ([keypadPage]). */
    fun elements(keypadPage: Boolean): List<SkinElement> = if (keypadPage) keypad?.elements.orEmpty() else elements

    /**
     * The page's background: the keypad page's own when it has one (its colour left out is the layout's), else the
     * layout's.
     */
    fun background(keypadPage: Boolean): SkinBackground =
        keypad?.background?.takeIf { keypadPage }?.let { it.copy(color = it.color ?: background.color) } ?: background

    /**
     * The screen-layout page [id] as a layout of its own: this one's size, the page's screens, frame and controls, and
     * its background (its colour left out is this one's) or, when it has none, this one's. Null when there is no such
     * page ("stacked", the layout itself, included).
     */
    fun screenPage(id: String): SkinLayout? = screenLayouts[id]?.let { page ->
        val bg = page.background?.let { it.copy(color = it.color ?: background.color) } ?: background
        SkinLayout(size, page.screens, bg, page.frame, page.elements)
    }
}

/**
 * One of an NDS layout's screen-layout pages (docs/skin-format.md): the art for the DS screens shown one way, which the
 * `screens` key steps to and the core's screen layout follows. The layout's size, with [screens] (one, or two side by
 * side), a [frame] and controls of its own; [background] null keeps the layout's (colour and image). Its images are in
 * the layout's folder of the page's id (`phone-portrait/top/`). The layout itself is the default page, both screens one
 * over the other.
 */
data class SkinScreenPage(
    val screens: List<SkinRect>,
    val background: SkinBackground? = null,
    val frame: SkinFrame? = null,
    val elements: List<SkinElement> = emptyList(),
) {
    companion object {
        /** How many screens each page shows, by id (data/Skin.kt's NdsLayouts ids), in the cycle's order. */
        val SCREENS = linkedMapOf("top" to 1, "bottom" to 1, "side" to 2)

        /** The page ids, in the cycle's order: a layout that has pages has all of them. */
        val IDS: List<String> = SCREENS.keys.toList()
    }
}

/**
 * A layout's keypad page (docs/skin-format.md): the layout's size, screens and frame with controls of its own, which a
 * [SkinFormat.KEYPAD_TOGGLE] key switches to and back from. Its images are in the layout's [FOLDER]; [background] null
 * keeps the layout's (colour and image).
 */
data class SkinKeypadPage(val elements: List<SkinElement>, val background: SkinBackground? = null) {
    /** Whether it has the key that leaves it: a page without one would keep the player on it. */
    val closable: Boolean get() = elements.any { it.bind == SkinFormat.KEYPAD_TOGGLE }

    companion object {
        const val FOLDER = "keypad"
    }
}
