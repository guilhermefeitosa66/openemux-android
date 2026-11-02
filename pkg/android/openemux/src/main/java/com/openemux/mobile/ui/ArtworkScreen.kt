package com.openemux.mobile.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import com.openemux.mobile.data.ArtKind
import com.openemux.mobile.data.ArtOutcome
import com.openemux.mobile.data.ArtSource
import com.openemux.mobile.data.ArtSources
import com.openemux.mobile.data.ArtState
import com.openemux.mobile.data.Rom
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** What the manager says after an action: the message's text and whether it is a warning. */
private data class ArtMessage(val text: String, val warn: Boolean = true)

/**
 * The per-game artwork manager (issue 746): one card per art kind showing where the art came
 * from, with Sync now, Remove and Reset. Search, import and the editor (issues 747 to 749) add
 * their own tabs and buttons here; until then the screen is "Current" alone.
 */
@Composable
fun ArtworkScreen(key: String) {
    val a = LocalActions.current
    val roms by a.app.library.roms.collectAsState()
    val rom = roms.firstOrNull { it.key == key }
    GamepadButtons(hints = listOf("A" to stringResource(R.string.settings_hint_choose), "B" to stringResource(R.string.settings_hint_back)))
    if (rom == null) {
        // Removed from the library while the manager was open (or a stale route).
        Box(Modifier.fillMaxSize().background(Oe.Bg))
        return
    }
    val artwork = a.app.artwork
    val changes by artwork.changes.collectAsState()
    // The sync can bring art in while the manager is open: its progress is part of what the cards show.
    val labelsFound by a.app.labels.found.collectAsState()
    val coverProgress by a.app.covers.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<Pair<ArtKind, String>?>(null) }
    var message by remember { mutableStateOf<ArtMessage?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf<ArtKind?>(null) }
    val kinds = remember(rom.console) { artwork.kinds(rom.console) }
    val states = produceState(initialValue = emptyMap<ArtKind, ArtState>(), rom, changes, labelsFound, coverProgress.running) {
        value = withContext(Dispatchers.IO) { kinds.associateWith { artwork.describe(rom, it) } }
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Oe.Bg)) {
        val wide = maxWidth >= 840.dp
        MarginFocusScroll(trailing = FocusMargin + hintBarInset) {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Column(
                    Modifier.fillMaxWidth().then(if (wide) Modifier.padding(horizontal = 32.dp) else Modifier.readingWidth().padding(horizontal = 16.dp))
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Header(rom)
                    message?.let { Banner(it) { message = null } }
                    val cards: @Composable (ArtKind, Modifier) -> Unit = { kind, modifier ->
                        val state = states[kind]
                        if (state != null) {
                            ArtCard(
                                rom, state, wide, modifier,
                                running = running?.takeIf { it.first == kind }?.second,
                                onSync = { run(kind, ACTION_SYNC, if (kind == ArtKind.COVER) notFoundCover else notFoundLabel) { artwork.syncNow(rom, kind) } },
                                onRemove = { run(kind, ACTION_REMOVE, "") { artwork.remove(rom, kind) } },
                                onReset = { confirmReset = kind },
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

private const val ACTION_SYNC = "sync"
private const val ACTION_RESET = "reset"
private const val ACTION_REMOVE = "remove"

@Composable
private fun Header(rom: Rom) {
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
    }
}

/** A message inside the manager, never a toast over something else (issue 746). */
@Composable
private fun Banner(message: ArtMessage, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Oe.WarnBg)
            .border(1.dp, if (Oe.dark) Color(0xFF3D3424) else Color(0xFFF0DDB8), RoundedCornerShape(14.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Info, Oe.Warn, 18.dp)
        Text(message.text, style = Type.Body.copy(fontSize = 14.sp, lineHeight = 19.sp, color = Oe.WarnText), modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        RoundIconButton(Icons.Close, stringResource(R.string.artwork_dismiss), onDismiss, tint = Oe.WarnText, background = Color.Transparent)
    }
}

@Composable
private fun ArtCard(
    rom: Rom,
    state: ArtState,
    wide: Boolean,
    modifier: Modifier,
    running: String?,
    onSync: () -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
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
                    onReset, Modifier.fillMaxWidth(), height = 48.dp, icon = Icons.Load, busy = running != null,
                )
            }
            if (state.removable) {
                DangerButton(stringResource(if (cover) R.string.artwork_remove_cover else R.string.artwork_remove_label), onRemove, running != null)
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

@Composable
private fun SourcePill(source: ArtSource?) {
    val (text, bg, fg) = when (source) {
        ArtSource.SYNCED -> Triple(R.string.artwork_pill_automatic, Oe.OkBg, Oe.OkText)
        ArtSource.MANUAL -> Triple(R.string.artwork_pill_manual, Oe.AccentSoft, Oe.AccentOnSoft)
        ArtSource.LOCAL -> Triple(R.string.artwork_pill_local, Oe.WarnBg, Oe.WarnText)
        null -> Triple(R.string.artwork_pill_missing, Oe.Surface2, Oe.Muted)
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
            file?.let { imageInfo(it) } ?: ImageInfo.UNREADABLE
        }
    }.value
    when {
        info == null -> Text("…", style = Type.Small)
        info === ImageInfo.UNREADABLE -> Text(stringResource(R.string.artwork_unreadable), style = Type.Small)
        else -> Text(stringResource(R.string.artwork_details, info.format, info.width, info.height), style = Type.Small)
    }
    val from = when {
        state.source == ArtSource.MANUAL -> stringResource(R.string.artwork_chosen_by_you) + (state.provider?.let { " · " + providerName(it) } ?: "")
        state.source == ArtSource.SYNCED && state.provider == ArtSources.PROVIDER_MIRROR -> stringResource(R.string.artwork_from_mirror)
        state.source == ArtSource.SYNCED -> stringResource(R.string.artwork_from_screenscraper)
        else -> null
    }
    from?.let { Text(it, style = Type.Small) }
}

private fun providerName(id: String) = when (id) {
    ArtSources.PROVIDER_MIRROR -> "OpenEmux"
    ArtSources.PROVIDER_SCREENSCRAPER -> "ScreenScraper"
    else -> id
}

private class ImageInfo(val format: String, val width: Int, val height: Int) {
    companion object {
        val UNREADABLE = ImageInfo("", 0, 0)
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

/** "Search artwork…" under a game that shows no art (desktop issue 127). */
@Composable
fun SearchArtworkButton(rom: Rom, modifier: Modifier = Modifier) {
    val a = LocalActions.current
    Row(
        modifier.height(40.dp).widthIn(min = 44.dp).gamepadFocus(corner = 20.dp).clip(RoundedCornerShape(20.dp))
            .background(Oe.AccentTint).border(1.dp, Oe.AccentSoft, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button) { a.nav.navigate(Routes.artwork(rom.key)) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Search, Oe.AccentOnSoft, 16.dp)
        Text(stringResource(R.string.detail_search_artwork), style = Type.BodyMedium.copy(fontSize = 14.sp, color = Oe.AccentOnSoft))
    }
}
