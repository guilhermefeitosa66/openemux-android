package com.openemux.mobile.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.openemux.mobile.data.theme.OeToken
import com.openemux.mobile.data.theme.ReadyThemes
import com.openemux.mobile.data.theme.UserTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.retroarch.R

/** The theme's colors; each reads [dark], so the whole UI recomposes when it flips. */
object Oe {
    /** The system's night mode; the activities set it from their configuration. */
    var systemDark by mutableStateOf(true)

    val dark: Boolean
        get() = when (ThemePref.value) {
            ThemePref.LIGHT -> false
            ThemePref.DARK -> true
            // A user theme (issue 1018) is its base; one that is gone paints as System.
            else -> ActiveTheme.resolved?.dark ?: systemDark
        }

    fun syncSystem(context: Context) {
        systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun pick(dark: Long, light: Long) = Color(if (this.dark) dark else light)

    /** The user theme's color for [token] (issue 1018), else the app's own. */
    private fun pick(token: OeToken, dark: Long, light: Long) = ActiveTheme.color(token) ?: pick(dark, light)

    val Bg: Color get() = pick(OeToken.BG, 0xFF121216, 0xFFF6F5F9)
    /** Under the tabs: the page's own color unless a user theme gives the navigation one. */
    val NavBg: Color get() = ActiveTheme.color(OeToken.NAV_BG) ?: Bg
    /** Behind the library's screens (the shelf, a console's games, Recent). */
    val LibraryBg: Color get() = ActiveTheme.color(OeToken.LIBRARY_BG) ?: Bg
    /** The consoles' cards on the shelf, when a user theme gives them one color (else each console's own). */
    val ConsoleList: Color? get() = ActiveTheme.color(OeToken.CONSOLE_LIST)
    /** Behind a game's details. */
    val DetailsBg: Color get() = ActiveTheme.color(OeToken.DETAILS_BG) ?: Bg
    val GameBg: Color get() = pick(0xFF0B0B0E, 0xFF0B0B0E)
    val Surface: Color get() = pick(OeToken.SURFACE, 0xFF1C1C23, 0xFFFFFFFF)
    val Surface2: Color get() = pick(OeToken.SURFACE2, 0xFF24242C, 0xFFEFEEF4)
    val Sheet: Color get() = pick(OeToken.SHEET, 0xFF1A1A20, 0xFFFFFFFF)
    val Border: Color get() = pick(OeToken.BORDER, 0xFF2B2B35, 0xFFE2E0EA)
    val Divider: Color get() = pick(OeToken.DIVIDER, 0xFF2A2A33, 0xFFE7E5ED)
    val Text: Color get() = pick(OeToken.TEXT, 0xFFF3F3F6, 0xFF17171C)
    val Muted: Color get() = pick(OeToken.MUTED, 0xFFA3A3B2, 0xFF5C5C6B)
    /** A tab's label and icon, and the current tab's. */
    val NavText: Color get() = ActiveTheme.color(OeToken.NAV_TEXT) ?: Muted
    val NavTextActive: Color get() = ActiveTheme.color(OeToken.NAV_TEXT_ACTIVE) ?: AccentText
    /** A user theme's color for the plain icons; null keeps each icon's own. */
    val IconTint: Color? get() = ActiveTheme.color(OeToken.ICON)
    val Faint: Color get() = pick(OeToken.FAINT, 0xFF8C8C9A, 0xFF6B6B7E)
    val Chevron: Color get() = pick(OeToken.CHEVRON, 0xFF6E6E7C, 0xFF8A8A9A)
    val Accent: Color get() = FocusTones.Accent
    /** The label on an [Accent] fill. */
    val OnAccent: Color get() = ActiveTheme.color(OeToken.ON_ACCENT) ?: Color.White
    val AccentBright: Color get() = pick(OeToken.ACCENT_BRIGHT, 0xFF8B5CF6, 0xFF7C3AED)
    val AccentText: Color get() = pick(OeToken.ACCENT_TEXT, 0xFFB79CFF, 0xFF6D28D9)
    val AccentTint: Color get() = if (dark) FocusTones.TintDark else FocusTones.TintLight
    val AccentSoft: Color get() = if (dark) FocusTones.SoftDark else FocusTones.SoftLight
    val AccentOnSoft: Color get() = pick(OeToken.ACCENT_ON_SOFT, 0xFFD4C4FF, 0xFF5B21B6)
    /** The list view's zebra stripe (issue 874): a few percent of white over the dark page, of black over the light one. */
    val RowAlt: Color get() = pick(0x0BFFFFFF, 0x0A000000)
    val Control: Color get() = pick(OeToken.CONTROL, 0xFF1E1E25, 0xFFEBEAF1)
    val Raised: Color get() = pick(OeToken.RAISED, 0xFF2A2A33, 0xFFE9E7EF)
    val Star: Color get() = pick(0xFFF5C543, 0xFFB07A00)
    /** The star on a fill of the text colour (a selected chip): the other theme's gold, which reads on it. */
    val StarOnText: Color get() = pick(0xFFB07A00, 0xFFF5C543)
    val Ok: Color get() = pick(0xFF7DD3A0, 0xFF1F9D57)
    val OkBg: Color get() = pick(0xFF14291E, 0xFFE3F5EA)
    val OkText: Color get() = pick(0xFF9BE3B8, 0xFF13703F)
    val Warn: Color get() = pick(0xFFF5B85A, 0xFFC77800)
    val WarnBg: Color get() = pick(0xFF2A2215, 0xFFFBF0DC)
    val WarnText: Color get() = pick(0xFFF5C27A, 0xFF9A5B00)
    val Danger: Color get() = pick(0xFFFF8A80, 0xFFC0302A)
    val DangerBg: Color get() = pick(0xFF2A1C1F, 0xFFFBE6E4)
}

/**
 * The background of an item the controller has focused (issue 951), replacing the ring the focus used to draw.
 * Over nothing, or over the item's own plain background, it is the tone of a toggle that is on ([Oe.AccentSoft]).
 * An item already filled with that tone or with [Oe.AccentTint] (a selected choice) goes a step deeper, so the focus still shows on it;
 * an accent button goes a deeper violet, so its white label stays readable. [dark] picks the theme's tones.
 */
fun focusFill(base: Color?, dark: Boolean): Color {
    val soft = if (dark) FocusTones.SoftDark else FocusTones.SoftLight
    val tint = if (dark) FocusTones.TintDark else FocusTones.TintLight
    return when (base) {
        FocusTones.Accent -> FocusTones.AccentFocus
        // Selected, in the soft tone or the lighter tint (a tab, a picked tile): one step deeper.
        soft, tint -> if (dark) FocusTones.OnDark else FocusTones.OnLight
        else -> soft
    }
}

/**
 * The tones [focusFill] picks from; the soft ones are [Oe.AccentSoft]'s, the accent [Oe.Accent]'s. A user theme
 * (issue 1018) gives its own, already kept visible against its surfaces, for both modes at once.
 */
internal object FocusTones {
    val Accent: Color get() = ActiveTheme.color(OeToken.ACCENT) ?: Color(0xFF7C3AED)
    val AccentFocus: Color get() = ActiveTheme.color(OeToken.ACCENT_FOCUS) ?: Color(0xFF5B21B6)
    val SoftDark: Color get() = ActiveTheme.color(OeToken.SOFT) ?: Color(0xFF3B2A6B)
    val SoftLight: Color get() = ActiveTheme.color(OeToken.SOFT) ?: Color(0xFFE4D9FD)
    val OnDark: Color get() = ActiveTheme.color(OeToken.ON_SOFT) ?: Color(0xFF4E3A8C)
    val OnLight: Color get() = ActiveTheme.color(OeToken.ON_SOFT) ?: Color(0xFFD3C2FB)
    val TintDark: Color get() = ActiveTheme.color(OeToken.TINT) ?: Color(0xFF221C33)
    val TintLight: Color get() = ActiveTheme.color(OeToken.TINT) ?: Color(0xFFEFE8FD)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Grotesk = FontFamily(
    Font(R.font.grotesk_medium, FontWeight.Medium),
    Font(R.font.grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.grotesk_bold, FontWeight.Bold),
)

/**
 * The text styles, built once per theme (issue 919): each getter still reads [Oe.dark], so a composable that uses
 * one recomposes when the theme flips, and gets the same instance until then.
 */
object Type {
    private class Styles {
        val display = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp, color = Oe.Text)
        val title = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = Oe.Text)
        val eyebrow = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp, color = Oe.AccentText)
        val section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Oe.Text)
        val sectionCaps = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp, color = Oe.Muted)
        val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = Oe.Text)
        val bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Oe.Text)
        val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Oe.Muted)
        val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Oe.Muted)
        val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Oe.OnAccent)
        val tag = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.4.sp)
    }

    private var darkStyles: Styles? = null
    private var lightStyles: Styles? = null
    /** A user theme's styles (issue 1018), with the theme they were built for. */
    private var themed: Pair<Any, Styles>? = null

    private fun styles(): Styles {
        val theme = ActiveTheme.resolved
        if (theme != null) {
            themed?.let { (owner, styles) -> if (owner === theme) return styles }
            return Styles().also { themed = theme to it }
        }
        return if (Oe.dark) darkStyles ?: Styles().also { darkStyles = it } else lightStyles ?: Styles().also { lightStyles = it }
    }

    val Display: TextStyle get() = styles().display
    val Title: TextStyle get() = styles().title
    val Eyebrow: TextStyle get() = styles().eyebrow
    val Section: TextStyle get() = styles().section
    val SectionCaps: TextStyle get() = styles().sectionCaps
    val Body: TextStyle get() = styles().body
    val BodyMedium: TextStyle get() = styles().bodyMedium
    val Small: TextStyle get() = styles().small
    val Caption: TextStyle get() = styles().caption
    val Button: TextStyle get() = styles().button
    val Tag: TextStyle get() = styles().tag
}

