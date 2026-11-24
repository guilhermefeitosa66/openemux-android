package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath

/** The app palette's colors a user theme can repaint (`Oe` in ui/Theme.kt), plus the focus tones. */
enum class OeToken {
    BG, NAV_BG, LIBRARY_BG, DETAILS_BG, CONSOLE_LIST, CONTROL, SURFACE, SURFACE2, RAISED, DIVIDER, SHEET, BORDER,
    TEXT, MUTED, FAINT, CHEVRON, NAV_TEXT, NAV_TEXT_ACTIVE,
    ACCENT, ACCENT_FOCUS, ACCENT_BRIGHT, ACCENT_TEXT, ACCENT_ON_SOFT, ON_ACCENT,
    SOFT, TINT, ON_SOFT, ICON,
}

/** A background picture as the app draws it: the file, how it fits, how much it is dimmed and blurred. */
data class ThemeImage(val path: String, val fit: String, val dim: Float, val blur: Float)

/**
 * A user theme turned into what the UI paints (issue 1018): an override for each [OeToken] the theme reaches (a
 * token it leaves alone is absent, so the app's own value shows), the shape and size factors, and the pictures.
 * Pure and immutable: the UI swaps one for another when a theme is saved or picked.
 */
class ResolvedTheme(
    val id: String,
    val dark: Boolean,
    private val colors: Map<OeToken, Int>,
    val cornerScale: Float,
    val borderWidth: Float,
    val borderColor: Int?,
    val shadowColor: Int,
    val shadowStrength: Float,
    val iconScale: Float,
    val fontScale: Float,
    val density: Float,
    /** The grid's gap in dp, or null for each grid's own (16 between cartridges, 12 between covers). */
    val gridGap: Float?,
    val cardScale: Float,
    val libraryImage: ThemeImage?,
    val navImage: ThemeImage?,
) {
    fun color(token: OeToken): Int? = colors[token]

    val tokens: Set<OeToken> get() = colors.keys

    companion object {
        /** The least contrast the focus fill keeps against every surface it can sit on (issue 951's fill, made safe). */
        const val FOCUS_FLOOR = 1.6

        /** The app's card radius, which `corner_radius` replaces; every other corner keeps its proportion to it. */
        const val CARD_RADIUS = 18.0

        fun resolve(theme: UserTheme): ResolvedTheme {
            val dark = theme.base == ThemeBase.DARK
            val c = LinkedHashMap<OeToken, Int>()
            fun changed(key: String) = theme.isChanged(key)
            fun col(key: String) = theme.color(key)
            val text = col("text_primary")
            val muted = col("text_secondary")
            val window = col("window_bg")

            if (changed("window_bg")) c[OeToken.BG] = window
            if (changed("headerbar_bg")) c[OeToken.CONTROL] = col("headerbar_bg")
            // The navigation, the library and the details follow the page unless they have a color of their own.
            if (changed("sidebar_bg") || changed("window_bg")) c[OeToken.NAV_BG] = col(if (changed("sidebar_bg")) "sidebar_bg" else "window_bg")
            if (changed("library_bg") || changed("window_bg")) c[OeToken.LIBRARY_BG] = col(if (changed("library_bg")) "library_bg" else "window_bg")
            if (changed("details_bg") || changed("window_bg")) c[OeToken.DETAILS_BG] = col(if (changed("details_bg")) "details_bg" else "window_bg")
            if (changed("card_bg") || changed("text_primary")) {
                val card = col("card_bg")
                c[OeToken.SURFACE] = card
                c[OeToken.SURFACE2] = SkinColorMath.mix(card, text, 0.06f)
                c[OeToken.RAISED] = SkinColorMath.mix(card, text, 0.09f)
                c[OeToken.DIVIDER] = SkinColorMath.mix(card, text, 0.08f)
            }
            if (changed("popover_bg")) c[OeToken.SHEET] = col("popover_bg")
            if (changed("console_list_bg")) c[OeToken.CONSOLE_LIST] = col("console_list_bg")
            if (changed("border_color")) c[OeToken.BORDER] = col("border_color")
            if (changed("text_primary")) c[OeToken.TEXT] = text
            if (changed("text_secondary") || changed("window_bg")) {
                c[OeToken.MUTED] = muted
                c[OeToken.FAINT] = SkinColorMath.mix(muted, window, 0.15f)
                c[OeToken.CHEVRON] = SkinColorMath.mix(muted, window, 0.35f)
            }
            if (changed("sidebar_text_primary")) c[OeToken.NAV_TEXT_ACTIVE] = col("sidebar_text_primary")
            if (changed("sidebar_text_secondary")) c[OeToken.NAV_TEXT] = col("sidebar_text_secondary")
            if (changed("accent")) {
                val accent = col("accent")
                c[OeToken.ACCENT_TEXT] = accent
                c[OeToken.ACCENT_BRIGHT] = accent
                c[OeToken.ACCENT_ON_SOFT] = accent
            }
            if (changed("accent_button_bg")) {
                val button = col("accent_button_bg")
                c[OeToken.ACCENT] = button
                c[OeToken.ACCENT_FOCUS] = step(button, 0.25f)
            }
            if (changed("accent_button_fg")) c[OeToken.ON_ACCENT] = col("accent_button_fg")
            if (changed("icon_color")) c[OeToken.ICON] = col("icon_color")

            // The focus fill (issue 951) is the selection tone: whatever the theme says, it stays visible. The floor
            // is never above what the base itself has, so a theme that touches none of this paints as the app does.
            val focusKeys = SURFACE_KEYS + listOf("selection_bg", "accent")
            if (focusKeys.any(::changed)) {
                val floor = minOf(FOCUS_FLOOR, baseFloor(theme.base))
                val soft = withFloor(col("selection_bg"), SURFACE_KEYS.map(::col), dark, floor)
                c[OeToken.SOFT] = soft
                c[OeToken.TINT] = SkinColorMath.mix(soft, window, 0.5f)
                c[OeToken.ON_SOFT] = withFloor(SkinColorMath.mix(soft, col("accent"), 0.3f), listOf(soft), dark, ON_SOFT_FLOOR)
            }

            val library = image(theme, "library")
            val nav = image(theme, "sidebar")
            return ResolvedTheme(
                id = theme.id,
                dark = dark,
                colors = c,
                cornerScale = (theme.number("corner_radius") / CARD_RADIUS).toFloat(),
                borderWidth = theme.number("border_width").toFloat(),
                borderColor = if (changed("border_color") || theme.number("border_width") > 0) col("border_color") else null,
                shadowColor = col("shadow_color"),
                shadowStrength = theme.number("shadow_strength").toFloat(),
                iconScale = theme.number("icon_scale").toFloat(),
                fontScale = theme.number("font_scale").toFloat(),
                density = ThemeDensity.factor(theme.choice("density")),
                gridGap = if (changed("grid_gap")) theme.number("grid_gap").toFloat() else null,
                cardScale = theme.number("card_scale").toFloat(),
                libraryImage = library,
                navImage = nav,
            )
        }

        /** The surfaces the focus fill can sit on. */
        private val SURFACE_KEYS = listOf("window_bg", "library_bg", "card_bg", "popover_bg", "details_bg", "sidebar_bg")

        /** How far a selected item's focus stands out from its selection tone. */
        const val ON_SOFT_FLOOR = 1.25

        /** The least contrast the base's own focus fill has against its own surfaces. */
        fun baseFloor(base: String): Double {
            val soft = ThemeKeys.BY_NAME.getValue("selection_bg").default(base) as Int
            return SURFACE_KEYS.minOf { SkinColorMath.contrast(soft, ThemeKeys.BY_NAME.getValue(it).default(base) as Int) }
        }

        private fun image(theme: UserTheme, slot: String): ThemeImage? {
            val path = theme.values["${slot}_image"] as? String ?: return null
            return ThemeImage(
                path,
                theme.choice("${slot}_image_fit"),
                theme.number("${slot}_image_dim").toFloat(),
                theme.number("${slot}_image_blur").toFloat(),
            )
        }

        /** [color] a step lighter when it is dark, darker when it is light: a pressed or focused version of it. */
        fun step(color: Int, t: Float): Int =
            if (SkinColorMath.luminance(color) < 0.18) SkinColorMath.lighten(color, t) else SkinColorMath.darken(color, t)

        /**
         * [color] moved toward white (on a dark base) or black (on a light one) until it has at least [floor]
         * contrast with every one of [against]; the other way when that way cannot get there.
         */
        fun withFloor(color: Int, against: List<Int>, dark: Boolean, floor: Double = FOCUS_FLOOR): Int {
            fun ok(c: Int) = against.all { SkinColorMath.contrast(c, it) >= floor }
            if (ok(color)) return color
            for (direction in listOf(dark, !dark)) {
                for (i in 1..20) {
                    val t = i / 20f
                    val candidate = if (direction) SkinColorMath.lighten(color, t) else SkinColorMath.darken(color, t)
                    if (ok(candidate)) return candidate
                }
            }
            // Nothing reaches the floor against every surface at once (they span black to white): the best of them.
            val all = (0..20).flatMap { i -> listOf(SkinColorMath.lighten(color, i / 20f), SkinColorMath.darken(color, i / 20f)) }
            return all.maxBy { c -> against.minOf { SkinColorMath.contrast(c, it) } }
        }
    }
}
