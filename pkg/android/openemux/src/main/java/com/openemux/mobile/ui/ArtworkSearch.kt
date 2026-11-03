package com.openemux.mobile.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openemux.mobile.data.ArtCandidate
import com.openemux.mobile.data.ArtImages
import com.openemux.mobile.data.ArtKind
import com.openemux.mobile.data.ArtOutcome
import com.openemux.mobile.data.ArtSearch
import com.openemux.mobile.data.Artwork
import com.openemux.mobile.data.Covers
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.RomIdentity
import com.openemux.mobile.data.SearchEvent
import com.openemux.mobile.data.SearchIssue
import com.retroarch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A suggestion as the screen shows it: its title, and its preview once fetched. */
class SuggestionItem(val title: String, val approximate: Boolean, val candidate: ArtCandidate? = null, val loading: Boolean = true)

/**
 * One search tab's state (issue 753), kept by the manager while it is open, so going to another
 * tab and back keeps the results.
 */
class SearchState(val kind: ArtKind, title: String) {
    var name by mutableStateOf(title)
    val results = mutableStateListOf<ArtCandidate>()
    /** The chosen result's SHA-1, or "s:<title>" for a suggestion. */
    var selected by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var issue by mutableStateOf<SearchIssue?>(null)
    var suggestions by mutableStateOf<List<SuggestionItem>?>(null)
    var suggesting by mutableStateOf(false)
    var saving by mutableStateOf(false)
    private var job: Job? = null
    private var suggestJob: Job? = null

    val started: Boolean get() = running || finished || cancelled

    fun start(scope: CoroutineScope, search: ArtSearch, rom: Rom, identity: RomIdentity?) {
        stop()
        results.clear()
        selected = null
        issue = null
        cancelled = false
        finished = false
        suggestions = null
        running = true
        val typed = name
        job = scope.launch {
            try {
                search.search(rom, kind, typed, identity).collect { e ->
                    when (e) {
                        is SearchEvent.Found -> {
                            results += e.candidate
                            if (selected == null) selected = e.candidate.sha1
                        }
                        is SearchEvent.Issue -> issue = e.issue
                        else -> Unit
                    }
                }
                finished = true
                running = false
                // A box-art search that finds nothing offers similar names at once (desktop issue 175).
                if (kind == ArtKind.COVER && results.isEmpty()) suggest(scope, search, rom)
            } finally {
                // A search cancelled by a newer one finishes after it started: the flag is the newer one's.
                if (job === coroutineContext[Job]) running = false
            }
        }
    }

    fun suggest(scope: CoroutineScope, search: ArtSearch, rom: Rom) {
        suggestJob?.cancel()
        suggesting = true
        val typed = name
        suggestJob = scope.launch {
            try {
                search.suggest(rom, typed).collect { e ->
                    when (e) {
                        is SearchEvent.Suggested -> suggestions = e.suggestions.map { SuggestionItem(it.title, it.approximate) }
                        is SearchEvent.Preview -> suggestions = suggestions?.map {
                            if (it.title == e.title) SuggestionItem(it.title, it.approximate, e.candidate, loading = false) else it
                        }
                        is SearchEvent.Issue -> if (issue == null) issue = e.issue
                        else -> Unit
                    }
                }
            } finally {
                if (suggestJob === coroutineContext[Job]) suggesting = false
            }
        }
    }

    /** Cancel search: what arrived stays, nothing more comes. */
    fun cancel() {
        if (!running && !suggesting) return
        stop()
        cancelled = true
    }

    private fun stop() {
        job?.cancel()
        suggestJob?.cancel()
        running = false
        suggesting = false
    }

    fun chosen(): ArtCandidate? {
        val key = selected ?: return null
        if (key.startsWith(SUGGESTION)) return suggestions?.firstOrNull { SUGGESTION + it.title == key }?.candidate
        return results.firstOrNull { it.sha1 == key }
    }

    /** Saves the chosen image as this tab's art kind, recorded as Manual with its provider. */
    suspend fun save(context: Context, artwork: Artwork, rom: Rom, candidate: ArtCandidate): ArtOutcome {
        if (saving) return ArtOutcome.BUSY
        saving = true
        try {
            stop()
            val (bytes, ext) = withContext(Dispatchers.Default) {
                // A label is always "<name>.png", and a cover only keeps the formats the library reads.
                if (kind == ArtKind.LABEL || candidate.format !in Covers.COVER_SUFFIXES) ArtImages.ensurePng(candidate.bytes) to "png"
                else candidate.bytes to candidate.format
            }
            bytes ?: return ArtOutcome.UNAVAILABLE
            return artwork.saveManual(rom, kind, bytes, ext, candidate.provider.id, candidate.origin)
        } finally {
            saving = false
        }
    }

