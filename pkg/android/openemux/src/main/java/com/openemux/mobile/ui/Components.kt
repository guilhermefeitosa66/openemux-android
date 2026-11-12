package com.openemux.mobile.ui

import androidx.annotation.PluralsRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.DEFAULT_COVER_ASPECT
import com.openemux.mobile.data.coverAspect
import com.openemux.mobile.data.Rom
import com.openemux.mobile.launch.CartArt
import com.retroarch.R
import java.io.File

/** Stroke icons from the design, as SVG path data on a 24-unit grid. */
object Icons {
    const val Back = "M15 18l-6-6 6-6"
    const val Chevron = "M9 6l6 6-6 6"
    const val ChevronDown = "M6 9l6 6 6-6"
    const val ChevronUp = "M6 15l6-6 6 6"
    const val Search = "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14M20 20l-3.5-3.5"
    const val Sort = "M4 7h16M7 12h10M10 17h4"
    const val Cart = "M4 5h16v11l-2 3H6l-2-3zM8 8h8v5H8z"
    const val Grid = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    /** The library's Size button: a small square and a large one. */
    const val Size = "M3 14h7v7H3zM12 3h9v9h-9z"
    const val List = "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"
    const val Clock = "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M12 8v4l3 2"
    const val Download = "M12 4v11M7 10l5 5 5-5M5 20h14"
    const val Upload = "M12 15V3M7 8l5-5 5 5M5 20h14"
    const val Sliders = "M4 7h10M18 7h2M4 17h4M12 17h8M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4"
    const val Play = "M7 4.5v15l13-7.5z"
    const val Star = "M12 3l2.8 5.9 6.2.8-4.6 4.3 1.2 6.3L12 17.3 6.4 20.3l1.2-6.3L3 9.7l6.2-.8z"
    const val Close = "M6 6l12 12M18 6L6 18"
    const val Plus = "M12 5v14M5 12h14"
    const val Check = "M5 12.5l4.5 4.5L19 7.5"
    const val Globe = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3.5 12h17M12 3c2.4 2.6 3.6 5.6 3.6 9s-1.2 6.4-3.6 9M12 3c-2.4 2.6-3.6 5.6-3.6 9s1.2 6.4 3.6 9"
    const val Info = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 11v5M12 8h.01"
    const val Warn = "M12 3l9.5 17h-19zM12 10v4.5M12 17.5h.01"
    const val Alert = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 7.5v5.5M12 16.5h.01"
    const val Folder = "M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5zM12 11v6M9 14l3 3 3-3"
    const val File = "M6 3h8l4 4v14H6zM14 3v4h4M12 11v6M9 14l3 3 3-3"
    const val Chip = "M5 5h14v14H5zM9 9h6v6H9zM9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3"
    const val Save = "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6"
    // RetroArch's savestate/loadstate icons (libretro/retroarch-assets, src/xmb/monochrome,
    // CC-BY 4.0), scaled from 64 to 24 units. Filled.
    const val SaveState = "M5.766 18C5.273 18 4.875 18.335 4.875 18.75C4.875 19.165 5.273 19.5 5.766 19.5L18.234 19.5C18.727 19.5 19.125 19.165 19.125 18.75C19.125 18.335 18.727 18 18.234 18L5.766 18zM11.11 4.5C10.618 4.5 10.125 4.835 10.125 5.25v5.25 0.001L6.75 10.5 12 16.5 17.25 10.5 13.922 10.5 13.875 5.25c-0.004-0.415-0.493-0.75-0.985-0.75z"
    const val LoadState = "M5.766 18C5.273 18 4.875 18.335 4.875 18.75C4.875 19.165 5.273 19.5 5.766 19.5L18.234 19.5C18.727 19.5 19.125 19.165 19.125 18.75C19.125 18.335 18.727 18 18.234 18L5.766 18zM11.11 16.5C10.618 16.5 10.125 16.165 10.125 15.75v-5.25-0.001L6.75 10.5 12 4.5 17.25 10.5 13.922 10.5 13.875 15.75c-0.004 0.415-0.493 0.75-0.985 0.75z"
    const val Menu = "M4 7h16M4 12h16M4 17h16"
    const val Load = "M4 12a8 8 0 1 0 2.3-5.6M4 4v5h5"
    const val FastForward = "M3 6v12l9-6zM12 6v12l9-6z"
    /** Slow motion: a play triangle inside a dashed ring of 8 dashes. Stroked, like the turbo bolt. */
    const val SlowMotion = "M13.17 3.08A9 9 0 0 1 17.48 4.86M19.14 6.52A9 9 0 0 1 20.92 10.83M20.92 13.17A9 9 0 0 1 19.14 17.48" +
        "M17.48 19.14A9 9 0 0 1 13.17 20.92M10.83 20.92A9 9 0 0 1 6.52 19.14M4.86 17.48A9 9 0 0 1 3.08 13.17" +
        "M3.08 10.83A9 9 0 0 1 4.86 6.52M6.52 4.86A9 9 0 0 1 10.83 3.08M10 8.5v7l5.5-3.5z"
    /** Anchor mode (issue 841): an anchor, ring, shank, stock and arms. Stroked, like the turbo bolt. */
    const val Anchor = "M12 2.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M12 7.5V21M8 11h8M4.5 13.5a7.5 7.5 0 0 0 15 0"
    const val Camera = "M4 8h3l2-3h6l2 3h3v11H4zM12 9.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7"
    const val Shader = "M3 5h18v14H3zM3 10h18M3 14h18M9 5v14M15 5v14"
    /** Two screens, one over the other: the NDS screen layout. */
    const val Screens = "M6 3h12v8H6zM6 13h12v8H6z"
    const val Audio = "M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7"
    /** Leaving: a door frame open on the right with an arrow going out (the pause sheet's Quit tile). */
    const val Quit = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"
    const val Mute = "M4 9v6h4l5 4V5L8 9zM17 9l4 6M21 9l-4 6"
    const val Reset = "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5"
    const val Trash = "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"
    /** A padlock: a bundled skin, which never changes (issue 782). */
    const val Lock = "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4"
    /** A picture: the artwork manager (issue 746). */
    // The artwork editor's tools (issue 753), from its design.
    const val Crop = "M6 2v16h16M2 6h16v16"
    const val RotateLeft = "M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4"
    const val RotateRight = "M20 12a8 8 0 1 1-2.4-5.7M20 4v4h-4"
    /** Undo: back to the original image. Not a circle, so it cannot be read as a rotation. */
    const val Undo = "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11"
    const val FlipH = "M12 3v18M8 7l-5 5 5 5M16 7l5 5-5 5"
    const val FlipV = "M3 12h18M7 8l5-5 5 5M7 16l5 5 5-5"
    const val Image = "M4 4h16v16H4zM9 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M20 15l-5-5-11 10"
    const val Expand = "M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7"
    const val Dots = "M5 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2M19 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2"
    const val Trophy = "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8M10 17h4"
    const val Eye = "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6"
    const val EyeOff = "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6M4 4l16 16"
    // Share nearby (#591).
    const val Share = "M6 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M18 3.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M18 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6"
    const val Desktop = "M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM8 20h8M12 16v4"
    const val Phone = "M8.5 2.5h7a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 19V5a2.5 2.5 0 0 1 2.5-2.5zM11 18.5h2"
    const val Tablet = "M6.5 3h11A2.5 2.5 0 0 1 20 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 18.5v-13A2.5 2.5 0 0 1 6.5 3zM11 18h2"
    const val WifiOff = "M2 8.5a15 15 0 0 1 20 0M5.5 12a10 10 0 0 1 13 0M9 15.5a5 5 0 0 1 6 0M12 19h.01M3 3l18 18"
    const val Qr = "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2M20 14v2M14 18h2v2M18 18h2v2"
    const val Pencil = "M4 20h4L19 9l-4-4L4 16v4z"
    const val Copy = "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2zM16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"
    /** The on-screen controls: a phone with a D-pad on it (the pause sheet's Touch controls tile, issue 865). */
    const val TouchControls = "M8.5 2.5h7a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 19V5a2.5 2.5 0 0 1 2.5-2.5zM12 8.5v5M9.5 11h5"
    /** A skin: a gamepad's outline, its D-pad and two buttons. */
    const val Skin = "M7.5 7h9a4.5 4.5 0 0 1 0 9c-1.4 0-2.2-.6-3-1.6h-3c-.8 1-1.6 1.6-3 1.6a4.5 4.5 0 0 1 0-9zM7.5 9.8v3.4M5.8 11.5h3.4M15.6 10.6h.01M17.4 12.4h.01"
}

