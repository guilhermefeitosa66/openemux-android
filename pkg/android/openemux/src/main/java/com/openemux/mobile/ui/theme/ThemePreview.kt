package com.openemux.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.theme.OeToken
import com.openemux.mobile.data.theme.ResolvedTheme
import com.openemux.mobile.data.theme.ThemeDraft
import com.openemux.mobile.data.theme.ThemeImage
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.ActiveTheme
import com.openemux.mobile.ui.Inter
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.gamepadFocus
import com.retroarch.R
import java.io.File

/** The colors the preview paints, from the draft: the resolved override when there is one, else the key's own. */
private class Swatches(val theme: UserTheme, val r: ResolvedTheme) {
    fun c(token: OeToken, key: String) = Color(r.color(token) ?: theme.color(key))
    val bg get() = c(OeToken.BG, "window_bg")
    val nav get() = c(OeToken.NAV_BG, "sidebar_bg")
    val library get() = c(OeToken.LIBRARY_BG, "library_bg")
    val details get() = c(OeToken.DETAILS_BG, "details_bg")
    val control get() = c(OeToken.CONTROL, "headerbar_bg")
    val card get() = c(OeToken.SURFACE, "card_bg")
    val sheet get() = c(OeToken.SHEET, "popover_bg")
    val text get() = c(OeToken.TEXT, "text_primary")
    val muted get() = c(OeToken.MUTED, "text_secondary")
    val navText get() = c(OeToken.NAV_TEXT, "sidebar_text_secondary")
    val navActive get() = c(OeToken.NAV_TEXT_ACTIVE, "sidebar_text_primary")
    val accent get() = c(OeToken.ACCENT_TEXT, "accent")
    val button get() = c(OeToken.ACCENT, "accent_button_bg")
    val onButton get() = c(OeToken.ON_ACCENT, "accent_button_fg")
    val soft get() = c(OeToken.SOFT, "selection_bg")
    val icon get() = r.color(OeToken.ICON)?.let { Color(it) } ?: muted
}

/**
 * A miniature of the app painted with [draft] (issue 1018): the library (top bar, chips, cards with one focused, the
 * tabs) or, with [details], a game's page. Drawn from the draft's own values, so it changes as each control moves
 * and the app around it keeps its look until Save. [onToggle] switches between the two.
 */
@Composable
fun ThemePreview(draft: ThemeDraft, folder: File?, details: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val r = ResolvedTheme.resolve(draft.theme)
    val s = Swatches(draft.theme, r)
    val corner = { dp: Dp -> dp * r.cornerScale * 0.5f }
    // The app around the preview is already scaled by the active theme's text size: only the draft's shows here.
    val font = r.fontScale / (ActiveTheme.resolved?.fontScale ?: 1f)
    fun style(size: Float, color: Color, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Inter, fontSize = (size * font).sp, color = color, fontWeight = weight)
    // Pictures picked but not saved yet are in the staging folder; the preview reads them where they are.
    fun image(key: String, img: ThemeImage?): Pair<ThemeImage?, File?> {
        val file = draft.imageFile(key, folder) ?: return null to null
        return img?.copy(path = file.name) to file.parentFile
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.theme_preview).uppercase(), style = Type.Caption.copy(letterSpacing = 1.sp), modifier = Modifier.weight(1f))
            Text(stringResource(if (details) R.string.theme_preview_to_library else R.string.theme_preview_to_details), style = Type.Caption.copy(color = Oe.AccentText))
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Oe.Border, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onToggle),
        ) {
            if (!details) {
                Column(Modifier.fillMaxSize().background(s.library)) {
                    val (libImg, libDir) = image("library_image", r.libraryImage)
                    ThemeBackdrop(libImg, libDir, s.library, Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Super Nintendo", style = style(13f, s.text, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
                                repeat(2) {
                                    Box(Modifier.padding(start = 4.dp).size((18 * r.iconScale).dp).clip(CircleShape).background(s.control), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size((7 * r.iconScale).dp).clip(CircleShape).border(1.5.dp, s.icon, CircleShape))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(corner(20.dp))).background(s.button).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text(stringResource(R.string.library_chip_all), style = style(9f, s.onButton, FontWeight.SemiBold))
                                }
                                listOf(stringResource(R.string.library_favorites), "NES").forEach {
                                    Box(Modifier.clip(RoundedCornerShape(corner(20.dp))).border(1.dp, s.muted.copy(alpha = 0.5f), RoundedCornerShape(corner(20.dp))).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text(it, style = style(9f, s.text))
                                    }
                                }
                            }
                            val gap = (r.gridGap ?: 16f) * 0.4f
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                                listOf("Alien Planet", "Beach Volley", "Brawl City").forEachIndexed { i, title ->
                                    MiniCard(title, i == 0, s, r, corner(18.dp), Modifier.weight(1f).fillMaxHeight(), ::style)
                                }
                            }
                        }
                    }
                    val (navImg, navDir) = image("sidebar_image", r.navImage)
                    ThemeBackdrop(navImg, navDir, s.nav, Modifier.fillMaxWidth().height(34.dp).background(s.nav)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                            listOf(R.string.settings_tab_library, R.string.settings_tab_recent, R.string.settings_tab_import, R.string.settings_tab_settings).map { stringResource(it) }.forEachIndexed { i, label ->
                                Text(label, style = style(8.5f, if (i == 0) s.navActive else s.navText, if (i == 0) FontWeight.SemiBold else FontWeight.Normal))
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().background(s.details).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier.width(70.dp).fillMaxHeight().themedPreviewEdge(r, corner(12.dp)).clip(RoundedCornerShape(corner(12.dp))).background(s.card),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("SUPER NINTENDO", style = style(8f, s.accent, FontWeight.SemiBold))
                            Text("Alien Planet Quest", style = style(13f, s.text, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.theme_preview_played), style = style(9f, s.muted), maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(corner(16.dp))).background(s.button), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.detail_play), style = style(10f, s.onButton, FontWeight.SemiBold))
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(corner(18.dp))).background(s.sheet).padding(8.dp)) {
                        Text(stringResource(R.string.detail_save_states), style = style(10f, s.text, FontWeight.SemiBold))
                        Text(stringResource(R.string.theme_preview_slot), style = style(9f, s.muted))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCard(title: String, focused: Boolean, s: Swatches, r: ResolvedTheme, corner: Dp, modifier: Modifier, style: (Float, Color, FontWeight) -> TextStyle) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier.themedPreviewEdge(r, corner).clip(shape).background(if (focused) s.soft else s.card)
            .then(if (focused) Modifier.border(2.dp, s.accent, shape) else Modifier).padding(4.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        val inner = (corner * 0.6f)
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(inner)).background(s.muted.copy(alpha = 0.45f)))
        Text(title, style = style(8f, if (focused) s.text else s.muted, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun Modifier.themedPreviewEdge(r: ResolvedTheme, corner: Dp): Modifier {
    val shape = RoundedCornerShape(corner)
    var m = this
    if (r.shadowStrength > 0f) {
        val c = Color(r.shadowColor)
        m = m.shadow((6 * r.shadowStrength).dp, shape, clip = false, ambientColor = c, spotColor = c)
    }
    val border = r.borderColor
    if (r.borderWidth > 0f && border != null) m = m.border((r.borderWidth * 0.5f).coerceAtLeast(0.5f).dp, Color(border), shape)
    return m
}
