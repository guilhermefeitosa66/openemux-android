package com.openemux.mobile.data

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.ZipFile

/** Which group of a console's skin list a stored skin goes in: the player's own, or a Delta/Manic package. */
enum class StoredSkinKind { OWN, IMPORTED }

/**
 * A skin in files/skins/<console>: [ref] is what the console's settings save to use it ([SkinStore.ref]), [forms] the
 * device forms it has a layout for, [body] and [key] the colours of its preview (ARGB, null when it has none), [broken]
 * a folder whose skin.json cannot be read: listed so the player can delete it, never used.
 */
data class StoredSkin(
    val ref: String,
    val kind: StoredSkinKind,
    val name: String,
    val forms: Set<SkinForm>,
    val file: File,
    val body: Int? = null,
    val key: Int? = null,
    val broken: Boolean = false,
) {
    val id: String get() = file.name
}

/** A store operation refused: a skin that is not the player's own, or a path outside the console's skins folder. */
class SkinStoreException(message: String) : IOException(message)

/**
 * The player's skins, per console, under [base]/skins/<console> (issue 782): folders in OpenEmux's own format
 * (`<id>/skin.json` + images, the folder name a stable id) and the .deltaskin/.manicskin packages put there.
 *
 * Only the player's own folders are ever written. Every write resolves the real path and refuses anything that is not
 * a folder with a skin.json directly inside the console's skins folder: a package, a bundled skin (those live in the
 * APK's assets and their copies in the cache, never under [base]), a symbolic link or a name that climbs out.
 */
class SkinStore(private val base: File) {
    fun root(console: Console) = File(base, "skins/${console.id}")

    /** The player's skins for [console], their own first, each group by name (case-insensitive). */
    fun list(console: Console): List<StoredSkin> {
        val files = root(console).listFiles().orEmpty().filter { !isLink(it) && !it.name.startsWith(".") }
        val own = files.filter(::isOwn).map(::readOwn)
        val imported = files.filter(::isPackage).map(::readPackage)
        return own.sortedBy { it.name.lowercase() } + imported.sortedBy { it.name.lowercase() }
    }

    /**
     * The skin a console played with before the choice was saved (issue 782): the first by name of its own folders and
     * packages, the rule [com.openemux.mobile.launch.ImportedSkinRenderer.imported] applied.
     */
    fun legacyPick(console: Console): File? =
        // Only what [list] shows and [file] accepts: a hidden or linked entry would be saved as a ref nothing can use.
        root(console).listFiles()?.filter { safeName(it.name) && !isLink(it) && (isOwn(it) || isPackage(it)) }?.minByOrNull { it.name }

    /** The file or folder [ref] names for [console], when it is still there; null for no ref or one that is gone. */
    fun file(console: Console, ref: String?): File? {
        val (kind, name) = parse(ref) ?: return null
        val f = File(root(console), name)
        if (!safeName(name) || isLink(f)) return null
        return f.takeIf { if (kind == StoredSkinKind.OWN) isOwn(it) else isPackage(it) }
    }

    /** Gives the player's skin [id] the display name [name] (trimmed); its folder, so its id, stays. */
    fun rename(console: Console, id: String, name: String) {
        val dir = own(console, id)
        val clean = cleanName(name)
        writeJson(dir, renamed(dir, clean))
    }

    /**
     * A copy of the player's skin [id] named [name], in a folder of its own: returns its ref. Built in a hidden folder
     * renamed into place, so a copy cut short never shows up half made.
     */
    fun duplicate(console: Console, id: String, name: String): String {
        val dir = own(console, id)
        val clean = cleanName(name)
        val json = renamed(dir, clean)
        val newId = freeId(console, clean)
        val tmp = File(root(console), ".$newId.tmp")
        deleteTree(tmp)
        try {
            copyTree(dir, tmp)
            writeJson(tmp, json)
            if (!tmp.renameTo(File(root(console), newId))) throw IOException("could not create $newId")
        } finally {
            deleteTree(tmp)
        }
        return ref(StoredSkinKind.OWN, newId)
    }

    /** Deletes the player's skin [id], its folder and nothing a link inside it points at. */
    fun delete(console: Console, id: String) {
        val dir = own(console, id)
        deleteTree(dir)
        if (dir.exists()) throw IOException("could not delete $id")
    }

    /** The player's own folder [id] for [console], or [SkinStoreException] when it is anything else. */
    private fun own(console: Console, id: String): File {
        if (!safeName(id)) throw SkinStoreException("not a skin id: $id")
        val root = root(console)
        val dir = File(root, id)
        if (isLink(dir)) throw SkinStoreException("$id is a link")
        val inside = runCatching { dir.canonicalFile.parentFile == root.canonicalFile && root.canonicalPath.startsWith(File(base, "skins").canonicalPath + File.separator) }
            .getOrDefault(false)
        if (!inside) throw SkinStoreException("$id is outside ${console.id}'s skins")
        if (!isOwn(dir)) throw SkinStoreException("$id is not one of the player's skins")
        return dir
    }

