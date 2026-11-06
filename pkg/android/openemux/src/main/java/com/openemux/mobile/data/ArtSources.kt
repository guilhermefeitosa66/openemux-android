package com.openemux.mobile.data

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

enum class ArtKind { COVER, LABEL }

enum class ArtSource(val id: String) {
    /** Written by the artwork sync. */
    SYNCED("synced"),

    /** Chosen by the user (search, import, editor). */
    MANUAL("manual"),

    /** A file on the device nothing here vouches for: replaced by hand, received through Share nearby. */
    LOCAL("local"),
}

/** One record: where a ROM's art of one kind came from. */
data class ArtEntry(
    val source: ArtSource,
    /** The art file's name; null for a cover served from the mirror, which has no file. */
    val file: String? = null,
    val mtimeNs: Long? = null,
    val size: Long? = null,
    /** The mirror URL a synced cover is served from. */
    val url: String? = null,
    /** When it was recorded, in epoch milliseconds; null when unknown (a label synced before records existed). */
    val updated: Long? = null,
    val provider: String? = null,
    val origin: String? = null,
)

/**
 * Where each artwork file came from, a port of the desktop's `core/artwork_sources.py` (issue 746).
 *
 * Synced and hand-picked art share one file per ROM and kind, so the file alone cannot say who
 * wrote it. The record lives beside the art, one hidden JSON file per console and kind, in the
 * desktop's schema:
 *
 *     <covers|labels>/<console>/.openemux-sources.json
 *     {"version": 1, "entries": {"<stem>": {"source": "synced", "file": "<stem>.png",
 *         "mtime_ns": 123, "size": 456, "updated": "2026-11-02T12:00:00Z", ...}}}
 *
 * A record is believed only while it still matches the file (name, mtime and size): art replaced
 * by hand reads as a plain local file again. A cover served from the mirror has no file on
 * Android; its record holds the URL instead.
 *
 * Provenance is a nicety: nothing here may break a save or a sync, so a read failure reads as "no
 * records" and a write failure is logged and swallowed.
 */
