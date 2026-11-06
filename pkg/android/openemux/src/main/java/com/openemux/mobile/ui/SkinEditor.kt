package com.openemux.mobile.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.core.content.edit
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.EditedSkinSource
import com.openemux.mobile.data.SkinApply
import com.openemux.mobile.data.SkinContrastCheck
import com.openemux.mobile.data.SkinEditJson
import com.openemux.mobile.data.SkinEditorModel
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFill
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinImageEdit
import com.openemux.mobile.data.SkinPainter
import com.openemux.mobile.data.SkinPalettes
import com.openemux.mobile.data.SkinPart
import com.openemux.mobile.data.SkinScope
import com.openemux.mobile.data.SkinTarget
import com.openemux.mobile.data.SkinTemplate
import com.openemux.mobile.data.SkinTextureFit
import com.openemux.mobile.data.SkinTextureFrame
import com.openemux.mobile.data.look
import com.openemux.mobile.data.withLook
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
import com.openemux.mobile.launch.PreviewSkinSource
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
private const val FRAME_ICON = "M3 5h18v14H3zM7 9h10v6H7z"

/** The longest side a texture is drawn at for the stage while it is being framed (issue 786). */
private const val PREVIEW_TEXTURE = 400f

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

/**
 * The preview of one layout: its page and each control, drawn by the game's renderer at [scale] of its units. [pageKey]
 * and [keys] are what each was drawn from (colours and image edits, issue 786): only what changed is drawn again.
 */
private class EditorArt(
    val layout: SkinLayoutId, val page: ImageBitmap, val elements: Map<String, ImageBitmap>,
    val pageKey: Any? = null, val keys: Map<String, Any?> = emptyMap(),
)

/** What the paint panel is open on (issue 786): the selection's controls, the background or the frame. */
private data class PanelOpen(
    val kind: PanelKind, val tab: PaintTab, val part: SkinPart, val scope: SkinScope = SkinScope.ONE, val palettes: Boolean = false,
)

private enum class PanelKind { CONTROLS, BACKGROUND, FRAME }

/** A picture being framed as a texture: decoded small enough for the parts it fills. */
private class TextureSession(val source: Bitmap, val name: String, val width: Int, val height: Int, val frame: SkinTextureFrame = SkinTextureFrame())

private const val PREFS = "openemux"
private const val PREF_PALETTE = "skin_editor_palette"
private const val PREF_RECENT = "skin_editor_recent"

private class Loaded(val pkg: SkinPackage, val json: String)

private enum class Gesture { NONE, MOVE, PINCH, HANDLE }