@Composable
fun Icon(path: String, color: Color, size: Dp = 20.dp, stroke: Float = 2f, filled: Boolean = false, modifier: Modifier = Modifier) {
    val vector = ImageVector.Builder(defaultWidth = size, defaultHeight = size, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = if (filled) SolidColor(color) else null,
            stroke = if (filled) null else SolidColor(color),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
    Image(vector, contentDescription = null, modifier = modifier.size(size))
}

/** What to draw as the ROM's box art; follows the artwork manager's changes (issue 746). */
@Composable
fun coverFor(rom: Rom): String? {
    val app = LocalActions.current.app
    // Read to recompose on a change; the lookup itself runs every time, so a cover that arrives later shows.
    app.artwork.changes.collectAsState().value
    return app.covers.urlFor(rom)
}

/** The ROM's cartridge label, once [com.openemux.mobile.data.Labels] has it. */
@Composable
fun labelFor(rom: Rom): File? {
    val found by LocalActions.current.app.labels.found.collectAsState()
    return found[rom.key]
}

/** Aspect of what [Cartridge] draws: the frame, or a box for consoles without one. */
val Console.cartAspect: Float get() = frame?.aspect ?: DEFAULT_COVER_ASPECT

/**
 * The cartridge: label art cropped into the label window, frame art on top.
 * Consoles with no frame (discs, Atari...) show the box art instead, like the desktop: [cropped] to the
 * console's cover shape in the library's grid, so its rows line up (issue 882), whole everywhere else.
 */
@Composable
fun Cartridge(
    console: Console,
    label: File?,
    title: String,
    color: String,
    modifier: Modifier = Modifier,
    shadow: Boolean = true,
    pending: Boolean = false,
    cover: String? = null,
    cropped: Boolean = false,
) {
    val context = LocalContext.current
    val cartFrame = console.frame
    if (cartFrame == null) {
        // No cartridge for this console: its box art, in the console's cover shape in the grid, as in the covers view.
        val shape = if (cropped) console.coverAspect else null
        if (cover != null) {
            CoverArt(cover, title, modifier.clip(RoundedCornerShape(10.dp)), aspect = shape)
        } else {
            Box(modifier.aspectRatio(shape ?: DEFAULT_COVER_ASPECT).clip(RoundedCornerShape(8.dp)).background(Oe.Surface2), contentAlignment = Alignment.Center) {
                BoxTitle(title, console)
            }
        }
        return
    }
    val frame = CartArt.frameRes(context, console, color)
    BoxWithConstraints(modifier.aspectRatio(cartFrame.aspect)) {
        val w = maxWidth
        val h = maxHeight
        val l = cartFrame.label
        Box(
            Modifier
                .offset(w * l.left, h * l.top)
                .size(w * l.width, h * l.height)
                .background(Color(0xFFDEDBD4))
        ) {
            if (label != null) {
                SubcomposeAsyncImage(
                    model = label,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    error = { BlankLabel(title) },
                    loading = { Box(Modifier.fillMaxSize().background(Color(0xFFDEDBD4))) },
                )
            } else if (pending) {
                LabelShimmer()
            } else {
                BlankLabel(title)
            }
        }
        Image(
            painterResource(frame),
            contentDescription = title,
            modifier = Modifier.fillMaxSize().let { if (shadow) it else it },
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun BoxTitle(title: String, console: Console) {
    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(console.short, style = Type.Tag.copy(color = Oe.Faint))
        Text(
            title,
            style = Type.Caption.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, color = Oe.Muted, fontSize = 11.sp),
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BlankLabel(title: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFFDEDBD4)).padding(6.dp), contentAlignment = Alignment.Center) {
        Text(
            title,
            style = Type.Caption.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, color = Color(0xFF28282E), fontSize = 11.sp),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RoundIconButton(
    path: String, label: String, onClick: () -> Unit, tint: Color = Oe.Text, background: Color = Oe.Control, filled: Boolean = false,
    // False for a screen's back arrow: B already goes back, so the pad never stops on it (issue 701).
    padFocus: Boolean = true,
) {
    Box(
        Modifier
            .size(44.dp)
            .then(if (padFocus) Modifier.gamepadFocus(corner = 22.dp) else Modifier.touchOnly())
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(path, tint, 20.dp, filled = filled) }
}

/** A screen's back link: for the finger only, the pad goes back with B and never stops here (issue 701). */
@Composable
fun BackLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).touchOnly().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Back, Oe.Muted, 20.dp)
        Text(label, style = Type.BodyMedium.copy(color = Oe.Muted))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, height: Dp = 52.dp, enabled: Boolean = true, sidePadding: Dp = 0.dp) {
    Row(
        modifier
            .height(height)
            .gamepadFocus(corner = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Oe.Accent else Oe.Surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = sidePadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, Color.White, 18.dp, filled = icon == Icons.Play)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.Button)
    }
}