class ArtSources(val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    val file = File(dir, SOURCES_FILE)

    /** Whether this console has a records file at all (the label migration asks). */
    fun exists(): Boolean = file.isFile

    fun entries(): Map<String, ArtEntry> = synchronized(lock) { load().mapNotNull { (k, v) -> parse(v)?.let { k to it } }.toMap() }

    fun entry(stem: String): ArtEntry? = entries()[stem]

    /** Notes that [art] (this ROM's art) came from [source]. */
    fun record(stem: String, art: File, source: ArtSource, provider: String? = null, origin: String? = null, dated: Boolean = true) {
        val entry = fileEntry(art, source, provider, origin, dated) ?: return
        apply { it[stem] = entry; true }
    }

    /** Notes that this ROM's cover is served from the mirror at [url]. */
    fun recordUrl(stem: String, url: String, provider: String = PROVIDER_MIRROR) {
        apply { it[stem] = urlEntry(url, provider); true }
    }

    /** Drops the record of art that was just removed. */
    fun forget(stem: String) {
        if (!file.exists()) return
        apply { it.remove(stem) != null }
    }

    /**
     * Moves the record of [oldStem] to [newStem] when a game is renamed (issue 790): its file name
     * follows the art's. A stale record under the new name goes, so it cannot describe the moved art.
     */
    fun rename(oldStem: String, newStem: String) {
        if (!file.exists()) return
        apply { entries ->
            val stale = entries.remove(newStem) != null
            val entry = entries.remove(oldStem) ?: return@apply stale
            entry.optString("file").takeIf { it.startsWith(oldStem) }?.let { entry.put("file", newStem + it.substring(oldStem.length)) }
            entries[newStem] = entry
            true
        }
    }

    /** Buffers records and writes the file once on exit, also when [block] throws. */
    fun <T> batch(block: (Batch) -> T): T {
        val batch = Batch()
        try {
            return block(batch)
        } finally {
            batch.flush()
        }
    }

    inner class Batch internal constructor() {
        private val pending = LinkedHashMap<String, JSONObject>()

        fun record(stem: String, art: File, source: ArtSource, provider: String? = null, dated: Boolean = true) {
            fileEntry(art, source, provider, null, dated)?.let { pending[stem] = it }
        }

        fun recordUrl(stem: String, url: String, provider: String = PROVIDER_MIRROR) {
            pending[stem] = urlEntry(url, provider)
        }

        internal fun flush() {
            if (pending.isEmpty()) return
            val todo = LinkedHashMap(pending)
            pending.clear()
            apply { entries ->
                var changed = false
                for ((stem, entry) in todo) {
                    // A run can last minutes, and the user may choose other art for a game the sync already
                    // reached: that save records itself at once, and the buffered entry must not write over it.
                    if (entry.has("file")) {
                        if (!stillCurrent(entry)) continue
                    } else if (entries[stem]?.has("file") == true) {
                        continue
                    }
                    entries[stem] = entry
                    changed = true
                }
                changed
            }
        }
    }

    /** The record of [stem] when it still describes [art]; null when there is none or [art] changed since. */
    fun matching(stem: String, art: File): ArtEntry? {
        val entry = entry(stem) ?: return null
        if (entry.file != art.name || entry.source == ArtSource.LOCAL) return null
        val (mtime, size) = stat(art) ?: return null
        return entry.takeIf { it.mtimeNs == mtime && it.size == size }
    }

    private fun stillCurrent(entry: JSONObject): Boolean {
        val art = File(dir, entry.optString("file"))
        val (mtime, size) = stat(art) ?: return false
        return entry.optLong("mtime_ns", -1) == mtime && entry.optLong("size", -1) == size
    }

    private fun fileEntry(art: File, source: ArtSource, provider: String?, origin: String?, dated: Boolean): JSONObject? {
        val (mtime, size) = stat(art) ?: run {
            Log.w(TAG, "artwork sources cannot stat art: ${art.name}")
            return null
        }
        return JSONObject().apply {
            put("source", source.id)
            put("file", art.name)
            put("mtime_ns", mtime)
            put("size", size)
            if (dated) put("updated", stamp())
            provider?.let { put("provider", it) }
            origin?.let { put("origin", it) }
        }
    }

    private fun urlEntry(url: String, provider: String) = JSONObject().apply {
        put("source", ArtSource.SYNCED.id)
        put("url", url)
        put("updated", stamp())
        put("provider", provider)
    }

    private fun stamp(): String = Instant.ofEpochMilli(now()).truncatedTo(ChronoUnit.SECONDS).toString()

    /** Reads the file, lets [change] edit the entries, writes them back when it says they changed. */
    private fun apply(change: (MutableMap<String, JSONObject>) -> Boolean) = synchronized(lock) {
        val entries = load()
        if (change(entries)) store(entries)
    }

    private fun load(): MutableMap<String, JSONObject> {
        val raw = try {
            file.readText()
        } catch (e: IOException) {
            if (file.exists()) Log.w(TAG, "artwork sources unreadable: ${e.javaClass.simpleName}")
            return LinkedHashMap()
        }
        val data = runCatching { JSONObject(raw) }.getOrNull()
        val entries = data?.optJSONObject("entries")
        if (data == null || data.optInt("version", -1) != SCHEMA_VERSION || entries == null) {
            Log.w(TAG, "artwork sources has an unknown layout, ignored: ${file.path}")
            return LinkedHashMap()
        }
        val out = LinkedHashMap<String, JSONObject>()
        for (key in entries.keys()) entries.optJSONObject(key)?.let { out[key] = it }
        return out
    }

    private fun store(entries: Map<String, JSONObject>) {
        val payload = JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("entries", JSONObject().apply { entries.forEach { (k, v) -> put(k, v) } })
        }
        try {
            dir.mkdirs()
            val tmp = File(dir, "$SOURCES_FILE.tmp")
            tmp.writeText(payload.toString(1))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw IOException("rename failed")
            }
        } catch (e: IOException) {
            Log.w(TAG, "artwork sources not written: ${e.message}")
        }
    }

    companion object {
        const val SOURCES_FILE = ".openemux-sources.json"
        const val SCHEMA_VERSION = 1
        const val PROVIDER_MIRROR = "openemux"
        const val PROVIDER_SCREENSCRAPER = "screenscraper"
        private const val TAG = "OpenEmuxArtSources"

        /** Every read-modify-write of every records file: the sync and the manager write from different threads. */
        private val lock = Any()

        fun parse(json: JSONObject): ArtEntry? {
            val source = ArtSource.entries.firstOrNull { it.id == json.optString("source") } ?: return null
            return ArtEntry(
                source = source,
                file = json.optString("file").ifEmpty { null },
                mtimeNs = if (json.has("mtime_ns")) json.optLong("mtime_ns") else null,
                size = if (json.has("size")) json.optLong("size") else null,
                url = json.optString("url").ifEmpty { null },
                updated = parseStamp(json.optString("updated")),
                provider = json.optString("provider").ifEmpty { null },
                origin = json.optString("origin").ifEmpty { null },
            )
        }

        fun parseStamp(value: String?): Long? {
            if (value.isNullOrEmpty()) return null
            return try {
                Instant.parse(value).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }

        /** The file's mtime in nanoseconds and its size; null when it cannot be read. */
        fun stat(art: File): Pair<Long, Long>? = try {
            if (!art.isFile) null else Files.getLastModifiedTime(art.toPath()).to(TimeUnit.NANOSECONDS) to art.length()
        } catch (_: IOException) {
            null
        }
    }
}
