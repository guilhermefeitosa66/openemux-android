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
) {
    /** The device forms the skin has at least one layout for, in [SkinForm] order. */
    val forms: List<SkinForm> get() = SkinForm.entries.filter { form -> layouts.keys.any { it.form == form } }
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
    BUTTON("button", listOf("button", "letter"), listOf("")),
    DPAD("dpad", listOf("pad", "ball"), listOf("", "-ball")),
    STICK("stick", listOf("base", "knob"), listOf("-base", "-knob")),
    /** A quick action on the screen's frame: drawn in the frame's colours, so it has none of its own. */
    ACTION("action", emptyList(), listOf("")),
    KEYPAD("keypad", listOf("button", "letter"), listOf("")),
    ;

    companion object {
        fun byId(id: String): SkinElementType? = entries.firstOrNull { it.id == id }
    }
}

/** The outline an element is drawn with when it has no image. */
enum class SkinShape(val id: String) {
    CIRCLE("circle"), PILL("pill"), RECT("rect");

    companion object {
        fun byId(id: String): SkinShape? = entries.firstOrNull { it.id == id }
    }
}

/** Whether a quick action's icon is light or dark on its "active" background. */
enum class SkinIconTone(val id: String) {
    LIGHT("light"), DARK("dark");

    companion object {
        fun byId(id: String): SkinIconTone? = entries.firstOrNull { it.id == id }
    }
}

/** A layout's pixel space: every rect in the layout is in these units, and its images at this scale. */
data class SkinSize(val width: Int, val height: Int)

data class SkinRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    /** Whether the rect lies wholly inside [size]. */
    fun inside(size: SkinSize) = x >= 0f && y >= 0f && x + width <= size.width && y + height <= size.height
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

/** The bezel around the game screen, where the quick actions sit; [image] replaces "frame" as its base name. */
data class SkinFrame(val rect: SkinRect, val colors: SkinFrameColors = SkinFrameColors(), val image: String? = null) {
    val imageFile: String get() = "${image ?: "frame"}.png"
}

/**
 * One control. [bind] is its input name (docs/skin-format.md; "dpad" for a D-pad), [group] what the editor moves it
 * with, [colors] its colour parts (ARGB, keys from [SkinElementType.colorParts]), [shape] and [label] what it is drawn
 * as without an image, [image] a base name for its image files other than [id].
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
) {
    /** The element's image files, one per [SkinElementType.imageSuffixes] entry, in that order. */
    val imageFiles: List<String> get() = type.imageSuffixes.map { "${image ?: id}$it.png" }
}

/** One layout: its pixel space, the game's one or two screens, the background, the frame and the controls. */
data class SkinLayout(
    val size: SkinSize,
    val screens: List<SkinRect>,
    val background: SkinBackground = SkinBackground(),
    val frame: SkinFrame? = null,
    val elements: List<SkinElement> = emptyList(),
)