/**
 * The quiet button. [compact] is the small one that sits at the end of a row
 * (40 dp, the label at 14 sp); [busy] swaps its icon for a spinner and stops it taking taps.
 * [enabled] false dims it and stops it taking taps too, with no spinner: nothing is running.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    icon: String? = null,
    compact: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val corner = if (compact) 14.dp else 16.dp
    val inert = busy || !enabled
    Row(
        modifier
            .height(if (compact) 40.dp else height)
            .gamepadFocus(corner = corner)
            .clip(RoundedCornerShape(corner))
            .background(Oe.Surface2)
            // Busy, it stays focusable and only ignores the press: a disabled clickable drops the pad's focus,
            // which then lands on another item, and the sheet that follows gives focus back there (issue 668).
            .clickable(role = Role.Button) { if (!inert) onClick() }
            .semantics { if (inert) disabled() }
            .alpha(if (inert) DISABLED_ALPHA else 1f)
            .padding(horizontal = if (compact) 14.dp else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 16.dp else 18.dp
        if (busy) {
            CircularProgressIndicator(Modifier.size(iconSize), color = Oe.Text, strokeWidth = 2.dp)
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        } else if (icon != null) {
            Icon(icon, Oe.Text, iconSize, stroke = if (compact) 2.2f else 2f)
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        Text(text, style = Type.Button.copy(color = Oe.Text, fontSize = if (compact) 14.sp else 15.sp))
    }
}

/** A setting as a tile: its name over its value; a tap opens it, when it opens anything. */
@Composable
fun SettingTile(label: String, value: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .heightIn(min = 58.dp)
            .let { if (onClick != null) it.gamepadFocus(corner = 14.dp).rowFocus(label) else it }
            .clip(RoundedCornerShape(14.dp))
            .background(Oe.Surface)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = Type.Caption.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Oe.Surface)) { content() }
}

