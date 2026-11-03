package com.openemux.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** One image a search or a suggestion found. */
class ArtCandidate(
    val provider: ArtProvider,
    val bytes: ByteArray,
    /** What the bytes are: "png", "jpg", "gif" or "webp". */
    val format: String,
    val sha1: String,
    /** The suggested title, for a suggestion. */
    val title: String? = null,
    val approximate: Boolean = false,
) {
    /** How it is recorded once saved. */
    val origin: String get() = if (title != null) ORIGIN_SUGGESTION else ORIGIN_SEARCH

    companion object {
        const val ORIGIN_SEARCH = "search"
        const val ORIGIN_SUGGESTION = "suggestion"
        const val ORIGIN_FILE = "file"
        const val ORIGIN_ADJUSTED = "adjusted"
    }
}

/** Why a search came back short, worst first: the manager shows the first one. */
enum class SearchIssue { OFFLINE, REFUSED, NO_CREDENTIALS, QUOTA, UNREACHABLE }

sealed interface SearchEvent {
    class Found(val candidate: ArtCandidate) : SearchEvent

    /** The suggestions' titles, before their previews arrive. */
    class Suggested(val suggestions: List<Suggestion>) : SearchEvent

    /** A suggestion's preview: null when it could not be fetched (offline, or none on the mirror). */
    class Preview(val title: String, val candidate: ArtCandidate?) : SearchEvent

    /** What went wrong, once at the end; nothing when nothing did. */
    class Issue(val issue: SearchIssue) : SearchEvent
}

/** The bundled title lists, as the search needs them ([Covers] in the app). */
interface TitleSource {
    fun titles(console: Console): List<String>
    fun titlesFor(console: Console, typed: String): List<String>
}

/**
 * The artwork manager's search (issue 753), the Android side of the desktop's `artwork_search.py`:
 * each provider the settings let serve [ArtKind], in their order, up to [PER_PROVIDER] distinct
 * images each, the same bytes never twice. Results go out as they arrive; cancelling the
 * collector stops the search between two requests.
 */
class ArtSearch(
    private val http: HttpGet,
    private val ss: ScreenScraper,
    private val titles: TitleSource,
    private val settings: () -> ProviderSettings,
    /** Whether the platform can really draw the bytes: a header can lie. */
    private val decodable: (ByteArray) -> Boolean = { true },
) {
    /**
     * Searches [kind] art for [rom] by [name]. With [identity] ScreenScraper looks it up by hash; the
     * providers that go by name (the mirror, libretro) use [name] either way, as on the desktop.
     */
    fun search(rom: Rom, kind: ArtKind, name: String, identity: RomIdentity?): Flow<SearchEvent> = flow {
        val run = Run()
        val send: suspend (ArtCandidate) -> Unit = { emit(SearchEvent.Found(it)) }
        for (provider in settings().serving(kind)) {
            when (provider) {
                ArtProvider.SCREENSCRAPER -> screenScraper(rom, kind, name, identity, run, send)
                else -> byName(provider, rom.console, name, run, send)
            }
        }
        run.issue()?.let { emit(SearchEvent.Issue(it)) }
    }.flowOn(Dispatchers.IO)

    /** Suggestions for box art, then each one's preview from the mirror. */
    fun suggest(rom: Rom, name: String): Flow<SearchEvent> = flow {
        val list = ArtSuggestions.suggest(name, titles.titles(rom.console))
        emit(SearchEvent.Suggested(list))
        val run = Run()
        for (s in list) {
            val url = Covers.urlOf(ArtProvider.MIRROR, rom.console, s.title)!!
            val image = fetch(ArtProvider.MIRROR, url, run)?.let {
                ArtCandidate(it.provider, it.bytes, it.format, it.sha1, s.title, s.approximate)
            }
            emit(SearchEvent.Preview(s.title, image))
        }
        if (run.offline) emit(SearchEvent.Issue(SearchIssue.OFFLINE))
    }.flowOn(Dispatchers.IO)

    /** What one search has seen so far: the bytes already shown, and what went wrong. */
    private class Run {
        val seen = HashSet<String>()
        var requests = 0
        var networkFailures = 0
        val issues = HashSet<SearchIssue>()
        val offline get() = requests > 0 && networkFailures == requests

        fun issue(): SearchIssue? = if (offline) SearchIssue.OFFLINE else SearchIssue.entries.firstOrNull { it in issues }
    }

    private suspend fun byName(provider: ArtProvider, console: Console, name: String, run: Run, send: suspend (ArtCandidate) -> Unit) {
        val typed = name.trim()
        if (typed.isEmpty()) return
        // The lists' names are the providers' file names: the matching ones first, then the name as typed.
        val names = (titles.titlesFor(console, typed) + typed).distinct().take(MAX_NAMES)
        var found = 0
        for (title in names) {
            if (found >= PER_PROVIDER) break
            val url = Covers.urlOf(provider, console, title) ?: break
            fetch(provider, url, run)?.let { found++; send(it) }
        }
    }

    private suspend fun screenScraper(rom: Rom, kind: ArtKind, name: String, identity: RomIdentity?, run: Run, send: suspend (ArtCandidate) -> Unit) {
        if (!ss.usable) {
            // Box art has other providers; a label has no other place to come from, so say why there is none.
            if (kind == ArtKind.LABEL) run.issues += SearchIssue.NO_CREDENTIALS
            return
        }
        val typed = name.trim()
        if (identity == null && typed.isEmpty()) return
        run.requests++
        val game = when (val reply = ss.game(rom.console.screenScraperId, typed, identity)) {
            is SsReply.Ok -> reply.game
            SsReply.NotFound -> return
            SsReply.Quota -> { run.issues += SearchIssue.QUOTA; return }
            SsReply.Refused -> { run.issues += SearchIssue.REFUSED; return }
            SsReply.NoCredentials -> { run.issues += SearchIssue.NO_CREDENTIALS; return }
            is SsReply.Unreachable -> {
                if (reply.network) run.networkFailures++ else run.issues += SearchIssue.UNREACHABLE
                return
            }
        }
        val regions = ScreenScraper.regionPriority(identity?.name ?: rom.file.name)
        var found = 0
        for (url in ScreenScraper.mediaUrls(game, kind, regions, PER_PROVIDER * 2)) {
            if (found >= PER_PROVIDER) break
            currentCoroutineContext().ensureActive()
            run.requests++
            val reply = ss.media(url)
            if (reply.code == -1) run.networkFailures++
            accept(ArtProvider.SCREENSCRAPER, reply, run)?.let { found++; send(it) }
        }
    }

    private suspend fun fetch(provider: ArtProvider, url: String, run: Run): ArtCandidate? {
        // Cancel search stops here, between two requests.
        currentCoroutineContext().ensureActive()
        run.requests++
        val reply = http.get(url)
        if (reply.code == -1) run.networkFailures++
        return accept(provider, reply, run)
    }

    /** The reply as a result, unless it is no image or one already shown. */
    private fun accept(provider: ArtProvider, reply: HttpReply, run: Run): ArtCandidate? {
        val bytes = reply.body?.takeIf { reply.code == 200 } ?: return null
        val format = ArtImageCheck.format(bytes) ?: return null
        val sha1 = ArtImageCheck.sha1(bytes)
        if (!run.seen.add(sha1) || !decodable(bytes)) return null
        return ArtCandidate(provider, bytes, format, sha1)
    }

    companion object {
        /** The desktop's MAX_RESULTS_PER_PROVIDER. */
        const val PER_PROVIDER = 4

        /** Names tried per name provider: each one is a request, most of them a miss. */
        const val MAX_NAMES = 8
    }
}
