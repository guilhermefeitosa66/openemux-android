package com.openemux.mobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.ArtCandidate
import com.openemux.mobile.data.ArtImages
import com.openemux.mobile.data.ArtKind
import com.openemux.mobile.data.ArtOutcome
import com.openemux.mobile.data.Artwork
import com.openemux.mobile.data.Corner
import com.openemux.mobile.data.CropRect
import com.openemux.mobile.data.EditSession
import com.openemux.mobile.data.Rom
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

/**
 * The Import tab's state (issue 753): the working copy in the app cache, the steps applied to it,
 * and what it will be saved as. The picked file itself is never opened again after the copy.
 */
class EditorState {
    var file by mutableStateOf<File?>(null)
        private set
    var name by mutableStateOf("")
        private set
    var original by mutableStateOf<Bitmap?>(null)
        private set
    var session by mutableStateOf(EditSession())
    var saveAs by mutableStateOf(ArtKind.COVER)
    var origin by mutableStateOf(ArtCandidate.ORIGIN_FILE)
        private set
    var saving by mutableStateOf(false)
        private set
    /** What the D-pad moves on the crop box; null while it moves focus as usual. */
    var target by mutableStateOf<CropTarget?>(null)

    /** Takes [copy] (already in the cache) as the image to edit; false when it cannot be decoded. */
    suspend fun load(copy: File, name: String, kind: ArtKind, origin: String): Boolean {
        val bitmap = withContext(Dispatchers.IO) { ArtImages.decode(copy) }
        if (bitmap == null) {
            copy.delete()
            return false
        }
        clear()
        file = copy
        this.name = name
        original = bitmap
        saveAs = kind
        this.origin = origin
        return true
    }

    /** "Remove this image": the working copy goes, nothing on the device is touched. */
    fun clear() {
        file?.delete()
        file = null
        original = null
        session = EditSession()
        target = null
    }

    suspend fun save(artwork: Artwork, rom: Rom): ArtOutcome {
        val bitmap = original ?: return ArtOutcome.NOT_FOUND
        if (saving) return ArtOutcome.BUSY
        saving = true
        try {
            val bytes = withContext(Dispatchers.Default) { ArtImages.png(ArtImages.render(bitmap, session.ops)) }
            val outcome = artwork.saveManual(rom, saveAs, bytes, "png", null, origin)
            if (outcome == ArtOutcome.DONE) clear()
            return outcome
        } finally {
            saving = false
        }
    }
}

/** What the D-pad moves while adjusting the crop: the whole box or one corner, in the order A cycles them. */
enum class CropTarget(val corner: Corner?) {
    BOX(null), TOP_LEFT(Corner.TOP_LEFT), TOP_RIGHT(Corner.TOP_RIGHT), BOTTOM_RIGHT(Corner.BOTTOM_RIGHT), BOTTOM_LEFT(Corner.BOTTOM_LEFT);

    /** The next one; after the last, null hands the D-pad back to focus. */
    fun next(): CropTarget? = entries.getOrNull(ordinal + 1)
}

@Composable
fun ImportTab(
    editor: EditorState,
    kinds: List<ArtKind>,
    wide: Boolean,
    side: Modifier,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MarginFocusScroll(trailing = FocusMargin) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).then(side).padding(top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val original = editor.original
                if (original == null) {
                    Column(
                        Modifier.fillMaxWidth().height(if (wide) 420.dp else 300.dp).clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Oe.Border, RoundedCornerShape(16.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Image, Oe.Faint, 36.dp, stroke = 1.6f)
                        Text(stringResource(R.string.artwork_import_hint), style = Type.Small, textAlign = TextAlign.Center)
                        PrimaryButton(stringResource(R.string.artwork_choose_image), onPick, sidePadding = 20.dp, icon = Icons.Folder, height = 48.dp)
                    }
                } else {
                    CropStage(editor, original, if (wide) 460.dp else 360.dp)
                    Tools(editor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            stringResource(R.string.artwork_apply_crop), { editor.session = editor.session.applyCrop(); editor.target = null },
                            Modifier.weight(1f), height = 44.dp, busy = editor.session.crop == null,
                        )
                        SecondaryButton(stringResource(R.string.artwork_remove_image), { editor.clear() }, Modifier.weight(1f), height = 44.dp)
                    }
                    if (ArtKind.LABEL in kinds) SaveAs(editor)
                }
            }
        }
        BottomBar(side) {
            SecondaryButton(stringResource(R.string.artwork_cancel), onCancel, Modifier.weight(1f), height = 50.dp)
            PrimaryButton(
                stringResource(if (editor.saving) R.string.artwork_saving else R.string.artwork_save),
                { if (!editor.saving) onSave() }, Modifier.weight(1f), height = 50.dp, enabled = editor.original != null,
            )
        }
    }
}