/** The theme the user picked: "system", "light", "dark" or a user theme's id (issue 1018). */
object ThemePref {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    var value by mutableStateOf(SYSTEM)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    /** The stored choice, user theme included; anything else stored reads as System. */
    fun parse(stored: String?): String = when {
        stored == LIGHT || stored == DARK || UserTheme.isCustomId(stored) || ReadyThemes.isReadyId(stored) -> stored!!
        else -> SYSTEM
    }

    fun load(context: Context) {
        // A user theme that was saved under a ready-made theme's id (before issue 1018's second part) moves to a
        // free id; the choice follows it, so the person keeps their own theme.
        val moved = runCatching { ActiveTheme.store(context).moveReserved() }.getOrDefault(emptyMap())
        val stored = prefs(context).getString("theme", SYSTEM)
        value = parse(moved[stored] ?: stored)
        if (moved.containsKey(stored)) prefs(context).edit().putString("theme", value).apply()
        ActiveTheme.load(context, value)
    }

    fun set(context: Context, theme: String) {
        value = parse(theme)
        prefs(context).edit().putString("theme", value).apply()
        ActiveTheme.load(context, value)
    }

    /** The user theme or ready-made theme picked, or null for System, Light and Dark. */
    val custom: String? get() = value.takeIf { UserTheme.isCustomId(it) || ReadyThemes.isReadyId(it) }
}

/** How the hint bar names the buttons: "auto" (the pad in use), or a family forced. */
object ButtonLabelsPref {
    var value by mutableStateOf(ButtonLabelsKeys.AUTO)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    fun load(context: Context) {
        value = ButtonLabelsKeys.parse(prefs(context).getString("button_labels", null))
    }

    fun set(context: Context, labels: String) {
        value = labels
        prefs(context).edit().putString("button_labels", labels).apply()
    }
}

@Composable
fun OpenEmuxTheme(content: @Composable () -> Unit) {
    // A user theme's text size (issue 1018) on top of the system's own.
    val scale = ActiveTheme.resolved?.fontScale ?: 1f
    val density = LocalDensity.current
    val themed = remember(density, scale) { if (scale == 1f) density else Density(density.density, density.fontScale * scale) }
    CompositionLocalProvider(LocalDensity provides themed) { OpenEmuxMaterial(content) }
}

@Composable
private fun OpenEmuxMaterial(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (Oe.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = Oe.Accent,
            background = Oe.Bg,
            surface = Oe.Sheet,
            onSurface = Oe.Text,
            onBackground = Oe.Text,
        ),
        content = content,
    )
}
