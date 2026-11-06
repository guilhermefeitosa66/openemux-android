package com.openemux.mobile.data

import org.json.JSONArray
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
        SkinFormat.readFolder(dir)
        return build(console, File(dir, SkinFormat.SKIN_JSON).readText(), name) { tmp -> copyTree(dir, tmp) }
    }

    /**
     * A new skin of the player's own named [name], made from [source]: a skin in OpenEmux's own format for [console], a
     * folder or a zip (a bundled package, issue 783). Every layout and image it has is kept and only its name changes in
     * skin.json; [source] is only read. Built in a hidden folder renamed into place, like [duplicate]: returns its ref.
     */
    fun create(console: Console, source: File, name: String): String =
        if (source.isDirectory) {
            checkConsole(SkinFormat.readFolder(source), console)
            build(console, File(source, SkinFormat.SKIN_JSON).readText(), name) { tmp -> copyTree(source, tmp) }
        } else {
            create(console, ZipSkinSource(source), name)
        }

    /** [create] from a zip's [source]: each of its files extracted, none outside the new skin's folder. */
    internal fun create(console: Console, source: SkinSource, name: String): String {
        checkConsole(SkinFormat.read(source), console)
        val json = source.open(SkinFormat.SKIN_JSON)!!.use { it.bufferedReader().readText() }
        return build(console, json, name) { tmp ->
            val top = tmp.canonicalFile
            for (path in source.files) {
                val dest = File(tmp, path)
                if (!dest.canonicalPath.startsWith(top.path + File.separator)) throw SkinStoreException("$path is outside the skin")
                dest.parentFile.mkdirs()
                source.open(path)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun checkConsole(pkg: SkinPackage, console: Console) {
        if (pkg.skin.console != console) throw SkinStoreException("a ${pkg.skin.console.id} skin is not for ${console.id}")
    }

    /**
     * A new skin of the player's own for [console] (skin.json [json]) named [name], its files put in by [fill]: its ref.
     * Built in a hidden folder renamed into place, so a copy cut short never shows up half made.
     */
    private fun build(console: Console, json: String, name: String, fill: (File) -> Unit): String {
        val clean = cleanName(name)
        val text = withName(json, clean)
        val root = root(console)
        val newId = freeId(console, clean)
        val tmp = File(root, ".$newId.tmp")
        deleteTree(tmp)
        try {
            if (!tmp.mkdirs()) throw IOException("could not make ${tmp.name}")
            fill(tmp)
            writeJson(tmp, text)
            if (!tmp.renameTo(File(root, newId))) throw IOException("could not create $newId")
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

    /**
     * Imports [zip], a skin in OpenEmux's own format for [console] (issue 787): as a new skin called [name], or, with
     * [replace], over the player's skin of that id, which keeps its id (so a console using it goes on using it) and
     * its name. Returns the skin's ref.
     *
     * Only skin.json and the images it names are taken from the zip, for the layouts whose folder the zip has
     * ([SkinImport.offered]); an image the zip lacks is the replaced skin's, if
     * it had one. The skin is built in a hidden folder, read back, and only then put in place (a replaced one moved
     * aside until the new one is in): a zip that fails at any point leaves the list as it was.
     */
    fun importOwn(console: Console, zip: File, name: String?, replace: String? = null): String {
        SkinImport.guard(zip)
        val pkg = SkinFormat.readZip(zip)
        if (pkg.skin.console != console) throw SkinStoreException("a ${pkg.skin.console.id} skin, not ${console.id}")
        val target = replace?.let { own(console, it) }
        val finalName = if (target != null) {
            runCatching { SkinFormat.readFolder(target).skin.name }.getOrDefault(pkg.skin.name)
        } else {
            cleanName(name ?: pkg.skin.name)
        }
        val id = target?.name ?: freeId(console, finalName)
        val root = root(console)
        val tmp = File(root, ".$id.tmp")
        deleteTree(tmp)
        try {
            tmp.mkdirs()
            val text = pkg.source.open(SkinFormat.SKIN_JSON)!!.use { String(it.readBytes(), Charsets.UTF_8) }.removePrefix("\uFEFF")
            val kept = SkinImport.offered(pkg)
            val json = JSONObject(text).put("name", finalName)
            val layoutsJson = json.getJSONObject("layouts")
            layoutsJson.keys().asSequence().toList().filter { key -> kept.none { it.id == key } }.forEach { layoutsJson.remove(it) }
            json.put("forms", JSONArray(SkinImport.forms(kept).map { it.id }))
            writeJson(tmp, json.toString(2) + "\n")
            var budget = SkinImport.MAX_BYTES
            for ((layoutId, layout) in pkg.skin.layouts.filterKeys { it in kept }) {
                val files = listOf(layout.background.imageFile) + listOfNotNull(layout.frame?.imageFile) +
                    layout.elements.flatMap { it.imageFiles }
                for (file in files) {
                    val out = File(tmp, "${layoutId.id}/$file")
                    val copied = pkg.image(layoutId, file)?.let { path ->
                        out.parentFile!!.mkdirs()
                        pkg.source.open(path)!!.use { input ->
                            out.outputStream().use { SkinImport.copyLimited(input, it, minOf(SkinImport.MAX_IMAGE, budget)) }
                        }
                    } ?: target?.let { File(it, "${layoutId.id}/$file") }?.takeIf { it.isFile && !isLink(it) }?.let { old ->
                        out.parentFile!!.mkdirs()
                        old.copyTo(out, overwrite = true).length()
                    } ?: 0L
                    budget -= copied
                }
            }
            SkinFormat.readFolder(tmp)
            val dest = File(root, id)
            if (target == null) {
                if (!tmp.renameTo(dest)) throw IOException("could not create $id")
            } else {
                val aside = File(root, ".$id.old")
                deleteTree(aside)
                if (!target.renameTo(aside)) throw IOException("could not move $id aside")
                if (!tmp.renameTo(dest)) {
                    aside.renameTo(dest)
                    throw IOException("could not replace $id")
                }
                deleteTree(aside)
            }
        } finally {
            deleteTree(tmp)
        }
        return ref(StoredSkinKind.OWN, id)
    }

    /**
     * Imports [zip], a Delta or Manic EMU package (issue 787), as `<name>.<extension>` in [console]'s skins under a
     * name no other file there has: it shows under Imported. Returns its ref.
     */
    fun importPackage(console: Console, zip: File, name: String, extension: String): String {
        if (extension != SkinImport.DELTA && extension != SkinImport.MANIC) throw SkinStoreException("not a package: $extension")
        SkinImport.guard(zip)
        val root = root(console)
        root.mkdirs()
        val file = freeId(console, name, ".$extension")
        val tmp = File(root, ".$file.tmp")
        try {
            zip.inputStream().use { input -> tmp.outputStream().use { SkinImport.copyLimited(input, it, SkinImport.MAX_BYTES) } }
            if (!tmp.renameTo(File(root, file))) throw IOException("could not create $file")
        } finally {
            tmp.delete()
        }
        return ref(StoredSkinKind.IMPORTED, file)
    }

    /** The player's skin [id] as a template zip's pieces: its model and its own skin.json, for [SkinTemplate.write]. */
    fun template(console: Console, id: String): Pair<SkinPackage, String> {
        val dir = own(console, id)
        val pkg = SkinFormat.readFolder(dir)
        return pkg to File(dir, SkinFormat.SKIN_JSON).readText()
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

    /**
     * The folder name for a new skin called [name] (or, with [suffix], a package's file name): its letters and digits,
     * made unique with -2, -3...
     */
    internal fun freeId(console: Console, name: String, suffix: String = ""): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_ID).trim('-').ifEmpty { "skin" }
        val taken = root(console).list().orEmpty().map { it.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }.map { n -> (if (n == 1) slug else "$slug-$n") + suffix }
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
        return withName(File(dir, SkinFormat.SKIN_JSON).readText(), name)
    }

    /** The skin.json [json] with only its name changed to [name]. */
    private fun withName(json: String, name: String): String =
        JSONObject(json.removePrefix("\uFEFF")).put("name", name).toString(2) + "\n"

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
        internal fun packageInfo(file: File): JSONObject = ZipFile(file).use { packageInfo(it) }

        /** The info.json of the open package [zip]; a byte-order mark and trailing commas (some community skins) forgiven. */
        fun packageInfo(zip: ZipFile): JSONObject {
            val entry = zip.entries().asSequence().first { it.name.endsWith("info.json") && !it.name.startsWith("__MACOSX") }
            val text = zip.getInputStream(entry).bufferedReader().readText().removePrefix("\uFEFF")
            return JSONObject(text.replace(Regex(",(\\s*[}\\]])"), "$1"))
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