    companion object {
        const val SUGGESTION = "s:"
    }
}

/** The search fields' share of their row, next to their button. */
private const val FIELD_WEIGHT = 1.25f

@Composable
fun SearchTab(
    rom: Rom,
    state: SearchState,
    hash: HashState,
    wide: Boolean,
    side: Modifier,
    onIssue: (SearchIssue?) -> Unit,
    onCancel: () -> Unit,
    onSave: (ArtCandidate) -> Unit,
) {
    val a = LocalActions.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cover = state.kind == ArtKind.COVER
    LaunchedEffect(state.issue) { onIssue(state.issue) }
    fun go(byHash: Boolean) {
        val identity = (hash as? HashState.Known)?.identity
        if (byHash && identity == null) return
        state.start(scope, a.app.artSearch, rom, if (byHash) identity else null)
    }
    Column(Modifier.fillMaxSize()) {
        MarginFocusScroll(trailing = FocusMargin) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).then(side).padding(top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Each field with its own search button on the same row (QA of issue 753).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    OeTextField(
                        stringResource(R.string.artwork_name), state.name, { state.name = it }, Modifier.weight(FIELD_WEIGHT),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onSearch = { go(false) }),
                    )
                    PrimaryButton(stringResource(R.string.artwork_search_by_name), { go(false) }, Modifier.weight(1f), icon = Icons.Search, height = 52.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(FIELD_WEIGHT)) {
                        Text(stringResource(R.string.artwork_hash), style = Type.Caption.copy(color = Oe.Muted), modifier = Modifier.padding(bottom = 6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Oe.Surface)
                                .border(1.5.dp, Oe.Border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                when (hash) {
                                    HashState.Computing -> stringResource(R.string.artwork_hash_computing)
                                    HashState.Failed -> stringResource(R.string.artwork_hash_unavailable)
                                    is HashState.Known -> hash.identity.crc
                                },
                                style = Type.Body.copy(color = Oe.Muted, fontFamily = if (hash is HashState.Known) FontFamily.Monospace else Type.Body.fontFamily),
                            )
                        }
                    }
                    SecondaryButton(
                        stringResource(R.string.artwork_search_by_hash), { go(true) }, Modifier.weight(1f), height = 52.dp,
                        icon = Icons.Search, busy = hash == HashState.Computing, enabled = hash is HashState.Known,
                    )
                }
                Status(state)
                ResultGrid(state, wide)
                if (cover) Suggestions(state, wide) { state.suggest(scope, a.app.artSearch, rom) }
            }
        }
        BottomBar(side) {
            SecondaryButton(stringResource(R.string.artwork_cancel), onCancel, Modifier.weight(1f), height = 50.dp)
            val chosen = state.chosen()
            PrimaryButton(
                stringResource(if (state.saving) R.string.artwork_saving else if (cover) R.string.artwork_save_as_cover else R.string.artwork_save_as_label),
                { chosen?.let { if (!state.saving) onSave(it) } }, Modifier.weight(1f), height = 50.dp, enabled = chosen != null,
            )
        }
    }
}

/** The bar of the design under a tab's content: Cancel and the tab's Save. */
@Composable
internal fun BottomBar(side: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Oe.Divider))
        Row(
            Modifier.fillMaxWidth().then(side).padding(top = 12.dp, bottom = 12.dp + hintBarInset),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Status(state: SearchState) {
    val count = state.results.size
    val text = when {
        state.running -> countString(R.plurals.artwork_searching, count, count)
        state.cancelled -> stringResource(R.string.artwork_search_cancelled)
        state.finished && count > 0 -> countString(R.plurals.artwork_found, count, count)
        else -> null
    } ?: return
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.running) CircularProgressIndicator(Modifier.size(14.dp), color = Oe.AccentText, strokeWidth = 2.dp)
        Text(text, style = Type.Small, modifier = Modifier.weight(1f))
        if (state.running) {
            Box(
                Modifier.height(padSize(32.dp, 40.dp)).gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Oe.Border, RoundedCornerShape(16.dp)).clickable(role = Role.Button) { state.cancel() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.artwork_cancel_search), style = Type.Button.copy(color = Oe.Text, fontSize = 13.sp)) }
        }
    }
}

