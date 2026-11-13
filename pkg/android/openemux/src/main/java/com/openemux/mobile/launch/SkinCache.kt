package com.openemux.mobile.launch

import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The skins drawn for launches, kept under `launch/skins/<id>` (issue 917): a
 * launch whose inputs match a kept skin draws nothing and writes no PNG. A
 * [Key] names everything the drawing reads (see [GameLauncher.skinKey]); the
 * newest [keep] entries stay, a hit touching its entry's time. An entry is
 * written aside and renamed into place, so a crash mid-draw leaves no half
 * skin to find.
 */
class SkinCache(private val root: File, private val keep: Int = KEEP) {
    class Key(val text: String) {
        val id: String = sha256Hex(text).take(24)
    }

    /** The kept skin for [key], or null. [layout] finds an NDS screen layout by orientation and id. */
    fun lookup(key: Key, layout: (orientation: String, id: String) -> Any?): GameSkin.Drawn? {
        val dir = File(root, key.id)
        val note = File(dir, NOTE).takeIf { it.isFile } ?: return null
        val fields = runCatching { note.readLines() }.getOrNull()
            ?.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap() ?: return null
        if (fields["key"] != key.text.replace('\n', ' ')) return null
        val file = fields["file"]?.let { File(dir, it) }?.takeIf { it.isFile } ?: return null
        val nds = fields["nds"]?.let { ids ->
            val (a, b) = ids.split(',').takeIf { it.size == 2 } ?: return null
            @Suppress("UNCHECKED_CAST")
            val pair = (layout("portrait", a) to layout("landscape", b)) as Pair<com.openemux.mobile.data.ScreenLayout?, com.openemux.mobile.data.ScreenLayout?>
            val first = pair.first ?: return null
            val second = pair.second ?: return null
            first to second
        }
        dir.setLastModified(System.currentTimeMillis())
        return GameSkin.Drawn(file, fields["packaged"] == "true", nds)
    }

    /** Draws through [draw] into a fresh entry for [key] and keeps it, replacing an older entry for the key. */
    fun render(key: Key, draw: (outDir: File) -> GameSkin.Drawn): GameSkin.Drawn {
        root.mkdirs()
        val tmp = File(root, "${key.id}.tmp").apply { deleteRecursively(); mkdirs() }
        val drawn = draw(File(tmp, "skin"))
        val relative = drawn.file.relativeTo(tmp).path
        File(tmp, NOTE).writeText(
            buildString {
                append("key=").append(key.text.replace('\n', ' ')).append('\n')
                append("file=").append(relative).append('\n')
                append("packaged=").append(drawn.packaged).append('\n')
                drawn.ndsLayouts?.let { (a, b) -> append("nds=").append(a.id).append(',').append(b.id).append('\n') }
            },
        )
        val dir = File(root, key.id)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) throw IOException("could not keep the skin")
        prune()
        return GameSkin.Drawn(File(dir, relative), drawn.packaged, drawn.ndsLayouts)
    }

    /** Drops the entries beyond [keep], oldest first, and anything left aside. */
    private fun prune() {
        val entries = root.listFiles { f -> f.isDirectory }.orEmpty()
        entries.filter { it.name.endsWith(".tmp") }.forEach { it.deleteRecursively() }
        entries.filter { !it.name.endsWith(".tmp") }.sortedByDescending { it.lastModified() }.drop(keep).forEach {
            if (!it.deleteRecursively()) Log.w(TAG, "old skin not dropped: ${it.name}")
        }
    }

    /** How many skins are kept now. */
    val size: Int get() = root.listFiles { f -> f.isDirectory && !f.name.endsWith(".tmp") }.orEmpty().size

    companion object {
        const val KEEP = 6
        private const val NOTE = "drawn.txt"
        private const val TAG = "OpenEmuxSkin"

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** What a skin file or folder is made of, for a key: every file's path, size and time; "" for none. */
        fun signature(file: File?): String = when {
            file == null -> ""
            file.isDirectory -> file.walkTopDown().filter { it.isFile }.map { "${it.relativeTo(file).path}:${it.length()}:${it.lastModified()}" }.sorted().joinToString(";")
            else -> "${file.length()}:${file.lastModified()}"
        }
    }
}
