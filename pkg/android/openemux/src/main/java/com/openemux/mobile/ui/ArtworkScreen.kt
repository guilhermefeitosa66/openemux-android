package com.openemux.mobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import com.openemux.mobile.data.ArtCandidate
import com.openemux.mobile.data.ArtImages
import com.openemux.mobile.data.ArtKind
import com.openemux.mobile.data.ArtOutcome
import com.openemux.mobile.data.ArtProvider
import com.openemux.mobile.data.ArtSource
import com.openemux.mobile.data.ArtState
import com.openemux.mobile.data.PickedArt
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.RomIdentity
import com.openemux.mobile.data.SearchIssue
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** What the manager says after an action: the message's text and how loud it is. */
internal data class ArtMessage(val text: String, val danger: Boolean = false)

/** The manager's tabs (issue 753); [id] is the route's `tab` argument. */
enum class ArtTab(val id: String) {
    CURRENT("current"), COVER("cover"), LABEL("label"), IMPORT("import");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
        fun of(kind: ArtKind) = if (kind == ArtKind.COVER) COVER else LABEL
    }
}

/**
 * The per-game artwork manager: Current (issue 746) shows where each art came from, with Sync now,
 * Remove and Reset; Cover and Label search the providers, and Import edits a picked image or the
 * current art (issue 753).
 */