@Composable
private fun Tools(editor: EditorState) {
    val s = editor.session
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Tool(Icons.Crop, stringResource(R.string.artwork_tool_crop), s.crop != null) { editor.session = s.toggleCrop(); editor.target = null }
        Tool(Icons.RotateLeft, stringResource(R.string.artwork_rotate_left)) { editor.session = s.rotateLeft(); editor.target = null }
        Tool(Icons.RotateRight, stringResource(R.string.artwork_rotate_right)) { editor.session = s.rotateRight(); editor.target = null }
        Tool(Icons.FlipH, stringResource(R.string.artwork_flip_horizontal)) { editor.session = s.flipHorizontal(); editor.target = null }
        Tool(Icons.FlipV, stringResource(R.string.artwork_flip_vertical)) { editor.session = s.flipVertical(); editor.target = null }
        Tool(Icons.Load, stringResource(R.string.artwork_reset_edits)) { editor.session = s.reset(); editor.target = null }
    }
}

@Composable
private fun Tool(icon: String, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) Oe.AccentSoft else Oe.Surface2).border(1.dp, if (on) Oe.AccentSoft else Oe.Border, RoundedCornerShape(14.dp))
            .semantics {
                contentDescription = label
                selected = on
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (on) Oe.AccentOnSoft else Oe.Text, 22.dp) }
}

@Composable
private fun SaveAs(editor: EditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.artwork_save_as), style = Type.Caption.copy(color = Oe.Muted))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Oe.Surface).border(1.dp, Oe.Border, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(ArtKind.COVER to R.string.artwork_cover, ArtKind.LABEL to R.string.artwork_label).forEach { (kind, text) ->
                val on = editor.saveAs == kind
                Box(
                    Modifier.weight(1f).height(padSize(40.dp, 44.dp)).gamepadFocus(corner = 10.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) Oe.AccentSoft else Color.Transparent)
                        .semantics { selected = on }
                        .clickable(role = Role.RadioButton) { editor.saveAs = kind },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(text), style = Type.Button.copy(fontSize = 14.sp, color = if (on) Oe.AccentOnSoft else Oe.Muted)) }
            }
        }
    }
}

/**
 * The image, fitted, with the crop box over it: dragged inside it moves, dragged by a corner it
 * resizes. With a pad, A cycles what the D-pad moves (the box, then each corner, then back to
 * moving focus); TalkBack gets the same moves as actions.
 */
