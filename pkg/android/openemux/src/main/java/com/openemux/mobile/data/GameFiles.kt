package com.openemux.mobile.data

import android.util.Log
import com.openemux.mobile.share.Units
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Why a picked file is not taken as a game's save, before anything is asked (issue 1005). */
enum class SaveRefusal {
    /** An empty file. */
    EMPTY,
    /** Larger than any console's battery save ([GameFiles.MAX_IMPORT_BYTES]). */
    TOO_BIG,
    /** A save state, a ROM, a disc sheet, an archive or an image: never a battery save. */
    NOT_A_SAVE,
}

/**
 * What importing a file named [name] as a game's save would do: become [target] in the console's saves folder,
 * replacing a save the game has when [replaces]. [refusal] stops it outright; the warnings only ask first.
 * [expected] is the size the game's own save has, else [range] the sizes the console's saves come in.
 */
data class SaveImportCheck(
    val name: String,
    val size: Long,
    val target: String,
    val replaces: Boolean,
    val refusal: SaveRefusal? = null,
    val expected: Long? = null,
    val range: LongRange? = null,
    val unusualExtension: Boolean = false,
) {
    /** The size is known and is not what this game's (or console's) saves are. */
    val sizeMismatch: Boolean
        get() = size > 0 && (expected?.let { size != it } ?: range?.let { size !in it } ?: false)
}

/** How an import or a restore ended. */
enum class SaveChange { DONE, FAILED, TOO_BIG }

/** A game's file for export: one file as it is, or several (a disc game, a save with its companions) in a zip. */
data class FilesExport(val name: String, val files: List<File>, val bytes: Long) {
    val zipped: Boolean get() = files.size > 1
}

/** The save an import replaced (issue 1005), kept for "Restore previous save": its files and when it was set aside. */
data class KeptSave(val files: List<File>, val keptAt: Long)

/**
 * A game's files in and out of the library (issue 1005): its ROM and its battery saves exported, and a battery
 * save brought in from a file, another emulator's included. A save is never lost to an import: the game's save
 * files are copied aside first ([Paths.saveBackup], one per game), and [restore] swaps them back, which can
 * itself be undone the same way.
 *
 * Battery saves are what RetroArch writes to `savefile_directory`, `<stem>.<suffix>` beside the game's other
 * saves, with the same rule [RomRenamer] and Share nearby use ([Units.isBatterySave]). Save states are not.
 */