@Composable
fun ArtworkScreen(key: String, startTab: String? = null) {
    val a = LocalActions.current
    val roms by a.app.library.roms.collectAsState()
    val rom = roms.firstOrNull { it.key == key }
    if (rom == null) {
        GamepadButtons(hints = listOf("B" to stringResource(R.string.settings_hint_back)))
        // Removed from the library while the manager was open (or a stale route).
        Box(Modifier.fillMaxSize().background(Oe.Bg))
        return
    }
    val context = LocalContext.current
    val artwork = a.app.artwork
    val scope = rememberCoroutineScope()
    val kinds = remember(rom.console) { artwork.kinds(rom.console) }
    val tabs = remember(kinds) { listOf(ArtTab.CURRENT, ArtTab.COVER) + (if (ArtKind.LABEL in kinds) listOf(ArtTab.LABEL) else emptyList()) + ArtTab.IMPORT }
    var tabId by rememberSaveable { mutableStateOf(ArtTab.of(startTab)?.takeIf { it in tabs }?.id ?: ArtTab.CURRENT.id) }
    val tab = ArtTab.of(tabId) ?: ArtTab.CURRENT
    var running by remember { mutableStateOf<Pair<ArtKind, String>?>(null) }
    var message by remember { mutableStateOf<ArtMessage?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf<ArtKind?>(null) }
    val searches = remember(rom.key) { kinds.associateWith { SearchState(it, rom.title) } }
    val editor = remember(rom.key) { EditorState() }
    DisposableEffect(editor) { onDispose { editor.clear() } }
    val states = rememberArtStates(rom, kinds)
    // The hash only when a search tab is opened: hashing a large disc image takes a while.
    var hashWanted by remember { mutableStateOf(false) }
    if (tab == ArtTab.COVER || tab == ArtTab.LABEL) hashWanted = true
    val identity = produceState<HashState>(HashState.Computing, hashWanted, rom) {
        if (!hashWanted) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { RomIdentity.of(rom.file, rom.console.extensions) }.getOrNull()?.let { HashState.Known(it) } ?: HashState.Failed
        }
    }.value

    val notFoundCover = stringResource(R.string.artwork_msg_sync_not_found_cover, rom.title)
    val notFoundLabel = stringResource(R.string.artwork_msg_sync_not_found_label, rom.title)
    val resetNotFoundCover = stringResource(R.string.artwork_msg_reset_not_found_cover, rom.title)
    val resetNotFoundLabel = stringResource(R.string.artwork_msg_reset_not_found_label, rom.title)
    val busy = stringResource(R.string.artwork_msg_busy)
    val unavailable = stringResource(R.string.artwork_msg_unavailable)
    fun run(kind: ArtKind, action: String, notFound: String, block: suspend () -> ArtOutcome) {
        if (running != null) return
        running = kind to action
        message = null
        scope.launch {
            val outcome = try {
                block()
            } finally {
                running = null
            }
            message = when (outcome) {
                ArtOutcome.DONE -> null
                // Remove has no "not found" text: the art was already gone, nothing to say.
                ArtOutcome.NOT_FOUND -> notFound.takeIf { it.isNotEmpty() }?.let { ArtMessage(it) }
                ArtOutcome.UNAVAILABLE -> ArtMessage(unavailable)
                ArtOutcome.BUSY -> ArtMessage(busy)
            }
        }
    }

    fun select(next: ArtTab) {
        tabId = next.id
        message = null
    }

    fun search(kind: ArtKind) {
        select(ArtTab.of(kind))
        searches[kind]?.start(scope, a.app.artSearch, rom, null)
    }

    // "Search artwork…" and the other ways in that name a search tab start its search at once.
    LaunchedEffect(rom.key) {
        ArtTab.of(startTab)?.let { if (it == ArtTab.COVER) search(ArtKind.COVER) else if (it == ArtTab.LABEL && ArtKind.LABEL in kinds) search(ArtKind.LABEL) }
    }

    // The image picker, and the art kind it was opened for (kept across a process death while the picker is up).
    var pickFor by rememberSaveable { mutableStateOf<ArtKind?>(null) }
    fun load(result: PickedArt, name: String, kind: ArtKind, origin: String) {
        when (result) {
            is PickedArt.Copy -> scope.launch {
                if (editor.load(result.file, name, kind, origin)) {
                    select(ArtTab.IMPORT)
                } else {
                    message = ArtMessage(context.getString(R.string.artwork_msg_unreadable), danger = true)
                }
            }
            PickedArt.NotImage -> message = ArtMessage(context.getString(R.string.artwork_msg_not_image), danger = true)
            PickedArt.Unreadable -> message = ArtMessage(context.getString(R.string.artwork_msg_unreadable), danger = true)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val kind = pickFor ?: ArtKind.COVER
        pickFor = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (result, name) = withContext(Dispatchers.IO) { ArtImages.importUri(context, uri) to displayName(context, uri) }
            load(result, name, kind, ArtCandidate.ORIGIN_FILE)
        }
    }
    fun pick(kind: ArtKind) {
        pickFor = kind
        message = null
        runCatching { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
            .onFailure { message = ArtMessage(context.getString(R.string.artwork_msg_unreadable), danger = true) }
    }
    fun adjust(kind: ArtKind) {
        val state = states[kind] ?: return
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                state.file?.let { ArtImages.copyOf(context, it) }
                    ?: state.url?.let { url -> context.imageLoader.diskCache?.openSnapshot(url)?.use { ArtImages.copyOf(context, it.data.toFile()) } }
            }
            if (result == null) message = ArtMessage(context.getString(R.string.artwork_msg_nothing_to_adjust))
            else load(result, rom.title, kind, ArtCandidate.ORIGIN_ADJUSTED)
        }
    }

    val saveFailed = stringResource(R.string.artwork_msg_save_failed)
    fun saved(outcome: ArtOutcome) {
        if (outcome == ArtOutcome.DONE) {
            select(ArtTab.CURRENT)
        } else {
            message = ArtMessage(saveFailed, danger = true)
        }
    }

    GamepadButtons(
        hints = listOf(
            "A" to stringResource(R.string.settings_hint_choose), "B" to stringResource(R.string.settings_hint_back),
            "L1 R1" to stringResource(R.string.settings_hint_tabs),
        ),
        l1 = { select(tabs[(tabs.indexOf(tab) - 1).coerceAtLeast(0)]) },
        r1 = { select(tabs[(tabs.indexOf(tab) + 1).coerceAtMost(tabs.lastIndex)]) },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Oe.Bg)) {
        val wide = maxWidth >= 840.dp
        val side = if (wide) Modifier.padding(horizontal = 32.dp) else Modifier.readingWidth().padding(horizontal = 16.dp)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().then(side).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(rom, if (wide) ({ ArtTabs(tabs, tab, ::select, Modifier.width(440.dp)) }) else null)
                if (!wide) ArtTabs(tabs, tab, ::select, Modifier.fillMaxWidth())
                message?.let { Banner(it) { message = null } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    ArtTab.CURRENT -> CurrentTab(
                        rom, kinds, states, wide, side, running,
                        onSync = { kind -> run(kind, ACTION_SYNC, if (kind == ArtKind.COVER) notFoundCover else notFoundLabel) { artwork.syncNow(rom, kind) } },
                        onRemove = { kind -> run(kind, ACTION_REMOVE, "") { artwork.remove(rom, kind) } },
                        onReset = { kind -> confirmReset = kind },
                        onSearch = ::search,
                        onPick = ::pick,
                        onAdjust = ::adjust,
                    )
                    ArtTab.COVER, ArtTab.LABEL -> {
                        val kind = if (tab == ArtTab.COVER) ArtKind.COVER else ArtKind.LABEL
                        val state = searches[kind]
                        if (state != null) {
                            SearchTab(
                                rom, state, identity, wide, side,
                                onIssue = { message = it?.let { issue -> issueMessage(context, issue) } },
                                onCancel = { select(ArtTab.CURRENT) },
                                onSave = { candidate -> scope.launch { saved(state.save(context, artwork, rom, candidate)) } },
                            )
                        }
                    }
                    ArtTab.IMPORT -> ImportTab(
                        editor, kinds, wide, side,
                        onPick = { pick(editor.saveAs) },
                        onCancel = { editor.clear(); select(ArtTab.CURRENT) },
                        onSave = { scope.launch { saved(editor.save(artwork, rom)) } },
                    )
                }
            }
        }
    }

    confirmReset?.let { kind ->
        val cover = kind == ArtKind.COVER
        OeConfirmDialog(
            { confirmReset = null },
            stringResource(if (cover) R.string.artwork_reset_title_cover else R.string.artwork_reset_title_label),
            stringResource(if (cover) R.string.artwork_reset_body_cover else R.string.artwork_reset_body_label),
            stringResource(R.string.artwork_reset_confirm),
            {
                confirmReset = null
                run(kind, ACTION_RESET, if (cover) resetNotFoundCover else resetNotFoundLabel) { artwork.reset(rom, kind) }
            },
            stringResource(R.string.detail_cancel),
        )
    }
}

