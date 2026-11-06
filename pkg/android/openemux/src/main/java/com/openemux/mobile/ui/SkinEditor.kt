package com.openemux.mobile.ui

import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openemux.mobile.data.BindWords
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.PanelSide
import com.openemux.mobile.data.SkinArea
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinForms
import com.openemux.mobile.data.SkinPanels
import com.openemux.mobile.data.SkinTry
import com.openemux.mobile.data.TryWord
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinEditorModel
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinGrid
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinResize
import com.openemux.mobile.data.SkinSelection
import com.openemux.mobile.data.SkinSnap
import com.openemux.mobile.data.SnapAxis
import com.openemux.mobile.data.SnapGuide
import com.openemux.mobile.data.SnapKind
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.rects
import com.openemux.mobile.launch.OpenEmuxSkinRenderer
import com.openemux.mobile.launch.SkinFit
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the skin editor opens on (issue 784): the player's skin [id], called [name]; [inUse]: the console plays it. */
data class SkinEditTarget(val id: String, val name: String, val inUse: Boolean)

// The editor's own surfaces, from the design (issue 666, "Editor").
private val StageBg = Color(0xFF24202E)
private val ScreenBg = Color(0xFF050507)
private val GridDot = Color(0x61B79CFF)
private val Guide = Color(0xFFB79CFF)
private val BarBg = Color(0xF01E1E25)
private val BarBorder = Color(0xFF34343F)
private val BarText = Color(0xFFE6E4EE)
private val BarOff = Color(0xFF5E5C6A)
private val Field = Color(0xFF26262E)
private val Lilac = Color(0xFFD4C4FF)
private val StageText = Color(0xFFF2F1F6)
private val StageMuted = Color(0xFFA9A7B5)

private const val NUDGE = "M12 3v18M3 12h18M12 3l-3 3M12 3l3 3M12 21l-3-3M12 21l3-3M3 12l3-3M3 12l3 3M21 12l-3-3M21 12l-3 3"
private const val SNAP = "M4 9h16M4 15h16M9 4v16M15 4v16"
private const val MINUS = "M5 12h14"

private const val TAG = "SkinEditor"

/** The longest side the preview is drawn at: big layouts are drawn smaller, the stage scales them anyway. */
private const val PREVIEW_LONG = 1600f

/** The name the editor gives a group of controls (the format's `group`). */
@StringRes
internal fun groupName(group: String): Int = when (group) {
    "face" -> R.string.editor_group_face
    "dpad" -> R.string.editor_group_dpad
    "center" -> R.string.editor_group_center
    "shoulders", "shoulder" -> R.string.editor_group_shoulders
    "actions" -> R.string.editor_group_actions
    "menu" -> R.string.editor_group_menu
    "keypad" -> R.string.editor_group_keypad
    else -> R.string.editor_group
}

/** A control's name: its own label, else what it is; [res] is used when it has no label. */
internal fun elementName(element: SkinElement, res: (Int) -> String): String =
    element.label?.takeIf { it.isNotBlank() } ?: when (element.type) {
        SkinElementType.DPAD -> res(R.string.editor_group_dpad)
        SkinElementType.STICK -> res(R.string.editor_stick)
        SkinElementType.ACTION -> res(R.string.editor_action)
        else -> element.id.uppercase()
    }

/**
 * The stage's subtitle: the layout being edited ([layout], "Phone · Portrait") and, on an NDS skin with screen-layout
 * pages, the page shown ([page], the layout itself: the screens one over the other), joined by [join]. The editor edits
 * that page only; the others are kept as they are.
 */
internal fun editorSubtitle(layout: String, page: String?, join: (String, String) -> String): String =
    if (page == null) layout else join(layout, page)

/** What a guide line says it snapped to. */
internal fun guideText(guide: SnapGuide, other: String?, res: (Int, String?) -> String): String = when (guide.kind) {
    SnapKind.CENTER -> res(R.string.editor_guide_center, null)
    SnapKind.ALIGN -> res(R.string.editor_guide_align, other)
    SnapKind.EDGE -> res(R.string.editor_guide_edge, other)
    SnapKind.MIRROR -> res(R.string.editor_guide_mirror, other)
}

@StringRes
internal fun orientationName(orientation: SkinOrientation): Int =
    if (orientation == SkinOrientation.PORTRAIT) R.string.editor_upright else R.string.editor_sideways

/** Try mode's word for what a control sends (issue 785). */
@StringRes
internal fun tryWord(word: TryWord): Int = when (word) {
    TryWord.DPAD -> R.string.editor_try_dpad
    TryWord.DPAD2 -> R.string.editor_try_dpad2
    TryWord.STICK -> R.string.editor_try_stick
    TryWord.RIGHT_STICK -> R.string.editor_try_right_stick
    TryWord.UP -> R.string.editor_try_up
    TryWord.DOWN -> R.string.editor_try_down
    TryWord.LEFT -> R.string.editor_try_left
    TryWord.RIGHT -> R.string.editor_try_right
    TryWord.MENU -> R.string.editor_try_menu
    TryWord.QUICK_SAVE -> R.string.editor_try_quick_save
    TryWord.QUICK_LOAD -> R.string.editor_try_quick_load
    TryWord.FAST_FORWARD_HOLD -> R.string.editor_try_fast_forward_hold
    TryWord.FAST_FORWARD -> R.string.editor_try_fast_forward
    TryWord.TURBO -> R.string.editor_try_turbo
    TryWord.SLOW_MOTION -> R.string.editor_try_slow_motion
    TryWord.SCREENSHOT -> R.string.editor_try_screenshot
    TryWord.RESTART -> R.string.editor_try_restart
    TryWord.SCREENS -> R.string.editor_try_screens
    TryWord.KEYPAD_PAGE -> R.string.editor_try_keypad_page
    TryWord.DISK -> R.string.editor_try_disk
}

/** What a control sends, in words: [res] reads a string, [resArg] one with an argument. */
internal fun bindText(words: BindWords, res: (Int) -> String, resArg: (Int, String) -> String): String = when (words) {
    is BindWords.Word -> res(tryWord(words.word))
    is BindWords.Key -> resArg(R.string.editor_try_key, words.key)
    is BindWords.Name -> words.text
}

