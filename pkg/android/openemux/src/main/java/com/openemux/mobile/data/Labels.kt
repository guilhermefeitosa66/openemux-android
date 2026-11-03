package com.openemux.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class SyncPause { Offline, Quota }

data class SyncProgress(
    val running: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val current: String? = null,
    val paused: SyncPause? = null,
    /** Known to have no label on ScreenScraper. */
    val missing: Int = 0,
    val finishedAt: Long = 0L,
)

/**
 * Cartridge labels ("support-texture") from ScreenScraper, looked up by ROM
 * hash like the desktop app does. Box art is a different asset ([Covers]).
 * Labels are kept on disk; a ".none" file remembers a ROM that has none.
 */
class Labels(
    private val paths: Paths,
    private val ss: ScreenScraper,
    private val settings: () -> ProviderSettings = { ProviderSettings() },
) : LabelOps {
    private val _found = MutableStateFlow<Map<String, File>>(emptyMap())
    val found: StateFlow<Map<String, File>> = _found.asStateFlow()

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    /** ROMs whose label is not known yet (neither found nor known to be missing). */
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val mutex = Mutex()

    override val syncing: Boolean get() = _progress.value.running

    /** ScreenScraper has credentials, and the provider settings let it serve labels (issue 753). */
    private val usable get() = ss.usable && settings().serves(ArtProvider.SCREENSCRAPER, ArtKind.LABEL)

    private fun dir(console: Console) = paths.labels(console)
    private fun file(rom: Rom) = File(dir(rom.console), "${rom.baseName}.png")
    private fun none(rom: Rom) = File(dir(rom.console), "${rom.baseName}.none")

    override fun labelFile(rom: Rom): File = file(rom)
    override fun noneMarker(rom: Rom): File = none(rom)

    override fun changed(rom: Rom, file: File?) {
        _found.value = if (file != null) _found.value + (rom.key to file) else _found.value - rom.key
        _pending.value = _pending.value - rom.key
    }

    /** One ROM's label, for the artwork manager: written to [target] only when one is found. */
    override suspend fun fetch(rom: Rom, target: File): Fetch = withContext(Dispatchers.IO) {
        if (!usable || !rom.file.exists()) return@withContext Fetch.UNAVAILABLE
        when (fetch(rom, target, replace = true)) {
            is Result.Found -> Fetch.FOUND
            Result.Missing -> Fetch.MISSING
            else -> Fetch.UNAVAILABLE
        }
    }

    /**
     * Labels synced before records existed (issue 746) carry none, and would all read as local
     * files: the first sync after the update vouches for them, undated, once per console.
     */
    private fun migrate(roms: List<Rom>) {
        roms.groupBy { it.console }.forEach { (console, list) ->
            val sources = ArtSources(dir(console))
            if (sources.exists()) return@forEach
            val present = list.map { file(it) }.filter { it.isFile }
            if (present.isEmpty()) return@forEach
            sources.batch { b -> present.forEach { b.record(it.nameWithoutExtension, it, ArtSource.SYNCED, ArtSources.PROVIDER_SCREENSCRAPER, dated = false) } }
        }
    }

    /** Fetches what is missing, one ROM at a time; quota and network errors pause the run. */
    suspend fun sync(all: List<Rom>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Only cartridges have a label window; the rest show box art.
            val roms = all.filter { it.console.frame != null }
            migrate(roms)
            _found.value = roms.mapNotNull { rom -> file(rom).takeIf { it.exists() }?.let { rom.key to it } }.toMap()
            val todo = roms.filter { !file(it).exists() && !none(it).exists() && it.file.exists() }
            _pending.value = todo.map { it.key }.toSet()
            if (!usable || todo.isEmpty()) {
                _progress.value = SyncProgress(total = roms.size, done = roms.size - todo.size)
                return@withContext
            }
            var done = roms.size - todo.size
            var failures = 0
            try {
                for (rom in todo) {
                    _progress.value = SyncProgress(running = true, total = roms.size, done = done, current = rom.title)
                    val result = fetch(rom, file(rom), replace = false)
                    when (result) {
                        is Result.Found -> {
                            if (result.wrote) {
                                ArtSources(dir(rom.console)).record(rom.baseName, result.file, ArtSource.SYNCED, ArtSources.PROVIDER_SCREENSCRAPER)
                            }
                            _found.value = _found.value + (rom.key to result.file)
                        }
                        Result.Missing -> { dir(rom.console).mkdirs(); none(rom).createNewFile() }
                        Result.Quota -> { _progress.value = _progress.value.copy(running = false, current = null, paused = SyncPause.Quota); return@withContext }
                        Result.Offline -> if (++failures >= 3) {
                            _progress.value = _progress.value.copy(running = false, current = null, paused = SyncPause.Offline)
                            return@withContext
                        }
                        Result.Skip -> Unit
                    }
                    if (result !is Result.Offline) failures = 0
                    if (result is Result.Found || result == Result.Missing) {
                        done++
                        _pending.value = _pending.value - rom.key
                    }
                }
                _progress.value = SyncProgress(total = roms.size, done = done, missing = roms.count { none(it).exists() }, finishedAt = System.currentTimeMillis())
            } finally {
                if (_progress.value.running) _progress.value = _progress.value.copy(running = false, current = null)
            }
        }
    }

    private sealed interface Result {
        class Found(val file: File, val wrote: Boolean = true) : Result
        data object Missing : Result
        data object Skip : Result
        data object Offline : Result
        data object Quota : Result
    }

    /**
     * Looks [rom] up and writes its label to [target]. Without [replace] a file that appeared at
     * [target] meanwhile (art the user chose while the sync ran) is kept, never written over.
     */
    private suspend fun fetch(rom: Rom, target: File, replace: Boolean): Result {
        val identity = runCatching { RomIdentity.of(rom.file, rom.console.extensions) }.getOrElse { return Result.Skip }
        val game = when (val reply = ss.game(rom.console.screenScraperId, rom.file.name, identity)) {
            is SsReply.Ok -> reply.game
            SsReply.NotFound -> return Result.Missing
            SsReply.Quota -> return Result.Quota
            is SsReply.Unreachable -> return if (reply.network) Result.Offline else Result.Skip
            SsReply.Refused, SsReply.NoCredentials -> return Result.Skip
        }
        val url = ScreenScraper.mediaUrls(game, ArtKind.LABEL, ScreenScraper.regionPriority(rom.file.name), 1).firstOrNull()
            ?: return Result.Missing
        val image = ss.media(url)
        if (image.code == -1) return Result.Offline
        if (image.code != 200 || image.body == null || ArtImageCheck.format(image.body) == null) return Result.Skip
        if (!replace && target.exists()) return Result.Found(target, wrote = false)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(image.body)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return Result.Skip
        }
        return Result.Found(target)
    }
}
