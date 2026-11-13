package com.openemux.mobile.share

import android.util.Log
import com.openemux.mobile.data.Paths
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The SHA-256 of the files Share nearby hashed before, each with the size and
 * modification time it had then (issue 921), so a second offer of the same
 * games hashes nothing and a file that changed is hashed again. Kept in
 * `files/share/hashes.json`; entries whose files are gone are dropped when it
 * is saved. One instance per manifest, on the thread that builds it.
 */
class HashCache(private val file: File) {
    private class Entry(val size: Long, val mtime: Long, val sha256: String)

    private val entries = HashMap<String, Entry>()
    private var dirty = false

    /** Files whose hash came from the cache, and files that had to be read. */
    var hits = 0
        private set
    var misses = 0
        private set

    init {
        load()
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val json = JSONObject(file.readText())
            for (path in json.keys()) {
                val e = json.getJSONObject(path)
                entries[path] = Entry(e.getLong("size"), e.getLong("mtime"), e.getString("sha256"))
            }
        } catch (e: Exception) {
            // Unreadable or malformed: hashed as if there were no cache, and written again.
            if (e !is IOException && e !is JSONException) throw e
            Log.w(TAG, "share hashes: cache unreadable, starting over", e)
            entries.clear()
            dirty = true
        }
    }

    /** The hash of [path] as it was when hashed, if it still has that [size] and [mtime]. */
    fun lookup(path: String, size: Long, mtime: Long): String? {
        val e = entries[path]
        if (e == null || e.size != size || e.mtime != mtime) {
            misses++
            return null
        }
        hits++
        return e.sha256
    }

    fun put(path: String, size: Long, mtime: Long, sha256: String) {
        entries[path] = Entry(size, mtime, sha256)
        dirty = true
    }

    /** Writes the cache when it changed, without the files that no longer exist. */
    fun save() {
        val gone = entries.keys.filter { !File(it).isFile }
        if (gone.isNotEmpty()) {
            gone.forEach { entries.remove(it) }
            dirty = true
        }
        if (!dirty) return
        val json = JSONObject()
        for ((path, e) in entries) json.put(path, JSONObject().put("size", e.size).put("mtime", e.mtime).put("sha256", e.sha256))
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) throw IOException("rename failed")
            dirty = false
        } catch (e: IOException) {
            // The next offer hashes again: nothing is lost but time.
            Log.w(TAG, "share hashes: cache not saved", e)
        }
    }

    val size: Int get() = entries.size

    companion object {
        private const val TAG = "OpenEmuxShare"

        fun of(paths: Paths) = HashCache(File(paths.internal, "share/hashes.json"))
    }
}