/** The Layouts panel's line under its title: what this device edits. */
@StringRes
internal fun layoutsBody(form: SkinForm): Int = when (form) {
    SkinForm.PHONE -> R.string.editor_layouts_body_phone
    SkinForm.TABLET -> R.string.editor_layouts_body_tablet
    SkinForm.FOLD -> R.string.editor_layouts_body_fold
}

@StringRes
internal fun layoutsHere(form: SkinForm): Int = when (form) {
    SkinForm.PHONE -> R.string.editor_layouts_on_phone
    SkinForm.TABLET -> R.string.editor_layouts_on_tablet
    SkinForm.FOLD -> R.string.editor_layouts_on_fold
}

/** The switch of another form: its title, and the line under it when on and when off. */
@StringRes
internal fun offerTitle(form: SkinForm): Int = when (form) {
    SkinForm.PHONE -> R.string.editor_offer_phones
    SkinForm.TABLET -> R.string.editor_offer_tablets
    SkinForm.FOLD -> R.string.editor_offer_folds
}

@StringRes
internal fun offerLine(form: SkinForm, on: Boolean): Int = when (form) {
    SkinForm.PHONE -> if (on) R.string.editor_offer_on_phone else R.string.editor_offer_off_phone
    SkinForm.TABLET -> if (on) R.string.editor_offer_on_tablet else R.string.editor_offer_off_tablet
    SkinForm.FOLD -> if (on) R.string.editor_offer_on_fold else R.string.editor_offer_off_fold
}

/** What a layout card of this device says: being edited, changed, both, or missing from the skin; null for nothing. */
@StringRes
internal fun layoutState(present: Boolean, editing: Boolean, changed: Boolean): Int? = when {
    !present -> R.string.editor_layout_missing
    editing && changed -> R.string.editor_editing_changed
    editing -> R.string.editor_editing
    changed -> R.string.editor_changed
    else -> null
}

/** The preview of one layout: its page, its frame and each control, drawn by the game's renderer. */
private class EditorArt(val layout: SkinLayoutId, val page: ImageBitmap, val frame: ImageBitmap?, val elements: Map<String, ImageBitmap>)

private class Loaded(val pkg: SkinPackage, val json: String)

private enum class Gesture { NONE, MOVE, PINCH, HANDLE }

private enum class Tool { NONE, SIZE, NUDGE }

private const val ROTATE = "M20 12a8 8 0 1 1-2.4-5.7M20 4v4h-4"
private const val SCREEN = "M4 5h16v11H4zM9 20h6M12 16v4"

/**
 * The skin editor (issue 784): the layout for this device's form and rotation drawn full screen, controls moved on a
 * magnetic grid and resized, saved into the player's skin [target]. Work in progress is kept as a draft.
 * [onChanged]: the skin list should be read again.
 */
@Composable
fun SkinEditor(library: Library, console: Console, target: SkinEditTarget, onChanged: () -> Unit, onClose: () -> Unit) {
    val opener = LocalPadScope.current
    val pad = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides pad) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Box(Modifier.fillMaxSize().background(StageBg).padRoot(pad)) {
                PadDialogWindow(pad, opener)
                EditorBody(library, console, target, onChanged, onClose)
            }
        }
    }
}