@Composable
private fun ResultGrid(state: SearchState, wide: Boolean) {
    val cover = state.kind == ArtKind.COVER
    if (state.finished && state.results.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Search, Oe.Muted, 36.dp, stroke = 1.6f)
            Text(stringResource(R.string.artwork_no_images), style = Type.BodyMedium.copy(fontSize = 15.sp))
            Text(
                stringResource(if (cover) R.string.artwork_no_images_cover_hint else R.string.artwork_no_images_label_hint),
                style = Type.Small, textAlign = TextAlign.Center,
            )
        }
        return
    }
    val columns = when {
        wide -> if (cover) 5 else 3
        else -> if (cover) 3 else 2
    }
    val tiles = state.results.toList()
    val pending = state.running
    TileGrid(tiles.size + (if (pending) 1 else 0), columns) { i, m ->
        if (i < tiles.size) {
            val c = tiles[i]
            ResultTile(
                c.bytes, providerName(c.provider.id), stringResource(R.string.artwork_result, i + 1, providerName(c.provider.id)),
                selected = state.selected == c.sha1, cover = cover, modifier = m,
            ) { state.selected = c.sha1 }
        } else {
            // Where the next result lands, while the search runs.
            Box(m.aspectRatio(if (cover) 0.62f else 1.25f).clip(RoundedCornerShape(10.dp)).border(2.dp, Oe.Border, RoundedCornerShape(10.dp)))
        }
    }
}

@Composable
private fun TileGrid(count: Int, columns: Int, tile: @Composable (Int, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ResultTile(
    image: ByteArray?,
    caption: String,
    description: String,
    selected: Boolean,
    cover: Boolean,
    modifier: Modifier,
    badge: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.gamepadFocus(corner = 10.dp).clip(RoundedCornerShape(10.dp)).background(Oe.Surface)
            .border(2.dp, if (selected) Oe.AccentBright else Oe.Border, RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .clickable(role = Role.RadioButton) { if (enabled) onClick() },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(if (cover) 0.72f else 1.45f).background(Oe.Surface2), contentAlignment = Alignment.Center) {
                if (image != null) {
                    AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Oe.AccentText, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.artwork_preview_unavailable), style = Type.Caption.copy(color = Oe.Muted), textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
                }
            }
            Text(caption, style = Type.Caption.copy(color = Oe.Muted, fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp))
            if (badge != null) {
                Text(badge, style = Type.Caption.copy(color = Oe.WarnText, fontSize = 11.sp), modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 5.dp))
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Oe.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Check, androidx.compose.ui.graphics.Color.White, 14.dp, stroke = 3f)
            }
        }
    }
}

@Composable
private fun Suggestions(state: SearchState, wide: Boolean, onAsk: () -> Unit) {
    val list = state.suggestions
    Text(
        stringResource(R.string.artwork_suggestions).uppercase(),
        style = Type.SectionCaps, modifier = Modifier.padding(top = 6.dp),
    )
    when {
        list == null && !state.suggesting -> SecondaryButton(stringResource(R.string.artwork_suggestions), onAsk, Modifier.fillMaxWidth(), height = 48.dp)
        list == null -> CircularProgressIndicator(Modifier.size(18.dp), color = Oe.AccentText, strokeWidth = 2.dp)
        list.isEmpty() -> Text(stringResource(R.string.artwork_no_suggestions), style = Type.Small)
        else -> {
            val approximate = list.all { it.approximate }
            Text(
                if (approximate) countString(R.plurals.artwork_suggestions_approximate, list.size, list.size)
                else countString(R.plurals.artwork_suggestions_found, list.size, list.size),
                style = Type.Small,
            )
            TileGrid(list.size, if (wide) 5 else 3) { i, m ->
                val s = list[i]
                val key = SearchState.SUGGESTION + s.title
                ResultTile(
                    s.candidate?.bytes, s.title, stringResource(R.string.artwork_suggestion, s.title),
                    selected = state.selected == key, cover = true, modifier = m,
                    badge = if (s.approximate) stringResource(R.string.artwork_approximate) else null,
                    enabled = s.candidate != null, loading = s.loading,
                ) { state.selected = key }
            }
        }
    }
}
