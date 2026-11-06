package com.openemux.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.SkinColorMath
import com.openemux.mobile.data.SkinContrast
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFill
import com.openemux.mobile.data.SkinIconTone
import com.openemux.mobile.data.SkinPalette
import com.openemux.mobile.data.SkinPalettes
import com.openemux.mobile.data.SkinPart
import com.openemux.mobile.data.SkinScope
import com.openemux.mobile.data.SkinTarget
import com.openemux.mobile.data.SkinTextureFit
import com.retroarch.R

/*
 * The skin editor's paint panel (issue 786), from the design of issue 666 ("Colour", "Palettes", "Texture", "Screen
 * frame parts"): a side panel over the stage with a Colour tab (the part, Apply to, presets and palettes, the picker,
 * hex and recent colours, the contrast warning) and a Texture tab (a picture as the part's fill).
 */

private val PanelBg = Color(0xFF1E1E25)
private val PanelBorder = Color(0xFF34343F)
private val Well = Color(0xFF16161B)
private val SegOn = Color(0xFF33333D)
private val TextOn = Color(0xFFF2F1F6)
private val TextOff = Color(0xFFA9A7B5)
private val ChipLine = Color(0xFF3A3A46)
private val ChipText = Color(0xFFC9C7D4)
private val ChipOnBg = Color(0x477C3AED)
private val ChipOnText = Color(0xFFE4DAFF)
private val FieldLine = Color(0xFF44445A)
private val Dashed = Color(0xFF8E8C9C)
private val Remove = Color(0xFFFF8A80)
private val Lilac = Color(0xFFD4C4FF)

/** Which tab of the panel is open. */
internal enum class PaintTab { COLOUR, TEXTURE }

@StringRes
internal fun partName(part: SkinPart): Int = when (part) {
    SkinPart.BACKGROUND -> R.string.editor_part_background
    SkinPart.FRAME -> R.string.editor_part_frame
    SkinPart.ICONS -> R.string.editor_part_icons
    SkinPart.ACTIVE -> R.string.editor_part_active
    SkinPart.BUTTON -> R.string.editor_part_button
    SkinPart.LETTER -> R.string.editor_part_letter
    SkinPart.PAD -> R.string.editor_part_pad
    SkinPart.BALL -> R.string.editor_part_ball
    SkinPart.BASE -> R.string.editor_part_base
    SkinPart.KNOB -> R.string.editor_part_knob
}

@StringRes
internal fun paletteName(palette: SkinPalette): Int = when (palette.id) {
    "dracula" -> R.string.editor_palette_dracula
    "pastel" -> R.string.editor_palette_pastel
    "nord" -> R.string.editor_palette_nord
    "gruvbox" -> R.string.editor_palette_gruvbox
    "neon" -> R.string.editor_palette_neon
    "earth" -> R.string.editor_palette_earth
    "green-screen" -> R.string.editor_palette_green_screen
    else -> R.string.editor_palette_basic
}

/** "All buttons", "All D-pads", "All sticks": what [SkinScope.ALL] paints for controls of [type]. */
@StringRes
internal fun allName(type: SkinElementType): Int = when (type) {
    SkinElementType.DPAD -> R.string.editor_apply_all_dpads
    SkinElementType.STICK -> R.string.editor_apply_all_sticks
    else -> R.string.editor_apply_all_buttons
}

@StringRes
internal fun contrastText(issue: SkinContrast): Int = when (issue) {
    SkinContrast.LETTER -> R.string.editor_contrast_letter
    SkinContrast.ICONS -> R.string.editor_contrast_icons
    SkinContrast.ACTIVE_ICON -> R.string.editor_contrast_active
}

@StringRes
internal fun fillName(fill: SkinFill): Int = when (fill) {
    SkinFill.STRETCH -> R.string.editor_fill_stretch
    SkinFill.FIT -> R.string.editor_fill_fit
    SkinFill.REPEAT -> R.string.editor_fill_repeat
}