/** The ROM's hash, for the Hash field and Search by hash. */
sealed interface HashState {
    data object Computing : HashState
    data object Failed : HashState
    class Known(val identity: RomIdentity) : HashState
}

/** The plain-word message for what went wrong in a search. */
internal fun issueMessage(context: Context, issue: SearchIssue) = when (issue) {
    SearchIssue.OFFLINE -> ArtMessage(context.getString(R.string.artwork_msg_offline), danger = true)
    SearchIssue.REFUSED -> ArtMessage(context.getString(R.string.artwork_msg_refused), danger = true)
    SearchIssue.NO_CREDENTIALS -> ArtMessage(context.getString(R.string.artwork_msg_no_credentials))
    SearchIssue.QUOTA -> ArtMessage(context.getString(R.string.artwork_msg_quota))
    SearchIssue.UNREACHABLE -> ArtMessage(context.getString(R.string.artwork_msg_ss_unreachable))
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "image"

private const val ACTION_SYNC = "sync"
private const val ACTION_RESET = "reset"
private const val ACTION_REMOVE = "remove"

@Composable
private fun Header(rom: Rom, trailing: (@Composable () -> Unit)?) {
    val a = LocalActions.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Icons.Back, stringResource(R.string.detail_back), { a.nav.popBackStack() }, background = Oe.Control, padFocus = false)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.artwork_title), style = Type.Title.copy(fontSize = 22.sp))
            Text(
                stringResource(R.string.artwork_subtitle, rom.title, rom.console.displayName),
                style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** The segmented tab row of the design: Current · Cover · Label · Import. */
@Composable
private fun ArtTabs(tabs: List<ArtTab>, selected: ArtTab, onSelect: (ArtTab) -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Oe.Surface).border(1.dp, Oe.Border, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { t ->
            val on = t == selected
            Box(
                Modifier.weight(1f).height(padSize(36.dp, 40.dp)).gamepadFocus(corner = 10.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Oe.AccentSoft else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when (t) {
                            ArtTab.CURRENT -> R.string.artwork_tab_current
                            ArtTab.COVER -> R.string.artwork_cover
                            ArtTab.LABEL -> R.string.artwork_label
                            ArtTab.IMPORT -> R.string.artwork_tab_import
                        },
                    ),
                    style = Type.Button.copy(fontSize = 14.sp, color = if (on) Oe.AccentOnSoft else Oe.Muted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A message inside the manager, never a toast over something else (issue 746). */
@Composable
internal fun Banner(message: ArtMessage, onDismiss: () -> Unit) {
    val bg = if (message.danger) Oe.DangerBg else Oe.WarnBg
    val fg = if (message.danger) Oe.Danger else Oe.WarnText
    val line = when {
        message.danger -> if (Oe.dark) Color(0xFF4A2A2E) else Color(0xFFF3C9C5)
        else -> if (Oe.dark) Color(0xFF3D3424) else Color(0xFFF0DDB8)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, line, RoundedCornerShape(14.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (message.danger) Icons.Alert else Icons.Info, if (message.danger) Oe.Danger else Oe.Warn, 18.dp)
        Text(message.text, style = Type.Body.copy(fontSize = 14.sp, lineHeight = 19.sp, color = fg), modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        RoundIconButton(Icons.Close, stringResource(R.string.artwork_dismiss), onDismiss, tint = fg, background = Color.Transparent)
    }
}

@Composable
private fun CurrentTab(
    rom: Rom,
    kinds: List<ArtKind>,
    states: Map<ArtKind, ArtState>,
    wide: Boolean,
    side: Modifier,
    running: Pair<ArtKind, String>?,
    onSync: (ArtKind) -> Unit,
    onRemove: (ArtKind) -> Unit,
    onReset: (ArtKind) -> Unit,
    onSearch: (ArtKind) -> Unit,
    onPick: (ArtKind) -> Unit,
    onAdjust: (ArtKind) -> Unit,
) {
    MarginFocusScroll(trailing = FocusMargin + hintBarInset) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(side).padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val cards: @Composable (ArtKind, Modifier) -> Unit = { kind, modifier ->
                val state = states[kind]
                if (state != null) {
                    ArtCard(
                        rom, state, wide, modifier,
                        running = running?.takeIf { it.first == kind }?.second,
                        busy = running != null,
                        onSync = { onSync(kind) }, onRemove = { onRemove(kind) }, onReset = { onReset(kind) },
                        onSearch = { onSearch(kind) }, onPick = { onPick(kind) }, onAdjust = { onAdjust(kind) },
                    )
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    kinds.forEach { cards(it, Modifier.weight(1f)) }
                    if (kinds.size == 1) Spacer(Modifier.weight(1f))
                }
            } else {
                kinds.forEach { cards(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun ArtCard(
    rom: Rom,
    state: ArtState,
    wide: Boolean,
    modifier: Modifier,
    running: String?,
    busy: Boolean,
    onSync: () -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
    onSearch: () -> Unit,
    onPick: () -> Unit,
    onAdjust: () -> Unit,
) {
    val cover = state.kind == ArtKind.COVER
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Oe.Surface).border(1.dp, Oe.Border, RoundedCornerShape(18.dp)).padding(if (wide) 20.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 14.dp)) {
            val scale = if (wide) 1.95f else 1f
            Preview(rom, state, if (cover) 92.dp * scale else 128.dp * scale, if (cover) 128.dp * scale else 92.dp * scale)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (cover) R.string.artwork_cover else R.string.artwork_label), style = Type.Section.copy(fontSize = 16.sp))
                    SourcePill(state.source)
                }
                Facts(state)
            }
        }
        if (state.missing) {
            PrimaryButton(
                stringResource(if (running == ACTION_SYNC) R.string.artwork_syncing else R.string.artwork_sync_now),
                { if (running == null) onSync() }, Modifier.fillMaxWidth(), icon = Icons.Reset, height = 48.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(if (cover) R.string.artwork_search_covers else R.string.artwork_search_labels), onSearch, Modifier.weight(1f), height = 48.dp, busy = busy)
                SecondaryButton(stringResource(R.string.artwork_import_file), onPick, Modifier.weight(1f), height = 48.dp, busy = busy)
            }
        } else {
            val pickAnother = @Composable { m: Modifier -> SecondaryButton(stringResource(R.string.artwork_pick_another), onSearch, m, height = 48.dp, busy = busy) }
            val replace = @Composable { m: Modifier -> SecondaryButton(stringResource(R.string.artwork_replace_file), onPick, m, height = 48.dp, busy = busy) }
            val adjust = @Composable { m: Modifier -> SecondaryButton(stringResource(R.string.artwork_adjust), onAdjust, m, height = 48.dp, busy = busy) }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pickAnother(Modifier.weight(1f)); replace(Modifier.weight(1f)); adjust(Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pickAnother(Modifier.weight(1f)); replace(Modifier.weight(1f))
                }
                adjust(Modifier.fillMaxWidth())
            }
        }
        if (state.resettable || state.removable) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Oe.Divider))
            if (state.resettable) {
                SecondaryButton(
                    stringResource(
                        when {
                            running == ACTION_RESET -> R.string.artwork_syncing
                            cover -> R.string.artwork_reset_cover
                            else -> R.string.artwork_reset_label
                        },
                    ),
                    onReset, Modifier.fillMaxWidth(), height = 48.dp, icon = Icons.Load, busy = busy,
                )
            }
            if (state.removable) {
                DangerButton(stringResource(if (cover) R.string.artwork_remove_cover else R.string.artwork_remove_label), onRemove, busy)
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit, busy: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, Oe.Border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { if (!busy) onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Trash, Oe.Danger, 20.dp)
        Text(text, style = Type.Button.copy(color = Oe.Danger, fontSize = 15.sp))
    }
}