@Composable
private fun EditorBody(library: Library, console: Console, target: SkinEditTarget, onChanged: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val store: SkinStore = library.skins
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var model by remember { mutableStateOf<SkinEditorModel?>(null) }
    var art by remember { mutableStateOf<EditorArt?>(null) }
    // Bumped after every change to the model, which is plain Kotlin: what reads it recomposes.
    var tick by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.NONE) }
    var resizing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var layoutsOpen by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf<SkinForm?>(null) }
    val drafts = remember { Mutex() }
    val form = currentSkinForm()
    val held = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) SkinOrientation.LANDSCAPE else SkinOrientation.PORTRAIT
    // The toolbar's switch (issue 785) shows the other layout; turning the device follows the device again.
    var turned by remember { mutableStateOf<SkinOrientation?>(null) }
    LaunchedEffect(held) { turned = null }
    val orientation = turned ?: held

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    LaunchedEffect(target.id) {
        Log.i(TAG, "editing ${console.id}/${target.id}")
        val read = withContext(Dispatchers.IO) {
            runCatching {
                val (pkg, json) = store.template(console, target.id)
                val draft = store.draft(console, target.id)?.let { runCatching { SkinFormat.parse(it) }.getOrNull() }
                Triple(pkg, json, draft)
            }.onFailure { Log.e(TAG, "${target.id} not opened in the editor", it) }.getOrNull()
        }
        if (read == null) {
            toast(R.string.skins_error)
            onClose()
            return@LaunchedEffect
        }
        val m = SkinEditorModel.fromDraft(read.first.skin, read.third)
        m.show(form, orientation)
        loaded = Loaded(read.first, read.second)
        model = m
        if (m.dirty) toast(R.string.editor_draft_back)
    }
    LaunchedEffect(model, form, orientation) {
        model?.show(form, orientation)
        tool = Tool.NONE
        tick++
    }
    val layoutId = tick.let { model?.layoutId }
    LaunchedEffect(loaded, layoutId) {
        val pkg = loaded?.pkg ?: return@LaunchedEffect
        val id = layoutId ?: return@LaunchedEffect
        if (art?.layout == id) return@LaunchedEffect
        art = withContext(Dispatchers.IO) {
            runCatching {
                val renderer = OpenEmuxSkinRenderer(context)
                // A layout switched on in the Layouts panel is only in the model.
                val layout = model?.layoutOf(id) ?: return@runCatching null
                val scale = minOf(1f, PREVIEW_LONG / maxOf(layout.size.width, layout.size.height))
                val page = renderer.previewPage(pkg, id, layout, scale).asImageBitmap()
                val frame = renderer.previewFrame(pkg, id, layout, scale)?.asImageBitmap()
                // Drawn at twice the size, so a control grown to 200 % stays sharp.
                val elements = layout.elements.associate { e ->
                    val w = (e.rect.width * scale * 2f).roundToInt().coerceIn(1, 1024)
                    val h = (e.rect.height * scale * 2f).roundToInt().coerceIn(1, 1024)
                    e.id to renderer.previewElement(pkg, id, layout, e, w, h).asImageBitmap()
                }
                EditorArt(id, page, frame, elements)
            }.onFailure { Log.e(TAG, "${target.id}: no preview of ${id.id}", it) }.getOrNull()
        }
    }

    /** The model changed; with [commit], the draft on disk follows it. */
    fun changed(commit: Boolean = false) {
        tick++
        // While a save runs, a draft queued behind it would outlive the save it raced and come back on the next Edit.
        if (!commit || busy) return
        val m = model ?: return
        val json = loaded?.json ?: return
        // m.json, not the rect edits alone: a form switched on or off in the Layouts panel is part of the draft too.
        val text = if (m.dirty) m.json(json) else null
        scope.launch(Dispatchers.IO) {
            drafts.withLock {
                runCatching {
                    if (text != null) store.saveDraft(console, target.id, text) else store.dropDraft(console, target.id)
                }
            }
        }
    }

    fun close(dropDraft: Boolean) {
        model?.cancelGesture()
        scope.launch {
            if (dropDraft) withContext(Dispatchers.IO) { drafts.withLock { runCatching { store.dropDraft(console, target.id) } } }
            onClose()
        }
    }

    fun leave() {
        if (busy) return
        if (model?.dirty == true) leaving = true else close(dropDraft = true)
    }

    /** Writes the edits into the skin; then closes ([andClose]) or goes on editing the saved skin. */
    fun save(andClose: Boolean) {
        val m = model ?: return
        val l = loaded ?: return
        if (busy) return
        val text = m.json(l.json)
        busy = true
        scope.launch {
            val reread = withContext(Dispatchers.IO) {
                drafts.withLock {
                    runCatching {
                        store.saveEdits(console, target.id, text)
                        store.dropDraft(console, target.id)
                        if (andClose) null else store.template(console, target.id)
                    }
                }
            }
            busy = false
            if (reread.isFailure) {
                toast(R.string.skins_error)
                return@launch
            }
            toast(R.string.editor_saved)
            onChanged()
            val (pkg, json) = reread.getOrNull() ?: return@launch onClose()
            loaded = Loaded(pkg, json)
            model = SkinEditorModel(pkg.skin).also { it.snap = m.snap; it.show(form, orientation) }
            art = null
        }
    }

    fun saveAsNew(name: String) {
        val m = model ?: return
        val l = loaded ?: return
        if (busy) return
        val text = m.json(l.json)
        busy = true
        scope.launch {
            val ref = withContext(Dispatchers.IO) {
                runCatching { library.migrateSkins() }
                drafts.withLock {
                    runCatching { store.saveAsNew(console, target.id, text, name).also { store.dropDraft(console, target.id) } }.getOrNull()
                }
            }
            busy = false
            if (ref == null) {
                toast(R.string.skins_error)
                return@launch
            }
            // The console played the skin edited: it goes on with the edited copy.
            if (target.inUse) library.useCustomSkin(console, ref)
            toast(R.string.editor_saved)
            onChanged()
            onClose()
        }
    }

    BackHandler {
        val m = model
        when {
            layoutsOpen -> layoutsOpen = false
            m?.trying == true -> { m.tryMode(false); changed() }
            tool != Tool.NONE -> tool = Tool.NONE
            m?.selection != null -> { m.clearSelection(); changed() }
            else -> leave()
        }
    }

    val m = tick.let { model }
    // L1/R1 pick a control (in Try mode: press the next one), L2/R2 resize it by a step. A dialog over the editor
    // takes the buttons away from it.
    GamepadButtons(
        hints = listOf(
            "L1/R1" to stringResource(R.string.editor_pad_pick), "L2/R2" to stringResource(R.string.editor_size),
            "X" to stringResource(R.string.editor_pad_group), "Y" to stringResource(R.string.editor_snap),
            "B" to stringResource(R.string.settings_hint_back),
        ),
        l1 = { if (m?.trying == true) m.tryStep(-1) else m?.step(-1); changed() },
        r1 = { if (m?.trying == true) m.tryStep(1) else m?.step(1); changed() },
        // Try mode moves nothing: the buttons that would edit the layout rest until it ends.
        l2 = { if (m?.trying != true) { m?.sizeStep(-1); changed(commit = true) } },
        r2 = { if (m?.trying != true) { m?.sizeStep(1); changed(commit = true) } },
        x = { m?.toggle(); changed() },
        y = { m?.let { it.snap = !it.snap }; changed() },
        select = { if (m?.trying != true) { m?.undo(); changed(commit = true) } },
        start = { if (m?.dirty == true) save(andClose = false) },
        // The Layouts panel covers the stage: the stage's buttons rest while it is open, as the D-pad nudge does.
        enabled = !leaving && !naming && dropping == null && !layoutsOpen,
    )

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // With a control selected, the D-pad nudges it; with none, it moves the focus as anywhere else.
            val sel = model?.selection?.takeIf { !layoutsOpen } ?: return@onPreviewKeyEvent false
            val step = when (e.key) {
                Key.DirectionUp -> 0 to -1
                Key.DirectionDown -> 0 to 1
                Key.DirectionLeft -> -1 to 0
                Key.DirectionRight -> 1 to 0
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown && sel.ids.isNotEmpty()) {
                model?.nudge(step.first, step.second)
                changed(commit = true)
            }
            true
        },
    ) {
        val layout = m?.layout
        val other = if (orientation == SkinOrientation.PORTRAIT) SkinOrientation.LANDSCAPE else SkinOrientation.PORTRAIT
        val otherShown = m?.layoutOf(SkinForms.id(form, other)) != null
        when {
            m == null -> Unit
            layout == null -> NoLayout(
                target.name, stringResource(R.string.editor_layout, stringResource(formLabel(form)), stringResource(orientationName(orientation))),
                other = stringResource(orientationName(other)).takeIf { otherShown },
                onOther = { turned = other },
            ) { leave() }
            else -> Stage(
                m, layout, art?.takeIf { it.layout == m.layoutId }, tool, resizing, busy,
                title = target.name,
                subtitle = editorSubtitle(
                    stringResource(R.string.editor_layout, stringResource(formLabel(form)), stringResource(orientationName(orientation))),
                    stringResource(R.string.game_screens_stacked).takeIf { layout.screenLayouts.isNotEmpty() },
                ) { a, b -> context.getString(R.string.editor_layout, a, b) },
                onChange = { commit -> changed(commit) },
                onResizing = { resizing = it },
                onTool = { tool = it },
                onSnapped = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                onClose = { leave() },
                onSave = { save(andClose = false) },
                onRotate = { turned = other; tool = Tool.NONE },
                onLayouts = { tool = Tool.NONE; layoutsOpen = true },
            )
        }
        if (m != null && layout != null && layoutsOpen) {
            LayoutsPanel(
                m, tick, form, orientation,
                onShow = { turned = it.takeIf { o -> o != held } },
                onForm = { f, on -> if (on) { if (m.setForm(f, true)) changed(commit = true) } else dropping = f },
                onResetLayout = { m.resetLayout(); changed(commit = true) },
                onClose = { layoutsOpen = false },
            )
        }
    }

    dropping?.let { f ->
        DropFormDialog(
            stringResource(formLabel(f)),
            onDismiss = { dropping = null },
            onRemove = { dropping = null; if (model?.setForm(f, false) == true) changed(commit = true) },
        )
    }

    if (leaving) {
        LeaveDialog(
            target.name,
            onDismiss = { leaving = false },
            onSave = { leaving = false; save(andClose = true) },
            onSaveNew = { leaving = false; naming = true },
            onDiscard = { leaving = false; close(dropDraft = true) },
        )
    }
    if (naming) {
        NameSkinSheet(
            stringResource(R.string.editor_save_new_title), stringResource(R.string.skins_copy_name, target.name).take(SkinStore.MAX_NAME),
            stringResource(R.string.skins_save), onClose = { naming = false },
        ) { name ->
            naming = false
            saveAsNew(name)
        }
    }
}