/** The contrast warning worth showing while [part] is painted: the pair it belongs to, else any. */
internal fun contrastFor(part: SkinPart, issues: Set<SkinContrast>): SkinContrast? {
    val own = when (part) {
        SkinPart.BUTTON, SkinPart.LETTER -> SkinContrast.LETTER
        SkinPart.FRAME, SkinPart.ICONS -> SkinContrast.ICONS
        SkinPart.ACTIVE -> SkinContrast.ACTIVE_ICON
        else -> null
    }
    return own?.takeIf { it in issues } ?: issues.firstOrNull()
}

/** The texture being framed: the picture ([name], [width] x [height] as picked) and its preview on the first target. */
internal class TextureDraft(val name: String, val width: Int, val height: Int, val preview: ImageBitmap?)

/** Everything the panel shows; the editor owns the state, the panel only draws it and reports what was done. */
internal class PaintView(
    val target: SkinTarget,
    /** "Editing" line under the tabs for the frame and the background; null for controls. */
    val editing: String?,
    val tab: PaintTab,
    val part: SkinPart,
    val scopes: List<Pair<SkinScope, String>>,
    val scope: SkinScope,
    val color: Int,
    val presets: SkinPalette,
    val recent: List<Int>,
    val contrast: SkinContrast?,
    val replacesImage: Boolean,
    val tone: SkinIconTone?,
    val palettes: Boolean,
    val textured: Boolean,
    val texture: TextureDraft?,
    val fill: SkinFill,
    val zoom: Float,
)

internal class PaintActions(
    val onTab: (PaintTab) -> Unit,
    val onPart: (SkinPart) -> Unit,
    val onScope: (SkinScope) -> Unit,
    /** A colour picked; [commit]: the pick is done (a tap, a typed hex, a drag let go). */
    val onColor: (Int, Boolean) -> Unit,
    val onTone: (SkinIconTone) -> Unit,
    val onPalettes: (Boolean) -> Unit,
    val onPalette: (SkinPalette) -> Unit,
    val onPickImage: () -> Unit,
    val onFill: (SkinFill) -> Unit,
    /** The picture moved by shares of its box and zoomed by a factor; [commit] when the gesture ends. */
    val onFrame: (Float, Float, Float, Boolean) -> Unit,
    val onZoom: (Float, Boolean) -> Unit,
    val onRemoveTexture: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
internal fun PaintPanel(view: PaintView, actions: PaintActions, modifier: Modifier) {
    Column(
        modifier.background(PanelBg).border(1.dp, PanelBorder).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (view.palettes) {
            Palettes(view.presets, actions)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Segmented(
                listOf(PaintTab.COLOUR to stringResource(R.string.editor_colour), PaintTab.TEXTURE to stringResource(R.string.editor_texture)),
                view.tab, Modifier.weight(1f), height = 32, onPick = actions.onTab,
            )
            Done(actions.onDone)
        }
        view.editing?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_paint_editing), style = Type.Small.copy(color = TextOff, fontSize = 12.sp))
                Text(it, style = Type.Body.copy(color = TextOn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        if (view.target.parts.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.editor_paint), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                Segmented(view.target.parts.map { it to stringResource(partName(it)) }, view.part, Modifier.weight(1f), height = 28, onPick = actions.onPart)
            }
        }
        if (view.scopes.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                view.scopes.forEach { (scope, label) -> Chip(label, scope == view.scope) { actions.onScope(scope) } }
            }
        }
        if (view.tab == PaintTab.TEXTURE) {
            TextureTab(view, actions)
        } else {
            ColourTab(view, actions)
        }
    }
}

