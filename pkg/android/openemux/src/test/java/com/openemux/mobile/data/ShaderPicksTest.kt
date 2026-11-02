package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A shader picked in game is remembered for the ROM (issue 717). */
class ShaderPicksTest {

    private lateinit var root: File
    private lateinit var paths: Paths
    private val dir get() = paths.shaderPicks

    private val gba = "GBA/Zelda (USA).gba"
    private val otherGba = "GBA/Metroid (USA).gba"
    private val consoleDefault = Console.GBA.defaultShader
    private val others = SHADERS.map { it.id }.filter { it != consoleDefault }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("shader-picks").toFile()
        paths = Paths(File(root, "external"), File(root, "internal"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun library() = Library(paths, { true }, storageRetryMs = 0)

    // --- ShaderPicks ---

    @Test
    fun aRecordedPickComesBackOnceFromTheDrain() {
        assertTrue(ShaderPicks.record(dir, gba, "zfast", at = 10))
        assertEquals(listOf(ShaderPicks.Pick(gba, "zfast", 10)), ShaderPicks.drain(dir))
        assertEquals(emptyList<ShaderPicks.Pick>(), ShaderPicks.drain(dir))
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun theDrainHandsPicksOldestFirst() {
        ShaderPicks.record(dir, gba, "dot", at = 30)
        ShaderPicks.record(dir, gba, "sharp", at = 10)
        ShaderPicks.record(dir, otherGba, "zfast", at = 20)
        assertEquals(listOf("sharp", "zfast", "dot"), ShaderPicks.drain(dir).map { it.shader })
    }

    @Test
    fun aMissingFolderDrainsNothing() {
        assertEquals(emptyList<ShaderPicks.Pick>(), ShaderPicks.drain(File(root, "nowhere")))
    }

    @Test
    fun aFileThatIsNotAPickIsDroppedAndAWriteInFlightIsLeftAlone() {
        dir.mkdirs()
        File(dir, "1-1.pick").writeText("not json")
        File(dir, "2-2.pick").writeText("""{"rom":"$gba"}""")
        val inFlight = File(dir, "3-3.tmp").apply { writeText("""{"rom":"$gba","shader":"dot","at":3}""") }
        ShaderPicks.record(dir, gba, "zfast", at = 4)
        assertEquals(listOf(ShaderPicks.Pick(gba, "zfast", 4)), ShaderPicks.drain(dir))
        assertEquals(listOf(inFlight.name), dir.list()!!.toList())
    }

    @Test
    fun aTmpLeftByAKilledWriteIsDroppedOnceStale() {
        dir.mkdirs()
        val orphan = File(dir, "1-1.tmp").apply { writeText("{") }
        ShaderPicks.drain(dir, now = orphan.lastModified() + 1_000)
        assertTrue(orphan.exists())
        ShaderPicks.drain(dir, now = orphan.lastModified() + 120_000)
        assertFalse(orphan.exists())
    }

    @Test
    fun aPickThatCannotBeWrittenReportsItAndThrowsNothing() {
        val blocked = File(root, "file").apply { writeText("") }
        assertFalse(ShaderPicks.record(blocked, gba, "dot"))
    }

    // --- Library.absorbShaderPicks ---

    @Test
    fun aPickBecomesTheRomsShaderAndLeavesTheOtherRomsAlone() {
        val lib = library()
        ShaderPicks.record(dir, gba, others[0])
        lib.absorbShaderPicks()
        assertEquals(others[0], lib.romMeta(gba).shader)
        assertNull(lib.romMeta(otherGba).shader)
        assertEquals(consoleDefault, lib.consoleSettings(Console.GBA).shader)
    }

    @Test
    fun theNewestPickWins() {
        val lib = library()
        ShaderPicks.record(dir, gba, others[1], at = 20)
        ShaderPicks.record(dir, gba, others[0], at = 10)
        lib.absorbShaderPicks()
        assertEquals(others[1], lib.romMeta(gba).shader)
    }

    @Test
    fun pickingTheConsolesShaderClearsTheOverride() {
        val lib = library()
        lib.updateRom(gba) { it.copy(shader = others[0]) }
        ShaderPicks.record(dir, gba, consoleDefault)
        lib.absorbShaderPicks()
        assertNull(lib.romMeta(gba).shader)
        // The console's own choice, not the built-in default, is what a pick is compared with.
        lib.updateConsole(Console.GBA) { it.copy(shader = others[1]) }
        ShaderPicks.record(dir, gba, consoleDefault)
        lib.absorbShaderPicks()
        assertEquals(consoleDefault, lib.romMeta(gba).shader)
    }

    @Test
    fun picksForAnUnknownConsoleOrShaderAreDropped() {
        val lib = library()
        ShaderPicks.record(dir, "XYZ/game.bin", others[0])
        ShaderPicks.record(dir, "no-console", others[0])
        ShaderPicks.record(dir, gba, "no-such-shader")
        lib.absorbShaderPicks()
        assertEquals(emptyMap<String, RomMeta>(), lib.meta.value.roms)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun theDetailScreenAfterAPickWinsAndAPickAfterTheDetailScreenWins() {
        val lib = library()
        ShaderPicks.record(dir, gba, others[0])
        lib.absorbShaderPicks()
        lib.updateRom(gba) { it.copy(shader = others[1]) } // the detail screen
        lib.absorbShaderPicks() // the next launch
        assertEquals(others[1], lib.romMeta(gba).shader)
        ShaderPicks.record(dir, gba, others[0])
        lib.absorbShaderPicks()
        assertEquals(others[0], lib.romMeta(gba).shader)
    }

    @Test
    fun aScanAbsorbsThePicksAndTheyOutliveTheProcess() = runBlocking {
        ShaderPicks.record(dir, gba, others[0])
        library().scan()
        val restarted = library()
        assertEquals(others[0], restarted.romMeta(gba).shader)
        assertEquals(shaderById(others[0]), restarted.effectiveShader(Rom(gba, Console.GBA, File("Zelda (USA).gba"), "Zelda")))
    }

    @Test
    fun aScanAbsorbsThePicksEvenWhileTheRomStorageIsNotReady() = runBlocking {
        ShaderPicks.record(dir, gba, others[0])
        val lib = Library(paths, { false }, storageRetryMs = 0)
        lib.scan()
        assertEquals(others[0], lib.romMeta(gba).shader)
    }
}