/** The skin has no layout for this form and rotation: say so, and offer to close. */
@Composable
private fun NoLayout(name: String, layout: String, other: String?, onOther: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = Type.Title.copy(color = StageText, fontSize = 22.sp), textAlign = TextAlign.Center)
        Text(stringResource(R.string.editor_no_layout, layout), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
        other?.let { SecondaryButton(stringResource(R.string.editor_edit_other, it), onOther, Modifier.widthIn(min = 200.dp), icon = ROTATE) }
        SecondaryButton(stringResource(R.string.editor_close), onClose, Modifier.widthIn(min = 200.dp), icon = Icons.Close)
    }
}

/** The stage: the layout fitted to the window, the grid, the controls, the guides, the selection and the bars. */
@Composable
private fun Stage(
    m: SkinEditorModel, layout: SkinLayout, art: EditorArt?, tool: Tool, resizing: Boolean, busy: Boolean,
    title: String, subtitle: String,
    onChange: (Boolean) -> Unit, onResizing: (Boolean) -> Unit, onTool: (Tool) -> Unit, onSnapped: () -> Unit,
    onClose: () -> Unit, onSave: () -> Unit, onRotate: () -> Unit, onLayouts: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fit = SkinFit.of(layout.size.width, layout.size.height, w, h)
        val px = { dp: Float -> with(density) { dp.dp.toPx() } }
        val latest = rememberUpdatedState(m)
        val selected = m.selection?.ids.orEmpty()
        // The selection's box: controls, or the screen area's screens and frame (issue 785).
        val all = layout.rects()
        val selBox = selected.mapNotNull { all[it] }.takeIf { it.isNotEmpty() }?.let { SkinSnap.bounds(it) }
        val selRect = selBox?.let { b -> fit.box(b).let { Rect(it.x - px(8f), it.y - px(8f), it.x + it.w + px(8f), it.y + it.h + px(8f)) } }

        Canvas(
            Modifier.fillMaxSize().pointerInput(fit, layout.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Read after the touch: a Save between two gestures puts a new model in place.
                    val model = latest.value
                    if (model.trying) {
                        // Try mode: the control under the finger is lit while held, and nothing moves.
                        model.hit((down.position.x - fit.ox) / fit.s, (down.position.y - fit.oy) / fit.s, px(10f) / fit.s)
                            ?.takeIf { !SkinArea.isArea(it) }?.let { model.press(it); onSnapped(); onChange(false) }
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        model.release()
                        onChange(false)
                        return@awaitEachGesture
                    }
                    val handleAt = currentHandle(model, fit, px(8f))
                    val onHandle = handleAt != null && (down.position - handleAt).getDistance() <= px(24f)
                    val hit = if (onHandle) null else
                        model.hit((down.position.x - fit.ox) / fit.s, (down.position.y - fit.oy) / fit.s, px(10f) / fit.s)
                    var mode = Gesture.NONE
                    var startDist = 1f
                    var centre = Offset.Zero
                    var lastGuides = model.guides
                    if (onHandle) {
                        model.beginResize()
                        centre = selectionCentre(model, fit) ?: down.position
                        startDist = (down.position - centre).getDistance().coerceAtLeast(px(10f))
                        mode = Gesture.HANDLE
                        onResizing(true)
                        onChange(false)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val first = event.changes.firstOrNull { it.id == down.id }
                        if (first == null || !first.pressed) break
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            mode == Gesture.HANDLE -> {
                                model.resizeBy((first.position - centre).getDistance() / startDist)
                                onChange(false)
                            }
                            mode == Gesture.MOVE -> {
                                // A second finger held down while dragging moves freely.
                                model.moveBy((first.position.x - down.position.x) / fit.s, (first.position.y - down.position.y) / fit.s, free = pressed.size >= 2)
                                if (model.guides.isNotEmpty() && model.guides != lastGuides) onSnapped()
                                lastGuides = model.guides
                                onChange(false)
                            }
                            pressed.size >= 2 -> {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (mode != Gesture.PINCH) {
                                    if (model.selection == null && hit != null) model.tap(hit)
                                    if (model.selection != null) {
                                        model.beginResize()
                                        startDist = d.coerceAtLeast(1f)
                                        mode = Gesture.PINCH
                                        onResizing(true)
                                    }
                                } else {
                                    model.resizeBy(d / startDist)
                                }
                                onChange(false)
                            }
                            mode == Gesture.NONE && hit != null && (first.position - down.position).getDistance() > viewConfiguration.touchSlop -> {
                                model.beginMove(hit)
                                mode = Gesture.MOVE
                                onChange(false)
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (mode == Gesture.NONE) {
                        if (hit != null) model.tap(hit) else model.clearSelection()
                        onTool(Tool.NONE)
                        onChange(false)
                    } else {
                        model.endGesture()
                        onResizing(false)
                        onChange(true)
                    }
                }
            },
        ) {
            drawRect(StageBg)
            val whole = fit.box(SkinRect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat()))
            art?.let {
                drawImage(it.page, dstOffset = IntOffset(whole.x.roundToInt(), whole.y.roundToInt()), dstSize = IntSize(whole.w.roundToInt(), whole.h.roundToInt()), filterQuality = FilterQuality.Medium)
            }
            // The frame and the screens are drawn where they are now, so they follow the screen area as it moves.
            art?.frame?.let { f ->
                layout.frame?.let { fit.box(it.rect) }?.let { b ->
                    drawImage(f, dstOffset = IntOffset(b.x.roundToInt(), b.y.roundToInt()), dstSize = IntSize(b.w.roundToInt().coerceAtLeast(1), b.h.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
                }
            }
            layout.screens.forEach { r -> fit.box(r).let { b -> drawRect(ScreenBg, Offset(b.x, b.y), Size(b.w, b.h)) } }
            // The grid, the whole time.
            val step = SkinGrid.step(layout.size) * fit.s
            val dot = px(1.2f)
            var gy = whole.y
            while (gy <= whole.y + whole.h + 0.5f) {
                var gx = whole.x
                while (gx <= whole.x + whole.w + 0.5f) {
                    drawCircle(GridDot, dot, Offset(gx, gy))
                    gx += step
                }
                gy += step
            }
            layout.elements.forEach { e ->
                val b = fit.box(e.rect)
                val image = art?.elements?.get(e.id)
                if (image != null) {
                    drawImage(image, dstOffset = IntOffset(b.x.roundToInt(), b.y.roundToInt()), dstSize = IntSize(b.w.roundToInt().coerceAtLeast(1), b.h.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
                } else {
                    drawRoundRect(Color(0xFF3A3448), Offset(b.x, b.y), Size(b.w, b.h), CornerRadius(px(10f)))
                }
                if (e.id == m.pressed) {
                    drawRoundRect(Oe.Accent.copy(alpha = 0.35f), Offset(b.x, b.y), Size(b.w, b.h), CornerRadius(px(12f)))
                    drawRoundRect(Guide, Offset(b.x - px(4f), b.y - px(4f)), Size(b.w + px(8f), b.h + px(8f)), CornerRadius(px(14f)), style = Stroke(px(3f)))
                }
            }
            m.guides.forEach { g ->
                if (g.axis == SnapAxis.X) {
                    val x = fit.ox + g.pos * fit.s
                    drawLine(Guide, Offset(x, 0f), Offset(x, size.height), px(2f))
                } else {
                    val y = fit.oy + g.pos * fit.s
                    drawLine(Guide, Offset(0f, y), Offset(size.width, y), px(2f))
                }
            }
            selRect?.let { r ->
                drawRoundRect(Guide, r.topLeft, r.size, CornerRadius(px(12f)), style = Stroke(px(2f)))
                val c = Offset(r.right - px(4f), r.bottom - px(4f))
                drawCircle(Color.White, px(11f), c)
                drawCircle(Oe.Accent, px(9.5f), c, style = Stroke(px(3f)))
            }
        }

        // Labels: what was snapped to while dragging, else the selection's name; the size while resizing.
        val area = stringResource(R.string.editor_screen_area)
        val names = layout.elements.associate { it.id to elementName(it) { res -> context.getString(res) } } +
            SkinArea.ids(layout).filter(SkinArea::isArea).associateWith { area }
        if (selRect != null) {
            val label = when {
                resizing -> null
                m.guides.isNotEmpty() -> m.guides.joinToString(" · ") { g ->
                    guideText(g, g.other?.let(names::get)) { res, arg -> if (arg == null) context.getString(res) else context.getString(res, arg) }
                }
                else -> when (val sel = m.selection) {
                    is SkinSelection.Group -> context.getString(groupName(sel.group)) + " · " + context.getString(R.string.editor_tap_again)
                    is SkinSelection.One -> names[sel.id].orEmpty()
                    is SkinSelection.Screen -> area
                    null -> null
                }
            }
            label?.let {
                val above = selRect.top - px(36f)
                Pill(it, Oe.Accent, Color.White, Modifier.offset { IntOffset(selRect.left.roundToInt().coerceIn(0, (w - px(120f)).roundToInt().coerceAtLeast(0)), (if (above < px(4f)) selRect.bottom + px(8f) else above).roundToInt()) })
            }
            if (resizing) m.percent()?.let { p ->
                Pill(stringResource(R.string.editor_percent, p), Color.White, Color(0xFF1E1E25), Modifier.offset { IntOffset((selRect.right - px(4f)).roundToInt().coerceAtMost((w - px(64f)).roundToInt()), (selRect.bottom + px(12f)).roundToInt().coerceAtMost((h - px(32f)).roundToInt())) })
            }
        }

        // The bars float over the game's screen, the one region no thumb rests on.
        val screens = layout.screens.map { fit.box(it) }
        val screenTop = screens.minOf { it.y }
        val screenBottom = screens.maxOf { it.y + it.h }
        val screenLeft = screens.minOf { it.x }
        val screenRight = screens.maxOf { it.x + it.w }
        val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat()
        val barW = minOf(maxOf(screenRight - screenLeft - px(16f), minOf(w - px(32f), px(340f))), px(420f), w - px(16f))
        val barX = ((screenLeft + screenRight) / 2f - barW / 2f).coerceIn(px(8f), maxOf(px(8f), w - barW - px(8f)))
        val topY = maxOf(screenTop + px(8f), safeTop + px(8f))
        val bottomY = maxOf(screenBottom - px(8f) - px(60f), topY + px(56f))
        val barWidth = with(density) { barW.toDp() }

        if (m.trying) {
            TryOverlay(m, layout, names, Modifier.offset { IntOffset(barX.roundToInt(), topY.roundToInt()) }.width(barWidth)) {
                m.tryMode(false); onChange(false)
            }
            return@BoxWithConstraints
        }
        Toolbar(
            title, subtitle, m.canUndo, m.canRedo, m.dirty && !busy,
            Modifier.offset { IntOffset(barX.roundToInt(), topY.roundToInt()) }.width(barWidth),
            onClose = onClose,
            onUndo = { m.undo(); onChange(true) },
            onRedo = { m.redo(); onChange(true) },
            onSave = onSave,
            onRotate = onRotate,
            onLayouts = onLayouts,
        )
        // The hints only where the screen leaves them room between the bars.
        if (m.selection == null && m.guides.isEmpty() && bottomY - topY >= px(140f)) {
            Column(
                Modifier.offset { IntOffset(barX.roundToInt(), ((topY + bottomY) / 2f - px(10f)).roundToInt()) }.width(barWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.editor_hint_tap), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
                Text(stringResource(R.string.editor_hint_drag), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
            }
        }
        // Panels open away from what is edited (issue 785): over the screen unless the selection is there.
        val barH = px(60f)
        val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat()
        val placed = selRect?.takeIf { it.overlaps(Rect(barX, bottomY, barX + barW, bottomY + barH)) }?.let { sel ->
            val screen = SkinRect(screenLeft, screenTop, screenRight - screenLeft, screenBottom - screenTop)
            when (SkinPanels.side(w > h, w.toFloat(), screen, SkinRect(sel.left, sel.top, sel.width, sel.height))) {
                PanelSide.TOP -> barX to topY + px(56f)
                PanelSide.BOTTOM -> barX to (h - safeBottom - barH - px(8f))
                PanelSide.START -> px(8f) to (h - barH) / 2f
                PanelSide.END -> (w - barW - px(8f)) to (h - barH) / 2f
            }
        } ?: (barX to bottomY)
        val bottom = Modifier.offset { IntOffset(placed.first.roundToInt(), placed.second.roundToInt()) }
        when {
            m.selection == null -> Box(bottom.width(barWidth), contentAlignment = Alignment.Center) {
                SnapBar(
                    m.snap, Modifier, onSnap = { m.snap = it; onChange(false) }, onResetLayout = { m.resetLayout(); onChange(true) },
                    onScreenArea = { m.selectScreen(); onChange(false) }, onTry = { m.tryMode(true); onTool(Tool.NONE); onChange(false) },
                )
            }
            tool == Tool.SIZE -> SizeRow(m.percent() ?: 100, bottom.width(barWidth), onStep = { m.sizeStep(it); onChange(true) }, onDone = { onTool(Tool.NONE) })
            tool == Tool.NUDGE -> NudgeRow(bottom.width(barWidth), onNudge = { x, y -> m.nudge(x, y); onChange(true) }, onDone = { onTool(Tool.NONE) })
            else -> ActionsBar(
                m.percent() ?: 100, bottom.width(barWidth),
                onSize = { onTool(Tool.SIZE) }, onNudge = { onTool(Tool.NUDGE) }, onReset = { m.reset(); onChange(true) },
            )
        }
    }
}

/** Where the selection's corner handle is on screen, or null with nothing selected. */
private fun currentHandle(m: SkinEditorModel, fit: SkinFit, outset: Float): Offset? {
    val box = selectionBox(m, fit) ?: return null
    return Offset(box.right + outset - outset / 2f, box.bottom + outset - outset / 2f)
}

private fun selectionCentre(m: SkinEditorModel, fit: SkinFit): Offset? = selectionBox(m, fit)?.center

private fun selectionBox(m: SkinEditorModel, fit: SkinFit): Rect? {
    val all = m.layout?.rects() ?: return null
    val rects = m.selection?.ids?.mapNotNull { all[it] }?.takeIf { it.isNotEmpty() } ?: return null
    val b = fit.box(SkinSnap.bounds(rects))
    return Rect(b.x, b.y, b.x + b.w, b.y + b.h)
}

@Composable
private fun Pill(text: String, background: Color, color: Color, modifier: Modifier) {
    Box(modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(background).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.Small.copy(color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

private fun Modifier.bar() = this.clip(RoundedCornerShape(16.dp)).background(BarBg).border(1.dp, BarBorder, RoundedCornerShape(16.dp)).padding(4.dp)

/** A 40 dp icon button of the bars; [label] is what a screen reader says. */
@Composable
private fun BarIcon(path: String, label: String, enabled: Boolean = true, mirrored: Boolean = false, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(path, if (enabled) BarText else BarOff, 20.dp, filled = filled, modifier = if (mirrored) Modifier.graphicsLayer(scaleX = -1f) else Modifier)
    }
}

@Composable
private fun Toolbar(
    title: String, subtitle: String, canUndo: Boolean, canRedo: Boolean, canSave: Boolean, modifier: Modifier,
    onClose: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onSave: () -> Unit, onRotate: () -> Unit, onLayouts: () -> Unit,
) {
    val layouts = stringResource(R.string.editor_layouts)
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BarIcon(Icons.Close, stringResource(R.string.editor_close), onClick = onClose)
        // The layout field opens the Layouts panel; the icon beside it shows the other orientation (issue 785).
        Column(
            Modifier.weight(1f).height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).background(Field)
                .clickable(onClick = onLayouts).semantics { contentDescription = "$layouts: $subtitle" }.padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = Type.Small.copy(color = StageMuted, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Type.Body.copy(color = StageText, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BarIcon(ROTATE, stringResource(R.string.editor_rotate), onClick = onRotate)
        BarIcon(Icons.Undo, stringResource(R.string.editor_undo), enabled = canUndo, onClick = onUndo)
        BarIcon(Icons.Undo, stringResource(R.string.editor_redo), enabled = canRedo, mirrored = true, onClick = onRedo)
        Box(
            Modifier.height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).background(if (canSave) Oe.Accent else Field)
                .clickable(enabled = canSave, onClick = onSave).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.editor_save), style = Type.Body.copy(color = if (canSave) Color.White else BarOff, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** With nothing selected: the Snap toggle, Try, and More (Screen area, Reset layout). */
@Composable
private fun SnapBar(
    snap: Boolean, modifier: Modifier, onSnap: (Boolean) -> Unit, onResetLayout: () -> Unit, onScreenArea: () -> Unit, onTry: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp))
                .background(if (snap) Oe.Accent.copy(alpha = 0.25f) else Color.Transparent)
                .toggleable(snap, role = Role.Switch, onValueChange = onSnap).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SNAP, if (snap) Lilac else BarText, 18.dp)
            Text(stringResource(R.string.editor_snap), style = Type.Body.copy(color = if (snap) Lilac else BarText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Row(
            Modifier.height(40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onTry).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Play, Lilac, 16.dp, filled = true)
            Text(stringResource(R.string.editor_try), style = Type.Body.copy(color = BarText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Box {
            BarIcon(Icons.Dots, stringResource(R.string.editor_more), filled = true) { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_screen_area), style = Type.Body) },
                    leadingIcon = { Icon(SCREEN, Oe.Text, 18.dp) },
                    onClick = { more = false; onScreenArea() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_reset_layout), style = Type.Body) },
                    leadingIcon = { Icon(Icons.Reset, Oe.Text, 18.dp) },
                    onClick = { more = false; onResetLayout() },
                )
            }
        }
    }
}

/** A tool of the actions bar: an icon (or [top], a value) over its name. */
@Composable
private fun ToolButton(label: String, modifier: Modifier, onClick: () -> Unit, top: @Composable () -> Unit) {
    Column(
        modifier.height(52.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        top()
        Text(label, style = Type.Small.copy(color = BarText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** For a selection: Size (its percentage), Nudge, Reset. Colour and Texture come with issue 786. */
@Composable
private fun ActionsBar(percent: Int, modifier: Modifier, onSize: () -> Unit, onNudge: () -> Unit, onReset: () -> Unit) {
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(stringResource(R.string.editor_size), Modifier.weight(1f), onSize) {
            Text(stringResource(R.string.editor_percent, percent), style = Type.Body.copy(color = Lilac, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        ToolButton(stringResource(R.string.editor_nudge), Modifier.weight(1f), onNudge) { Icon(NUDGE, BarText, 22.dp) }
        ToolButton(stringResource(R.string.editor_reset), Modifier.weight(1f), onReset) { Icon(Icons.Reset, BarText, 22.dp) }
    }
}

/** The Size tool: one 5 % step smaller or larger. */
@Composable
private fun SizeRow(percent: Int, modifier: Modifier, onStep: (Int) -> Unit, onDone: () -> Unit) {
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BarIcon(MINUS, stringResource(R.string.editor_smaller), enabled = percent > SkinResize.MIN) { onStep(-1) }
        Text(
            stringResource(R.string.editor_percent, percent), Modifier.weight(1f),
            style = Type.Body.copy(color = Lilac, fontSize = 16.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center,
        )
        BarIcon(Icons.Plus, stringResource(R.string.editor_larger), enabled = percent < SkinResize.MAX) { onStep(1) }
        DoneButton(onDone)
    }
}

/** The Nudge tool: one grid step per tap. */
@Composable
private fun NudgeRow(modifier: Modifier, onNudge: (Int, Int) -> Unit, onDone: () -> Unit) {
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        BarIcon(Icons.Back, stringResource(R.string.editor_left)) { onNudge(-1, 0) }
        BarIcon(Icons.ChevronUp, stringResource(R.string.editor_up)) { onNudge(0, -1) }
        BarIcon(Icons.ChevronDown, stringResource(R.string.editor_down)) { onNudge(0, 1) }
        BarIcon(Icons.Chevron, stringResource(R.string.editor_right)) { onNudge(1, 0) }
        DoneButton(onDone)
    }
}

@Composable
private fun DoneButton(onDone: () -> Unit) {
    val label = stringResource(R.string.editor_done)
    Box(
        Modifier.size(44.dp, 40.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp)).background(Oe.Accent).clickable(onClick = onDone)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Check, Color.White, 20.dp) }
}

/** Leaving with changes (in either rotation's layout): Save, Save as a new skin…, Discard. */
@Composable
private fun LeaveDialog(name: String, onDismiss: () -> Unit, onSave: () -> Unit, onSaveNew: () -> Unit, onDiscard: () -> Unit) {
    val opener = LocalPadScope.current
    val pad = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides pad) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier.padRoot(pad).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Oe.Sheet).border(1.dp, BarBorder, RoundedCornerShape(24.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PadDialogWindow(pad, opener)
                Text(stringResource(R.string.editor_leave_title, name), style = Type.Title.copy(fontSize = 21.sp))
                Text(stringResource(R.string.editor_leave_body), style = Type.Small.copy(fontSize = 14.sp, lineHeight = 20.sp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(stringResource(R.string.editor_save), onSave, Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.editor_save_new), onSaveNew, Modifier.fillMaxWidth())
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onDiscard),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.editor_discard), style = Type.Body.copy(color = Oe.Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Clock, Oe.Muted, 16.dp)
                    Text(stringResource(R.string.editor_draft_note), style = Type.Small.copy(fontSize = 12.sp, lineHeight = 17.sp))
                }
            }
        }
    }
}

/**
 * Try mode (issue 785, design 3.4): a bar to go back, and over the screen what was pressed last and what it sends to
 * the game, with the last few presses.
 */
@Composable
private fun TryOverlay(m: SkinEditorModel, layout: SkinLayout, names: Map<String, String>, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Play, Guide, 16.dp, filled = true, modifier = Modifier.padding(start = 10.dp))
            Text(stringResource(R.string.editor_try_title), Modifier.weight(1f), style = Type.Body.copy(color = StageText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 2)
            Box(
                Modifier.height(36.dp).gamepadFocus(corner = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF33333D))
                    .clickable(onClick = onBack).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.editor_try_back), style = Type.Body.copy(color = StageText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1) }
        }
        val shown = (m.pressed ?: m.recent.lastOrNull())?.let { id -> layout.elements.firstOrNull { it.id == id } }
        if (shown != null) {
            val words = bindText(SkinTry.words(shown.bind), { context.getString(it) }) { res, arg -> context.getString(res, arg) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.editor_try_pressed), style = Type.Small.copy(color = StageMuted, fontSize = 12.sp))
                Text(names[shown.id].orEmpty(), style = Type.Title.copy(color = Color.White, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(stringResource(R.string.editor_try_sends, words), style = Type.Small.copy(color = StageMuted, fontSize = 12.sp), textAlign = TextAlign.Center)
            }
        }
        if (m.recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.editor_try_last), style = Type.Small.copy(color = StageMuted, fontSize = 12.sp))
                m.recent.forEachIndexed { i, id ->
                    val last = i == m.recent.lastIndex
                    Box(
                        Modifier.height(26.dp).widthIn(min = 26.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (last) Oe.Accent.copy(alpha = 0.35f) else Field).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(names[id].orEmpty(), style = Type.Small.copy(color = StageText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1) }
                }
            }
        }
    }
}

