package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinImportTooLarge
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Turns a picked or imported picture into the theme's own copy; the Android one decodes, downsizes and re-encodes. */
fun interface ThemeImageCodec {
    /** Writes [input] as a JPEG to [output]; false when [input] is not a picture it can read. */
    fun reencode(input: File, output: File): Boolean
}

/** Why a theme file was refused. */
enum class ThemeImportFailure { NOT_THEME, DAMAGED, IMAGE, TOO_LARGE }

class ThemeImportError(val reason: ThemeImportFailure, message: String) : Exception(message)

/**
 * The themes folder (issue 1018): one subfolder per theme, named by its id, holding `theme.yaml` and an `images/`
 * folder. Ids are checked before any path is built from one, so nothing a caller or a file says reaches outside it.
 */
class ThemeStore(val root: File, private val codec: ThemeImageCodec, private val log: (String) -> Unit = {}) {
    private fun folder(id: String): File {
        require(UserTheme.isCustomId(id)) { "not a theme id: $id" }
        return File(root, id)
    }

    fun imageFile(id: String, path: String): File? =
        if (UserTheme.isCustomId(id) && ThemeKeys.IMAGE_PATH.matches(path)) File(folder(id), path) else null

    private fun load(id: String): UserTheme? {
        val file = File(folder(id), THEME_FILE)
        return try {
            if (file.length() > ThemeYaml.MAX_BYTES) throw ThemeFileError("too large")
            UserTheme.parse(ThemeYaml.parse(file.readText()), id) { log("theme $id: ignoring $it") }
        } catch (e: Exception) {
            if (e !is IOException && e !is ThemeFileError && e !is ThemeYamlError) throw e
            log("skipping theme $id: ${e.message}")
            null
        }
    }

    /** Every readable theme, by name. */
    fun list(): List<UserTheme> = (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory && UserTheme.isCustomId(it.name) }
        .mapNotNull { load(it.name) }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

    fun get(id: String?): UserTheme? = if (UserTheme.isCustomId(id)) load(id!!) else null

    fun newId(name: String): String {
        val slug = UserTheme.slugify(name)
        var candidate = slug
        var n = 2
        while (candidate in UserTheme.BUILT_IN || File(root, candidate).exists()) candidate = "$slug-${n++}"
        return candidate
    }

