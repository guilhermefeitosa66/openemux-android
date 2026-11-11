package com.openemux.mobile.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** How long a name that does not fit holds still before it scrolls, and again before each repeat. */
private const val MARQUEE_PAUSE_MS = 1500

/**
 * Whether a game's name scrolls (issue 869). Never with the system's animations removed (a scale of 0);
 * with the gamepad look only the focused item's, as a TV launcher does, so a dense grid is not a wall of
 * moving text; by touch, every one on screen that does not fit.
 */
internal fun marqueeOn(animatorScale: Float, padLook: Boolean, focused: Boolean): Boolean =
    animatorScale > 0f && (!padLook || focused)

/** The system's animator duration scale ("Remove animations" sets 0), followed while the app is open. */
@Composable
fun rememberAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    val read = { runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) }
    var scale by remember { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/**
 * A game's name on one line. One that does not fit scrolls right to left and loops, so it can be read in
 * full; one that fits stays still. When it may not scroll ([marqueeOn]), it ends in an ellipsis as before.
 * [focused] is the item's gamepad focus, which only matters with the gamepad look.
 */
@Composable
fun GameName(
    text: String, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign? = null, focused: Boolean = false,
) {
    if (marqueeOn(rememberAnimatorScale(), InputMode.gamepad, focused)) {
        Text(
            text, style = style, textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = modifier.basicMarquee(
                iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_PAUSE_MS, repeatDelayMillis = MARQUEE_PAUSE_MS,
            ),
        )
    } else {
        Text(text, style = style, textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}