/**
 * The Layouts panel (issue 785, design 4.2): this device's two layouts (tap one to edit it) and a switch per other
 * form, which is what the skin's badges show. It opens away from the selection ([SkinPanels]).
 */
@Composable
private fun LayoutsPanel(
    // [tick]: the model is plain Kotlin, so the panel is told when it changed.
    m: SkinEditorModel, @Suppress("UNUSED_PARAMETER") tick: Int, form: SkinForm, orientation: SkinOrientation,
    onShow: (SkinOrientation) -> Unit, onForm: (SkinForm, Boolean) -> Unit, onResetLayout: () -> Unit, onClose: () -> Unit,
) {
    val layout = m.layout ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fit = SkinFit.of(layout.size.width, layout.size.height, w, h)
        fun stage(r: SkinRect) = fit.box(r).let { SkinRect(it.x, it.y, it.w, it.h) }
        val rects = layout.rects()
        val screen = stage(SkinSnap.bounds(layout.screens.ifEmpty { listOf(SkinRect(0f, 0f, layout.size.width.toFloat(), layout.size.height / 2f)) }))
        val selected = m.selection?.ids?.mapNotNull { rects[it] }?.takeIf { it.isNotEmpty() }?.let { stage(SkinSnap.bounds(it)) }
        val side = SkinPanels.side(w > h, w.toFloat(), screen, selected)
        Box(Modifier.fillMaxSize().background(Color(0x66050508)).pointerInput(Unit) { detectTapGestures { onClose() } })
        val wide = minOf(360.dp, maxWidth * 0.5f)
        val (align, shape, size) = when (side) {
            PanelSide.TOP -> Triple(Alignment.TopCenter, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.62f))
            PanelSide.BOTTOM -> Triple(Alignment.BottomCenter, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.62f))
            PanelSide.START -> Triple(Alignment.CenterStart, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), Modifier.width(wide).fillMaxHeight())
            PanelSide.END -> Triple(Alignment.CenterEnd, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp), Modifier.width(wide).fillMaxHeight())
        }
        Column(
            Modifier.align(align).then(size).clip(shape).background(Oe.Sheet).border(1.dp, BarBorder, shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_layouts), Modifier.weight(1f), style = Type.Title.copy(fontSize = 22.sp))
                DoneButton(onClose)
            }
            Text(stringResource(layoutsBody(form)), style = Type.Small.copy(fontSize = 13.sp, lineHeight = 18.sp))
            SectionTitle(stringResource(layoutsHere(form)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkinOrientation.entries.forEach { o ->
                    val id = SkinForms.id(form, o)
                    val present = m.layoutOf(id) != null
                    val editing = o == orientation
                    LayoutCard(o, layoutState(present, editing, m.changed(id)), editing, present, Modifier.weight(1f)) { onShow(o) }
                }
            }
            SectionTitle(stringResource(R.string.editor_layouts_other))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Raised)) {
                SkinForm.entries.filter { it != form }.forEachIndexed { i, f ->
                    val on = m.offered(f)
                    SwitchRow(stringResource(offerTitle(f)), stringResource(offerLine(f, on)), on, { onForm(f, it) }, divider = i > 0)
                }
            }
            FormBadges(SkinForm.entries.filter(m::offered).toSet())
            Text(stringResource(R.string.editor_layouts_note), style = Type.Small.copy(fontSize = 12.sp, lineHeight = 18.sp))
            SecondaryButton(stringResource(R.string.editor_reset_layout), onResetLayout, Modifier.fillMaxWidth(), icon = Icons.Reset)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = Type.Small.copy(fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold))
}

