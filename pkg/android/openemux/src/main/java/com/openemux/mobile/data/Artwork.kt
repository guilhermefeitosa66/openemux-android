package com.openemux.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What one lookup of a single ROM's art came to. */
enum class Fetch { FOUND, MISSING, UNAVAILABLE }

/** What a manager action came to; the screen turns each one into a message. */
enum class ArtOutcome { DONE, NOT_FOUND, UNAVAILABLE, BUSY }

/** What the manager shows about one ROM's art of one kind. */
data class ArtState(
    val kind: ArtKind,
    /** Null when the ROM has none: Missing. */
    val source: ArtSource?,
    /** The file kept on the device, if any. */
    val file: File? = null,
    /** The mirror URL a synced cover is served from. */
    val url: String? = null,
    val updated: Long? = null,
    val provider: String? = null,
) {
    val missing: Boolean get() = source == null

    /** Remove deletes a file: a cover served from the mirror has none. */
    val removable: Boolean get() = file != null

    /** Reset brings back what the sync finds: pointless when that is what is shown. */
    val resettable: Boolean get() = source != null && source != ArtSource.SYNCED
}

/** The mirror's box art, as the manager needs it ([Covers] in the app). */
interface CoverOps {
    val syncing: Boolean
    fun localCover(rom: Rom): File?
    /** Drops the remembered mirror match, so the next lookup matches again. */
    fun forgetMatch(rom: Rom)
    fun mirrorUrl(rom: Rom): String?
    /** Fetches [url] into the image cache. */
    suspend fun fetch(url: String): Fetch
}

/** ScreenScraper's labels, as the manager needs them ([Labels] in the app). */
interface LabelOps {
    val syncing: Boolean
    fun labelFile(rom: Rom): File
    fun noneMarker(rom: Rom): File
    /** Looks the label up and writes it to [target]. */
    suspend fun fetch(rom: Rom, target: File): Fetch
    /** Tells the library the label of [rom] is now [file] (null: none). */
    fun changed(rom: Rom, file: File?)
}

/**
 * The per-game artwork manager's model (issue 746): where each art came from, and Sync now,
 * Remove and Reset on one ROM. Every change bumps [changes], so the library and the detail
 * screen show it at once.
 */