@Composable
fun ListRow(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    divider: Boolean = false,
    minHeight: Dp = 50.dp,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subtitleLines: Int = 1,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    if (divider) HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .let { if (selected) it.background(Oe.AccentTint).semantics { this.selected = true } else it }
            .let { if (onClick != null && enabled) it.paneOpener(selected).gamepadFocus(corner = 0.dp, inset = true).rowFocus(label).clickable(role = Role.Button, onClick = onClick) else it }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(label, style = if (selected) Type.Body.copy(fontWeight = FontWeight.SemiBold) else Type.Body)
            if (subtitle != null) Text(subtitle, style = Type.Small, maxLines = subtitleLines, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
        if (value != null) Text(value, style = Type.Body.copy(color = Oe.Muted, fontSize = 14.sp))
        if (onClick != null) Icon(Icons.Chevron, Oe.Chevron, 16.dp, stroke = 2.2f)
    }
}

/** What a control that cannot be used right now fades to. */
const val DISABLED_ALPHA = 0.45f

/** A setting that is on or off: the whole row toggles. */
@Composable
fun SwitchRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    divider: Boolean = false,
) {
    if (divider) HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (enabled) it.gamepadFocus(corner = 0.dp, inset = true).rowFocus(label) else it }
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body)
            if (subtitle != null) Text(subtitle, style = Type.Small, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Oe.Accent, checkedBorderColor = Oe.Accent,
                uncheckedThumbColor = Oe.Chevron, uncheckedTrackColor = Oe.Raised, uncheckedBorderColor = Oe.Chevron,
                disabledCheckedThumbColor = Color.White, disabledCheckedTrackColor = Oe.Accent, disabledCheckedBorderColor = Oe.Accent,
                disabledUncheckedThumbColor = Oe.Chevron, disabledUncheckedTrackColor = Oe.Raised, disabledUncheckedBorderColor = Oe.Chevron,
            ),
        )
    }
}