/** One of this device's layouts in the Layouts panel: a small device turned its way, its name and its state. */
@Composable
private fun LayoutCard(orientation: SkinOrientation, @StringRes state: Int?, editing: Boolean, present: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.gamepadFocus(corner = 16.dp).clip(shape)
            .let { if (editing) it.background(Oe.Accent.copy(alpha = 0.12f)).border(2.dp, Oe.Accent, shape) else it.border(1.dp, BarBorder, shape) }
            .clickable(enabled = present && !editing, onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val upright = orientation == SkinOrientation.PORTRAIT
        Box(Modifier.height(78.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(if (upright) 44.dp else 78.dp, if (upright) 78.dp else 44.dp).clip(RoundedCornerShape(8.dp)).background(StageBg).border(2.dp, Color(0xFF6E6C7A), RoundedCornerShape(8.dp)))
        }
        Text(stringResource(orientationName(orientation)), style = Type.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Text(state?.let { stringResource(it) } ?: " ", style = Type.Small.copy(color = if (editing) Oe.AccentText else Oe.Muted, fontSize = 12.sp), maxLines = 1)
    }
}

/** Switching a form off: its layouts leave the skin when it is saved (issue 785). */
@Composable
private fun DropFormDialog(form: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val opener = LocalPadScope.current
    val pad = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides pad) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier.padRoot(pad).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Oe.Sheet).border(1.dp, BarBorder, RoundedCornerShape(24.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PadDialogWindow(pad, opener)
                Text(stringResource(R.string.editor_form_off_title, form), style = Type.Title.copy(fontSize = 21.sp))
                Text(stringResource(R.string.editor_form_off_body), style = Type.Small.copy(fontSize = 14.sp, lineHeight = 20.sp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.editor_remove), style = Type.Body.copy(color = Oe.Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
                    SecondaryButton(stringResource(R.string.detail_cancel), onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