class Artwork(
    private val paths: Paths,
    private val covers: CoverOps,
    private val labels: LabelOps,
    private val scratch: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** One action at a time: two of them on the same file would race. */
    private val mutex = Mutex()

    fun sources(console: Console, kind: ArtKind) = ArtSources(dir(console, kind), now)

    private fun dir(console: Console, kind: ArtKind) = if (kind == ArtKind.COVER) paths.covers(console) else paths.labels(console)

    /** The art kinds [console] shows: the label only where a cartridge frame has a window for it. */
    fun kinds(console: Console): List<ArtKind> = if (console.frame != null) listOf(ArtKind.COVER, ArtKind.LABEL) else listOf(ArtKind.COVER)

    /** The art the game shows in the library: the label on a cartridge console, the cover on the others. */
    fun primaryKind(console: Console): ArtKind = if (console.frame != null) ArtKind.LABEL else ArtKind.COVER

    fun describe(rom: Rom, kind: ArtKind): ArtState {
        val sources = sources(rom.console, kind)
        val file = if (kind == ArtKind.COVER) covers.localCover(rom) else labels.labelFile(rom).takeIf { it.isFile }
        if (file != null) {
            val entry = sources.matching(rom.baseName, file) ?: return ArtState(kind, ArtSource.LOCAL, file)
            return ArtState(kind, entry.source, file, updated = entry.updated, provider = entry.provider)
        }
        if (kind == ArtKind.LABEL) return ArtState(kind, null)
        val url = covers.mirrorUrl(rom) ?: return ArtState(kind, null)
        val entry = sources.entry(rom.baseName)?.takeIf { it.url == url }
        return ArtState(kind, ArtSource.SYNCED, url = url, updated = entry?.updated, provider = Covers.providerOf(url))
    }

    private val busy get() = covers.syncing || labels.syncing

    /** Looks for this game's art now, the way the library sync does. */
    suspend fun syncNow(rom: Rom, kind: ArtKind): ArtOutcome = act {
        if (busy) return@act ArtOutcome.BUSY
        when (kind) {
            ArtKind.COVER -> {
                covers.forgetMatch(rom)
                val url = covers.mirrorUrl(rom) ?: return@act ArtOutcome.NOT_FOUND
                when (covers.fetch(url)) {
                    Fetch.FOUND -> {
                        sources(rom.console, kind).recordUrl(rom.baseName, url, Covers.providerOf(url))
                        ArtOutcome.DONE
                    }
                    Fetch.MISSING -> ArtOutcome.NOT_FOUND
                    Fetch.UNAVAILABLE -> ArtOutcome.UNAVAILABLE
                }
            }
            ArtKind.LABEL -> {
                labels.noneMarker(rom).delete()
                val target = labels.labelFile(rom)
                when (labels.fetch(rom, target)) {
                    Fetch.FOUND -> {
                        sources(rom.console, kind).record(rom.baseName, target, ArtSource.SYNCED, ArtSources.PROVIDER_SCREENSCRAPER)
                        labels.changed(rom, target)
                        ArtOutcome.DONE
                    }
                    Fetch.MISSING -> {
                        // What the library sync would note too: it stops asking for this game.
                        labels.noneMarker(rom).apply { parentFile?.mkdirs(); createNewFile() }
                        ArtOutcome.NOT_FOUND
                    }
                    Fetch.UNAVAILABLE -> ArtOutcome.UNAVAILABLE
                }
            }
        }
    }

    /**
     * Deletes the art kept on the device and its record. A label gets no ".none" marker, so a later
     * sync may bring the synced one back, as on the desktop; a removed cover falls back to the mirror's.
     */
    suspend fun remove(rom: Rom, kind: ArtKind): ArtOutcome = act {
        val file = describe(rom, kind).file ?: return@act ArtOutcome.NOT_FOUND
        if (kind == ArtKind.COVER) deleteLocalCovers(rom) else file.delete()
        sources(rom.console, kind).forget(rom.baseName)
        if (kind == ArtKind.COVER) covers.forgetMatch(rom) else labels.changed(rom, null)
        ArtOutcome.DONE
    }

    /** Replaces the art with what the sync finds; when it finds nothing, the current art stays. */
    suspend fun reset(rom: Rom, kind: ArtKind): ArtOutcome = act {
        if (busy) return@act ArtOutcome.BUSY
        when (kind) {
            ArtKind.COVER -> {
                covers.forgetMatch(rom)
                val url = covers.mirrorUrl(rom) ?: return@act ArtOutcome.NOT_FOUND
                when (covers.fetch(url)) {
                    Fetch.FOUND -> {
                        deleteLocalCovers(rom)
                        // The URL entry replaces the file's: recordUrl writes over it.
                        sources(rom.console, kind).recordUrl(rom.baseName, url, Covers.providerOf(url))
                        ArtOutcome.DONE
                    }
                    Fetch.MISSING -> ArtOutcome.NOT_FOUND
                    Fetch.UNAVAILABLE -> ArtOutcome.UNAVAILABLE
                }
            }
            ArtKind.LABEL -> {
                scratch.mkdirs()
                val tmp = File(scratch, "label-reset-${rom.console.id}.png")
                try {
                    when (labels.fetch(rom, tmp)) {
                        Fetch.FOUND -> {
                            val target = labels.labelFile(rom)
                            target.parentFile?.mkdirs()
                            if (!tmp.renameTo(target)) {
                                tmp.copyTo(target, overwrite = true)
                            }
                            labels.noneMarker(rom).delete()
                            sources(rom.console, kind).record(rom.baseName, target, ArtSource.SYNCED, ArtSources.PROVIDER_SCREENSCRAPER)
                            labels.changed(rom, target)
                            ArtOutcome.DONE
                        }
                        Fetch.MISSING -> ArtOutcome.NOT_FOUND
                        Fetch.UNAVAILABLE -> ArtOutcome.UNAVAILABLE
                    }
                } finally {
                    tmp.delete()
                }
            }
        }
    }

    /**
     * Saves art the user chose (issue 753): a search result, a suggestion, an imported or adjusted
     * image. Written beside the synced art under the same name, recorded as Manual, so the sync
     * never writes over it. A cover keeps its format ([ext]) and the other suffixes go; a label is
     * always `<name>.png` (the caller hands PNG bytes) and loses its ".none" marker.
     */
    suspend fun saveManual(rom: Rom, kind: ArtKind, bytes: ByteArray, ext: String, provider: String?, origin: String): ArtOutcome = act {
        val target = when (kind) {
            ArtKind.COVER -> File(paths.covers(rom.console), "${rom.baseName}.${ext.lowercase()}")
            ArtKind.LABEL -> labels.labelFile(rom)
        }
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) return@act ArtOutcome.UNAVAILABLE
            }
        } catch (_: java.io.IOException) {
            return@act ArtOutcome.UNAVAILABLE
        } finally {
            tmp.delete()
        }
        if (kind == ArtKind.COVER) {
            Covers.COVER_SUFFIXES.filter { it != target.extension }.forEach { File(paths.covers(rom.console), "${rom.baseName}.$it").delete() }
            covers.forgetMatch(rom)
        } else {
            labels.noneMarker(rom).delete()
        }
        sources(rom.console, kind).record(rom.baseName, target, ArtSource.MANUAL, provider, origin)
        if (kind == ArtKind.LABEL) labels.changed(rom, target)
        ArtOutcome.DONE
    }

    /**
     * Deletes every cover kept for [rom]: one per suffix may exist, and deleting only the first
     * would leave the next one showing as a local file.
     */
    private fun deleteLocalCovers(rom: Rom) {
        repeat(Covers.COVER_SUFFIXES.size) {
            val file = covers.localCover(rom) ?: return
            if (!file.delete()) return
        }
    }

    private suspend fun act(block: suspend () -> ArtOutcome): ArtOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            block().also { if (it == ArtOutcome.DONE) _changes.update { v -> v + 1 } }
        }
    }
}