@Composable
private fun ColourTab(view: PaintView, actions: PaintActions) {
    if (view.part == SkinPart.ACTIVE) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Well).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(view.color)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Icon("M13 2L4 14h7l-1 8 9-12h-7z", if (view.tone == SkinIconTone.DARK) Color.Black else Color.White, 18.dp)
            }
            Text(stringResource(R.string.editor_active_note), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        view.presets.colors.take(7).forEach { c -> Swatch(c, 34, c == view.color, SkinColorMath.hex(c)) { actions.onColor(c, true) } }
        PalettesButton(stringResource(R.string.editor_palettes) + ": " + stringResource(paletteName(view.presets))) { actions.onPalettes(true) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Picker(view.color, actions)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_hex), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            HexField(view.color) { actions.onColor(it, true) }
            if (view.recent.isNotEmpty()) {
                Text(stringResource(R.string.editor_recent), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    view.recent.take(4).forEach { c -> Swatch(c, 28, c == view.color, SkinColorMath.hex(c)) { actions.onColor(c, true) } }
                }
            }
        }
    }
    if (view.part == SkinPart.ACTIVE) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.editor_icon_when_on), Modifier.weight(1f), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            Chip(stringResource(R.string.editor_light), view.tone != SkinIconTone.DARK) { actions.onTone(SkinIconTone.LIGHT) }
            Chip(stringResource(R.string.editor_dark), view.tone == SkinIconTone.DARK) { actions.onTone(SkinIconTone.DARK) }
        }
    }
    view.contrast?.let { issue ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Warn, Oe.Warn, 16.dp)
            Text(stringResource(contrastText(issue)), style = Type.Small.copy(color = Oe.WarnText, fontSize = 12.sp, lineHeight = 17.sp))
        }
    }
    if (view.replacesImage) {
        Text(stringResource(R.string.editor_replaces_image), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, lineHeight = 17.sp))
    }
}

