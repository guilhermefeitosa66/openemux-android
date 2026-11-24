package com.openemux.mobile.data

import com.retroarch.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

class GameFilesTest {
    private val root = Files.createTempDirectory("gamefiles").toFile()
    private val paths = Paths(File(root, "ext"), File(root, "int"))
    private val files = GameFiles(paths)
    private val srm = core("snes9x")

    private fun core(id: String) = CoreDef(id, id, R.string.data_core_snes9x)

    private fun put(dir: File, name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        File(dir, name).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun rom(name: String, console: Console = Console.SFC, bytes: ByteArray = byteArrayOf(9, 9)): Rom {
        val file = put(paths.roms(console), name, bytes)
        return Rom("${console.id}/$name", console, file, file.nameWithoutExtension)
    }

    private fun saves(console: Console = Console.SFC) = paths.saves(console)

    private fun names(dir: File) = dir.list().orEmpty().sorted()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = ZipInputStream(bytes.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes() }
    }

    private fun written(export: FilesExport, progress: MutableList<Long> = mutableListOf()): ByteArray =
        ByteArrayOutputStream().also { files.write(export, it) { n -> progress += n } }.toByteArray()

    // --- Export game file ---

    @Test
    fun aSingleFileGameExportsAsItself() {
        val mario = rom("Mario.sfc", bytes = ByteArray(10) { 4 })
        val export = files.romExport(mario)!!
        assertEquals("Mario.sfc", export.name)
        assertFalse(export.zipped)
        assertEquals(10L, export.bytes)
        val progress = mutableListOf<Long>()
        assertArrayEquals(ByteArray(10) { 4 }, written(export, progress))
        assertEquals(10L, progress.last())
    }

    @Test
    fun aDiscGameExportsEveryPartInOneZip() {
        val dir = paths.roms(Console.PS)
        put(dir, "Track 1.bin", byteArrayOf(1))
        put(dir, "Track 2.bin", byteArrayOf(2, 2))
        val game = rom("Game.cue", Console.PS, "FILE \"Track 1.bin\" BINARY\nFILE \"Track 2.bin\" BINARY\n".toByteArray())
        val export = files.romExport(game)!!
        assertEquals("Game.zip", export.name)
        assertTrue(export.zipped)
        assertEquals(3, export.files.size)
        val entries = unzip(written(export))
        assertEquals(setOf("Game.cue", "Track 1.bin", "Track 2.bin"), entries.keys)
        assertArrayEquals(byteArrayOf(2, 2), entries["Track 2.bin"])
    }

    @Test
    fun aDiscGameWithAMissingTrackCannotBeExported() {
        val game = rom("Game.cue", Console.PS, "FILE \"Gone.bin\" BINARY\n".toByteArray())
        assertNull(files.romExport(game))
    }

    // --- The game's saves ---

    @Test
    fun onlyTheGamesOwnBatterySavesCount() {
        val mario = rom("Mario.sfc")
        listOf("Mario.srm", "Mario.rtc", "Mario.Bros.srm", ".Mario.srm", "Mario.png", "Mario.sfc", "Luigi.srm", "Mario.srm.shared-2026").forEach { put(saves(), it) }
        File(saves(), "Mario.dir").mkdirs()
        assertEquals(listOf("Mario.rtc", "Mario.srm"), files.saves(mario).map { it.name })
    }

    @Test
    fun noSaveMeansNothingToExport() {
        val mario = rom("Mario.sfc")
        assertNull(files.saveExport(mario))
        assertTrue(files.saves(mario).isEmpty())
    }