/** A labelled one-line text field; [onReveal] adds the show/hide button of a password. */
@Composable
fun OeTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    readOnly: Boolean = false,
    password: Boolean = false,
    revealed: Boolean = false,
    onReveal: (() -> Unit)? = null,
    revealLabel: String = "",
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    OeFieldFrame(label, isError, interaction, modifier, trailing = {
        if (onReveal != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = revealLabel, role = Role.Button, onClick = onReveal)
                    .semantics { contentDescription = revealLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(if (revealed) Icons.EyeOff else Icons.Eye, Oe.Muted, 20.dp) }
        }
    }) { fieldModifier ->
        BasicTextField(
            value, onChange,
            fieldModifier.let { m -> if (contentType != null) m.semantics { this.contentType = contentType } else m },
            readOnly = readOnly,
            singleLine = true,
            textStyle = Type.Body,
            cursorBrush = SolidColor(Oe.AccentText),
            visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
        )
    }
}

/**
 * The same field over a [TextFieldValue], so it can open with its text selected (issue 790's
 * rename); [suffix] is fixed text after what is typed, such as the extension a rename keeps.
 */
@Composable
fun OeTextField(
    label: String,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    suffix: String? = null,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    OeFieldFrame(label, isError, interaction, modifier, trailing = {
        if (suffix != null) Text(suffix, style = Type.Body.copy(color = Oe.Muted), modifier = Modifier.padding(start = 4.dp, end = 10.dp))
    }) { fieldModifier ->
        BasicTextField(
            value, onChange,
            fieldModifier.let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m },
            singleLine = true,
            textStyle = Type.Body,
            cursorBrush = SolidColor(Oe.AccentText),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
        )
    }
}

/** The label, border and focus ring both [OeTextField]s share; [field] gets the modifier its text field takes. */
@Composable
private fun OeFieldFrame(
    label: String,
    isError: Boolean,
    interaction: MutableInteractionSource,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
    field: @Composable (Modifier) -> Unit,
) {
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        Text(label, style = Type.Caption.copy(color = Oe.Muted), modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .gamepadFocus(corner = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Oe.Surface)
                .border(1.5.dp, if (isError) Oe.Danger else if (focused) Oe.AccentBright else Oe.Border, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            field(Modifier.weight(1f).padding(vertical = 14.dp).leaveFieldVertically())
            trailing()
        }
    }
}

@Composable
fun Tag(text: String, background: Color, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = Type.Tag.copy(color = color))
    }
}

@Composable
fun RadioCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
    badge: String? = null,
    background: Color = Oe.Surface,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .gamepadFocus(corner = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Oe.AccentTint else background)
            .border(1.5.dp, if (selected) Oe.AccentBright else Oe.Border, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Oe.AccentBright else Oe.Chevron, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Oe.AccentBright))
        }
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = Type.Section)
                if (badge != null) Tag(badge, Oe.AccentTint, Oe.AccentText)
            }
            if (description.isNotEmpty()) Text(description, style = Type.Small)
        }
    }
}

@Composable
fun ConsoleChip(console: Console, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(console.shelfColor),
        contentAlignment = Alignment.Center,
    ) {
        // Longer abbreviations (SATURN, VECTREX...) shrink to stay on one line.
        val fontSize = when {
            console.short.length <= 4 -> 11.sp
            console.short.length <= 6 -> 9.sp
            else -> 8.sp
        }
        Text(
            console.short, style = Type.Tag.copy(color = Color.White, fontSize = fontSize, letterSpacing = 0.sp),
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
        )
    }
}

/** How far [FitText] shrinks a long text: down to this share of its style's size. */
const val FIT_TEXT_MIN_SCALE = 0.6f

