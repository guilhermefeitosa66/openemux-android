package com.openemux.mobile.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The per-console BIOS import, with no Android types so plain JUnit tests it.
 * A picked file is accepted only for the console it was imported for: by its
 * name (any case, any alternative of an entry, saved under the catalog
 * spelling, which is what the cores look up), or by its MD5 when that console
 * knows one. A bad file never stops the batch: each one gets its own outcome.
 */

/** One picked file: its display name, its size (-1 when the provider does not say) and how to read it. */
class BiosSource(val name: String, val size: Long, val open: () -> InputStream?)

data class BiosImportReport(val console: Console, val imported: List<Imported>, val rejected: List<Rejected>) {
    /** What became of one picked file. */
    sealed interface Outcome

    /** A file that landed in the system folder as [savedAs]. */
    data class Imported(val source: String, val savedAs: String, val byContent: Boolean, val replaced: Boolean) : Outcome

    data class Rejected(val source: String, val reason: Reason)

    enum class Reason : Outcome { WRONG_NAME, IO, TOO_LARGE }

    val total: Int get() = imported.size + rejected.size
    val allImported: Boolean get() = rejected.isEmpty() && imported.isNotEmpty()
    val noneImported: Boolean get() = imported.isEmpty()
}

/** Every file name [console]'s entries take, lowercase, to the catalog spelling. */
fun biosNamesFor(console: Console): Map<String, String> =
    KNOWN_BIOS.filter { it.console == console }.flatMap { it.files }.associateBy { it.lowercase() }

/** The known MD5s of [console]'s entries, to the name the file is saved under. */
fun biosHashesFor(console: Console): Map<String, String> =
    KNOWN_BIOS.filter { it.console == console }.flatMap { def -> def.md5.map { it to def.files.first() } }.toMap()

/** A BIOS dump this large is a game, a disc or a video. The NDS's dsi_nand.bin (~240 MB) is the largest we know. */
const val BIOS_MAX_BYTES = 512L * 1024 * 1024

/** Only files up to this size are hashed to recognise one by its content; every known hash is far below. */
const val BIOS_HASH_CAP = 32L * 1024 * 1024

fun importBiosFiles(
    console: Console,
    sources: List<BiosSource>,
    systemDir: File,
    maxBytes: Long = BIOS_MAX_BYTES,
    hashCap: Long = BIOS_HASH_CAP,
    hashes: Map<String, String> = biosHashesFor(console),
): BiosImportReport {
    val names = biosNamesFor(console)
    val imported = mutableListOf<BiosImportReport.Imported>()
    val rejected = mutableListOf<BiosImportReport.Rejected>()
    systemDir.mkdirs()
    for (source in sources) {
        val outcome = try {
            importOne(source, names, hashes, systemDir, maxBytes, hashCap)
        } catch (e: IOException) {
            BiosImportReport.Reason.IO
        } catch (e: SecurityException) {
            BiosImportReport.Reason.IO
        }
        when (outcome) {
            is BiosImportReport.Imported -> imported += outcome
            is BiosImportReport.Reason -> rejected += BiosImportReport.Rejected(source.name, outcome)
        }
    }
    return BiosImportReport(console, imported, rejected)
}

private class TooLarge : IOException()

private fun importOne(
    source: BiosSource,
    names: Map<String, String>,
    hashes: Map<String, String>,
    systemDir: File,
    maxBytes: Long,
    hashCap: Long,
): BiosImportReport.Outcome {
    if (source.size > maxBytes) return BiosImportReport.Reason.TOO_LARGE
    var byContent = false
    val target = names[source.name.lowercase()] ?: run {
        // Only a console that knows a hash, and only a file small enough to be the one it knows.
        if (hashes.isEmpty() || source.size > hashCap) return BiosImportReport.Reason.WRONG_NAME
        val stream = source.open() ?: return BiosImportReport.Reason.IO
        val hash = stream.use { md5Capped(it, hashCap) }
        byContent = true
        hash?.let { hashes[it] } ?: return BiosImportReport.Reason.WRONG_NAME
    }
    val dest = File(systemDir, target)
    val part = File(systemDir, ".$target.part")
    try {
        val input = source.open() ?: return BiosImportReport.Reason.IO
        val hash = input.use { copyCapped(it, part, maxBytes) }
        val replaced = dest.exists() && (dest.length() != part.length() || md5Of(dest) != hash)
        if (!part.renameTo(dest)) {
            // renameTo does not replace on every filesystem: drop the old file and try again.
            dest.delete()
            if (!part.renameTo(dest)) return BiosImportReport.Reason.IO
        }
        return BiosImportReport.Imported(source.name, target, byContent, replaced)
    } catch (e: TooLarge) {
        return BiosImportReport.Reason.TOO_LARGE
    } finally {
        part.delete()
    }
}

/** Streams [input] into [out], hashing it, and gives up past [max] bytes. */
private fun copyCapped(input: InputStream, out: File, max: Long): String {
    val digest = MessageDigest.getInstance("MD5")
    var total = 0L
    out.outputStream().use { sink ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw TooLarge()
            digest.update(buf, 0, n)
            sink.write(buf, 0, n)
        }
    }
    return hex(digest.digest())
}

/** The MD5 of [input], or null when it runs past [max] bytes (the size the provider gave can be wrong). */
private fun md5Capped(input: InputStream, max: Long): String? {
    val digest = MessageDigest.getInstance("MD5")
    var total = 0L
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        digest.update(buf, 0, n)
    }
    return hex(digest.digest())
}

private fun md5Of(file: File): String = file.inputStream().use { md5Capped(it, Long.MAX_VALUE)!! }

private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