/**
 * Where each of [kinds] came from for [rom], read off the main thread and again after every
 * artwork change, a label found or a cover sync starting or ending; empty until the first read.
 * Shared by the manager's cards and the detail screen's Artwork row, so they cannot disagree.
 */
@Composable
fun rememberArtStates(rom: Rom, kinds: List<ArtKind>): Map<ArtKind, ArtState> {
    val app = LocalActions.current.app
    val changes by app.artwork.changes.collectAsState()
    // The sync can bring art in while the screen is open: its progress is part of what it shows.
    val labelsFound by app.labels.found.collectAsState()
    // Only whether the cover sync runs matters: collecting every progress tick would recompose the caller on each cover.
    val coversRunning by remember(app) { app.covers.progress.map { it.running }.distinctUntilChanged() }
        .collectAsState(app.covers.progress.value.running)
    return produceState(initialValue = emptyMap<ArtKind, ArtState>(), rom, kinds, changes, labelsFound, coversRunning) {
        value = withContext(Dispatchers.IO) { kinds.associateWith { app.artwork.describe(rom, it) } }
    }.value
}

/** The name of where an art came from, as its pill and the detail screen's Artwork row say it. */
@StringRes
fun artworkSourceLabel(source: ArtSource?): Int = when (source) {
    ArtSource.SYNCED -> R.string.artwork_pill_automatic
    ArtSource.MANUAL -> R.string.artwork_pill_manual
    ArtSource.LOCAL -> R.string.artwork_pill_local
    null -> R.string.artwork_pill_missing
}