    @Test
    fun aLoneSaveExportsAsItselfAndCompanionsAsAZip() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(5))
        val one = files.saveExport(mario)!!
        assertEquals("Mario.srm", one.name)
        assertArrayEquals(byteArrayOf(5), written(one))
        put(saves(), "Mario.rtc", byteArrayOf(6))
        val both = files.saveExport(mario)!!
        assertEquals("Mario save.zip", both.name)
        assertEquals(2L, both.bytes)
        assertEquals(setOf("Mario.srm", "Mario.rtc"), unzip(written(both)).keys)
    }

    // --- Where an imported save goes ---

    @Test
    fun theTargetIsTheCoresUsualNameWithoutASave() {
        val mario = rom("Mario.sfc")
        assertEquals(File(saves(), "Mario.srm"), files.target(mario, srm))
        val ds = rom("Pokemon.nds", Console.NDS)
        assertEquals("Pokemon.dsv", files.target(ds, core("desmume")).name)
        assertEquals("Pokemon.sav", files.target(ds, core("melonds")).name)
        assertEquals("srm", GameFiles.defaultSuffix(core("mgba")))
    }

    @Test
    fun theGamesOwnSaveDecidesTheTarget() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.rtc")
        put(saves(), "Mario.sav")
        // Not the core's usual name, but what this game has: the clock is never the save.
        assertEquals("Mario.sav", files.target(mario, srm).name)
        put(saves(), "Mario.srm")
        assertEquals("Mario.srm", files.target(mario, srm).name)
    }

    @Test
    fun onlyAClockIsNotASaveToReplace() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.rtc")
        assertEquals("Mario.srm", files.target(mario, srm).name)
    }

    @Test
    fun importIsOfferedWhereTheGamesSavesCarryItsName() {
        assertTrue(GameFiles.importSupported(Console.GBA))
        assertFalse(GameFiles.importSupported(Console.GC))
        assertFalse(GameFiles.importSupported(Console.PSP))
        assertFalse(GameFiles.importSupported(Console.SATURN))
    }

    // --- Checking a picked file ---

    @Test
    fun aSaveOfTheRightSizeAsksNothingMore() {
        val gba = rom("Golden Sun.gba", Console.GBA)
        val check = files.inspect(gba, core("mgba"), "golden sun.sav", 65_536)
        assertNull(check.refusal)
        assertFalse(check.sizeMismatch)
        assertFalse(check.unusualExtension)
        assertFalse(check.replaces)
        assertEquals("Golden Sun.srm", check.target)
        assertNull(check.expected)
    }

    @Test
    fun theSizeIsComparedWithTheGamesOwnSave() {
        val gba = rom("Golden Sun.gba", Console.GBA)
        put(saves(Console.GBA), "Golden Sun.srm", ByteArray(131_072))
        val check = files.inspect(gba, core("mgba"), "other.sav", 65_536)
        assertTrue(check.replaces)
        assertEquals(131_072L, check.expected)
        assertTrue(check.sizeMismatch)
        assertFalse(files.inspect(gba, core("mgba"), "same.sav", 131_072).sizeMismatch)
    }

    @Test
    fun anEmptyOwnSaveFallsBackToTheConsolesSizes() {
        val gba = rom("Golden Sun.gba", Console.GBA)
        put(saves(Console.GBA), "Golden Sun.srm", ByteArray(0))
        val check = files.inspect(gba, core("mgba"), "big.sav", 1_000_000)
        assertNull(check.expected)
        assertTrue(check.sizeMismatch)
    }

    @Test
    fun withoutASaveTheSizeIsComparedWithTheConsoles() {
        val ps = rom("Game.cue", Console.PS, "FILE \"x.bin\" BINARY".toByteArray())
        assertFalse(files.inspect(ps, srm, "card.mcr", 131_072).sizeMismatch)
        val gme = files.inspect(ps, srm, "card.gme", 134_976)
        assertTrue(gme.sizeMismatch)
        assertEquals(131_072L..131_072L, gme.range)
        // A console with no known sizes, or a size the provider did not give, is never a mismatch.
        val vb = rom("Wario.vb", Console.VB)
        assertFalse(files.inspect(vb, srm, "w.srm", 5).sizeMismatch)
        assertFalse(files.inspect(rom("Mario.sfc"), srm, "m.srm", -1).sizeMismatch)
    }

    @Test
    fun anUnusualExtensionAsksFirst() {
        val mario = rom("Mario.sfc")
        assertTrue(files.inspect(mario, srm, "mario.dat", 8192).unusualExtension)
        assertTrue(files.inspect(mario, srm, "mario", 8192).unusualExtension)
        assertFalse(files.inspect(mario, srm, "MARIO.SRM", 8192).unusualExtension)
    }

    @Test
    fun whatCannotBeASaveIsRefused() {
        val mario = rom("Mario.sfc")
        fun refusal(name: String, size: Long = 8192) = files.inspect(mario, srm, name, size).refusal
        assertEquals(SaveRefusal.EMPTY, refusal("mario.srm", 0))
        assertEquals(SaveRefusal.TOO_BIG, refusal("mario.srm", GameFiles.MAX_IMPORT_BYTES + 1))
        listOf("Mario.state", "Mario.state3", "Mario.state.auto", "Mario.slot", "Mario.sfc", "Mario.png", "Mario.zip", "Mario.cue")
            .forEach { assertEquals(it, SaveRefusal.NOT_A_SAVE, refusal(it)) }
        assertNull(refusal("mario.srm", GameFiles.MAX_IMPORT_BYTES))
    }

    // --- Importing ---

    @Test
    fun anImportWithoutASaveWritesTheTargetAndKeepsNothing() {
        val mario = rom("Mario.sfc")
        assertEquals(SaveChange.DONE, files.import(mario, srm, ByteArrayInputStream(byteArrayOf(7, 7))))
        assertArrayEquals(byteArrayOf(7, 7), File(saves(), "Mario.srm").readBytes())
        assertNull(files.kept(mario))
        assertEquals(listOf("Mario.srm"), names(saves()))
    }

    @Test
    fun anImportKeepsTheSaveItReplacesAndItsCompanions() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        put(saves(), "Mario.rtc", byteArrayOf(2))
        assertEquals(SaveChange.DONE, files.import(mario, srm, ByteArrayInputStream(byteArrayOf(3))))
        assertArrayEquals(byteArrayOf(3), File(saves(), "Mario.srm").readBytes())
        // The clock stays: only the save itself is replaced.
        assertArrayEquals(byteArrayOf(2), File(saves(), "Mario.rtc").readBytes())
        val kept = files.kept(mario)!!
        assertEquals(listOf("Mario.rtc", "Mario.srm"), kept.files.map { it.name })
        assertArrayEquals(byteArrayOf(1), kept.files[1].readBytes())
        assertTrue(kept.keptAt > 0)
        assertEquals(listOf("Mario.rtc", "Mario.srm"), names(saves()))
    }

    @Test
    fun theNextImportReplacesTheKeptSave() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(3)))
        assertArrayEquals(byteArrayOf(2), files.kept(mario)!!.files.single().readBytes())
        assertArrayEquals(byteArrayOf(3), File(saves(), "Mario.srm").readBytes())
    }

    @Test
    fun anImportIntoTheGamesOwnSaveName() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.sav", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(4)))
        assertArrayEquals(byteArrayOf(4), File(saves(), "Mario.sav").readBytes())
        assertFalse(File(saves(), "Mario.srm").exists())
    }

    @Test
    fun aFileThatTurnsOutEmptyOrTooBigChangesNothing() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        assertEquals(SaveChange.FAILED, files.import(mario, srm, ByteArrayInputStream(ByteArray(0))))
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = len
        }
        assertEquals(SaveChange.TOO_BIG, files.import(mario, srm, endless))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
        assertNull(files.kept(mario))
        assertEquals(listOf("Mario.srm"), names(saves()))
    }

    @Test
    fun aReadThatFailsHalfwayChangesNothing() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        val broken = object : InputStream() {
            var sent = false
            override fun read(): Int = throw IOException("gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (sent) throw IOException("gone") else { sent = true; 1 }
        }
        assertEquals(SaveChange.FAILED, files.import(mario, srm, broken))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
        assertEquals(listOf("Mario.srm"), names(saves()))
    }

    @Test
    fun aMoveThatFailsLeavesTheSaveInPlace() {
        val failing = GameFiles(paths) { from, to -> if (from.name == GameFiles.TMP_IMPORT) false else from.renameTo(to) }
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        assertEquals(SaveChange.FAILED, failing.import(mario, srm, ByteArrayInputStream(byteArrayOf(2))))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
        assertEquals(listOf("Mario.srm"), names(saves()))
    }

    @Test
    fun aBackupThatCannotBeKeptStopsTheImport() {
        val failing = GameFiles(paths) { from, to -> if (from.name.endsWith(".staging")) false else from.renameTo(to) }
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        assertEquals(SaveChange.FAILED, failing.import(mario, srm, ByteArrayInputStream(byteArrayOf(2))))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
    }

    @Test
    fun aStagingFolderThatCannotBeMadeStopsTheImport() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        // A file where the kept saves' folder goes.
        put(paths.saveBackup(Console.SFC, "x").parentFile!!.parentFile!!, Console.SFC.id)
        assertEquals(SaveChange.FAILED, files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2))))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
    }

    // --- Restoring ---

    @Test
    fun restoreSwapsAndCanBeUndone() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        assertEquals(SaveChange.DONE, files.restore(mario))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
        assertArrayEquals(byteArrayOf(2), files.kept(mario)!!.files.single().readBytes())
        assertEquals(SaveChange.DONE, files.restore(mario))
        assertArrayEquals(byteArrayOf(2), File(saves(), "Mario.srm").readBytes())
        assertEquals(listOf("Mario.srm"), names(saves()))
    }

    @Test
    fun restorePutsBackExactlyWhatWasThere() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        // A clock the core wrote after the import was not there before it.
        put(saves(), "Mario.rtc", byteArrayOf(9))
        files.restore(mario)
        assertEquals(listOf("Mario.srm"), names(saves()))
        assertEquals(listOf("Mario.rtc", "Mario.srm"), files.kept(mario)!!.files.map { it.name })
    }

    @Test
    fun restoreWithNoSaveInUseClearsTheKeptOne() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        File(saves(), "Mario.srm").delete()
        assertEquals(SaveChange.DONE, files.restore(mario))
        assertArrayEquals(byteArrayOf(1), File(saves(), "Mario.srm").readBytes())
        assertNull(files.kept(mario))
    }

    @Test
    fun nothingKeptNothingToRestore() {
        assertEquals(SaveChange.FAILED, files.restore(rom("Mario.sfc")))
    }

    @Test
    fun aRestoreThatFailsPutsTheSaveInUseBack() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        put(saves(), "Mario.rtc", byteArrayOf(5))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        var moves = 0
        // The first file goes in, the second cannot: the first is undone from the copy set aside.
        val failing = GameFiles(paths) { from, to -> if (from.name == GameFiles.TMP_IMPORT && ++moves == 2) false else from.renameTo(to) }
        assertEquals(SaveChange.FAILED, failing.restore(mario))
        assertArrayEquals(byteArrayOf(2), File(saves(), "Mario.srm").readBytes())
        assertArrayEquals(byteArrayOf(5), File(saves(), "Mario.rtc").readBytes())
        assertArrayEquals(byteArrayOf(1), files.kept(mario)!!.files.first { it.name == "Mario.srm" }.readBytes())
        assertFalse(File(paths.saveBackup(Console.SFC, "x").parentFile, ".Mario.staging").exists())
    }

    @Test
    fun aRestoreThatCannotEvenUndoKeepsTheCopy() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        val failing = GameFiles(paths) { from, to -> if (from.name == GameFiles.TMP_IMPORT) false else from.renameTo(to) }
        assertEquals(SaveChange.FAILED, failing.restore(mario))
        assertTrue(File(File(paths.saveBackup(Console.SFC, "x").parentFile, ".Mario.staging"), "Mario.srm").isFile)
    }

    @Test
    fun aSaveThatCannotBeSetAsideIsNotRestored() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        // A staging folder left behind that cannot be cleared: the folder holding it is read-only.
        val parent = paths.saveBackup(Console.SFC, "x").parentFile!!
        put(File(parent, ".Mario.staging"), "leftover")
        parent.setWritable(false)
        try {
            assertEquals(SaveChange.FAILED, files.restore(mario))
        } finally {
            parent.setWritable(true)
        }
        assertArrayEquals(byteArrayOf(2), File(saves(), "Mario.srm").readBytes())
        assertArrayEquals(byteArrayOf(1), files.kept(mario)!!.files.single().readBytes())
    }

    // --- Rename ---

    @Test
    fun theKeptSaveFollowsARename() {
        val mario = rom("Mario.sfc")
        put(saves(), "Mario.srm", byteArrayOf(1))
        files.import(mario, srm, ByteArrayInputStream(byteArrayOf(2)))
        files.renamed(Console.SFC, "Mario", "Super Mario")
        assertFalse(paths.saveBackup(Console.SFC, "Mario").exists())
        assertTrue(File(paths.saveBackup(Console.SFC, "Super Mario"), "Mario.srm").isFile)
        // Nothing kept, or a folder already there under the new name: nothing moves.
        files.renamed(Console.SFC, "Zelda", "Link")
        assertFalse(paths.saveBackup(Console.SFC, "Link").exists())
        paths.saveBackup(Console.SFC, "Other").mkdirs()
        files.renamed(Console.SFC, "Super Mario", "Other")
        assertTrue(paths.saveBackup(Console.SFC, "Super Mario").isDirectory)
    }

    // --- Is the game running ---

    @Test
    fun theEnginesStatusSaysWhetherThisGameRuns() {
        assertTrue(GameFiles.runningGame("GET_STATUS PLAYING super_nes,Mario", "Mario"))
        assertTrue(GameFiles.runningGame("GET_STATUS PAUSED super_nes,mario.sfc\n", "Mario"))
        assertTrue(GameFiles.runningGame("GET_STATUS PLAYING super_nes,Mario, Luigi & Co", "Mario, Luigi & Co"))
        assertFalse(GameFiles.runningGame("GET_STATUS PLAYING super_nes,Zelda", "Mario"))
        // Up, but which game cannot be told: taken as this one.
        assertTrue(GameFiles.runningGame("GET_STATUS PLAYING super_nes,UNKNOWN", "Mario"))
        assertTrue(GameFiles.runningGame("GET_STATUS PLAYING", "Mario"))
        assertTrue(GameFiles.runningGame("GET_STATUS PAUSED super_nes", "Mario"))
        assertFalse(GameFiles.runningGame("GET_STATUS CONTENTLESS", "Mario"))
        assertFalse(GameFiles.runningGame("GET_STATUS", "Mario"))
        assertFalse(GameFiles.runningGame("SOMETHING ELSE", "Mario"))
        assertFalse(GameFiles.runningGame(null, "Mario"))
    }
}
