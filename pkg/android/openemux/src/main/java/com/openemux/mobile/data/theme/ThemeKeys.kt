package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath

/**
 * The settings a user theme may change (issue 1018), shared by name with the desktop's `theme.yaml` (issue 1011).
 *
 * The color keys are the desktop's own (part 1), so a theme moves between the two apps unchanged; the shape, size and
 * image keys are defined here first and documented in `docs/theme-format.md` for the desktop to follow. A key one
 * app does not know is ignored by it on read.
 *
 * [ThemeKey.light] and [ThemeKey.dark] are what this app paints when the theme leaves the key alone: today's look on
 * each base. They are never written to a file (only changed keys are), so they may differ from the desktop's.
 */
enum class ThemeKind { COLOR, NUMBER, CHOICE, IMAGE }

enum class ThemeSection { COLORS, TEXT, ACCENT, SHAPES, ICONS, SIZES, BACKGROUNDS }

/** The heading a row sits under within its section. */
enum class ThemeGroup { SURFACES, CONTENT, NAVIGATION, ACCENT, BORDERS, ICONS, TEXT_SIZE, SPACING, LIBRARY_IMAGE, NAVIGATION_IMAGE }

/** One setting. Colors are ARGB ints, numbers doubles, choices and images strings. */
class ThemeKey(
    val key: String,
    val section: ThemeSection,
    val group: ThemeGroup,
    val kind: ThemeKind,
    val light: Any?,
    val dark: Any?,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val choices: List<String> = emptyList(),
    /** The image key a background setting belongs to: hidden while that image is not set. */
    val image: String? = null,
) {
    fun default(base: String): Any? = if (base == ThemeBase.DARK) dark else light
}

object ThemeBase {
    const val LIGHT = "light"
    const val DARK = "dark"
    val ALL = listOf(LIGHT, DARK)
}

object ThemeFit {
    const val COVER = "cover"
    const val CONTAIN = "contain"
    const val TILE = "tile"
    val ALL = listOf(COVER, CONTAIN, TILE)
}

object ThemeDensity {
    const val COMPACT = "compact"
    const val DEFAULT = "default"
    const val COMFORTABLE = "comfortable"
    val ALL = listOf(COMPACT, DEFAULT, COMFORTABLE)

    /** How much a row's height and the screen's padding grow or shrink. */
    fun factor(value: String?): Float = when (value) {
        COMPACT -> 0.85f
        COMFORTABLE -> 1.15f
        else -> 1f
    }
}

object ThemeKeys {
    /** The image slots: the library area, and the navigation (the desktop's sidebar). */
    const val LIBRARY_IMAGE = "library_image"
    const val SIDEBAR_IMAGE = "sidebar_image"
    val IMAGE_KEYS = listOf(LIBRARY_IMAGE, SIDEBAR_IMAGE)

    /** What an image key may name: a file in the theme's `images/` folder, nothing else. */
    val IMAGE_PATH = Regex("images/[a-z0-9_-]{1,40}\\.(png|jpg|jpeg|webp)")

    private fun color(key: String, section: ThemeSection, group: ThemeGroup, light: Long, dark: Long) =
        ThemeKey(key, section, group, ThemeKind.COLOR, light.toInt(), dark.toInt())

    private fun number(key: String, section: ThemeSection, group: ThemeGroup, light: Double, dark: Double, min: Double, max: Double, image: String? = null) =
        ThemeKey(key, section, group, ThemeKind.NUMBER, light, dark, min, max, image = image)

    private fun choice(key: String, section: ThemeSection, group: ThemeGroup, default: String, choices: List<String>, image: String? = null) =
        ThemeKey(key, section, group, ThemeKind.CHOICE, default, default, choices = choices, image = image)

    private fun image(key: String, group: ThemeGroup) = ThemeKey(key, ThemeSection.BACKGROUNDS, group, ThemeKind.IMAGE, null, null)

    private fun background(slot: String, group: ThemeGroup) = listOf(
        image("${slot}_image", group),
        choice("${slot}_image_fit", ThemeSection.BACKGROUNDS, group, ThemeFit.COVER, ThemeFit.ALL, image = "${slot}_image"),
        number("${slot}_image_dim", ThemeSection.BACKGROUNDS, group, 0.5, 0.5, 0.0, 0.9, image = "${slot}_image"),
        number("${slot}_image_blur", ThemeSection.BACKGROUNDS, group, 0.0, 0.0, 0.0, 24.0, image = "${slot}_image"),
    )

