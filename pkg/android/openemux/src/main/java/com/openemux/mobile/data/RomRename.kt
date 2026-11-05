package com.openemux.mobile.data

import android.util.Log
import com.openemux.mobile.share.Units
import java.io.File
import java.io.IOException

/** Why a new name cannot be used, or [OK]; the sheet shows each as it is typed (issue 790). */
enum class RenameCheck {
    OK, UNCHANGED, EMPTY, BAD_CHARS, LEADING_DOT, TOO_LONG, CLASH, LEFTOVERS,
    /** Another game of the console has the same base name, so its states, saves and art are this one's too. */
    SHARED,
    /** An artwork sync is running: it could write art under the old name mid-rename. */
    BUSY,
}

sealed interface RenameResult {
    data class Done(val rom: Rom) : RenameResult
    data class Refused(val check: RenameCheck) : RenameResult
    /** A file could not be moved: every move made so far was undone. */
    data object Failed : RenameResult
}

/**
 * Renames a game's file and carries everything the app keys on its name along (issue 790), as the
 * desktop's `rename_rom` does: save states with their thumbnails (the auto state included), battery
 * saves, playtime, covers and labels with their records, and the per-game metadata. Every file moves
 * before anything else changes, and a move that fails undoes the ones before it, so a failed rename
 * leaves the game exactly as it was.
 *
 * No archive entry is renamed: the app extracts a zip on import and no console lists archives, so a
 * library game is never one. A `.cue`/`.m3u` keeps its tracks' names; only the sheet file is the game.
 */
