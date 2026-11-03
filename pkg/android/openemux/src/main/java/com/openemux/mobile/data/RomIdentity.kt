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

        fun of(file: File, extensions: Set<String>): RomIdentity {
            if (file.extension.equals("zip", ignoreCase = true)) zipped(file, extensions)?.let { return it }
            val (crc, md5) = file.inputStream().use { digests(it) }
            return RomIdentity(crc, md5, file.name, file.length())
        }

        private fun zipped(file: File, extensions: Set<String>): RomIdentity? = try {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { e ->
                    !e.isDirectory && e.name.substringAfterLast('.', "").lowercase() in extensions
                } ?: return null
                val size = entry.size
                val crc = "%08X".format(entry.crc)
                val md5 = if (size in 0..MAX_ZIP_MD5) zip.getInputStream(entry).use { digests(it).second } else null
                RomIdentity(crc, md5, entry.name.substringAfterLast('/'), size)
            }
        } catch (_: IOException) {
            // Not a zip after all, or a damaged one: the whole file is what there is to hash.
            null
        }

        private fun digests(input: InputStream): Pair<String, String> {
            val crc = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            val buf = ByteArray(64 * 1024)
            input.buffered().let { stream ->
                while (true) {
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
