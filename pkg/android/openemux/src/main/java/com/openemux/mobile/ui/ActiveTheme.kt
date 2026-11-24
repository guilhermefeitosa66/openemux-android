package com.openemux.mobile.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openemux.mobile.data.theme.OeToken
import com.openemux.mobile.data.theme.ResolvedTheme
import com.openemux.mobile.data.theme.ThemeStore
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.theme.AndroidThemeCodec
import java.io.File

/**
 * The user theme the app paints with (issue 1018), or null for System, Light and Dark. [Oe], [Type], the shapes
 * and the sizes read [resolved], so picking or saving a theme recomposes the app without a restart.
 */
object ActiveTheme {
    var resolved: ResolvedTheme? by mutableStateOf(null)
        private set

    /** The pictures' folder of the active theme, for the backgrounds. */
    var folder: File? = null
        private set

    fun store(context: Context): ThemeStore =
        ThemeStore(File(context.filesDir, "themes"), AndroidThemeCodec) { Log.i("OpenEmuxTheme", it) }

    /** Reads [id] from disk and paints with it; a built-in id, or a theme that is gone, paints the app's own look. */
    fun load(context: Context, id: String) {
        val theme = if (UserTheme.isCustomId(id)) store(context).get(id) else null
        apply(theme, theme?.let { File(File(context.filesDir, "themes"), it.id) })
    }

    fun apply(theme: UserTheme?, folder: File?) {
        this.folder = folder
        resolved = theme?.let(ResolvedTheme::resolve)
    }

    fun color(token: OeToken): Color? = resolved?.color(token)?.let { Color(it) }
}

/** [this] corner (or any size drawn in proportion to a card's) as the theme's corner radius scales it. */
val Dp.themed: Dp get() = this * (ActiveTheme.resolved?.cornerScale ?: 1f)

/** A row's height or a screen's padding as the theme's density scales it, never under the touch minimum when it was over. */
val Dp.dense: Dp get() {
    val scaled = this * (ActiveTheme.resolved?.density ?: 1f)
    return if (this >= MinTouchTarget) maxOf(scaled, MinTouchTarget) else scaled
}

/** The theme's border and shadow around a surface of [shape]: nothing when the theme has neither (the app's own look). */
fun Modifier.themedEdge(shape: Shape): Modifier {
    val theme = ActiveTheme.resolved ?: return this
    var m = this
    if (theme.shadowStrength > 0f) {
        val shadow = Color(theme.shadowColor)
        m = m.shadow((12 * theme.shadowStrength).dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
    }
    val border = theme.borderColor
    if (theme.borderWidth > 0f && border != null) m = m.border(theme.borderWidth.dp, Color(border), shape)
    return m
}
