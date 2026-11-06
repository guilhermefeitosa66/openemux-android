package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.ImportCheck
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A skin of the player's own to send: the console it is for, its id in the store and the name its list shows. */
data class SkinToSend(val console: Console, val id: String, val name: String)

/**
 * A controller skin in Share nearby. Android only: the desktop app has no skins.
 *
 * It travels as an entry of `games[]` that is no game: the console it is for, its name as the title, and one item
 * of kind `skin`, a zip of the skin in OpenEmux's own format (`docs/skin-format.md`, as `SkinStore.export` writes
 * it). Like `bios` before it, the kind came to protocol v1 without a version bump: a receiver that predates it (and
 * the desktop app) answers a kind it does not know `skip`, so the offer ends with nothing taken, never in an error.
 *
 * The receiver stages the zip in the console's skins folder, checks that it is a skin for that console, and imports
 * it with the store's own import as a new skin of the player's: never over one that is there, never put in use.
 */
object SkinShare {
    /**
     * The manifest that offers [skin], its zip written to [work] (one at a time: the next build writes over it).
     * Whatever makes the skin unreadable is thrown as it is; [cancelled] is checked between the steps.
     */
    fun build(library: ShareLibrary, skin: SkinToSend, work: File, cancelled: () -> Boolean = { false }): BuiltManifest {
        fun check() {
            if (cancelled()) throw ShareError(ErrorCode.CANCELLED, "cancelled while packing")
        }
        check()
        work.mkdirs()
        val zip = File(work, ZIP)
        val tmp = File(work, "$ZIP.tmp")
        try {
            tmp.outputStream().use { library.skins.export(skin.console, skin.id, it) }
            check()
            // Renamed over the last one, so an upload still reading that one keeps the file it opened.
            if (!tmp.renameTo(zip)) throw IOException("could not write ${zip.name}")
        } finally {
            tmp.delete()
        }
        val size = zip.length()
        val item = JSONObject().put("id", ITEM_ID).put("kind", Item.SKIN).put("name", SkinTemplate.fileName(skin.name))
            .put("size", size).put("mtime", zip.lastModified() / 1000)
        if (size <= ManifestBuilder.HASH_LIMIT) item.put("sha256", sha256(zip, ::check))
        val game = JSONObject().put("id", GAME_ID).put("console", skin.console.id).put("title", skin.name)
            .put("items", JSONArray().put(item))
        val json = JSONObject().put("games", JSONArray().put(game)).put("total_size", size).put("file_count", 1)
        return BuiltManifest(json, mapOf(ITEM_ID to zip), emptyList())
    }

    /** Whether [peer] can take a skin at all: a phone or a tablet. The desktop app would only answer `skip`. */
    fun accepts(peer: Peer): Boolean = ShareFormat.platformOf(peer.platform) != ShareProtocol.DEFAULT_PLATFORM

    /** Whether the received [zip] is a skin in OpenEmux's format for [console], within the import's limits. */
    fun isSkinFor(zip: File, console: Console): Boolean = try {
        SkinImport.inspect(zip, console, null) is ImportCheck.Own
    } catch (_: Exception) {
        false
    }

    /**
     * Imports the received [zip] as a new skin of the player's own for [console], every layout it has kept, under
     * its own name or, when a skin here already shows that name, `name (2)`: the new skin's folder. The console goes
     * on playing with the skin it had.
     */
    fun import(library: ShareLibrary, console: Console, zip: File): File {
        library.beforeSkinImport()
        val skins = library.skins
        val name = skins.freeName(console, SkinFormat.readZip(zip).skin.name)
        val ref = skins.importOwn(console, zip, name, allLayouts = true)
        return skins.file(console, ref) ?: throw IOException("the imported skin is gone")
    }

    private fun sha256(file: File, check: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(ManifestBuilder.HASH_CHUNK)
            while (true) {
                check()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private const val ZIP = "skin.zip"
    private const val GAME_ID = "g1"
    private const val ITEM_ID = "g1i1"
}