    /** The folder name for a new skin called [name]: its letters and digits, made unique with -2, -3... */
    internal fun freeId(console: Console, name: String): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_ID).trim('-').ifEmpty { "skin" }
        val taken = root(console).list().orEmpty().map { it.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }.map { n -> if (n == 1) slug else "$slug-$n" }
            .first { it !in taken && ".$it.tmp" !in taken }
    }

    private fun readOwn(dir: File): StoredSkin = try {
        val skin = SkinFormat.readFolder(dir).skin
        val layout = skin.layouts.values.firstOrNull()
        val key = layout?.elements?.firstNotNullOfOrNull { e -> e.colors.values.firstOrNull() }
        StoredSkin(ref(StoredSkinKind.OWN, dir.name), StoredSkinKind.OWN, skin.name, skin.forms.toSet(), dir, layout?.background?.color, key)
    } catch (e: Exception) {
        StoredSkin(ref(StoredSkinKind.OWN, dir.name), StoredSkinKind.OWN, dir.name, emptySet(), dir, broken = true)
    }

    private fun readPackage(file: File): StoredSkin {
        val info = runCatching { packageInfo(file) }.getOrNull()
        val name = info?.optString("name").orEmpty().trim().ifEmpty { file.nameWithoutExtension }
        return StoredSkin(
            ref(StoredSkinKind.IMPORTED, file.name), StoredSkinKind.IMPORTED, name, info?.let(::packageForms).orEmpty(), file,
            broken = info == null,
        )
    }

    /**
     * [dir]'s skin.json with only its name changed to [name]: checked as a skin first, then edited as JSON, so the keys
     * this build does not model (a newer format's, a creator's own) survive a rename or a copy.
     */
    private fun renamed(dir: File, name: String): String {
        SkinFormat.readFolder(dir)
        val text = File(dir, SkinFormat.SKIN_JSON).readText().removePrefix("\uFEFF")
        return JSONObject(text).put("name", name).toString(2) + "\n"
    }

    private fun writeJson(dir: File, text: String) {
        val target = File(dir, SkinFormat.SKIN_JSON)
        val tmp = File(dir, SkinFormat.SKIN_JSON + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("could not write ${target.path}")
        }
    }

    companion object {
        private const val OWN_PREFIX = "own:"
        private const val PKG_PREFIX = "pkg:"
        private const val MAX_ID = 40
        const val MAX_NAME = 60

        fun ref(kind: StoredSkinKind, name: String) = (if (kind == StoredSkinKind.OWN) OWN_PREFIX else PKG_PREFIX) + name

        /** A saved ref split into its kind and its file name; null for none or one this build cannot read. */
        fun parse(ref: String?): Pair<StoredSkinKind, String>? = when {
            ref == null -> null
            ref.startsWith(OWN_PREFIX) -> StoredSkinKind.OWN to ref.removePrefix(OWN_PREFIX)
            ref.startsWith(PKG_PREFIX) -> StoredSkinKind.IMPORTED to ref.removePrefix(PKG_PREFIX)
            else -> null
        }?.takeIf { it.second.isNotEmpty() }

        /** The ref of [file], the skin [ImportedSkinRenderer]'s first-by-name rule picked before issue 782. */
        fun refOf(file: File): String =
            ref(if (file.isDirectory) StoredSkinKind.OWN else StoredSkinKind.IMPORTED, file.name)

        /** A display name the store accepts: trimmed, not blank, at most [MAX_NAME] characters. */
        fun cleanName(name: String): String {
            val clean = name.trim().take(MAX_NAME).trim()
            if (clean.isEmpty()) throw SkinStoreException("a skin needs a name")
            return clean
        }

        /** One file name, nothing that climbs or hides: what an id or a package name in a ref may be. */
        internal fun safeName(name: String) =
            name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && !name.startsWith(".")

        fun isOwn(file: File) = file.isDirectory && File(file, SkinFormat.SKIN_JSON).isFile

        fun isPackage(file: File) =
            file.isFile && (file.name.endsWith(".deltaskin", true) || file.name.endsWith(".manicskin", true))

        internal fun isLink(file: File) = Files.isSymbolicLink(file.toPath())

        /** A package's info.json, the way the renderer reads it (a byte-order mark and trailing commas forgiven). */
        internal fun packageInfo(file: File): JSONObject = ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().first { it.name.endsWith("info.json") && !it.name.startsWith("__MACOSX") }
            val text = zip.getInputStream(entry).bufferedReader().readText().removePrefix("\uFEFF")
            JSONObject(text.replace(Regex(",(\\s*[}\\]])"), "$1"))
        }

        /**
         * The forms a Delta/Manic package covers: Phone with iPhone art in both orientations, Tablet with iPad art in
         * both, Fold with both (a fold is a phone folded and a tablet open): those packages have no fold art of their own.
         */
        fun packageForms(info: JSONObject): Set<SkinForm> {
            val reps = info.optJSONObject("representations") ?: return emptySet()
            fun covers(family: String) = listOf("portrait", "landscape").all { o ->
                listOf("edgeToEdge", "standard").any { reps.optJSONObject(family)?.optJSONObject(it)?.optJSONObject(o) != null }
            }
            val phone = covers("iphone")
            val tablet = covers("ipad")
            return buildSet {
                if (phone) add(SkinForm.PHONE)
                if (tablet) add(SkinForm.TABLET)
                if (phone && tablet) add(SkinForm.FOLD)
            }
        }

        /** Copies [from] into [to], files and folders only: a link inside is left behind, never followed. */
        internal fun copyTree(from: File, to: File) {
            to.mkdirs()
            from.listFiles().orEmpty().forEach { f ->
                val dest = File(to, f.name)
                when {
                    isLink(f) -> Unit
                    f.isDirectory -> copyTree(f, dest)
                    f.isFile -> f.copyTo(dest, overwrite = true)
                }
            }
        }

        /** Deletes [file] and, for a folder, what is in it, removing links themselves without going through them. */
        internal fun deleteTree(file: File) {
            if (!isLink(file) && Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                file.listFiles().orEmpty().forEach(::deleteTree)
            }
            file.delete()
        }
    }
}