/** One line, never cut: [style]'s size when it fits, shrinking down to 60% of it for a long text. */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(
        text, modifier, style, maxLines = 1, softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * FIT_TEXT_MIN_SCALE, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

/**
 * In a bottom sheet, which has a window of its own: its bar icons follow the app's theme, as the activity's do.
 * Material sets them from the system's dark mode, which the theme picked in the app may not match.
 */
@Composable
fun SheetBarIcons() {
    val view = LocalView.current
    val dark = Oe.dark
    LaunchedEffect(view, dark) {
        val provider = generateSequence<Any>(view) { (it as? android.view.View)?.parent ?: (it as? android.view.ViewParent)?.parent }
            .firstOrNull { it is DialogWindowProvider } as? DialogWindowProvider
        provider?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

/** The console's pixel-art icon, unsmoothed at a whole multiple of its size; the chip when it has none. */
@Composable
fun ConsoleIcon(console: Console, size: Dp = 40.dp) {
    val res = console.icon ?: run { ConsoleChip(console, size); return }
    val bitmap = ImageBitmap.imageResource(res)
    val side = with(LocalDensity.current) { ((size.roundToPx() / bitmap.width).coerceAtLeast(1) * bitmap.width).toDp() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Image(bitmap, null, Modifier.size(side), filterQuality = FilterQuality.None)
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.readingWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Oe.Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Cart, Oe.AccentText, 30.dp)
        }
        Text(title, style = Type.Title.copy(fontSize = 20.sp), textAlign = TextAlign.Center)
        Text(body, style = Type.Small, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            PrimaryButton(action, onAction, Modifier.fillMaxWidth(), icon = Icons.Download)
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)

/** Full width, centred, never wider than a comfortable line of text: no change on a phone, a column on a tablet. */
// The last fillMaxWidth keeps the column at that width even when its content is narrower, so nothing shrinks and recentres.
fun Modifier.readingWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()

@Composable
fun formatPlaytime(seconds: Long): String = when {
    seconds <= 0 -> stringResource(R.string.library_playtime_never)
    seconds < 60 -> stringResource(R.string.library_playtime_under_minute)
    seconds < 3600 -> stringResource(R.string.library_playtime_minutes, seconds / 60)
    else -> stringResource(R.string.library_playtime_hours, seconds / 3600, (seconds % 3600) / 60)
}

@Composable
fun formatAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - time) / 1000
    return when {
        d < 60 -> stringResource(R.string.library_ago_now)
        d < 3600 -> stringResource(R.string.library_ago_minutes, d / 60)
        d < 86400 -> stringResource(R.string.library_ago_hours, d / 3600)
        d < 2 * 86400 -> stringResource(R.string.library_ago_yesterday)
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
            java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(time))
        }
    }
}

/**
 * A count in its plural form. CLDR files 0 under "one" in Portuguese and
 * French ("0 jogo"); every language we ship reads 0 as plural, so 0 takes
 * the form of 2 while the text still shows the real number.
 */
@Composable
fun countString(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    pluralStringResource(id, if (count == 0) 2 else count, *args)

/**
 * Box art at its own aspect ratio; a placeholder of the usual box shape until it loads. With [aspect],
 * the art is drawn in that shape instead, filling it and cropped from the centre, and the placeholder
 * takes the same shape: every cover of a console is then the same size (issue 882).
 */
@Composable
fun CoverArt(url: String?, title: String, modifier: Modifier, maxHeight: Dp? = null, aspect: Float? = null) {
    var measured by remember(url) { mutableStateOf(boxAspects[url]) }
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = Modifier.let { if (maxHeight != null) it.height(maxHeight) else it.fillMaxWidth() }
        .aspectRatio(aspect ?: measured ?: DEFAULT_COVER_ASPECT, matchHeightConstraintsFirst = maxHeight != null)
    Box(modifier.then(shape).background(Oe.Surface), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                url, title, Modifier.fillMaxSize(), contentScale = if (aspect != null) ContentScale.Crop else ContentScale.Fit,
                onSuccess = { state ->
                    loaded = true
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        measured = size.width / size.height
                        boxAspects[url] = measured!!
                    }
                },
            )
        }
        // The title stands in until the art shows (a shape measured before counts as shown).
        if (url == null || (!loaded && measured == null)) {
            Text(
                title, style = Type.Caption.copy(color = Oe.Muted, fontSize = if (maxHeight != null) 9.sp else 12.sp),
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp),
            )
        }
    }
}

/** Loaded box art proportions, so scrolling back does not reflow the grid. */
private val boxAspects = HashMap<String, Float>()