@Composable
private fun CropStage(editor: EditorState, original: Bitmap, height: androidx.compose.ui.unit.Dp) {
    val ops = editor.session.ops
    val preview = produceState<ImageBitmap?>(null, original, ops) {
        value = withContext(Dispatchers.Default) { ArtImages.render(original, ops).asImageBitmap() }
    }.value
    val crop = editor.session.crop
    val session by rememberUpdatedState(editor.session)
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<CropTarget?>(null) }
    val step = CropRect.PAD_STEP

    fun change(rect: CropRect) {
        editor.session = editor.session.moveCrop(rect)
    }
    fun nudge(target: CropTarget, dx: Float, dy: Float) {
        val rect = editor.session.crop ?: return
        change(target.corner?.let { rect.resized(it, dx, dy) } ?: rect.moved(dx, dy))
    }
    fun grow(by: Float) {
        val rect = editor.session.crop ?: return
        change(rect.resized(Corner.TOP_LEFT, -by, -by).resized(Corner.BOTTOM_RIGHT, by, by))
    }

    val describe = stringResource(R.string.artwork_working_copy, editor.name)
    val cropArea = stringResource(R.string.artwork_crop_area)
    val targetText = when (editor.target) {
        null -> stringResource(R.string.artwork_crop_idle)
        CropTarget.BOX -> stringResource(R.string.artwork_crop_box)
        CropTarget.TOP_LEFT -> stringResource(R.string.artwork_crop_top_left)
        CropTarget.TOP_RIGHT -> stringResource(R.string.artwork_crop_top_right)
        CropTarget.BOTTOM_RIGHT -> stringResource(R.string.artwork_crop_bottom_right)
        CropTarget.BOTTOM_LEFT -> stringResource(R.string.artwork_crop_bottom_left)
    }
    val actions = listOf(
        stringResource(R.string.artwork_crop_move_left) to { nudge(CropTarget.BOX, -step, 0f) },
        stringResource(R.string.artwork_crop_move_right) to { nudge(CropTarget.BOX, step, 0f) },
        stringResource(R.string.artwork_crop_move_up) to { nudge(CropTarget.BOX, 0f, -step) },
        stringResource(R.string.artwork_crop_move_down) to { nudge(CropTarget.BOX, 0f, step) },
        stringResource(R.string.artwork_crop_grow) to { grow(step) },
        stringResource(R.string.artwork_crop_shrink) to { grow(-step) },
    ).map { (label, run) -> CustomAccessibilityAction(label) { run(); true } }

    val handle = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(Oe.GameBg)
            .gamepadFocus(corner = 16.dp)
            .semantics {
                contentDescription = if (crop != null) cropArea else describe
                if (crop != null) {
                    stateDescription = targetText
                    customActions = actions
                }
            }
            .clickable(enabled = crop != null, role = Role.Button) { editor.target = editor.target?.next() ?: CropTarget.BOX }
            .onPreviewKeyEvent { e ->
                val target = editor.target ?: return@onPreviewKeyEvent false
                if (editor.session.crop == null) return@onPreviewKeyEvent false
                val (dx, dy) = when (e.key) {
                    Key.DirectionLeft -> -step to 0f
                    Key.DirectionRight -> step to 0f
                    Key.DirectionUp -> 0f to -step
                    Key.DirectionDown -> 0f to step
                    else -> return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) nudge(target, dx, dy)
                true
            }
            .pointerInput(crop != null) {
                if (crop == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { p ->
                        val rect = session.crop ?: return@detectDragGestures
                        val r = imageRect
                        fun at(x: Float, y: Float) = Offset(r.left + x * r.width, r.top + y * r.height)
                        val corners = listOf(
                            CropTarget.TOP_LEFT to at(rect.left, rect.top), CropTarget.TOP_RIGHT to at(rect.right, rect.top),
                            CropTarget.BOTTOM_RIGHT to at(rect.right, rect.bottom), CropTarget.BOTTOM_LEFT to at(rect.left, rect.bottom),
                        )
                        val near = corners.minBy { (_, c) -> hypot(c.x - p.x, c.y - p.y) }
                        val inside = p.x in at(rect.left, 0f).x..at(rect.right, 0f).x && p.y in at(0f, rect.top).y..at(0f, rect.bottom).y
                        dragging = when {
                            hypot(near.second.x - p.x, near.second.y - p.y) <= handle -> near.first
                            inside -> CropTarget.BOX
                            else -> null
                        }
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                    onDrag = { change, amount ->
                        val target = dragging ?: return@detectDragGestures
                        val r = imageRect
                        if (r.width <= 0f || r.height <= 0f) return@detectDragGestures
                        change.consume()
                        nudge(target, amount.x / r.width, amount.y / r.height)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 12.dp.toPx()
                val scale = min((size.width - 2 * pad) / preview.width, (size.height - 2 * pad) / preview.height)
                val w = preview.width * scale
                val h = preview.height * scale
                val r = Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
                imageRect = r
                drawImage(preview, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
                val c = crop ?: return@Canvas
                val box = Rect(r.left + c.left * w, r.top + c.top * h, r.left + c.right * w, r.top + c.bottom * h)
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset(r.left, r.top), Size(w, box.top - r.top))
                drawRect(dim, Offset(r.left, box.bottom), Size(w, r.bottom - box.bottom))
                drawRect(dim, Offset(r.left, box.top), Size(box.left - r.left, box.height))
                drawRect(dim, Offset(box.right, box.top), Size(r.right - box.right, box.height))
                drawRect(Color.White, box.topLeft, box.size, style = Stroke(2.dp.toPx()))
                val len = 18.dp.toPx()
                val thick = 3.dp.toPx()
                val active = editor.target ?: dragging
                listOf(
                    CropTarget.TOP_LEFT to Triple(box.topLeft, 1f, 1f), CropTarget.TOP_RIGHT to Triple(box.topRight, -1f, 1f),
                    CropTarget.BOTTOM_RIGHT to Triple(box.bottomRight, -1f, -1f), CropTarget.BOTTOM_LEFT to Triple(box.bottomLeft, 1f, -1f),
                ).forEach { (t, spec) ->
                    val (p, sx, sy) = spec
                    val color = if (active == t || active == CropTarget.BOX) Oe.AccentBright else Color.White
                    drawLine(color, p, Offset(p.x + sx * len, p.y), thick)
                    drawLine(color, p, Offset(p.x, p.y + sy * len), thick)
                }
            }
        }
    }
}