@Composable
private fun SourcePill(source: ArtSource?) {
    val text = artworkSourceLabel(source)
    val (bg, fg) = when (source) {
        ArtSource.SYNCED -> Oe.OkBg to Oe.OkText
        ArtSource.MANUAL -> Oe.AccentSoft to Oe.AccentOnSoft
        ArtSource.LOCAL -> Oe.WarnBg to Oe.WarnText
        null -> Oe.Surface2 to Oe.Muted
    }
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(stringResource(text), style = Type.Caption.copy(color = fg, fontSize = 12.sp))
    }
}

/** The lines under the title: when and where from, then the file's format and size. */
@Composable
private fun Facts(state: ArtState) {
    val cover = state.kind == ArtKind.COVER
    val locale = LocalConfiguration.current.locales[0]
    val date = state.updated?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it)) }
    val primary = Type.Body.copy(fontSize = 14.sp)
    when (state.source) {
        null -> {
            Text(stringResource(if (cover) R.string.artwork_missing_cover else R.string.artwork_missing_label), style = Type.BodyMedium.copy(fontSize = 14.sp))
            Text(stringResource(if (cover) R.string.artwork_missing_cover_hint else R.string.artwork_missing_label_hint), style = Type.Small)
            return
        }
        ArtSource.SYNCED -> Text(date?.let { stringResource(R.string.artwork_synced_on, it) } ?: stringResource(R.string.artwork_synced_unknown), style = primary)
        ArtSource.MANUAL -> Text(date?.let { stringResource(R.string.artwork_saved_on, it) } ?: stringResource(R.string.artwork_chosen_by_you), style = primary)
        ArtSource.LOCAL -> Text(stringResource(R.string.artwork_found_on_device), style = primary)
    }
    val context = LocalContext.current
    val info = produceState<ImageInfo?>(null, state.file, state.file?.lastModified(), state.url) {
        value = withContext(Dispatchers.IO) {
            val file = state.file ?: state.url?.let { url ->
                context.imageLoader.diskCache?.openSnapshot(url)?.use { it.data.toFile() }
            }
            when {
                file != null -> imageInfo(file) ?: ImageInfo.UNREADABLE
                // A mirror cover not in the image cache yet: nothing is wrong with it, there is just no file to read.
                else -> ImageInfo.UNKNOWN
            }
        }
    }.value
    when {
        info == null -> Text("…", style = Type.Small)
        info === ImageInfo.UNKNOWN -> Unit
        info === ImageInfo.UNREADABLE -> Text(stringResource(R.string.artwork_unreadable), style = Type.Small)
        else -> Text(stringResource(R.string.artwork_details, info.format, info.width, info.height), style = Type.Small)
    }
    val from = when {
        state.source == ArtSource.MANUAL -> stringResource(R.string.artwork_chosen_by_you) + (state.provider?.let { " · " + providerName(it) } ?: "")
        state.source == ArtSource.SYNCED && state.provider == ArtProvider.MIRROR.id -> stringResource(R.string.artwork_from_mirror)
        state.source == ArtSource.SYNCED && state.provider == ArtProvider.LIBRETRO.id -> stringResource(R.string.artwork_from_libretro)
        state.source == ArtSource.SYNCED -> stringResource(R.string.artwork_from_screenscraper)
        else -> null
    }
    from?.let { Text(it, style = Type.Small) }
}

