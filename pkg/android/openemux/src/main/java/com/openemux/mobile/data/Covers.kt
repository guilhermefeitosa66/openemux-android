package com.openemux.mobile.data

import android.content.Context
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Box art from the openemux-artwork mirror, matched by name against the
 * No-Intro titles it holds (shipped as one list per console). A cover kept on
 * the device ([Paths.covers], one received through Share nearby) wins over
 * the mirror; it is looked for on every call, so one that arrives later shows.
 */
class Covers(
    private val context: Context,
    private val paths: Paths,
    private val settings: () -> ProviderSettings = { ProviderSettings() },
) : CoverOps, TitleSource {
    private val indexes = ConcurrentHashMap<Console, Index>()
    /** The matched title per ROM; "" marks a ROM with no match: ConcurrentHashMap takes no nulls. */
    private val cache = ConcurrentHashMap<String, String>()

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    override val syncing: Boolean get() = _progress.value.running

    /** Warms the image cache with every ROM's box art, so the library fills in without waiting. */
    suspend fun prefetch(roms: List<Rom>) = coroutineScope {
        val withArt = roms.mapNotNull { rom -> urlFor(rom)?.let { rom to it } }
        val missing = roms.size - withArt.size
        // A local cover needs no network: it counts as cached.
        val cached = withArt.filter { (rom, url) ->
            localCover(rom) != null || context.imageLoader.diskCache?.openSnapshot(url)?.use { true } == true
        }
        val todo = withArt - cached.toSet()
        val done = AtomicInteger(missing + cached.size)
        val offline = AtomicInteger()
        _progress.value = SyncProgress(running = todo.isNotEmpty(), total = roms.size, done = done.get(), missing = missing)
        if (todo.isEmpty()) {
            record(cached)
            return@coroutineScope
        }
        val gate = Semaphore(4)
        todo.map { (rom, url) ->
            async {
                gate.withPermit {
                    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).build())
                    if ((result as? ErrorResult)?.throwable is IOException) offline.incrementAndGet()
                    _progress.value = _progress.value.copy(done = done.incrementAndGet(), current = rom.title)
                    result is SuccessResult
                }
            }
        }.awaitAll().let { fetched -> record(cached + todo.filterIndexed { i, _ -> fetched[i] }) }
        _progress.value = SyncProgress(
            total = roms.size, done = done.get(), missing = missing,
            paused = if (offline.get() > 0) SyncPause.Offline else null,
            finishedAt = if (offline.get() > 0) 0L else System.currentTimeMillis(),
        )
    }

    /** [tokens] serve the fuzzy match only, so they are built when a name misses the exact map, not for every console shown (issue 919). */
    private class Index(val names: List<String>, val exact: Map<String, List<String>>) {
        val tokens: List<Pair<Set<String>, String>> by lazy { names.map { tokens(it) to it } }
    }

    /**
     * What to draw for [rom]. A cover kept on the device carries its mtime, so one replaced in place
     * is a new model to the image views and they load it again.
     */
    fun urlFor(rom: Rom): String? =
        localCover(rom)?.let { "${it.toURI().toASCIIString()}?v=${it.lastModified()}" } ?: mirrorUrl(rom)

    /** The cover kept on the device for [rom], if any. */
    override fun localCover(rom: Rom): File? = localCover(paths, rom.console, rom.baseName)

    /** Drops the mirror matches of [keys], so they are looked up again. */
    fun forget(keys: Collection<String>) {
        keys.forEach(cache::remove)
    }

    override fun forgetMatch(rom: Rom) {
        cache.remove(rom.key)
    }

    override fun keepCachedCover(url: String, target: File): Boolean {
        if (target.exists()) return false
        return runCatching {
            context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot ->
                snapshot.data.toFile().copyTo(target, overwrite = false)
                true
            } ?: false
        }.getOrElse {
            // A copy cut short (storage full) must not stay behind as a local cover with no record.
            target.delete()
            false
        }
    }

    override suspend fun fetch(url: String): Fetch {
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        return when {
            result is SuccessResult -> Fetch.FOUND
            (result as? ErrorResult)?.throwable is IOException -> Fetch.UNAVAILABLE
            else -> Fetch.MISSING
        }
    }

    /**
     * Notes where the covers now in the image cache came from, one write per console (issue 746).
     * Only served covers: a cover kept on the device has a record of its own, which this never touches.
     */
    private suspend fun record(served: List<Pair<Rom, String>>) = withContext(Dispatchers.IO) {
        served.filter { (rom, _) -> localCover(rom) == null }.groupBy { it.first.console }.forEach { (console, list) ->
            val sources = ArtSources(paths.covers(console))
            val known = sources.entries()
            val fresh = list.filter { (rom, url) -> known[rom.baseName]?.url != url }
            if (fresh.isNotEmpty()) sources.batch { b -> fresh.forEach { (rom, url) -> b.recordUrl(rom.baseName, url, providerOf(url)) } }
        }
    }

    /** The title the bundled list matched [rom] to, if any. */
    fun matchedTitle(rom: Rom): String? = cache.getOrPut(rom.key) {
        match(rom.console, rom.file.nameWithoutExtension).orEmpty()
    }.ifEmpty { null }

    /**
     * The synced cover of [rom]: the matched title from the first provider the settings let serve
     * box art by name (the mirror or libretro, issue 753). ScreenScraper box art is search-only:
     * the sync never spends the shared quota on covers.
     */
    override fun mirrorUrl(rom: Rom): String? {
        val title = matchedTitle(rom) ?: return null
        return settings().serving(ArtKind.COVER).firstNotNullOfOrNull { urlOf(it, rom.console, title) }
    }

    /** Every title in the bundled list of [console] (the suggestions' game-name database). */
    override fun titles(console: Console): List<String> = index(console).names

    /** The titles whose normalised form is [typed]'s, best region first, else the best close match. */
    override fun titlesFor(console: Console, typed: String): List<String> {
        val idx = index(console)
        idx.exact[normalize(typed)]?.let { return it.sortedBy(::regionRank) }
        return listOfNotNull(match(console, typed))
    }

    private fun index(console: Console): Index = indexes.getOrPut(console) {
        val names = context.assets.open("covers/covers_${console.id.lowercase()}.txt")
            .bufferedReader().readLines().filter { it.isNotBlank() }
        Index(names = names, exact = names.groupBy { normalize(it) })
    }

    private fun match(console: Console, fileName: String): String? {
        val idx = index(console)
        idx.exact[normalize(fileName)]?.let { return preferRegion(it) }
        val wanted = tokens(fileName)
        if (wanted.isEmpty()) return null
        var best: String? = null
        var bestScore = 0.0
        for ((candidate, name) in idx.tokens) {
            if (candidate.isEmpty()) continue
            val common = wanted.count { it in candidate }
            val score = common.toDouble() / (wanted.size + candidate.size - common)
            if (score > bestScore || (score == bestScore && best != null && regionRank(name) < regionRank(best))) {
                bestScore = score
                best = name
            }
        }
        return if (bestScore >= 0.5) best else null
    }

    private fun preferRegion(names: List<String>) = names.minBy { regionRank(it) }

    private fun regionRank(name: String) = when {
        "(USA" in name -> 0
        "(World" in name -> 1
        "(Europe" in name || "(USA, Europe" in name -> 2
        "(Brazil" in name -> 3
        "(Japan" in name -> 5
        else -> 4
    } + (if ("Beta" in name || "Proto" in name || "Demo" in name) 10 else 0)

    companion object {
        private const val MIRROR = "https://raw.githubusercontent.com/guilhermefeitosa66/openemux-artwork/main/"
        private const val LIBRETRO = "https://thumbnails.libretro.com/"

        /**
         * Where [provider] serves the box art of [title] (a name from the bundled lists); null for
         * ScreenScraper, which is looked up by its API, not by name.
         */
        fun urlOf(provider: ArtProvider, console: Console, title: String): String? = when (provider) {
            // The mirror's file names are the lists' names as they are: no sanitising.
            ArtProvider.MIRROR -> MIRROR + "${console.artworkDir}/${encode(title)}.webp"
            ArtProvider.LIBRETRO -> LIBRETRO + "${encode(console.artworkDir.replace('_', ' '))}/Named_Boxarts/${encode(libretroName(title))}.png"
            ArtProvider.SCREENSCRAPER -> null
        }

        /** Which provider a synced cover URL is from. */
        fun providerOf(url: String): String = if (url.startsWith(LIBRETRO)) ArtProvider.LIBRETRO.id else ArtProvider.MIRROR.id

        /** libretro-thumbnails replaces the characters a file name cannot hold (desktop `cover_sync.py`). */
        fun libretroName(title: String): String = title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

        /**
         * Percent-encodes like android.net.Uri.encode (letters, digits and `_-!.~'()*` kept), so the
         * mirror URLs stay the ones already in the image cache and the source records.
         */
        fun encode(text: String): String = buildString {
            for (b in text.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "_-!.~'()*")) append(c.toChar())
                else append('%').append("%02X".format(c))
            }
        }

        /** The suffixes a local cover may have, in the order they are looked for. */
        val COVER_SUFFIXES = listOf("png", "jpg", "jpeg", "webp")

        fun localCover(paths: Paths, console: Console, stem: String): File? =
            COVER_SUFFIXES.map { File(paths.covers(console), "$stem.$it") }.firstOrNull { it.isFile }

        private val tags = Regex("[(\\[][^)\\]]*[)\\]]")
        private val stop = setOf("the", "a", "of", "and", "version", "u", "e", "j")

        fun normalize(name: String): String {
            val noTags = name.replace(tags, " ")
            val ascii = Normalizer.normalize(noTags, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            return ascii.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "")
        }

        fun tokens(name: String): Set<String> {
            val noTags = name.replace(tags, " ")
            val ascii = Normalizer.normalize(noTags, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            return ascii.lowercase().replace("&", " and ")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() && it !in stop }
                .toSet()
        }
    }
}
