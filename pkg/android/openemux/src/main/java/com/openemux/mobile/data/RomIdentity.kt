package com.openemux.mobile.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * What ScreenScraper identifies a ROM by: CRC32 and MD5 (uppercase hex), the file name and its
 * size. For a zip, the entry with one of the console's extensions, as the desktop's `hasher.py`
 * does: the CRC from the zip's directory, the MD5 only when the entry is small enough to inflate.
 */
data class RomIdentity(val crc: String, val md5: String?, val name: String, val size: Long) {
    companion object {
        /** The desktop's ceiling for inflating a zipped ROM just to hash it. */
        const val MAX_ZIP_MD5 = 64L * 1024 * 1024

        /**
         * [active] is asked between two blocks: a large disc image takes a while, and a caller that
         * left (the manager closed) stops the read with a [java.util.concurrent.CancellationException].
         */
        fun of(file: File, extensions: Set<String>, active: () -> Boolean = { true }): RomIdentity {
            if (file.extension.equals("zip", ignoreCase = true)) zipped(file, extensions, active)?.let { return it }
            val (crc, md5) = file.inputStream().use { digests(it, active) }
            return RomIdentity(crc, md5, file.name, file.length())
        }

        private fun zipped(file: File, extensions: Set<String>, active: () -> Boolean): RomIdentity? = try {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { e ->
                    !e.isDirectory && e.name.substringAfterLast('.', "").lowercase() in extensions
                } ?: return null
                val size = entry.size
                val crc = "%08X".format(entry.crc)
                val md5 = if (size in 0..MAX_ZIP_MD5) zip.getInputStream(entry).use { digests(it, active).second } else null
                RomIdentity(crc, md5, entry.name.substringAfterLast('/'), size)
            }
        } catch (_: IOException) {
            // Not a zip after all, or a damaged one: the whole file is what there is to hash.
            null
        }

        private fun digests(input: InputStream, active: () -> Boolean): Pair<String, String> {
            val crc = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            val buf = ByteArray(64 * 1024)
            input.buffered().let { stream ->
                while (true) {
                    if (!active()) throw java.util.concurrent.CancellationException("hash abandoned")
                    val n = stream.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n)
                    md5.update(buf, 0, n)
                }
            }
            return "%08X".format(crc.value) to md5.digest().joinToString("") { "%02X".format(it) }
        }
    }
}