class GameFiles(
    private val paths: Paths,
    private val move: (File, File) -> Boolean = File::renameTo,
) {
    /** What exporting [rom] writes: its file, or a `<stem>.zip` of a disc game's parts; null when a part is missing. */
    fun romExport(rom: Rom): FilesExport? {
        val unit = Units.collectUnit(rom.file) ?: return null
        val files = unit.parts.map { it.first }
        val name = if (files.size > 1) "${rom.baseName}.zip" else rom.file.name
        return FilesExport(name, files, files.sumOf { it.length() })
    }

    /** The game's battery saves, by name: `<stem>.<suffix>` only, so "Foo.Bros.srm" stays another game's. */
    fun saves(rom: Rom): List<File> {
        val stem = rom.baseName
        return paths.saves(rom.console).listFiles().orEmpty().filter { file ->
            val name = file.name
            val rest = if (name.startsWith(stem)) name.substring(stem.length) else null
            file.isFile && rest != null && rest.startsWith('.') && rest.count { it == '.' } == 1 &&
                Units.isBatterySave(name, stem, rom.console)
        }.sortedBy { it.name }
    }

    /** What exporting [rom]'s save writes: the save itself, or `<stem> save.zip` with its companions; null with none. */
    fun saveExport(rom: Rom): FilesExport? {
        val files = saves(rom).ifEmpty { return null }
        val name = if (files.size > 1) "${rom.baseName} save.zip" else files[0].name
        return FilesExport(name, files, files.sumOf { it.length() })
    }

    /**
     * Writes [export] to [out]: the file's bytes, or a zip of its files by name. [progress] gets the bytes
     * written so far. The caller closes [out].
     */
    fun write(export: FilesExport, out: OutputStream, progress: (Long) -> Unit = {}) {
        var done = 0L
        val count: (Int) -> Unit = { n -> done += n; progress(done) }
        if (!export.zipped) {
            export.files[0].inputStream().use { copy(it, out, count) }
            return
        }
        val zip = ZipOutputStream(out)
        // Disc tracks are large and barely compress: speed over size.
        zip.setLevel(Deflater.BEST_SPEED)
        for (file in export.files) {
            zip.putNextEntry(ZipEntry(file.name).apply { time = file.lastModified() })
            file.inputStream().use { copy(it, zip, count) }
            zip.closeEntry()
        }
        zip.finish()
    }

    /**
     * The save file [rom] loads with [core]: `<stem>.<suffix>` with the core's suffix (the game's own file when it
     * has one, whatever its case). Never a save another core left: an import there would never be loaded.
     */
    fun target(rom: Rom, core: CoreDef): File {
        val suffix = defaultSuffix(core)
        return saves(rom).firstOrNull { it.extension.equals(suffix, ignoreCase = true) }
            ?: File(paths.saves(rom.console), "${rom.baseName}.$suffix")
    }

    /** What importing a file named [name] of [size] bytes (negative: unknown) as [rom]'s save would do. */
    fun inspect(rom: Rom, core: CoreDef, name: String, size: Long): SaveImportCheck {
        val target = target(rom, core)
        val replaces = target.isFile
        val ext = name.substringAfterLast('.', "").lowercase()
        val refusal = when {
            size == 0L -> SaveRefusal.EMPTY
            size > MAX_IMPORT_BYTES -> SaveRefusal.TOO_BIG
            isNotASave(name, ext, rom.console) -> SaveRefusal.NOT_A_SAVE
            else -> null
        }
        return SaveImportCheck(
            name, size, target.name, replaces, refusal,
            expected = target.takeIf { replaces }?.length()?.takeIf { it > 0 },
            range = SIZE_RANGES[rom.console],
            unusualExtension = ext !in KNOWN_SAVE_SUFFIXES,
        )
    }

    /**
     * Installs [input] as [rom]'s save for [core]: written beside the target under a temporary name, the game's
     * save files copied aside (the save kept before, if any, is replaced), then moved onto the target in one
     * step. Anything failing before that move leaves the game's save as it was.
     */
    fun import(rom: Rom, core: CoreDef, input: InputStream): SaveChange {
        val dir = paths.saves(rom.console)
        val target = target(rom, core)
        val tmp = File(dir, TMP_IMPORT)
        try {
            dir.mkdirs()
            tmp.outputStream().use { out ->
                var total = 0L
                copy(input, out) { n ->
                    total += n
                    if (total > MAX_IMPORT_BYTES) throw TooBig()
                }
                if (total == 0L) throw IOException("empty file")
            }
            val current = saves(rom)
            if (current.isNotEmpty()) keep(rom, current)
            if (!move(tmp, target)) throw IOException("could not move the save into place")
            target.setLastModified(System.currentTimeMillis())
            return SaveChange.DONE
        } catch (_: TooBig) {
            return SaveChange.TOO_BIG
        } catch (e: IOException) {
            Log.w(TAG, "import of a save for ${rom.key} failed", e)
            return SaveChange.FAILED
        } finally {
            tmp.delete()
        }
    }

    /** The save the last import replaced, still kept; null when none is. */
    fun kept(rom: Rom): KeptSave? {
        val dir = paths.saveBackup(rom.console, rom.baseName)
        val files = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.ifEmpty { return null }
        return KeptSave(files, dir.lastModified())
    }

    /**
     * Puts the kept save back in place, and keeps the one in use instead, so restoring again undoes it.
     * Save files the kept save does not have are set aside too: what was there before the import is what
     * the game finds.
     */
    fun restore(rom: Rom): SaveChange {
        val kept = kept(rom) ?: return SaveChange.FAILED
        val current = saves(rom)
        val dir = paths.saves(rom.console)
        // The save in use, set aside first: nothing below may lose it.
        val swap = try {
            stage(rom, current)
        } catch (e: IOException) {
            Log.w(TAG, "the save of ${rom.key} could not be set aside", e)
            return SaveChange.FAILED
        }
        try {
            kept.files.forEach { put(it, dir) }
            val names = kept.files.map { it.name }.toSet()
            current.filter { it.name !in names }.forEach { it.delete() }
            replaceKept(rom, swap)
            return SaveChange.DONE
        } catch (e: IOException) {
            Log.w(TAG, "restore of the save of ${rom.key} failed, putting the one in use back", e)
            // What was moved in is undone from the copy set aside; the copy stays if even that fails.
            val back = swap.listFiles().orEmpty().all { runCatching { put(it, dir) }.isSuccess }
            // A file only the kept save had was put in, not put back: it goes, the kept save still has it.
            val inUse = swap.list().orEmpty().toSet()
            kept.files.filter { it.name !in inUse }.forEach { File(dir, it.name).delete() }
            if (back) swap.deleteRecursively()
            return SaveChange.FAILED
        }
    }

    /** Copies [file] into [dir] under its own name, replacing what is there in one step. */
    private fun put(file: File, dir: File) {
        val tmp = File(dir, TMP_IMPORT)
        try {
            file.copyTo(tmp, overwrite = true)
            tmp.setLastModified(file.lastModified())
            if (!move(tmp, File(dir, file.name))) throw IOException("could not put ${file.name} in place")
        } finally {
            tmp.delete()
        }
    }

    /**
     * A rename (issue 790) takes the kept save along, its files renamed like the game's own saves, so a restore
     * puts them back under the name the core loads; nothing is lost when it cannot.
     */
    fun renamed(console: Console, oldStem: String, newStem: String) {
        val from = paths.saveBackup(console, oldStem)
        if (!from.isDirectory) return
        val to = paths.saveBackup(console, newStem)
        if (to.exists() || !move(from, to)) {
            Log.w(TAG, "kept save of $oldStem not carried to $newStem")
            return
        }
        for (file in to.listFiles().orEmpty()) {
            if (!file.name.startsWith("$oldStem.")) continue
            val renamed = File(to, newStem + file.name.substring(oldStem.length))
            if (renamed.exists() || !move(file, renamed)) Log.w(TAG, "kept ${file.name} not renamed for $newStem")
        }
    }

    /** Copies [files] aside as the game's kept save, replacing the one kept before. */
    private fun keep(rom: Rom, files: List<File>) = replaceKept(rom, stage(rom, files))

    /** A copy of [files] in a staging folder beside the kept saves, dated now. */
    private fun stage(rom: Rom, files: List<File>): File {
        val staging = File(paths.saveBackup(rom.console, rom.baseName).parentFile, ".${rom.baseName}$STAGING")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("could not create ${staging.name}")
        for (file in files) {
            file.copyTo(File(staging, file.name), overwrite = true)
            File(staging, file.name).setLastModified(file.lastModified())
        }
        staging.setLastModified(System.currentTimeMillis())
        return staging
    }

    /** [staging] becomes the kept save; an empty one just clears it. */
    private fun replaceKept(rom: Rom, staging: File) {
        val dir = paths.saveBackup(rom.console, rom.baseName)
        val stamp = staging.lastModified()
        if (staging.list().isNullOrEmpty()) {
            staging.delete()
            dir.deleteRecursively()
            return
        }
        // The save kept before goes only once the new one is in: a move that fails leaves it where it was.
        val old = File(dir.parentFile, ".${rom.baseName}$OLD")
        old.deleteRecursively()
        if (dir.exists() && !move(dir, old)) throw IOException("could not set the kept save aside")
        if (!move(staging, dir)) {
            if (old.exists() && !move(old, dir)) Log.w(TAG, "the kept save of ${rom.key} stays in ${old.name}")
            throw IOException("could not keep the save aside")
        }
        old.deleteRecursively()
        dir.setLastModified(stamp)
    }

    private class TooBig : IOException()

    companion object {
        private const val TAG = "OpenEmuxGameFiles"

        /** Larger than any battery save a console here writes (a DS cartridge's flash is 8 MB). */
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

        /** The temporary name an imported or restored save is written under; a dotfile, so no scan lists it. */
        internal const val TMP_IMPORT = ".openemux-import"
        private const val STAGING = ".staging"
        private const val OLD = ".old"

        /** The extensions emulators give battery saves and memory cards; another one only asks first. */
        val KNOWN_SAVE_SUFFIXES = setOf("srm", "sav", "dsv", "eep", "fla", "sra", "mpk", "mcr", "mcd", "mc", "brm", "bkr", "ram", "sa1")

        /** Archives and disc sheets are never a save: a zip is not opened here. */
        private val NEVER_SAVES = setOf("zip", "7z", "rar", "cue", "m3u")
        private val STATE_NAME = Regex("""\.(state(\d+|\.auto)?|slot)$""", RegexOption.IGNORE_CASE)

        /**
         * Consoles whose cores keep saves under their own names or folders rather than the game's: the GameCube's
         * memory cards, the PSP's SAVEDATA and the Saturn's backup RAM. Their saves are exported when the game has
         * them, never imported.
         */
        private val NO_IMPORT = setOf(Console.GC, Console.PSP, Console.SATURN)

        fun importSupported(console: Console): Boolean = console !in NO_IMPORT

        /** The suffix [core] gives the save it loads: RetroArch's `.srm`, except the DS cores, which write their own. */
        fun defaultSuffix(core: CoreDef): String = when (core.id) {
            "desmume" -> "dsv"
            "melonds" -> "sav"
            else -> "srm"
        }

        /**
         * The sizes a console's battery saves come in, from the smallest EEPROM to the largest flash (and a
         * cartridge clock appended by some cores); a file outside asks first. Consoles left out are not checked.
         */
        val SIZE_RANGES: Map<Console, LongRange> = mapOf(
            Console.GB to 512L..131_120L,
            Console.GBC to 512L..131_120L,
            Console.GBA to 512L..139_264L,
            Console.SFC to 512L..262_144L,
            Console.FC to 128L..65_536L,
            Console.N64 to 512L..296_960L,
            Console.NDS to 512L..8_389_632L,
            // A memory card, raw: 128 KB exactly. A .gme or .vgs carries a header and asks first.
            Console.PS to 131_072L..131_072L,
            Console.MD to 64L..65_536L,
            Console.SMS to 64L..65_536L,
            Console.GG to 64L..65_536L,
            Console.SG1000 to 64L..65_536L,
            Console.S32X to 64L..65_536L,
            Console.PCE to 512L..65_536L,
        )

        private fun isNotASave(name: String, ext: String, console: Console): Boolean =
            STATE_NAME.containsMatchIn(name) || ext in console.extensions || ext in Units.ART_SUFFIXES || ext in NEVER_SAVES

        /**
         * Whether the engine's `GET_STATUS` [reply] says [stem]'s game is up, playing or paused. The reply is
         * `GET_STATUS <PLAYING|PAUSED> <system>,<content>`; a running reply whose content cannot be read counts as
         * this game, and so does no reply while the game's process is alive ([gameProcess]): a doubt never lets a
         * save be swapped under a running core.
         */
        fun runningGame(reply: String?, stem: String, gameProcess: Boolean = false): Boolean {
            val parts = reply?.trim()?.split(' ', limit = 3) ?: return gameProcess
            if (parts.size < 2 || parts[0] != "GET_STATUS" || parts[1] !in setOf("PLAYING", "PAUSED")) return false
            val content = parts.getOrNull(2)?.substringAfter(',', "")?.trim().orEmpty()
            if (content.isEmpty() || content == "UNKNOWN") return true
            return content.equals(stem, ignoreCase = true) || content.substringBeforeLast('.').equals(stem, ignoreCase = true)
        }

        private fun copy(input: InputStream, out: OutputStream, count: (Int) -> Unit) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                count(n)
            }
        }
    }
}