@Composable
private fun TextureTab(view: PaintView, actions: PaintActions) {
    if (!view.part.texturable) {
        Text(stringResource(R.string.editor_colour_only), style = Type.Small.copy(color = TextOff, fontSize = 13.sp, lineHeight = 18.sp))
        return
    }
    val t = view.texture
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val latest = rememberUpdatedState(actions)
        Box(
            Modifier.size(132.dp, 96.dp).clip(RoundedCornerShape(12.dp)).background(Well)
                .pointerInput(t != null) {
                    if (t == null) return@pointerInput
                    detectTransformGestures(onGesture = { _, pan, zoom, _ ->
                        latest.value.onFrame(pan.x / size.width, pan.y / size.height, zoom, false)
                    })
                }
                .pointerInput(t != null, "end") {
                    if (t == null) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.all { !it.pressed }) latest.value.onFrame(0f, 0f, 1f, true)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (t?.preview != null) {
                Image(t.preview, null, Modifier.size(132.dp, 96.dp), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Image, TextOff, 28.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t != null) {
                Text(t.name, style = Type.Body.copy(color = TextOn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.editor_image_size, t.width, t.height), style = Type.Small.copy(color = TextOff, fontSize = 12.sp))
            } else {
                Text(
                    stringResource(if (view.textured) R.string.editor_texture_set else R.string.editor_texture_none),
                    style = Type.Small.copy(color = TextOff, fontSize = 12.sp, lineHeight = 16.sp),
                )
            }
            OutlineButton(stringResource(if (t != null || view.textured) R.string.editor_choose_another else R.string.editor_choose_image), Icons.Image, actions.onPickImage)
        }
    }
    if (t != null) {
        Text(stringResource(R.string.editor_fill), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        Segmented(SkinFill.entries.map { it to stringResource(fillName(it)) }, view.fill, Modifier.fillMaxWidth(), height = 34, onPick = actions.onFill)
        Row {
            Text(stringResource(R.string.editor_move_zoom), Modifier.weight(1f), style = Type.Small.copy(color = TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            Text(stringResource(R.string.editor_move_zoom_hint), style = Type.Small.copy(color = Lilac, fontSize = 12.sp))
        }
        ZoomSlider(view.zoom, actions)
    }
    if (t != null || view.textured) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = actions.onRemoveTexture),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Close, Remove, 16.dp)
            Text(stringResource(R.string.editor_remove_texture), style = Type.Body.copy(color = Remove, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun Palettes(current: SkinPalette, actions: PaintActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val back = stringResource(R.string.editor_back_to_colour)
        Box(
            Modifier.size(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.onPalettes(false) }
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Back, TextOn, 20.dp) }
        Text(stringResource(R.string.editor_palettes), Modifier.weight(1f), style = Type.Body.copy(color = TextOn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Text(stringResource(R.string.editor_palettes_hint), style = Type.Small.copy(color = TextOff, fontSize = 12.sp))
    }
    SkinPalettes.ALL.forEach { p ->
        val on = p.id == current.id
        Column(
            Modifier.fillMaxWidth().gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp))
                .background(if (on) Color(0x1F7C3AED) else Color(0xFF26262E)).border(2.dp, if (on) Oe.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                .clickable { actions.onPalette(p) }.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp))) {
                p.colors.forEach { c -> Box(Modifier.weight(1f).height(30.dp).background(Color(c))) }
            }
            Row(Modifier.padding(horizontal = 2.dp)) {
                Text(stringResource(paletteName(p)), Modifier.weight(1f), style = Type.Small.copy(color = TextOn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                if (on) Text(stringResource(R.string.editor_palette_in_row), style = Type.Small.copy(color = Oe.AccentText, fontSize = 12.sp))
            }
        }
    }
}

/** The saturation/brightness square and the hue slider. The hue is kept while the colour is grey, where it has none. */
@Composable
private fun Picker(color: Int, actions: PaintActions) {
    var hsv by remember { mutableStateOf(SkinColorMath.toHsv(color)) }
    // A colour from outside (a preset, hex, undo) moves the knobs; one the picker made itself keeps its hue.
    LaunchedEffect(color) { if (SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2]) != color) hsv = SkinColorMath.toHsv(color) }
    val latest = rememberUpdatedState(actions)
    fun set(h: Float, s: Float, v: Float, commit: Boolean) {
        hsv = floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        latest.value.onColor(SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2]), commit)
    }
    val hueColor = Color(SkinColorMath.fromHsv(hsv[0], 1f, 1f))
    val squareLabel = stringResource(R.string.editor_saturation)
    Canvas(
        Modifier.size(196.dp, 96.dp).clip(RoundedCornerShape(12.dp)).semantics { contentDescription = squareLabel }
            .gamepadFocus(corner = 12.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> set(hsv[0], hsv[1] - 0.05f, hsv[2], true)
                    Key.DirectionRight -> set(hsv[0], hsv[1] + 0.05f, hsv[2], true)
                    Key.DirectionUp -> set(hsv[0], hsv[1], hsv[2] + 0.05f, true)
                    Key.DirectionDown -> set(hsv[0], hsv[1], hsv[2] - 0.05f, true)
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(Unit) {
                fun at(p: Offset, commit: Boolean) = set(hsv[0], p.x / size.width, 1f - p.y / size.height, commit)
                val end = { latest.value.onColor(SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2]), true) }
                detectDragGestures(onDragStart = { at(it, false) }, onDragEnd = end, onDragCancel = end) { change, _ ->
                    at(change.position, false)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p -> set(hsv[0], p.x / size.width, 1f - p.y / size.height, true) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val o = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
        drawCircle(Color(SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2])), 9.dp.toPx(), o)
        drawCircle(Color.White, 10.dp.toPx(), o, style = Stroke(3.dp.toPx()))
    }
    val hueLabel = stringResource(R.string.editor_hue)
    Canvas(
        Modifier.size(196.dp, 24.dp).semantics { contentDescription = hueLabel }.gamepadFocus(corner = 12.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> set(hsv[0] - 10f, hsv[1], hsv[2], true)
                    Key.DirectionRight -> set(hsv[0] + 10f, hsv[1], hsv[2], true)
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(Unit) {
                fun at(x: Float, commit: Boolean) = set((x / size.width).coerceIn(0f, 0.999f) * 360f, hsv[1], hsv[2], commit)
                val end = { latest.value.onColor(SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2]), true) }
                detectDragGestures(onDragStart = { at(it.x, false) }, onDragEnd = end, onDragCancel = end) { change, _ ->
                    at(change.position.x, false)
                }
            },
    ) {
        val bar = 16.dp.toPx()
        val top = (size.height - bar) / 2f
        drawRoundRect(
            Brush.horizontalGradient((0..6).map { Color(SkinColorMath.fromHsv(it * 60f, 1f, 1f)) }),
            Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, bar), androidx.compose.ui.geometry.CornerRadius(bar / 2f),
        )
        val x = hsv[0] / 360f * size.width
        drawCircle(hueColor, 9.dp.toPx(), Offset(x, size.height / 2f))
        drawCircle(Color.White, 10.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun ZoomSlider(zoom: Float, actions: PaintActions) {
    val share = ((zoom - SkinTextureFit.MIN_ZOOM) / (SkinTextureFit.MAX_ZOOM - SkinTextureFit.MIN_ZOOM)).coerceIn(0f, 1f)
    val latest = rememberUpdatedState(actions)
    val label = stringResource(R.string.editor_zoom)
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = label }.gamepadFocus(corner = 12.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> latest.value.onZoom(zoom - 0.25f, true)
                    Key.DirectionRight -> latest.value.onZoom(zoom + 0.25f, true)
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(Unit) {
                fun at(x: Float, commit: Boolean) =
                    latest.value.onZoom(SkinTextureFit.MIN_ZOOM + (x / size.width).coerceIn(0f, 1f) * (SkinTextureFit.MAX_ZOOM - SkinTextureFit.MIN_ZOOM), commit)
                detectDragGestures(onDragStart = { at(it.x, false) }, onDragEnd = { latest.value.onZoom(Float.NaN, true) }) { change, _ -> at(change.position.x, false) }
            },
    ) {
        val bar = 6.dp.toPx()
        val y = size.height / 2f
        drawRoundRect(SegOn, Offset(0f, y - bar / 2f), androidx.compose.ui.geometry.Size(size.width, bar), androidx.compose.ui.geometry.CornerRadius(bar / 2f))
        drawRoundRect(Oe.Accent, Offset(0f, y - bar / 2f), androidx.compose.ui.geometry.Size(size.width * share, bar), androidx.compose.ui.geometry.CornerRadius(bar / 2f))
        drawCircle(Color.White, 12.dp.toPx(), Offset(size.width * share, y))
    }
}