    /** [name], or "[name] (2)", "(3)"… when another theme is already called that. */
    fun freeName(name: String, except: String? = null): String {
        val taken = list().filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = name.take(UserTheme.MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    /**
     * Writes [theme] whole: the file is replaced, never half-written. [staged] holds pictures picked in the editor,
     * by their image path; they are copied in, and images the theme no longer names are removed.
     */
    fun save(theme: UserTheme, staged: Map<String, File> = emptyMap()): UserTheme {
        val dir = folder(theme.id)
        val images = File(dir, IMAGES)
        images.mkdirs()
        for (key in ThemeKeys.IMAGE_KEYS) {
            val path = theme.values[key] as? String ?: continue
            val source = staged[path] ?: continue
            writeAtomic(File(dir, path)) { out -> source.inputStream().use { it.copyTo(out) } }
        }
        writeAtomic(File(dir, THEME_FILE)) { out -> out.write(ThemeYaml.write(UserTheme.serialize(theme)).toByteArray()) }
        val named = ThemeKeys.IMAGE_KEYS.mapNotNull { theme.values[it] as? String }.map { it.substringAfter('/') }.toSet()
        images.listFiles()?.filter { it.name !in named }?.forEach { it.delete() }
        return theme
    }

    /** A new theme from raw [values] (as a file holds them: colors as `#RRGGBB`), validated. */
    fun create(name: String, base: String, values: Map<String, Any> = emptyMap(), staged: Map<String, File> = emptyMap()): UserTheme =
        createFrom(UserTheme("new", name, base, ThemeKeys.validateAll(values)), name, staged)

    /** A new theme with [theme]'s base and (already validated) values, under [name] and a fresh id. */
    fun createFrom(theme: UserTheme, name: String, staged: Map<String, File> = emptyMap()): UserTheme {
        val clean = UserTheme.cleanName(name)
        require(clean.isNotEmpty()) { "a theme needs a name" }
        require(theme.base in ThemeBase.ALL) { "not a base: ${theme.base}" }
        return save(theme.copy(id = newId(clean), name = clean), staged)
    }

    /** A copy of [id] named [name], its pictures copied too. */
    fun duplicate(id: String, name: String): UserTheme {
        val source = get(id) ?: throw NoSuchElementException(id)
        val staged = ThemeKeys.IMAGE_KEYS.mapNotNull { source.values[it] as? String }
            .associateWith { File(folder(id), it) }.filterValues { it.isFile }
        val values = source.values.filter { (k, v) -> k !in ThemeKeys.IMAGE_KEYS || staged.containsKey(v) }
        return createFrom(source.copy(values = values), name, staged)
    }

    fun rename(id: String, name: String): UserTheme {
        val theme = get(id) ?: throw NoSuchElementException(id)
        val clean = UserTheme.cleanName(name)
        require(clean.isNotEmpty()) { "a theme needs a name" }
        return save(theme.copy(name = clean))
    }

    fun delete(id: String) {
        folder(id).deleteRecursively()
    }

    /** Re-encodes [picture] for [key]'s slot into [stagingDir]; the image path to store and the staged file, or null. */
    fun stage(key: String, picture: File, stagingDir: File, stamp: Long = System.currentTimeMillis()): Pair<String, File>? {
        require(key in ThemeKeys.IMAGE_KEYS) { "not an image key: $key" }
        stagingDir.mkdirs()
        val path = "$IMAGES/${key.removeSuffix("_image")}-${stamp.toString(36)}.jpg"
        val out = File(stagingDir, path.substringAfter('/'))
        return if (codec.reencode(picture, out)) path to out else null.also { out.delete() }
    }

    /** [id] as a theme file: `theme.yaml` and the pictures it names, nothing else (and never through a link). */
    fun export(id: String, out: OutputStream) {
        val theme = get(id) ?: throw NoSuchElementException(id)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(THEME_FILE))
            zip.write(ThemeYaml.write(UserTheme.serialize(theme)).toByteArray())
            zip.closeEntry()
            for (key in ThemeKeys.IMAGE_KEYS) {
                val path = theme.values[key] as? String ?: continue
                val file = File(folder(id), path)
                if (!file.isFile || Files.isSymbolicLink(file.toPath())) continue
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * A theme file as a new theme, never over another: a clashing name gets " (2)". Everything is checked before
     * anything is written under [root]: entry count, sizes counted by the bytes actually read, names that would leave
     * the folder, the YAML and every key, and each picture the theme names, re-encoded. [ThemeImportError] says why not.
     */
    fun import(file: File): UserTheme {
        if (file.length() > MAX_ZIP_BYTES) throw ThemeImportError(ThemeImportFailure.TOO_LARGE, "${file.length()} bytes")
        val work = Files.createTempDirectory(root.apply { mkdirs() }.toPath(), ".import").toFile()
        try {
            val entries = try {
                ZipFile(file).use { zip -> unpack(zip, work) }
            } catch (e: ZipException) {
                throw ThemeImportError(ThemeImportFailure.NOT_THEME, "not a zip: ${e.message}")
            } catch (e: SkinImportTooLarge) {
                throw ThemeImportError(ThemeImportFailure.TOO_LARGE, e.message ?: "too large")
            }
            val yaml = entries[THEME_FILE] ?: throw ThemeImportError(ThemeImportFailure.NOT_THEME, "no $THEME_FILE")
            val parsed = try {
                UserTheme.parse(ThemeYaml.parse(yaml.readText()), "import") { log("import: ignoring $it") }
            } catch (e: ThemeYamlError) {
                throw ThemeImportError(ThemeImportFailure.DAMAGED, e.message ?: "bad yaml")
            } catch (e: ThemeFileError) {
                throw ThemeImportError(ThemeImportFailure.DAMAGED, e.message ?: "bad theme")
            }
            val staged = LinkedHashMap<String, File>()
            val values = parsed.values.toMutableMap()
            for (key in ThemeKeys.IMAGE_KEYS) {
                val path = values[key] as? String ?: continue
                val picture = entries[path] ?: throw ThemeImportError(ThemeImportFailure.IMAGE, "missing $path")
                val (newPath, out) = stage(key, picture, File(work, ".staged"))
                    ?: throw ThemeImportError(ThemeImportFailure.IMAGE, "unreadable $path")
                values[key] = newPath
                staged[newPath] = out
            }
            return createFrom(parsed.copy(values = values), freeName(parsed.name), staged)
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * The entries a theme can use, written under [work] by their path in the theme (a single top folder and
     * `__MACOSX/` tolerated): `theme.yaml` and `images/<name>` only, the rest skipped unread.
     */
    private fun unpack(zip: ZipFile, work: File): Map<String, File> {
        val all = zip.entries().asSequence().filter { !it.isDirectory }.toList()
        if (all.size > MAX_ENTRIES) throw ThemeImportError(ThemeImportFailure.TOO_LARGE, "${all.size} entries")
        all.firstOrNull { SkinImport.unsafe(it.name) }?.let { throw ThemeImportError(ThemeImportFailure.DAMAGED, "unsafe entry ${it.name}") }
        val named = all.filter { !it.name.startsWith("__MACOSX/") }
        val top = named.map { it.name.substringBefore('/', "") }.distinct()
        val prefix = if (named.none { it.name == THEME_FILE } && top.size == 1 && top[0].isNotEmpty()) top[0] + "/" else ""
        val out = HashMap<String, File>()
        var budget = MAX_TOTAL_BYTES
        for (entry in named) {
            val path = entry.name.removePrefix(prefix)
            val limit = when {
                path == THEME_FILE -> ThemeYaml.MAX_BYTES.toLong()
                ThemeKeys.IMAGE_PATH.matches(path) -> MAX_IMAGE_BYTES
                else -> continue
            }
            val target = File(work, path.replace('/', '_'))
            val written = zip.getInputStream(entry).use { input ->
                FileOutputStream(target).use { SkinImport.copyLimited(input, it, minOf(limit, budget)) }
            }
            budget -= written
            out[path] = target
        }
        return out
    }

    private fun writeAtomic(target: File, write: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                write(out)
                out.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("cannot replace $target")
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    companion object {
        const val THEME_FILE = "theme.yaml"
        const val IMAGES = "images"
        const val EXTENSION = "openemux-theme"
        const val MAX_ENTRIES = 32
        const val MAX_IMAGE_BYTES = 16L shl 20
        const val MAX_TOTAL_BYTES = 48L shl 20
        const val MAX_ZIP_BYTES = 48L shl 20
    }
}