class RomRenamer(
    private val paths: Paths,
    private val library: Library,
    private val covers: CoverOps,
    private val labels: LabelOps,
    private val move: (File, File) -> Boolean = File::renameTo,
) {
    /** What [input] would be as a base name: the extension is the game's own, never typed. */
    fun stemOf(input: String): String = input.trim()

    fun check(rom: Rom, input: String): RenameCheck {
        val stem = stemOf(input)
        if (stem == rom.baseName) return RenameCheck.UNCHANGED
        if (stem.isEmpty()) return RenameCheck.EMPTY
        if (sharer(rom) != null) return RenameCheck.SHARED
        if (stem.any { it in FORBIDDEN || it < ' ' || it == '\u007f' }) return RenameCheck.BAD_CHARS
        if (stem.startsWith('.')) return RenameCheck.LEADING_DOT
        val name = nameFor(rom, stem)
        // The longest name a game's files take is its auto state's thumbnail, "<stem>.state.auto.png".
        if (maxOf(name.toByteArray().size, stem.toByteArray().size + LONGEST_SUFFIX) > MAX_NAME_BYTES) return RenameCheck.TOO_LONG
        // The shared storage ignores case: "mario.sfc" and "Mario.sfc" are one file there.
        val others = rom.file.parentFile?.listFiles().orEmpty().filter { it.name != rom.file.name }
        if (others.any { it.name.equals(name, ignoreCase = true) }) return RenameCheck.CLASH
        if (library.roms.value.any { it.console == rom.console && it.key != rom.key && it.file.name.equals(name, ignoreCase = true) }) {
            return RenameCheck.CLASH
        }
        val own = filesOf(rom, rom.baseName, ignoreCase = false).toSet()
        if (filesOf(rom, stem, ignoreCase = true).any { it !in own }) return RenameCheck.LEFTOVERS
        if (covers.syncing || labels.syncing) return RenameCheck.BUSY
        return RenameCheck.OK
    }

    /**
     * Another listed game of [rom]'s console with the same base name ("Foo.sfc" beside "Foo.smc"):
     * the engine keys states and saves on the base name, so the two share them, and moving them
     * with one game would take them from the other. Its title, for the message; null when none.
     */
    fun sharer(rom: Rom): Rom? =
        library.roms.value.firstOrNull { it.console == rom.console && it.key != rom.key && it.baseName.equals(rom.baseName, ignoreCase = true) }

    fun rename(rom: Rom, input: String): RenameResult {
        val check = check(rom, input)
        if (check != RenameCheck.OK) return RenameResult.Refused(check)
        // A shader picked in game for the old key must land before the key changes.
        library.absorbShaderPicks()
        val stem = stemOf(input)
        val name = nameFor(rom, stem)
        val renamed = Rom("${rom.console.id}/$name", rom.console, File(rom.file.parentFile, name), titleFromFileName(name))
        // A cover the mirror serves follows the name: when the new one would match another title, keep the one shown.
        val shown = if (covers.localCover(rom) == null) covers.mirrorUrl(rom) else null
        val keepCover = shown != null && covers.mirrorUrl(renamed) != shown
        val moves = listOf(rom.file to renamed.file) + filesOf(rom, rom.baseName, ignoreCase = false)
            .filter { it != rom.file }
            .map { it to targetOf(rom, it, stem) }
        val done = mutableListOf<Pair<File, File>>()
        try {
            for ((from, to) in moves) {
                if (!moveSafely(from, to)) throw IOException("could not move ${from.name}")
                done += from to to
            }
            library.renamed(rom, renamed)
        } catch (e: Exception) {
            Log.w(TAG, "rename of ${rom.key} failed, undoing it", e)
            done.asReversed().forEach { (from, to) -> if (!moveSafely(to, from)) Log.w(TAG, "could not move ${to.name} back") }
            if (done.size == moves.size) runCatching { library.renamed(renamed, rom) }
            return RenameResult.Failed
        }
        // The art records are provenance only: they follow the files, and nothing here may undo the rename.
        runCatching {
            ArtSources(paths.covers(rom.console)).rename(rom.baseName, stem)
            ArtSources(paths.labels(rom.console)).rename(rom.baseName, stem)
            if (keepCover) keepMirrorCover(renamed, shown!!)
        }.onFailure { Log.w(TAG, "art records of ${renamed.key} not carried", it) }
        covers.forgetMatch(rom)
        labels.changed(rom, null)
        labels.labelFile(renamed).takeIf { it.isFile }?.let { labels.changed(renamed, it) }
        return RenameResult.Done(renamed)
    }

    private fun keepMirrorCover(rom: Rom, url: String) {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it in Covers.COVER_SUFFIXES } ?: "png"
        val target = File(paths.covers(rom.console), "${rom.baseName}.$ext")
        target.parentFile?.mkdirs()
        if (!covers.keepCachedCover(url, target)) return
        ArtSources(paths.covers(rom.console)).record(rom.baseName, target, ArtSource.SYNCED, Covers.providerOf(url))
    }

    private fun nameFor(rom: Rom, stem: String): String =
        if (rom.file.extension.isEmpty()) stem else "$stem.${rom.file.extension}"

    /** Where [file] goes: the same name with [stem] in place of the old one (playtime puts the console first). */
    private fun targetOf(rom: Rom, file: File, stem: String): File =
        if (file == paths.playtime(rom.console, rom.baseName)) paths.playtime(rom.console, stem)
        else File(file.parentFile, stem + file.name.substring(rom.baseName.length))

    /**
     * Every file of [rom]'s that carries [stem] in its name: the ROM, its states and their
     * thumbnails, its battery saves, its playtime, its covers and its label (with the marker of
     * a label known to be missing). With [ignoreCase], what the case-blind storage would collide with.
     */
    internal fun filesOf(rom: Rom, stem: String, ignoreCase: Boolean): List<File> {
        val c = rom.console
        fun named(dir: File, accept: (String) -> Boolean) = dir.listFiles().orEmpty().filter { it.isFile && accept(it.name) }
        fun rest(name: String): String? = if (name.startsWith(stem, ignoreCase)) name.substring(stem.length) else null
        val romFiles = named(paths.roms(c)) { it.equals(nameFor(rom, stem), ignoreCase) }
        val states = named(paths.states(c)) { n -> rest(n)?.let { STATE_SUFFIX.matches(it) } == true }
        // "<stem>.<suffix>" only: another game's "<stem>.Bros.srm" has a longer stem of its own.
        val saves = named(paths.saves(c)) { n ->
            rest(n)?.let { r -> r.startsWith('.') && r.count { it == '.' } == 1 && Units.isBatterySave(stem + r, stem, c) } == true
        }
        val playtime = named(paths.playtime) { it.equals(paths.playtime(c, stem).name, ignoreCase) }
        val art = named(paths.covers(c)) { n -> Covers.COVER_SUFFIXES.any { n.equals("$stem.$it", ignoreCase) } } +
            named(paths.labels(c)) { n -> n.equals("$stem.png", ignoreCase) || n.equals("$stem.none", ignoreCase) }
        return romFiles + states + saves + playtime + art
    }

    /** Never over another file; a change of case only goes through a temporary name, which the case-blind storage needs. */
    private fun moveSafely(from: File, to: File): Boolean {
        if (!from.name.equals(to.name, ignoreCase = true)) return !to.exists() && move(from, to)
        // Short and fixed: "<name>.renaming" would pass the 255-byte limit for a name close to it.
        val tmp = File(from.parentFile, TMP_NAME)
        if (tmp.exists() || !move(from, tmp)) return false
        if (move(tmp, to)) return true
        move(tmp, from)
        return false
    }

    companion object {
        private const val TAG = "OpenEmuxRename"
        /** What FAT, exFAT and the other file systems a phone's storage may use refuse in a name. */
        const val FORBIDDEN = "/\\:*?\"<>|"
        const val MAX_NAME_BYTES = 255
        private const val LONGEST_SUFFIX = ".state.auto.png".length
        /** The temporary name a change of case goes through; a dotfile, so no scan lists it. */
        internal const val TMP_NAME = ".openemux-renaming"
        /** After "<stem>": ".state", ".state<N>", ".state.auto", each with its ".png" thumbnail. */
        private val STATE_SUFFIX = Regex("""\.state(\.auto|\d+)?(\.png)?""")
    }
}
