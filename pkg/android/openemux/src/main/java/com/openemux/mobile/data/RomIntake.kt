package com.openemux.mobile.data

import java.io.File
import java.io.InputStream

/**
 * Which files the app takes in, and how one lands in a console's folder: the
 * rules shared by the in-app import, "Open with"/"Share" (ImportActivity) and
 * the manifest's intent filters, all derived from [Console].
 */
object RomIntake {
    /**
     * Extensions common outside emulation, left out of the manifest's name
     * filter so OpenEmux is never offered for a README.md or any .bin: they
     * still arrive through the MIME filter (octet-stream, zip, ISO types).
     */
    val GENERIC = setOf("bin", "iso", "zip", "rom", "md")

    /** How many dots a name may carry before its extension and still match: pathPattern does not backtrack. */
    const val PATH_DEPTH = 4

    /** Every extension the import takes: each console's, plus .zip. */
    fun importableExtensions(): Set<String> = Console.entries.flatMap { it.extensions }.toSet() + "zip"

    /** The extensions matched by name in the manifest, whatever MIME type the file manager sends. */
    fun pathFilterExtensions(): Set<String> = importableExtensions() - GENERIC

    /** `.*\\.nes`, `.*\\..*\\.nes`, …: one per dot depth, as the manifest writes them (XML-escaped backslash). */
    fun pathPatterns(ext: String): List<String> = (0..PATH_DEPTH).map { depth -> ".*\\\\.".repeat(depth + 1) + ext }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isImportable(name: String): Boolean = extensionOf(name) in importableExtensions()

    enum class Placed { COPIED, DUPLICATE }

    /**
     * Copies [input] to [name] in [dir], through a hidden `.part` file so a
     * broken copy never shows up as a game. A file of that name already there
     * is the same game: it is kept as it is, never overwritten.
     */
    fun place(dir: File, name: String, input: InputStream): Placed {
        dir.mkdirs()
        val target = File(dir, name.replace('/', '_'))
        if (target.exists()) return Placed.DUPLICATE
        val tmp = File(dir, ".${target.name}.part")
        try {
            tmp.outputStream().use { input.copyTo(it) }
            if (!tmp.renameTo(target)) throw java.io.IOException("cannot rename ${tmp.name}")
        } finally {
            tmp.delete()
        }
        return Placed.COPIED
    }
}
