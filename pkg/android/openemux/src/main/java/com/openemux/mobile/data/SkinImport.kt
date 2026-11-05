package com.openemux.mobile.data

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/** Why "Import .zip" turned a file down (issue 787). */
enum class ImportRefusal {
    /** Not a zip, or a zip with neither a skin.json nor a package's info.json. */
    NOT_A_SKIN,

    /** A skin.json or an info.json the app cannot read; [ImportCheck.Refused.detail] says what. */
    BROKEN,

    /** A skin made for another console; [ImportCheck.Refused.console] says which. */
    OTHER_CONSOLE,

    /** Larger than [SkinImport.MAX_BYTES], or more entries than [SkinImport.MAX_ENTRIES]. */
    TOO_LARGE,

    /** An entry whose name would leave the skin's folder (`/…`, `..`, a backslash). */
    UNSAFE,
}

/** What a picked file is, as the import sheet shows it before anything is written. */
sealed interface ImportCheck {
    /**
     * A skin in OpenEmux's own format for this console: imported as a new skin or over one of the player's, offering
     * the [forms] of the layouts it keeps ([SkinImport.offered]).
     */
    data class Own(val skin: OpenEmuxSkin, val forms: Set<SkinForm>) : ImportCheck

    /** A Delta or Manic EMU package: imported as it is, under Imported, as a file ending in [extension]. */
    data class Package(val name: String, val forms: Set<SkinForm>, val extension: String) : ImportCheck

    data class Refused(val reason: ImportRefusal, val console: Console? = null, val detail: String? = null) : ImportCheck
}

/** A file over one of [SkinImport]'s limits. */
class SkinImportTooLarge(message: String) : IOException(message)

/**
 * "Import .zip" (issue 787): tells what a picked file is ([inspect]); [SkinStore.importOwn] and
 * [SkinStore.importPackage] write it. Every byte read from a zip is counted, so a zip that claims small entries and
 * inflates to gigabytes stops at the limit.
 */
object SkinImport {
    /** The largest file, and the most a skin's files may add up to once read. */
    const val MAX_BYTES = 128L shl 20

    /** The largest single image. */
    const val MAX_IMAGE = 16L shl 20

    const val MAX_ENTRIES = 2000

    const val DELTA = "deltaskin"
    const val MANIC = "manicskin"

    private const val DELTA_PREFIX = "com.rileytestut.delta.game."

    /** The consoles a Delta package's game type is for. */
    private val DELTA_GAMES = mapOf(
        "nes" to setOf(Console.FC), "snes" to setOf(Console.SFC), "gbc" to setOf(Console.GB, Console.GBC),
        "gba" to setOf(Console.GBA), "n64" to setOf(Console.N64), "ds" to setOf(Console.NDS), "genesis" to setOf(Console.MD),
    )

    /**
     * What [file] (a copy of the picked document; [pickedName] its name as the picker gave it) is for the skin list of
     * [console].
     */
    fun inspect(file: File, console: Console, pickedName: String?): ImportCheck {
        if (file.length() > MAX_BYTES) return ImportCheck.Refused(ImportRefusal.TOO_LARGE)
        val names = try {
            ZipFile(file).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList() }
        } catch (e: IOException) {
            return ImportCheck.Refused(ImportRefusal.NOT_A_SKIN)
        }
        if (names.size > MAX_ENTRIES) return ImportCheck.Refused(ImportRefusal.TOO_LARGE)
        if (names.any { unsafe(it) }) return ImportCheck.Refused(ImportRefusal.UNSAFE)
        val pkg = try {
            SkinFormat.readZip(file)
        } catch (e: SkinFormatException) {
            if (e.error != SkinError.NO_SKIN_JSON) return ImportCheck.Refused(ImportRefusal.BROKEN, detail = e.error.name)
            null
        }
        if (pkg != null) {
            val skin = pkg.skin
            if (skin.console != console) return ImportCheck.Refused(ImportRefusal.OTHER_CONSOLE, skin.console)
            val kept = offered(pkg)
            return ImportCheck.Own(skin, SkinForm.entries.filter { f -> kept.any { l -> l.form == f } }.toSet())
        }
        if (names.none { isInfo(it) }) return ImportCheck.Refused(ImportRefusal.NOT_A_SKIN)
        val info = try {
            SkinStore.packageInfo(file)
        } catch (e: Exception) {
            return ImportCheck.Refused(ImportRefusal.BROKEN, detail = "info.json")
        }
        val consoles = deltaConsoles(info)
        if (consoles.isNotEmpty() && console !in consoles) return ImportCheck.Refused(ImportRefusal.OTHER_CONSOLE, consoles.first())
        val picked = pickedName?.substringAfterLast('/')?.trim().orEmpty()
        val ext = picked.substringAfterLast('.', "").lowercase()
        val name = info.optString("name").trim().ifEmpty { picked.substringBeforeLast('.').trim() }.ifEmpty { "Skin" }
        // The renderer reads both alike: the name only keeps what the file was.
        val extension = if (ext == MANIC) MANIC else DELTA
        return ImportCheck.Package(name.take(SkinStore.MAX_NAME), SkinStore.packageForms(info), extension)
    }

    /**
     * The layouts an imported skin keeps: the ones whose folder is in the zip, so that deleting a layout's folder from a
     * template stops the skin offering that screen (issue 666). A zip with no layout folder at all is a skin drawn
     * from its colours alone, and keeps every layout its skin.json has.
     */
    fun offered(pkg: SkinPackage): Set<SkinLayoutId> {
        val present = pkg.skin.layouts.keys.filter { id -> pkg.source.files.any { file -> file.startsWith("${id.id}/") } }.toSet()
        return present.ifEmpty { pkg.skin.layouts.keys }
    }

    /** The consoles a Delta package's `gameTypeIdentifier` names; empty for one that names none the app knows. */
    fun deltaConsoles(info: JSONObject): Set<Console> {
        val id = info.optString("gameTypeIdentifier")
        if (!id.startsWith(DELTA_PREFIX)) return emptySet()
        return DELTA_GAMES[id.removePrefix(DELTA_PREFIX)].orEmpty()
    }

    /** Whether a zip entry's name would leave the skin: [SkinFormat.safePath] without its `__MACOSX/` leniency. */
    fun unsafe(name: String) = name.startsWith("/") || '\\' in name || name.split('/').any { it == ".." }

    private fun isInfo(name: String) = name.substringAfterLast('/') == "info.json" && !name.startsWith("__MACOSX/")

    /** Throws [SkinImportTooLarge] (or [SkinStoreException] for an unsafe name) unless [zip] is one [inspect] can take. */
    fun guard(zip: File) {
        if (zip.length() > MAX_BYTES) throw SkinImportTooLarge("${zip.length()} bytes")
        val names = ZipFile(zip).use { z -> z.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList() }
        if (names.size > MAX_ENTRIES) throw SkinImportTooLarge("${names.size} entries")
        names.firstOrNull { unsafe(it) }?.let { throw SkinStoreException("unsafe entry $it") }
    }

    /**
     * Copies [input] to [output], at most [limit] bytes: one more throws [SkinImportTooLarge]. Returns how many were
     * copied.
     */
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (total > limit) throw SkinImportTooLarge("over $limit bytes")
            output.write(buf, 0, n)
        }
    }
}