    /** The whitelist, in the editor's order. Nothing outside it is read from a file or written to one. */
    val ALL: List<ThemeKey> = listOf(
        // Part 1's colors, with the Android surface each one paints (see docs/theme-format.md).
        color("window_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFF6F5F9, 0xFF121216),
        color("headerbar_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFEBEAF1, 0xFF1E1E25),
        color("sidebar_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFF6F5F9, 0xFF121216),
        color("library_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFF6F5F9, 0xFF121216),
        color("card_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFFFFFFF, 0xFF1C1C23),
        color("popover_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFFFFFFF, 0xFF1A1A20),
        color("details_bg", ThemeSection.COLORS, ThemeGroup.SURFACES, 0xFFF6F5F9, 0xFF121216),
        color("text_primary", ThemeSection.TEXT, ThemeGroup.CONTENT, 0xFF17171C, 0xFFF3F3F6),
        color("text_secondary", ThemeSection.TEXT, ThemeGroup.CONTENT, 0xFF5C5C6B, 0xFFA3A3B2),
        color("sidebar_text_primary", ThemeSection.TEXT, ThemeGroup.NAVIGATION, 0xFF6D28D9, 0xFFB79CFF),
        color("sidebar_text_secondary", ThemeSection.TEXT, ThemeGroup.NAVIGATION, 0xFF5C5C6B, 0xFFA3A3B2),
        color("accent", ThemeSection.ACCENT, ThemeGroup.ACCENT, 0xFF6D28D9, 0xFFB79CFF),
        color("selection_bg", ThemeSection.ACCENT, ThemeGroup.ACCENT, 0xFFE4D9FD, 0xFF3B2A6B),
        color("accent_button_bg", ThemeSection.ACCENT, ThemeGroup.ACCENT, 0xFF7C3AED, 0xFF7C3AED),
        color("accent_button_fg", ThemeSection.ACCENT, ThemeGroup.ACCENT, 0xFFFFFFFF, 0xFFFFFFFF),
        // Shapes: the radius is a card's (18 dp today); every other corner keeps its proportion to it.
        number("corner_radius", ThemeSection.SHAPES, ThemeGroup.BORDERS, 18.0, 18.0, 0.0, 28.0),
        number("border_width", ThemeSection.SHAPES, ThemeGroup.BORDERS, 0.0, 0.0, 0.0, 4.0),
        color("border_color", ThemeSection.SHAPES, ThemeGroup.BORDERS, 0xFFE2E0EA, 0xFF2B2B35),
        color("shadow_color", ThemeSection.SHAPES, ThemeGroup.BORDERS, 0xFF000000, 0xFF000000),
        number("shadow_strength", ThemeSection.SHAPES, ThemeGroup.BORDERS, 0.0, 0.0, 0.0, 1.0),
        number("icon_scale", ThemeSection.ICONS, ThemeGroup.ICONS, 1.0, 1.0, 0.75, 1.5),
        color("icon_color", ThemeSection.ICONS, ThemeGroup.ICONS, 0xFF5C5C6B, 0xFFA3A3B2),
        number("font_scale", ThemeSection.SIZES, ThemeGroup.TEXT_SIZE, 1.0, 1.0, 0.85, 1.3),
        choice("density", ThemeSection.SIZES, ThemeGroup.SPACING, ThemeDensity.DEFAULT, ThemeDensity.ALL),
        number("grid_gap", ThemeSection.SIZES, ThemeGroup.SPACING, 12.0, 12.0, 0.0, 32.0),
        number("card_scale", ThemeSection.SIZES, ThemeGroup.SPACING, 1.0, 1.0, 0.75, 1.5),
    ) + background("library", ThemeGroup.LIBRARY_IMAGE) + background("sidebar", ThemeGroup.NAVIGATION_IMAGE)

    val BY_NAME: Map<String, ThemeKey> = ALL.associateBy { it.key }

    fun inSection(section: ThemeSection): List<ThemeKey> = ALL.filter { it.section == section }

    /** The keys of a file that are not settings. */
    val RESERVED = setOf("version", "name", "base")

    /**
     * Foreground keys and the surfaces they are read on: the editor warns when a pair the theme changed falls under
     * WCAG AA. The desktop's pairs, plus the navigation labels on the navigation.
     */
    val CONTRAST_PAIRS: List<Pair<String, List<String>>> = listOf(
        "text_primary" to listOf("window_bg", "library_bg", "card_bg", "popover_bg", "details_bg"),
        "text_secondary" to listOf("window_bg", "library_bg", "card_bg", "popover_bg", "details_bg"),
        "sidebar_text_primary" to listOf("sidebar_bg"),
        "sidebar_text_secondary" to listOf("sidebar_bg"),
        "accent" to listOf("window_bg", "card_bg"),
        "accent_button_fg" to listOf("accent_button_bg"),
    )

    const val WCAG_AA = 4.5

    /**
     * [value] as [spec] stores it, or null when it is unusable: a color is `#RRGGBB` (any case), a number finite and
     * clamped into range (never a boolean), a choice one of its own, an image a path inside `images/`.
     */
    fun validate(spec: ThemeKey, value: Any?): Any? = when (spec.kind) {
        // Only `#RRGGBB` text: a bare number (`accent: 123` in a file) is not a color.
        ThemeKind.COLOR -> (value as? String)?.takeIf { it.trim().startsWith("#") }?.let { SkinColorMath.parseHex(it) }
        ThemeKind.NUMBER -> when (value) {
            is Boolean -> null
            is Number -> value.toDouble().takeIf { it.isFinite() }?.coerceIn(spec.min, spec.max)
            else -> null
        }
        ThemeKind.CHOICE -> (value as? String)?.takeIf { it in spec.choices }
        ThemeKind.IMAGE -> (value as? String)?.takeIf { IMAGE_PATH.matches(it) }
    }

    /** Only the whitelisted keys of [raw], each validated or dropped. [dropped] hears each one left out. */
    fun validateAll(raw: Map<String, Any?>, dropped: (String) -> Unit = {}): Map<String, Any> {
        val values = LinkedHashMap<String, Any>()
        for ((key, value) in raw) {
            if (key in RESERVED) continue
            val spec = BY_NAME[key]
            val cleaned = spec?.let { validate(it, value) }
            if (cleaned == null) dropped(key) else values[key] = cleaned
        }
        return values
    }
}