/** A provider's name as the screens show it. */
@Composable
internal fun providerName(id: String) = when (ArtProvider.of(id)) {
    ArtProvider.MIRROR -> stringResource(R.string.artwork_provider_mirror)
    ArtProvider.LIBRETRO -> stringResource(R.string.artwork_provider_libretro)
    ArtProvider.SCREENSCRAPER -> "ScreenScraper"
    null -> id
}

private class ImageInfo(val format: String, val width: Int, val height: Int) {
    companion object {
        val UNREADABLE = ImageInfo("", 0, 0)
        val UNKNOWN = ImageInfo("", 0, 0)
    }
}

/** The format and size from the file's header, without decoding the pixels. */
private fun imageInfo(file: File): ImageInfo? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val format = options.outMimeType?.substringAfter('/')?.uppercase()?.replace("JPG", "JPEG") ?: file.extension.uppercase()
    return ImageInfo(format, options.outWidth, options.outHeight)
}

@Composable
private fun Preview(rom: Rom, state: ArtState, width: Dp, height: Dp) {
    val cover = state.kind == ArtKind.COVER
    val description = stringResource(if (cover) R.string.artwork_preview_cover else R.string.artwork_preview_label, rom.title)
    val model: Any? = state.file?.let { "${it.toURI().toASCIIString()}?v=${it.lastModified()}" } ?: state.url
    Box(
        Modifier.size(width, height).clip(RoundedCornerShape(if (cover) 8.dp else 6.dp))
            .then(
                if (model == null) Modifier.border(2.dp, Oe.Border, RoundedCornerShape(if (cover) 8.dp else 6.dp))
                else Modifier.background(Oe.Surface2),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Icon(if (cover) Icons.Grid else Icons.Cart, Oe.Faint, 28.dp)
        }
    }
}

/**
 * "Search artwork…" under a game that shows no art (desktop issue 127): straight to the search of
 * what is missing, the label on a cartridge console, the cover elsewhere.
 */
@Composable
fun SearchArtworkButton(rom: Rom, modifier: Modifier = Modifier) {
    val a = LocalActions.current
    val tab = if (rom.console.frame != null) ArtTab.LABEL else ArtTab.COVER
    Row(
        modifier.height(40.dp).widthIn(min = 44.dp).gamepadFocus(corner = 20.dp).clip(RoundedCornerShape(20.dp))
            .background(Oe.AccentTint).border(1.dp, Oe.AccentSoft, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button) { a.nav.navigate(Routes.artwork(rom.key, tab.id)) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Search, Oe.AccentOnSoft, 16.dp)
        Text(stringResource(R.string.detail_search_artwork), style = Type.BodyMedium.copy(fontSize = 14.sp, color = Oe.AccentOnSoft))
    }
}