@Composable
private fun HexField(color: Int, onColor: (Int) -> Unit) {
    var text by remember { mutableStateOf(SkinColorMath.hex(color)) }
    LaunchedEffect(color) { if (SkinColorMath.parseHex(text) != color) text = SkinColorMath.hex(color) }
    Row(
        Modifier.height(44.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, FieldLine, RoundedCornerShape(12.dp)).background(Well)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color(color)))
        BasicTextField(
            text,
            onValueChange = { v ->
                text = v.take(7).uppercase()
                SkinColorMath.parseHex(text)?.let(onColor)
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextOn),
            cursorBrush = SolidColor(TextOn),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { SkinColorMath.parseHex(text)?.let(onColor) ?: run { text = SkinColorMath.hex(color) } }),
            modifier = Modifier.weight(1f).gamepadFocus(corner = 8.dp),
        )
    }
}

/** A colour circle; [selected] gets the white ring of the design. */
@Composable
private fun Swatch(color: Int, sizeDp: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size((sizeDp + 6).dp).gamepadFocus(corner = 99.dp).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(color))) }
}

/** The preset row's last circle: dashed, a little wheel of colours; it opens Palettes. */
@Composable
private fun PalettesButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).gamepadFocus(corner = 99.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            drawCircle(Well)
            drawCircle(Dashed, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            val wheel = listOf(0xFFF9A8D4, 0xFFBD93F9, 0xFF8BE9FD, 0xFFFDE68A)
            val r = 10.dp.toPx()
            wheel.forEachIndexed { i, c ->
                drawArc(Color(c), -90f + i * 90f, 90f, true, Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).gamepadFocus(corner = 99.dp).clip(RoundedCornerShape(99.dp))
            .background(if (on) ChipOnBg else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(1.dp, ChipLine, RoundedCornerShape(99.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.Small.copy(color = if (on) ChipOnText else ChipText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1) }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, value: T, modifier: Modifier, height: Int, onPick: (T) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(Well).padding(3.dp)) {
        options.forEach { (option, label) ->
            val on = option == value
            Box(
                Modifier.weight(1f).height(height.dp).gamepadFocus(corner = 9.dp).clip(RoundedCornerShape(9.dp)).background(if (on) SegOn else Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Small.copy(color = if (on) TextOn else TextOff, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OutlineButton(label: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, FieldLine, RoundedCornerShape(12.dp))
            .background(Color(0xFF26262E)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, TextOn, 16.dp)
        Text(label, style = Type.Body.copy(color = TextOn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Done(onDone: () -> Unit) {
    val label = stringResource(R.string.editor_done)
    Box(
        Modifier.size(44.dp, 38.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).background(Oe.Accent).clickable(onClick = onDone)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Check, Color.White, 20.dp) }
}
