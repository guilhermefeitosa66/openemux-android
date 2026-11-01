package com.openemux.mobile.data

import com.openemux.mobile.data.BiosImportReport.Imported
import com.openemux.mobile.data.BiosImportReport.Reason
import com.openemux.mobile.data.BiosImportReport.Rejected
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest

class BiosImportTest {
    private lateinit var dir: File
    private lateinit var system: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bios-import").toFile()
        system = File(dir, "system")
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        system.setWritable(true)
        dir.deleteRecursively()
    }

    private var opens = 0

    private fun source(name: String, bytes: ByteArray = name.toByteArray(), size: Long = bytes.size.toLong()) =
        BiosSource(name, size) { opens++; ByteArrayInputStream(bytes) }

    private fun import(console: Console, vararg sources: BiosSource, max: Long = BIOS_MAX_BYTES, hashCap: Long = BIOS_HASH_CAP) =
        importBiosFiles(console, sources.toList(), system, max, hashCap)

    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Nothing but the imported files: no `.part` left behind. */
    private fun onDisk() = system.list().orEmpty().sorted()

    @Test
    fun anExactNameIsImported() {
        val r = import(Console.PS, source("scph5501.bin", byteArrayOf(1, 2, 3)))
        assertEquals(listOf(Imported("scph5501.bin", "scph5501.bin", byContent = false, replaced = false)), r.imported)
        assertTrue(r.rejected.isEmpty())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(system, "scph5501.bin").readBytes())
        assertEquals(listOf("scph5501.bin"), onDisk())
    }

    @Test
    fun anotherCaseIsSavedUnderTheCatalogSpelling() {
        val ps = import(Console.PS, source("SCPH1001.BIN"))
        assertEquals("scph1001.bin", ps.imported.single().savedAs)
        val atari = import(Console.A5200, source("ataribas.rom"))
        assertEquals("ATARIBAS.ROM", atari.imported.single().savedAs)
        assertEquals(listOf("ATARIBAS.ROM", "scph1001.bin"), onDisk())
    }

    @Test
    fun everyAlternativeOfAnEntryCounts() {
        val r = import(Console.MCD, source("bios_CD_U.bin"), source("bios_CD_E.bin"), source("bios_CD_J.bin"))
        assertEquals(3, r.imported.size)
        assertTrue(r.allImported)
    }

    @Test
    fun aNameOfAnotherConsoleOrOfNoneIsRejected() {
        val r = import(Console.PS, source("bios_CD_U.bin"), source("psx bios (copy).bin"))
        assertEquals(
            listOf(Rejected("bios_CD_U.bin", Reason.WRONG_NAME), Rejected("psx bios (copy).bin", Reason.WRONG_NAME)),
            r.rejected,
        )
        assertTrue(r.noneImported)
        assertEquals(0, opens)
        assertEquals(emptyList<String>(), onDisk())
    }

    @Test
    fun gbaIsRecognizedByNameOrByItsKnownHash() {
        // No real BIOS bytes here: the catalog's hash is swapped for the hash of these.
        val known = byteArrayOf(4, 2, 4, 2)
        val hashes = mapOf(md5(known) to "gba_bios.bin")
        val byName = import(Console.GBA, source("gba_bios.bin", byteArrayOf(9, 9)))
        assertEquals(Imported("gba_bios.bin", "gba_bios.bin", byContent = false, replaced = false), byName.imported.single())

        val byContent = importBiosFiles(Console.GBA, listOf(source("GBA BIOS.bin", known)), system, hashes = hashes)
        assertEquals(Imported("GBA BIOS.bin", "gba_bios.bin", byContent = true, replaced = true), byContent.imported.single())
        assertArrayEquals(known, File(system, "gba_bios.bin").readBytes())

        val unknown = importBiosFiles(Console.GBA, listOf(source("other.bin", byteArrayOf(7))), system, hashes = hashes)
        assertEquals(Rejected("other.bin", Reason.WRONG_NAME), unknown.rejected.single())
    }

    @Test
    fun aFileRecognizedByContentIsReadOnce() {
        // What is hashed is what is saved: no second read that could hand back other bytes.
        val known = byteArrayOf(4, 2, 4, 2)
        opens = 0
        val r = importBiosFiles(Console.GBA, listOf(source("GBA BIOS.bin", known)), system, hashes = mapOf(md5(known) to "gba_bios.bin"))
        assertTrue(r.imported.single().byContent)
        assertEquals(1, opens)
        assertEquals(listOf("gba_bios.bin"), onDisk())
    }

    @Test
    fun aFileTooLargeToHashIsNotHashed() {
        opens = 0
        val r = import(Console.GBA, source("big.bin", ByteArray(10)), hashCap = 4)
        assertEquals(Reason.WRONG_NAME, r.rejected.single().reason)
        assertEquals(0, opens)
    }

    @Test
    fun aStreamPastTheHashCapIsNotTheKnownFile() {
        // The provider said nothing about the size: the stream itself runs past the cap.
        val r = import(Console.GBA, source("big.bin", ByteArray(10), size = -1), hashCap = 4)
        assertEquals(Reason.WRONG_NAME, r.rejected.single().reason)
    }

    @Test
    fun hashingNeedsAStream() {
        val r = importBiosFiles(Console.GBA, listOf(BiosSource("other.bin", 4) { null }), system)
        assertEquals(Reason.IO, r.rejected.single().reason)
    }

    @Test
    fun aConsoleWithoutHashesNeverReadsAWrongName() {
        opens = 0
        import(Console.LYNX, source("random.bin"), source("5200.rom"))
        assertEquals(0, opens)
    }

    @Test
    fun replacingSaysSoOnlyWhenTheBytesDiffer() {
        File(system.apply { mkdirs() }, "lynxboot.img").writeBytes(byteArrayOf(1))
        val other = import(Console.LYNX, source("lynxboot.img", byteArrayOf(2)))
        assertTrue(other.imported.single().replaced)
        val sameSizeOtherBytes = import(Console.LYNX, source("lynxboot.img", byteArrayOf(3)))
        assertTrue(sameSizeOtherBytes.imported.single().replaced)
        val same = import(Console.LYNX, source("lynxboot.img", byteArrayOf(3)))
        assertFalse(same.imported.single().replaced)
        assertArrayEquals(byteArrayOf(3), File(system, "lynxboot.img").readBytes())
    }

    @Test
    fun aKnownSizeAboveTheCapIsRejectedWithoutOpening() {
        opens = 0
        val r = import(Console.NDS, source("dsi_nand.bin", ByteArray(8)), max = 4)
        assertEquals(Rejected("dsi_nand.bin", Reason.TOO_LARGE), r.rejected.single())
        assertEquals(0, opens)
    }

    @Test
    fun anUnknownSizeIsCappedWhileCopying() {
        val r = import(Console.NDS, source("dsi_nand.bin", ByteArray(8), size = -1), max = 4)
        assertEquals(Reason.TOO_LARGE, r.rejected.single().reason)
        assertEquals(emptyList<String>(), onDisk())
    }

    @Test
    fun aFailingFileOnlyFailsItself() {
        val failing = object : InputStream() {
            var sent = false
            override fun read(): Int = throw IOException("gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent) throw IOException("gone")
                sent = true
                b[off] = 1
                return 1
            }
        }
        val r = importBiosFiles(
            Console.INTV,
            listOf(
                BiosSource("exec.bin", -1) { null },
                BiosSource("grom.bin", -1) { failing },
                BiosSource("exec.bin", -1) { throw SecurityException("revoked") },
                source("exec.bin"),
            ),
            system,
        )
        assertEquals(listOf(Reason.IO, Reason.IO, Reason.IO), r.rejected.map { it.reason })
        assertEquals(listOf("exec.bin"), r.imported.map { it.savedAs })
        assertEquals(listOf("exec.bin"), onDisk())
        assertEquals(4, r.total)
        assertFalse(r.allImported)
        assertFalse(r.noneImported)
    }

    @Test
    fun anUnwritableSystemFolderIsAnIoFailure() {
        system.mkdirs()
        system.setWritable(false)
        val r = import(Console.LYNX, source("lynxboot.img"))
        assertEquals(Reason.IO, r.rejected.single().reason)
    }

    @Test
    fun anEmptyFolderInTheWayIsReplaced() {
        // rename(2) will not put a file over a directory: the import clears it and renames again.
        File(system, "lynxboot.img").mkdirs()
        val r = import(Console.LYNX, source("lynxboot.img", byteArrayOf(5)))
        assertTrue(r.imported.single().replaced)
        assertArrayEquals(byteArrayOf(5), File(system, "lynxboot.img").readBytes())
    }

    @Test
    fun aFolderThatCannotBeClearedIsAnIoFailure() {
        File(system, "lynxboot.img/keep").mkdirs()
        val r = import(Console.LYNX, source("lynxboot.img", byteArrayOf(5)))
        assertEquals(Reason.IO, r.rejected.single().reason)
        assertEquals(listOf("lynxboot.img"), onDisk())
    }

    @Test
    fun twoFilesForTheSameTargetReplaceInPickOrder() {
        val r = import(Console.PS, source("scph5501.bin", byteArrayOf(1)), source("SCPH5501.BIN", byteArrayOf(2)))
        assertEquals(listOf(false, true), r.imported.map { it.replaced })
        assertArrayEquals(byteArrayOf(2), File(system, "scph5501.bin").readBytes())
    }

    @Test
    fun theReportHelpers() {
        val none = BiosImportReport(Console.PS, emptyList(), emptyList())
        assertEquals(0, none.total)
        assertFalse(none.allImported)
        assertTrue(none.noneImported)
    }

    @Test
    fun namesAndHashesCoverTheCatalog() {
        for (console in Console.entries) {
            val defs = KNOWN_BIOS.filter { it.console == console }
            val names = biosNamesFor(console)
            assertEquals(defs.flatMap { it.files }.toSet(), names.values.toSet())
            assertTrue(names.keys.all { it == it.lowercase() })
            assertEquals(defs.flatMap { it.md5 }.toSet(), biosHashesFor(console).keys)
        }
        assertEquals(mapOf("a860e8c0b6d573d191e4ec7db1b1e4f6" to "gba_bios.bin"), biosHashesFor(Console.GBA))
    }
}
