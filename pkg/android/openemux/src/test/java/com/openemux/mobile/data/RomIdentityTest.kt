package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomIdentityTest {
    private val dir = Files.createTempDirectory("identity").toFile()
    private val data = "OpenEmux".toByteArray()

    private fun crcOf(bytes: ByteArray) = "%08X".format(CRC32().apply { update(bytes) }.value)

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>) = File(dir, name).apply {
        ZipOutputStream(outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }

    @Test
    fun `a plain ROM is hashed whole`() {
        val rom = File(dir, "Tetris (World).gb").apply { writeBytes(data) }
        val id = RomIdentity.of(rom, setOf("gb"))
        assertEquals(crcOf(data), id.crc)
        assertEquals("1F6F1F8C1E1A8D3F48AC1B4C0F2C21B5".length, id.md5!!.length)
        assertEquals(id.md5, id.md5!!.uppercase())
        assertEquals("Tetris (World).gb", id.name)
        assertEquals(data.size.toLong(), id.size)
    }

    @Test
    fun `a zipped ROM is the entry with the console's extension`() {
        val rom = zip("Tetris.zip", "readme.txt" to "hi".toByteArray(), "dir/Tetris (World).GB" to data)
        val id = RomIdentity.of(rom, setOf("gb"))
        assertEquals(crcOf(data), id.crc)
        assertEquals(RomIdentity.of(File(dir, "plain.gb").apply { writeBytes(data) }, setOf("gb")).md5, id.md5)
        assertEquals("Tetris (World).GB", id.name)
        assertEquals(data.size.toLong(), id.size)
    }

    @Test
    fun `a zip without such an entry, or no zip at all, is hashed whole`() {
        val other = zip("Other.zip", "readme.txt" to "hi".toByteArray())
        assertEquals("Other.zip", RomIdentity.of(other, setOf("gb")).name)
        val fake = File(dir, "Fake.zip").apply { writeBytes(data) }
        val id = RomIdentity.of(fake, setOf("gb"))
        assertEquals(crcOf(data), id.crc)
        assertEquals("Fake.zip", id.name)
    }

    @Test
    fun `the MD5 of a huge zipped entry is left out`() {
        assertEquals(64L * 1024 * 1024, RomIdentity.MAX_ZIP_MD5)
        assertNull(RomIdentity("00000000", null, "x", 0).md5)
    }
}
