package com.openemux.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
        val json = File(dir, SkinFormat.SKIN_JSON).readText()
        return build(console, name) { tmp -> copyTree(dir, tmp); json }
    }

    /**
     * A new skin of the player's own named [name], made from [source]: a skin in OpenEmux's own format for [console], a
     * folder or a zip (a bundled package, issue 783). Every layout and image it has is kept and only its name changes in
     * skin.json; [source] is only read. Built in a hidden folder renamed into place, like [duplicate]: returns its ref.
     */
    fun create(console: Console, source: File, name: String): String =
        if (source.isDirectory) {
            checkConsole(SkinFormat.readFolder(source), console)
            val json = File(source, SkinFormat.SKIN_JSON).readText()
            build(console, name) { tmp -> copyTree(source, tmp); json }
        } else {
            create(console, ZipSkinSource(source), name)
        }

    /** [create] from a zip's [source]: each of its files extracted, none outside the new skin's folder. */
    internal fun create(console: Console, source: SkinSource, name: String): String {
        checkConsole(SkinFormat.read(source), console)
        val json = source.open(SkinFormat.SKIN_JSON)!!.use { it.bufferedReader().readText() }
        return build(console, name) { tmp ->
            val top = tmp.canonicalFile
            for (path in source.files) {
                val dest = File(tmp, path)
                if (!dest.canonicalPath.startsWith(top.path + File.separator)) throw SkinStoreException("$path is outside the skin")
                dest.parentFile.mkdirs()
                source.open(path)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            json
        }
    }

    /**
     * A new skin of the player's own for [console] named [name], made by [fill] (issue 797: a Delta/Manic package
     * converted): [fill] writes the images into the new skin's folder and returns the skin, whose skin.json is written
     * after them. The folder is read back before it is put in place, so a conversion that went wrong anywhere leaves
     * nothing behind: returns its ref.
     */
    fun createConverted(console: Console, name: String, fill: (File) -> OpenEmuxSkin): String =
        build(console, name, verify = true) { tmp ->
            val skin = fill(tmp)
            if (skin.console != console) throw SkinStoreException("a ${skin.console.id} skin is not for ${console.id}")
            SkinFormat.toJson(skin)
        }

    /**
     * The player's imported package [id] (a .deltaskin or .manicskin directly in [console]'s skins folder), only to read
     * (issue 797: Duplicate makes a skin of the player's own out of it), or [SkinStoreException] for anything else.
     */
    fun importedPackage(console: Console, id: String): File {
        if (!safeName(id)) throw SkinStoreException("not a skin id: $id")
        val file = File(root(console), id)
        if (isLink(file) || !isPackage(file)) throw SkinStoreException("$id is not an imported package")
        return file
    }

    private fun checkConsole(pkg: SkinPackage, console: Console) = checkConsole(pkg.skin, console)

    private fun checkConsole(skin: OpenEmuxSkin, console: Console) {
        if (skin.console != console) throw SkinStoreException("a ${skin.console.id} skin is not for ${console.id}")
    }

    /**
     * A new skin of the player's own for [console] named [name], its files put in by [fill], which returns its skin.json
     * (named [name] here): its ref. Built in a hidden folder renamed into place, so a copy cut short never shows up half
     * made; [verify] reads the folder back as a skin first.
     */
    private fun build(console: Console, name: String, verify: Boolean = false, fill: (File) -> String): String {
        val clean = cleanName(name)
        val root = root(console)
        val newId = freeId(console, clean)
        val tmp = File(root, ".$newId.tmp")
        deleteTree(tmp)
        try {
            if (!tmp.mkdirs()) throw IOException("could not make ${tmp.name}")
            writeJson(tmp, withName(fill(tmp), clean))
            if (verify) SkinFormat.readFolder(tmp)
            if (!tmp.renameTo(File(root, newId))) throw IOException("could not create $newId")
        } finally {
            deleteTree(tmp)
        }
        return ref(StoredSkinKind.OWN, newId)
    }

    /** Deletes the player's skin [id], its folder and nothing a link inside it points at, and its draft. */
    fun delete(console: Console, id: String) {
        val dir = own(console, id)
        deleteTree(dir)
        if (dir.exists()) throw IOException("could not delete $id")
        dropDraft(console, id)
    }

    /**
     * The editor's Save (issue 784): [json] becomes the player's skin [id]'s skin.json. It must read as a skin for
     * [console] first. [images] (issue 786) are the image edits: a staged texture copied into place, a removed image
     * deleted; the folders of layouts [json] no longer has go with their images (issue 785). A failing save leaves
     * skin.json as it was: the new images go in first and only show once it names them.
     */
    fun saveEdits(console: Console, id: String, json: String, images: SkinImageEdits = emptyMap()) {
        val dir = own(console, id)
        val skin = SkinFormat.parse(json)
        checkConsole(skin, console)
        images.keys.forEach { target(dir, it) }
        val stage = stageDir(console, id)
        placeImages(dir, stage, images)
        writeJson(dir, json)
        removeImages(dir, images)
        dropLayoutFolders(dir, skin)
    }

    /**
     * The editor's "Save as a new skin": a copy of the player's skin [id] (its images with [images] applied, but those
     * of layouts [json] no longer has) with [json] as its skin.json, named [name]; [id] is left as it was. Returns the
     * new skin's ref.
     */
    fun saveAsNew(console: Console, id: String, json: String, name: String, images: SkinImageEdits = emptyMap()): String {
        val dir = own(console, id)
        val skin = SkinFormat.parse(json)
        checkConsole(skin, console)
        images.keys.forEach { target(dir, it) }
        val stage = stageDir(console, id)
        return build(console, name) { tmp ->
            copyTree(dir, tmp)
            placeImages(tmp, stage, images)
            removeImages(tmp, images)
            dropLayoutFolders(tmp, skin)
            json
        }
    }

    /** Deletes the image folders in [dir] of the layouts [skin] does not offer. */
    private fun dropLayoutFolders(dir: File, skin: OpenEmuxSkin) {
        SkinLayoutId.entries.filter { it !in skin.layouts }.forEach { deleteTree(File(dir, it.id)) }
    }

    /**
     * Keeps [json], the editor's unsaved skin.json for the player's skin [id], beside it as a hidden file, so work in
     * progress survives the app being closed (issue 784); with [images], its image edits too (issue 786).
     */
    fun saveDraft(console: Console, id: String, json: String, images: SkinImageEdits = emptyMap()) {
        own(console, id)
        val list = File(stageDir(console, id), IMAGES)
        if (images.isEmpty()) {
            if (!isLink(list.parentFile!!) && !isLink(list)) list.delete()
        } else {
            val text = JSONObject().apply {
                images.forEach { (path, edit) -> put(path, if (edit is SkinImageEdit.Staged) STAGED + edit.name else REMOVED) }
            }.toString(2)
            stageDir(console, id).let { if (isLink(it)) throw SkinStoreException("draft of $id is a link") else it.mkdirs() }
            writeAtomic(list, text)
        }
        writeAtomic(draftFile(console, id), json)
    }

    /** The draft of the player's skin [id], or null for none; one that no longer reads as a skin for [console] goes. */
    fun draft(console: Console, id: String): String? {
        if (!safeName(id)) return null
        val file = draftFile(console, id)
        if (isLink(file) || !file.isFile) return null
        val json = runCatching { file.readText().also { checkConsole(SkinFormat.parse(it), console) } }.getOrNull()
        if (json == null) file.delete()
        return json
    }

    /**
     * The image edits of the draft of the player's skin [id] (issue 786): each path a layout's file, each staged
     * texture one that is still there; anything else is left out. Empty for none.
     */
    fun draftImages(console: Console, id: String): SkinImageEdits {
        if (!safeName(id)) return emptyMap()
        val stage = stageDir(console, id)
        val list = File(stage, IMAGES)
        if (isLink(stage) || isLink(list) || !list.isFile) return emptyMap()
        val json = runCatching { JSONObject(list.readText()) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().toList().mapNotNull { path ->
            val value = json.optString(path)
            val edit = when {
                !imagePath(path) -> null
                value == REMOVED -> SkinImageEdit.Removed
                value.startsWith(STAGED) -> value.removePrefix(STAGED).takeIf { stagedFile(stage, it) != null }?.let { SkinImageEdit.Staged(it) }
                else -> null
            }
            edit?.let { path to it }
        }.toMap()
    }

    /**
     * Keeps [png], a texture the editor made for the player's skin [id], until a Save puts it in place (issue 786):
     * returns its name in the draft folder.
     */
    fun stageTexture(console: Console, id: String, png: ByteArray): String {
        own(console, id)
        val stage = stageDir(console, id)
        if (isLink(stage)) throw SkinStoreException("draft of $id is a link")
        if (!stage.isDirectory && !stage.mkdirs()) throw IOException("could not make ${stage.name}")
        val name = "t-" + java.util.UUID.randomUUID().toString().replace("-", "") + ".png"
        val tmp = File(stage, "$name.tmp")
        java.io.FileOutputStream(tmp).use { it.write(png); it.fd.sync() }
        if (!tmp.renameTo(File(stage, name))) {
            tmp.delete()
            throw IOException("could not stage $name")
        }
        return name
    }

    /** The staged texture [name] of the player's skin [id], or null when there is none by that name. */
    fun staged(console: Console, id: String, name: String): File? =
        if (safeName(id)) stagedFile(stageDir(console, id), name) else null

    /**
     * Forgets the draft of the player's skin [id] (saved, discarded or deleted); its staged textures too, unless
     * [keepTextures] (an undo may bring one back while the editor is open).
     */
    fun dropDraft(console: Console, id: String, keepTextures: Boolean = false) {
        if (!safeName(id)) return
        draftFile(console, id).takeIf { !isLink(it) }?.delete()
        val stage = stageDir(console, id)
        if (keepTextures) File(stage, IMAGES).takeIf { !isLink(stage) && !isLink(it) }?.delete() else deleteTree(stage)
    }

    /** Where the draft's textures wait for a Save: a hidden folder beside the skin, never listed as one. */
    private fun stageDir(console: Console, id: String) = File(root(console), ".$id$STAGE")

    /** Copies each staged texture of [images] from [stage] over its path in [dir]; a missing one fails the save. */
    private fun placeImages(dir: File, stage: File, images: SkinImageEdits) {
        if (images.values.any { it is SkinImageEdit.Staged } && isLink(stage)) throw SkinStoreException("draft is a link")
        images.forEach { (path, edit) ->
            if (edit !is SkinImageEdit.Staged) return@forEach
            val target = target(dir, path)
            val from = stagedFile(stage, edit.name) ?: throw IOException("texture ${edit.name} is gone")
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            from.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("could not write $path")
            }
        }
    }

    /** Deletes each removed image of [images] from [dir]. */
    private fun removeImages(dir: File, images: SkinImageEdits) {
        images.forEach { (path, edit) -> if (edit == SkinImageEdit.Removed) target(dir, path).delete() }
    }

    /** [path] in [dir], refused unless it is a layout's image file and no link stands in its way. */
    private fun target(dir: File, path: String): File {
        if (!imagePath(path)) throw SkinStoreException("not an image of the skin: $path")
        val folder = File(dir, path.substringBefore('/'))
        val file = File(folder, path.substringAfter('/'))
        if (isLink(folder) || isLink(file)) throw SkinStoreException("$path is a link")
        return file
    }

    private fun draftFile(console: Console, id: String) = File(root(console), ".$id$DRAFT")

    /**
     * Imports [zip], a skin in OpenEmux's own format for [console] (issue 787): as a new skin called [name], or, with
     * [replace], over the player's skin of that id, which keeps its id (so a console using it goes on using it) and
     * its name. Returns the skin's ref.
     *
     * Only skin.json and the images it names (its keypad pages' included) are taken from the zip, for the layouts whose folder the zip has
     * ([SkinImport.offered]); an image the zip lacks is the replaced skin's, if
     * it had one. The skin is built in a hidden folder, read back, and only then put in place (a replaced one moved
     * aside until the new one is in): a zip that fails at any point leaves the list as it was.
     *
     * [allLayouts] keeps every layout skin.json has, whatever folders the zip holds: for [export]'s zip, a copy of a
     * skin (a layout with no image has no folder there), not a template whose deleted folders mean something.
     */
    fun importOwn(console: Console, zip: File, name: String?, replace: String? = null, allLayouts: Boolean = false): String {
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
            val kept = if (allLayouts) pkg.skin.layouts.keys else SkinImport.offered(pkg)
            val json = JSONObject(text).put("name", finalName)
            val layoutsJson = json.getJSONObject("layouts")
            layoutsJson.keys().asSequence().toList().filter { key -> kept.none { it.id == key } }.forEach { layoutsJson.remove(it) }
            json.put("forms", JSONArray(SkinImport.forms(kept).map { it.id }))
            writeJson(tmp, json.toString(2) + "\n")
            var budget = SkinImport.MAX_BYTES
            for ((layoutId, layout) in pkg.skin.layouts.filterKeys { it in kept }) {
                val files = SkinTemplate.images(layout).map { it.path }.distinct()
                for (file in files) {
                    // Each image keeps the name it has, PNG or WebP (issue 806), the zip's or the replaced skin's.
                    val copied = pkg.image(layoutId, file)?.let { path ->
                        val out = File(tmp, path)
                        out.parentFile!!.mkdirs()
                        pkg.source.open(path)!!.use { input ->
                            out.outputStream().use { SkinImport.copyLimited(input, it, minOf(SkinImport.MAX_IMAGE, budget)) }
                        }
                    } ?: target?.let { dir ->
                        SkinFormat.imageNames(file).map { "${layoutId.id}/$it" }
                            .firstOrNull { File(dir, it).let { f -> f.isFile && !isLink(f) } }
                    }?.let { path ->
                        val out = File(tmp, path)
                        out.parentFile!!.mkdirs()
                        File(target, path).copyTo(out, overwrite = true).length()
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
                // The replaced skin's unsaved editor work no longer matches it (issue 784).
                dropDraft(console, id)
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

    /**
     * Writes the player's skin [id] to [out] as a zip to hand to another device (Share nearby): its own skin.json and
     * the images its layouts have, at the zip's root, and nothing else of its folder (a file the format does not name,
     * a draft, anything behind a link). [importOwn] with `allLayouts` reads it back as the same skin. [out] is left open.
     */
    fun export(console: Console, id: String, out: OutputStream) {
        val dir = own(console, id)
        val pkg = SkinFormat.readFolder(dir)
        val zip = ZipOutputStream(out)
        fun put(path: String) {
            val file = File(dir, path)
            // Never through a link: what it points at is no part of the skin, and would leave the device with it.
            if (generateSequence(file) { it.parentFile }.takeWhile { it != dir }.any(::isLink)) {
                if (path == SkinFormat.SKIN_JSON) throw SkinStoreException("skin.json of $id is a link")
                return
            }
            zip.putNextEntry(ZipEntry(path))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        put(SkinFormat.SKIN_JSON)
        for ((layoutId, layout) in pkg.skin.layouts) {
            SkinTemplate.images(layout).map { it.path }.distinct().mapNotNull { pkg.image(layoutId, it) }.forEach(::put)
        }
        zip.finish()
    }

    /**
     * [name], or `name (2)`, `name (3)`...: the first that none of [console]'s skins shows, so a skin received from
     * another device (Share nearby) can be told from the one already here by that name.
     */
    fun freeName(console: Console, name: String): String {
        val clean = cleanName(name)
        val taken = list(console).map { it.name.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }
            .map { n -> if (n == 1) clean else " ($n)".let { clean.take(MAX_NAME - it.length).trim() + it } }
            .first { it.lowercase() !in taken }
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

    private fun writeJson(dir: File, text: String) = writeAtomic(File(dir, SkinFormat.SKIN_JSON), text)

    /** [text] written beside [target], synced, then renamed over it: never a half-written [target]. */
    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        writeSynced(tmp, text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("could not write ${target.path}")
        }
    }

    companion object {
        private const val OWN_PREFIX = "own:"
        private const val PKG_PREFIX = "pkg:"
        private const val MAX_ID = 40
        private const val DRAFT = ".draft.json"
        private const val STAGE = ".draft"
        private const val IMAGES = "images.json"
        private const val STAGED = "staged:"
        private const val REMOVED = "removed"
        private val STAGED_NAME = Regex("t-[0-9a-f]{32}\\.png")
        private val IMAGE_FILE = Regex("[a-z0-9]+(-[a-z0-9]+)*\\.png")

        /** Whether [path] is `<layout>/<file>.png`: a layout's folder and a plain image name, nothing more. */
        internal fun imagePath(path: String): Boolean {
            val parts = path.split('/')
            return parts.size == 2 && SkinLayoutId.byId(parts[0]) != null && IMAGE_FILE.matches(parts[1])
        }

        /** The staged texture [name] in [stage]: a name the store gives, a real file, no link. */
        internal fun stagedFile(stage: File, name: String): File? =
            File(stage, name).takeIf { STAGED_NAME.matches(name) && !isLink(stage) && !isLink(it) && it.isFile }
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

        fun isPackage(file: File) = file.isFile && isPackageName(file.name)

        /** Whether [name] is a Delta or Manic EMU package's file name (.deltaskin, .manicskin). */
        fun isPackageName(name: String) = name.endsWith(".deltaskin", true) || name.endsWith(".manicskin", true)

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

        /**
         * Writes [text] to [file] and waits for it to reach the disk, so the rename that follows never puts an empty
         * skin.json in place when the device loses power or the app is killed right after a save.
         */
        internal fun writeSynced(file: File, text: String) {
            java.io.FileOutputStream(file).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
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