private enum class Tool { NONE, SIZE, NUDGE }

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
    var panel by remember { mutableStateOf<PanelOpen?>(null) }
    var texture by remember { mutableStateOf<TextureSession?>(null) }
    var overrides by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    // The images the texture being framed fills, as last drawn: a new selection must not retarget it before it goes in.
    var textureFor by remember { mutableStateOf<List<Pair<String, com.openemux.mobile.data.TemplateImage>>>(emptyList()) }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var palette by remember { mutableStateOf(SkinPalettes.byId(prefs.getString(PREF_PALETTE, null))) }
    var recent by remember { mutableStateOf(SkinPalettes.recentFrom(prefs.getString(PREF_RECENT, null))) }
    val drafts = remember { Mutex() }
    val form = currentSkinForm()
    val orientation = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) SkinOrientation.LANDSCAPE else SkinOrientation.PORTRAIT

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    LaunchedEffect(target.id) {
        Log.i(TAG, "editing ${console.id}/${target.id}")
        val read = withContext(Dispatchers.IO) {
            runCatching {
                val (pkg, json) = store.template(console, target.id)
                val draftJson = store.draft(console, target.id)
                val draft = draftJson?.let { runCatching { SkinFormat.parse(it) }.getOrNull() }
                // No draft: textures left staged by an editor that never saved are of no use any more.
                val images = if (draft != null) store.draftImages(console, target.id) else emptyMap<String, SkinImageEdit>().also { store.dropDraft(console, target.id) }
                Triple(pkg, json, Triple(draft, images, pkg.source.files))
            }.onFailure { Log.e(TAG, "${target.id} not opened in the editor", it) }.getOrNull()
        }
        if (read == null) {
            toast(R.string.skins_error)
            onClose()
            return@LaunchedEffect
        }
        val (draft, images, files) = read.third
        val m = SkinEditorModel(read.first.skin, draft?.rects(), draft?.layouts?.mapValues { it.value.look() }, images, files)
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
    val paintKey = tick.let { model?.let { mm -> mm.layoutId?.let { mm.looks[it] } to mm.images } } to overrides
    LaunchedEffect(loaded, layoutId, paintKey) {
        val pkg = loaded?.pkg ?: return@LaunchedEffect
        val mm = model ?: return@LaunchedEffect
        val id = layoutId ?: return@LaunchedEffect
        val look = mm.looks[id] ?: return@LaunchedEffect
        val images = mm.images
        val shown = overrides
        val before = art?.takeIf { it.layout == id }
        art = withContext(Dispatchers.IO) {
            runCatching {
                val renderer = OpenEmuxSkinRenderer(context)
                val layout = pkg.skin.layouts.getValue(id).withLook(look)
                // The skin as edited: its colours, its image edits, and the textures being framed.
                val source = PreviewSkinSource(EditedSkinSource(pkg.source, images) { store.staged(console, target.id, it) }, shown)
                val view = SkinPackage(pkg.skin.copy(layouts = pkg.skin.layouts + (id to layout)), source)
                fun edits(files: List<String>) = files.map { "${id.id}/$it" }.map { it to (images[it] to shown[it]) }
                val scale = minOf(1f, PREVIEW_LONG / maxOf(layout.size.width, layout.size.height))
                val pageKey = listOf(look.background, look.frame, edits(listOfNotNull(layout.background.imageFile, layout.frame?.imageFile)))
                val page = if (before != null && before.pageKey == pageKey) before.page else renderer.previewPage(view, id, scale).asImageBitmap()
                val keys = layout.elements.associate { e ->
                    e.id to listOf(e.colors, if (e.type == SkinElementType.ACTION) look.frame else null, edits(e.imageFiles))
                }
                // Drawn at twice the size, so a control grown to 200 % stays sharp.
                val elements = layout.elements.associate { e ->
                    val kept = before?.elements?.get(e.id)?.takeIf { before.keys[e.id] == keys[e.id] }
                    e.id to (kept ?: run {
                        val w = (e.rect.width * scale * 2f).roundToInt().coerceIn(1, 1024)
                        val h = (e.rect.height * scale * 2f).roundToInt().coerceIn(1, 1024)
                        renderer.previewElement(view, id, e, w, h).asImageBitmap()
                    })
                }
                EditorArt(id, page, elements, pageKey, keys)
            }.onFailure { Log.e(TAG, "${target.id}: no preview of ${id.id}", it) }.getOrNull()
        } ?: before
    }

    /** The model changed; with [commit], the draft on disk follows it. */
    fun changed(commit: Boolean = false) {
        tick++
        // While a save runs, a draft queued behind it would outlive the save it raced and come back on the next Edit.
        if (!commit || busy) return
        val m = model ?: return
        val json = loaded?.json ?: return
        val text = if (m.dirty) m.json(json) else null
        val images = m.images
        scope.launch(Dispatchers.IO) {
            drafts.withLock {
                runCatching {
                    // Not dirty (undone back to the skin): the staged textures stay, a redo may bring one back.
                    if (text != null) store.saveDraft(console, target.id, text, images) else store.dropDraft(console, target.id, keepTextures = true)
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
        val images = m.images
        busy = true
        scope.launch {
            val reread = withContext(Dispatchers.IO) {
                drafts.withLock {
                    runCatching {
                        store.saveEdits(console, target.id, text, images)
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
            model = SkinEditorModel(pkg.skin, existing = pkg.source.files).also { it.snap = m.snap; it.show(form, orientation) }
            art = null
        }
    }

    fun saveAsNew(name: String) {
        val m = model ?: return
        val l = loaded ?: return
        if (busy) return
        val text = m.json(l.json)
        val images = m.images
        busy = true
        scope.launch {
            val ref = withContext(Dispatchers.IO) {
                runCatching { library.migrateSkins() }
                drafts.withLock {
                    runCatching { store.saveAsNew(console, target.id, text, name, images).also { store.dropDraft(console, target.id) } }.getOrNull()
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

    // ---- The paint panel (issue 786). ----

    /** What [p] paints now: its target and the controls it is open on (the selection's, none for the background or frame). */
    fun panelTarget(p: PanelOpen): Pair<SkinTarget, List<String>>? {
        val mm = model ?: return null
        val l = mm.layout ?: return null
        return when (p.kind) {
            PanelKind.BACKGROUND -> SkinTarget.Background to emptyList()
            PanelKind.FRAME -> if (l.frame != null) SkinTarget.Frame to emptyList() else null
            PanelKind.CONTROLS -> {
                val ids = mm.selection?.ids ?: return null
                (SkinTarget.of(l, ids) ?: return null) to ids
            }
        }
    }

    /** The controls a colour or texture goes on, by [p]'s Apply to. */
    fun painted(p: PanelOpen): List<String> {
        val (t, ids) = panelTarget(p) ?: return emptyList()
        val l = model?.layout ?: return emptyList()
        return if (t is SkinTarget.Controls) SkinApply.targets(l, ids, p.scope, p.part) else ids
    }

    /** The images a texture of [p] fills: path in the skin and the template's image (its exact size and kind). */
    fun textureTargets(p: PanelOpen): List<Pair<String, com.openemux.mobile.data.TemplateImage>> {
        val mm = model ?: return emptyList()
        val l = mm.layout ?: return emptyList()
        val id = mm.layoutId ?: return emptyList()
        if (!p.part.texturable) return emptyList()
        val files = SkinPainter.files(id, l, painted(p), p.part).toSet()
        return SkinTemplate.images(l).filter { !it.keypadPage && "${id.id}/${it.file}" in files }.map { "${id.id}/${it.file}" to it }
    }

    /** The texture being framed goes into the skin (staged until Save), one undo step; then [after]. */
    fun commitTexture(after: () -> Unit = {}) {
        val t = texture ?: return after()
        val p = panel
        val mm = model
        texture = null
        if (p == null || mm == null || busy) {
            overrides = emptyMap()
            return after()
        }
        // What it was framed on: by now the selection may be another one (or none), which is what made this commit.
        val targets = textureFor
        busy = true
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val r = OpenEmuxSkinRenderer(context)
                    targets.associate { (path, img) ->
                        val b = r.texture(t.source, img.kind, img.element, img.width, img.height, t.frame)
                        try {
                            path to store.stageTexture(console, target.id, r.png(b))
                        } finally {
                            b.recycle()
                        }
                    }
                }.onFailure { Log.e(TAG, "${target.id}: texture not kept", it) }.getOrNull()
            }
            busy = false
            if (staged == null) toast(R.string.skins_error) else mm.texture(staged)
            overrides = emptyMap()
            changed(commit = true)
            after()
        }
    }

    fun openPanel(kind: PanelKind, tab: PaintTab) {
        commitTexture {
            val l = model?.layout
            val ids = model?.selection?.ids.orEmpty()
            val parts = when (kind) {
                PanelKind.BACKGROUND -> SkinTarget.Background.parts
                PanelKind.FRAME -> SkinTarget.Frame.parts
                PanelKind.CONTROLS -> l?.let { SkinTarget.of(it, ids) }?.let { t -> if (t is SkinTarget.Frame) listOf(SkinPart.ICONS) + t.parts else t.parts }.orEmpty()
            }
            val part = parts.firstOrNull() ?: return@commitTexture
            panel = PanelOpen(kind, tab, part)
            tool = Tool.NONE
            tick++
        }
    }

    fun closePanel() {
        commitTexture {
            panel = null
            tick++
        }
    }

    // Another rotation or form is another layout: a picture being framed goes in first, then the panel closes.
    LaunchedEffect(layoutId) {
        if (panel != null) closePanel()
    }

    fun paint(color: Int, commit: Boolean) {
        val p = panel ?: return
        val mm = model ?: return
        val (t, _) = panelTarget(p) ?: return
        // A colour and a picture are one fill: the colour replaces the picture being framed.
        texture = null
        mm.paint(t, painted(p), p.part, color)
        if (commit) {
            mm.endGesture()
            recent = SkinPalettes.recent(recent, color)
            prefs.edit { putString(PREF_RECENT, SkinPalettes.recentText(recent)) }
        }
        changed(commit)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val p = panel
        if (uri == null || p == null) return@rememberLauncherForActivityResult
        val targets = textureTargets(p)
        val w = targets.maxOfOrNull { it.second.width } ?: 256
        val h = targets.maxOfOrNull { it.second.height } ?: 256
        scope.launch {
            val picked = withContext(Dispatchers.IO) { runCatching { decodePicture(context, uri, w, h) }.getOrNull() }
            if (picked == null) toast(R.string.editor_image_unreadable) else texture = picked
        }
    }

    // The texture as framed, drawn small on every part it fills while the panel is open: the stage shows it live.
    val bake = remember { Mutex() }
    LaunchedEffect(texture, panel?.part, panel?.scope, panel?.kind, layoutId) {
        val t = texture
        val p = panel
        if (t == null || p == null) {
            if (overrides.isNotEmpty()) overrides = emptyMap()
            return@LaunchedEffect
        }
        val targets = textureTargets(p)
        textureFor = targets
        bake.withLock {
            overrides = withContext(Dispatchers.Default) {
                val r = OpenEmuxSkinRenderer(context)
                targets.associate { (path, img) ->
                    val k = minOf(1f, PREVIEW_TEXTURE / maxOf(img.width, img.height))
                    path to r.texture(t.source, img.kind, img.element, (img.width * k).roundToInt(), (img.height * k).roundToInt(), t.frame)
                }
            }
        }
    }

    // A new selection retargets the panel: a picture being framed goes in first; with none left, the panel closes.
    val selectedIds = tick.let { model?.selection?.ids }
    LaunchedEffect(selectedIds) {
        val p = panel ?: return@LaunchedEffect
        if (p.kind != PanelKind.CONTROLS) return@LaunchedEffect
        commitTexture {
            val now = panel ?: return@commitTexture
            val target = panelTarget(now)?.first
            panel = when {
                target == null -> null
                now.part in target.parts || (target is SkinTarget.Frame && now.part == SkinPart.ICONS) -> now
                else -> now.copy(part = target.parts.first(), scope = SkinScope.ONE)
            }
            tick++
        }
    }

    BackHandler {
        val m = model
        val p = panel
        when {
            p?.palettes == true -> panel = p.copy(palettes = false)
            p != null -> closePanel()
            tool != Tool.NONE -> tool = Tool.NONE
            m?.selection != null -> { m.clearSelection(); changed() }
            else -> leave()
        }
    }

    val m = tick.let { model }
    GamepadButtons(
        hints = listOf(
            "L1/R1" to stringResource(R.string.editor_pad_pick), "X" to stringResource(R.string.editor_pad_group),
            "Y" to stringResource(R.string.editor_snap), "B" to stringResource(R.string.settings_hint_back),
        ),
        l1 = { m?.step(-1); changed() },
        r1 = { m?.step(1); changed() },
        x = { m?.toggle(); changed() },
        y = { m?.let { it.snap = !it.snap }; changed() },
        select = { m?.undo(); changed(commit = true) },
        start = { if (m?.dirty == true) save(andClose = false) },
    )

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // With a control selected, the D-pad nudges it; with none (or the paint panel open), it moves the focus.
            if (panel != null) return@onPreviewKeyEvent false
            val sel = model?.selection ?: return@onPreviewKeyEvent false
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
        when {
            m == null -> Unit
            layout == null -> NoLayout(target.name, stringResource(R.string.editor_layout, stringResource(formLabel(form)), stringResource(orientationName(orientation)))) { leave() }
            else -> Stage(
                m, layout, art?.takeIf { it.layout == m.layoutId }, tool, resizing, busy,
                panel = panel?.let { p -> paintView(context, m, layout, p, panelTarget(p), painted(p), palette, recent, texture, overrides, textureTargets(p).firstOrNull()?.first) }?.let { v ->
                    v to PaintActions(
                        onTab = { tab -> commitTexture { panel = panel?.copy(tab = tab); tick++ } },
                        onPart = { part -> panel = panel?.copy(part = part); tick++ },
                        onScope = { sc -> panel = panel?.copy(scope = sc); tick++ },
                        onColor = { c, commit -> paint(c, commit) },
                        onTone = { tone -> m.tone(tone); changed(commit = true) },
                        onPalettes = { open -> panel = panel?.copy(palettes = open) },
                        onPalette = { pal ->
                            palette = pal
                            prefs.edit { putString(PREF_PALETTE, pal.id) }
                            panel = panel?.copy(palettes = false)
                        },
                        onPickImage = { runCatching { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }.onFailure { toast(R.string.editor_image_unreadable) } },
                        onFill = { fill -> texture = texture?.let { TextureSession(it.source, it.name, it.width, it.height, it.frame.copy(fill = fill)) } },
                        onFrame = { x, y, f, _ ->
                            if (x != 0f || y != 0f || f != 1f) texture = texture?.let { TextureSession(it.source, it.name, it.width, it.height, it.frame.moved(x, y, f)) }
                        },
                        onZoom = { z, _ ->
                            if (!z.isNaN()) texture = texture?.let { TextureSession(it.source, it.name, it.width, it.height, it.frame.copy(zoom = z.coerceIn(SkinTextureFit.MIN_ZOOM, SkinTextureFit.MAX_ZOOM))) }
                        },
                        onRemoveTexture = {
                            texture = null
                            panel?.let { p -> panelTarget(p)?.let { (t, _) -> m.removeTexture(t, painted(p), p.part) } }
                            changed(commit = true)
                        },
                        onDone = { closePanel() },
                    )
                },
                onColour = { openPanel(PanelKind.CONTROLS, PaintTab.COLOUR) },
                onTexture = { openPanel(PanelKind.CONTROLS, PaintTab.TEXTURE) },
                onBackground = { m.clearSelection(); openPanel(PanelKind.BACKGROUND, PaintTab.COLOUR) },
                onFrame = layout.frame?.let { { m.clearSelection(); openPanel(PanelKind.FRAME, PaintTab.COLOUR) } },
                title = target.name,
                subtitle = stringResource(R.string.editor_layout, stringResource(formLabel(form)), stringResource(orientationName(orientation))),
                onChange = { commit -> changed(commit) },
                onResizing = { resizing = it },
                onTool = { tool = it },
                onSnapped = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                onClose = { leave() },
                onSave = { save(andClose = false) },
            )
        }
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
private fun NoLayout(name: String, layout: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = Type.Title.copy(color = StageText, fontSize = 22.sp), textAlign = TextAlign.Center)
        Text(stringResource(R.string.editor_no_layout, layout), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
        SecondaryButton(stringResource(R.string.editor_close), onClose, Modifier.widthIn(min = 200.dp), icon = Icons.Close)
    }
}

/** The stage: the layout fitted to the window, the grid, the controls, the guides, the selection and the bars. */
@Composable
private fun Stage(
    m: SkinEditorModel, layout: SkinLayout, art: EditorArt?, tool: Tool, resizing: Boolean, busy: Boolean,
    panel: Pair<PaintView, PaintActions>?,
    onColour: () -> Unit, onTexture: () -> Unit, onBackground: () -> Unit, onFrame: (() -> Unit)?,
    title: String, subtitle: String,
    onChange: (Boolean) -> Unit, onResizing: (Boolean) -> Unit, onTool: (Tool) -> Unit, onSnapped: () -> Unit,
    onClose: () -> Unit, onSave: () -> Unit,
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
        val selBox = selected.mapNotNull { id -> layout.elements.firstOrNull { it.id == id }?.rect }.takeIf { it.isNotEmpty() }?.let { SkinSnap.bounds(it) }
        val selRect = selBox?.let { b -> fit.box(b).let { Rect(it.x - px(8f), it.y - px(8f), it.x + it.w + px(8f), it.y + it.h + px(8f)) } }

        Canvas(
            Modifier.fillMaxSize().pointerInput(fit, layout.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Read after the touch: a Save between two gestures puts a new model in place.
                    val model = latest.value
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
        val names = layout.elements.associate { it.id to elementName(it) { res -> context.getString(res) } }
        if (selRect != null) {
            val label = when {
                resizing -> null
                m.guides.isNotEmpty() -> m.guides.joinToString(" · ") { g ->
                    guideText(g, g.other?.let(names::get)) { res, arg -> if (arg == null) context.getString(res) else context.getString(res, arg) }
                }
                else -> when (val sel = m.selection) {
                    is SkinSelection.Group -> context.getString(groupName(sel.group)) + " · " + context.getString(R.string.editor_tap_again)
                    is SkinSelection.One -> names[sel.id].orEmpty()
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

        if (panel != null) {
            // Away from what is being edited: sideways, the other half of the window; upright, over the game's screen.
            val focus = selBox ?: if (panel.first.target == SkinTarget.Frame) layout.frame?.rect else null
            val sideways = w > h
            if (sideways) {
                val panelW = minOf(px(380f), w * 0.55f)
                val onLeft = focus == null || fit.box(focus).let { it.x + it.w / 2f } > w / 2f
                val x = if (onLeft) 0f else w - panelW
                PaintPanel(
                    panel.first, panel.second,
                    Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(with(density) { panelW.toDp() }).height(with(density) { h.toDp() }),
                )
            } else {
                // Over the game's screen; the frame borders it, so for the frame the panel goes to the bottom instead.
                val atBottom = panel.first.target == SkinTarget.Frame
                Box(Modifier.fillMaxSize(), contentAlignment = if (atBottom) Alignment.BottomCenter else Alignment.TopCenter) {
                    PaintPanel(panel.first, panel.second, Modifier.fillMaxWidth().heightIn(max = with(density) { (h * 0.6f).toDp() }))
                }
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
        )
        if (m.selection == null && m.guides.isEmpty()) {
            Column(
                Modifier.offset { IntOffset(barX.roundToInt(), ((topY + bottomY) / 2f - px(10f)).roundToInt()) }.width(barWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.editor_hint_tap), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
                Text(stringResource(R.string.editor_hint_drag), style = Type.Small.copy(color = StageMuted, fontSize = 14.sp), textAlign = TextAlign.Center)
            }
        }
        val bottom = Modifier.offset { IntOffset(barX.roundToInt(), bottomY.roundToInt()) }
        when {
            m.selection == null -> Box(bottom.width(barWidth), contentAlignment = Alignment.Center) {
                SnapBar(m.snap, Modifier, onSnap = { m.snap = it; onChange(false) }, onResetLayout = { m.resetLayout(); onChange(true) }, onBackground = onBackground, onFrame = onFrame)
            }
            tool == Tool.SIZE -> SizeRow(m.percent() ?: 100, bottom.width(barWidth), onStep = { m.sizeStep(it); onChange(true) }, onDone = { onTool(Tool.NONE) })
            tool == Tool.NUDGE -> NudgeRow(bottom.width(barWidth), onNudge = { x, y -> m.nudge(x, y); onChange(true) }, onDone = { onTool(Tool.NONE) })
            else -> ActionsBar(
                m.percent() ?: 100, bottom.width(barWidth), paintable = SkinTarget.of(layout, m.selection?.ids.orEmpty()) != null,
                onColour = onColour, onTexture = onTexture,
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
    val layout = m.layout ?: return null
    val rects = m.selection?.ids?.mapNotNull { id -> layout.elements.firstOrNull { it.id == id }?.rect }?.takeIf { it.isNotEmpty() } ?: return null
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
    onClose: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onSave: () -> Unit,
) {
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BarIcon(Icons.Close, stringResource(R.string.editor_close), onClick = onClose)
        Column(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Field).padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = Type.Small.copy(color = StageMuted, fontSize = 11.sp, lineHeight = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Type.Body.copy(color = StageText, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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

/** With nothing selected: the Snap toggle and More (Reset layout). */
@Composable
private fun SnapBar(snap: Boolean, modifier: Modifier, onSnap: (Boolean) -> Unit, onResetLayout: () -> Unit, onBackground: () -> Unit, onFrame: (() -> Unit)?) {
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
        Box {
            BarIcon(Icons.Dots, stringResource(R.string.editor_more), filled = true) { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_background), style = Type.Body) },
                    leadingIcon = { Icon(Icons.Image, Oe.Text, 18.dp) },
                    onClick = { more = false; onBackground() },
                )
                onFrame?.let { open ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_screen_frame), style = Type.Body) },
                        leadingIcon = { Icon(FRAME_ICON, Oe.Text, 18.dp) },
                        onClick = { more = false; open() },
                    )
                }
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

/** For a selection: Colour and Texture (issue 786; not for a quick action on a layout with no frame), Size (its percentage), Nudge, Reset. */
@Composable
private fun ActionsBar(
    percent: Int, modifier: Modifier, paintable: Boolean, onColour: () -> Unit, onTexture: () -> Unit,
    onSize: () -> Unit, onNudge: () -> Unit, onReset: () -> Unit,
) {
    Row(modifier.bar(), verticalAlignment = Alignment.CenterVertically) {
        if (paintable) {
            ToolButton(stringResource(R.string.editor_colour), Modifier.weight(1f), onColour) {
                Canvas(Modifier.size(22.dp)) {
                    listOf(0xFFC4B5FD, 0xFF8B5CF6, 0xFF6D28D9, 0xFFA78BFA).forEachIndexed { i, c -> drawArc(Color(c), -90f + i * 90f, 90f, true) }
                }
            }
            ToolButton(stringResource(R.string.editor_texture), Modifier.weight(1f), onTexture) { Icon(Icons.Image, BarText, 22.dp) }
        }
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

/** The paint panel's view of [p] (issue 786): everything it shows, read from the model. */
private fun paintView(
    context: Context, m: SkinEditorModel, layout: SkinLayout, p: PanelOpen, at: Pair<SkinTarget, List<String>>?, painted: List<String>,
    palette: com.openemux.mobile.data.SkinPalette, recent: List<Int>, texture: TextureSession?, overrides: Map<String, Bitmap>, first: String?,
): PaintView? {
    val (target, ids) = at ?: return null
    val sel = m.selection
    val scopes = if (target is SkinTarget.Controls) {
        SkinApply.scopes(layout, ids, p.part).map { sc ->
            sc to when (sc) {
                SkinScope.ONE -> if (sel is SkinSelection.Group) context.getString(groupName(sel.group)) else context.getString(R.string.editor_apply_one)
                SkinScope.GROUP -> layout.elements.firstOrNull { it.id == ids.first() }?.group?.let { context.getString(groupName(it)) }.orEmpty()
                SkinScope.ALL -> context.getString(allName(target.type))
            }
        }
    } else emptyList()
    val textured = m.textured(painted, p.part)
    return PaintView(
        target = target,
        editing = when (target) {
            SkinTarget.Background -> context.getString(R.string.editor_background)
            SkinTarget.Frame -> context.getString(R.string.editor_screen_frame)
            is SkinTarget.Controls -> null
        },
        tab = p.tab,
        part = p.part,
        scopes = scopes,
        scope = p.scope,
        color = SkinPainter.colorOf(layout, painted, p.part),
        presets = palette,
        recent = recent,
        contrast = contrastFor(p.part, SkinContrastCheck.issues(layout, target, painted)),
        replacesImage = p.part.texturable && textured,
        tone = layout.frame?.colors?.activeIcon,
        palettes = p.palettes,
        textured = textured,
        texture = texture?.let { t -> TextureDraft(t.name, t.width, t.height, first?.let { overrides[it] }?.asImageBitmap()) },
        fill = texture?.frame?.fill ?: SkinFill.STRETCH,
        zoom = texture?.frame?.zoom ?: 1f,
    )
}

/**
 * The picture at [uri] decoded for a texture of at most [w] x [h] (subsampled, never whole when it is a big photo), with
 * the name the picker shows; null when it is not an image this device decodes.
 */
private fun decodePicture(context: Context, uri: Uri, w: Int, h: Int): TextureSession? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = SkinTextureFit.sample(bounds.outWidth, bounds.outHeight, w, h)
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    return TextureSession(bitmap, name, bounds.outWidth, bounds.outHeight)
}
