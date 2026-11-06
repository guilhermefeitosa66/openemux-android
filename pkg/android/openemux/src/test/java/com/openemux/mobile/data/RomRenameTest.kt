package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RomRenameTest {
    private val root = Files.createTempDirectory("rename").toFile()
    private val paths = Paths(File(root, "ext"), File(root, "int"))

    private class FakeCovers(private val paths: Paths) : CoverOps {
        override var syncing = false
        /** The mirror match per base name. */
        val matches = mutableMapOf<String, String>()
        var cached: ByteArray? = null
        val forgotten = mutableListOf<String>()
        override fun localCover(rom: Rom): File? = Covers.localCover(paths, rom.console, rom.baseName)
        override fun forgetMatch(rom: Rom) { forgotten += rom.key }
        override fun mirrorUrl(rom: Rom): String? = matches[rom.baseName]
        override suspend fun fetch(url: String): Fetch = Fetch.FOUND
        override fun keepCachedCover(url: String, target: File): Boolean {
            val bytes = cached ?: return false
            target.writeBytes(bytes)
            return true
        }
    }

    private class FakeLabels(private val paths: Paths) : LabelOps {
        override var syncing = false
        val changes = mutableListOf<Pair<String, File?>>()
        override fun labelFile(rom: Rom) = File(paths.labels(rom.console), "${rom.baseName}.png")
        override fun noneMarker(rom: Rom) = File(paths.labels(rom.console), "${rom.baseName}.none")
        override suspend fun fetch(rom: Rom, target: File): Fetch = Fetch.MISSING
        override fun changed(rom: Rom, file: File?) { changes += rom.key to file }
    }

    private val covers = FakeCovers(paths)
    private val labels = FakeLabels(paths)

    private fun put(dir: File, name: String, bytes: Int = 3) = File(dir, name).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes) { 7 }) }

    private fun library(): Library = Library(paths, { true }).also { runBlocking { it.scan() } }

    private fun game(lib: Library, name: String, console: Console = Console.SFC) = lib.roms.value.first { it.key == "${console.id}/$name" }

    private fun names(dir: File) = dir.list().orEmpty().filter { !it.startsWith(".") }.sorted()

    /** Super Mario World with a state in every slot kind, a battery save, playtime, a cover and a label. */
    private fun seed(): Library {
        put(paths.roms(Console.SFC), "Mario (USA).sfc", 10)
        put(paths.roms(Console.SFC), "Zelda.sfc")
        val states = paths.states(Console.SFC)
        listOf("Mario (USA).state", "Mario (USA).state.png", "Mario (USA).state1", "Mario (USA).state1.png",
            "Mario (USA).state.auto", "Mario (USA).state.auto.png", "Mario (USA).state999", "Zelda.state").forEach { put(states, it) }
        put(paths.saves(Console.SFC), "Mario (USA).srm")
        put(paths.saves(Console.SFC), "Mario (USA).rtc")
        // Another game's save whose stem starts the same: it stays.
        put(paths.saves(Console.SFC), "Mario (USA).Bros.srm")
        paths.playtime.mkdirs()
        File(paths.playtime, "SFC_Mario (USA).txt").writeText("120")
        put(paths.covers(Console.SFC), "Mario (USA).png")
        put(paths.labels(Console.SFC), "Mario (USA).png")
        val lib = library()
        val mario = game(lib, "Mario (USA).sfc")
        lib.updateRom(mario.key) { it.copy(favorite = true, shader = "crt", color = "red", core = "bsnes", lastPlayed = 42) }
        ArtSources(paths.covers(Console.SFC)).record("Mario (USA)", File(paths.covers(Console.SFC), "Mario (USA).png"), ArtSource.MANUAL, "mirror")
        ArtSources(paths.labels(Console.SFC)).record("Mario (USA)", File(paths.labels(Console.SFC), "Mario (USA).png"), ArtSource.SYNCED, "screenscraper")
        return lib
    }

    @Test
    fun `a rename carries every file and the metadata to the new name`() {
        val lib = seed()
        val renamer = RomRenamer(paths, lib, covers, labels)
        val mario = game(lib, "Mario (USA).sfc")

        val result = renamer.rename(mario, "  Super Mario World  ") as RenameResult.Done

        val renamed = result.rom
        assertEquals("SFC/Super Mario World.sfc", renamed.key)
        assertEquals("Super Mario World", renamed.title)
        assertEquals(listOf("Super Mario World.sfc", "Zelda.sfc"), names(paths.roms(Console.SFC)))
        assertEquals(10L, renamed.file.length())
        assertEquals(
            listOf("Super Mario World.state", "Super Mario World.state.auto", "Super Mario World.state.auto.png", "Super Mario World.state.png",
                "Super Mario World.state1", "Super Mario World.state1.png", "Super Mario World.state999", "Zelda.state"),
            names(paths.states(Console.SFC)),
        )
        assertEquals(listOf("Mario (USA).Bros.srm", "Super Mario World.rtc", "Super Mario World.srm"), names(paths.saves(Console.SFC)))
        assertEquals(listOf("SFC_Super Mario World.txt"), names(paths.playtime))
        assertEquals(120L, lib.playtimeSeconds(renamed))
        assertEquals(listOf("Super Mario World.png"), names(paths.covers(Console.SFC)))
        assertEquals(listOf("Super Mario World.png"), names(paths.labels(Console.SFC)))
        // The list keeps its order under the new title, and the index on disk follows.
        assertEquals(listOf("SFC/Super Mario World.sfc", "SFC/Zelda.sfc"), lib.roms.value.map { it.key })
        assertEquals(listOf("SFC/Super Mario World.sfc", "SFC/Zelda.sfc"), Library(paths, { true }).roms.value.map { it.key })
        val meta = lib.romMeta(renamed.key)
        assertTrue(meta.favorite)
        assertEquals("crt", meta.shader)
        assertEquals("red", meta.color)
        assertEquals("bsnes", meta.core)
        assertEquals(42L, meta.lastPlayed)
        assertNull(lib.meta.value.roms[mario.key])
        assertEquals(RomMeta(), lib.romMeta(mario.key))
        // The auto state is the one "Continue" loads under the new name.
        assertEquals(-1, lib.autoState(renamed)!!.slot)
        assertEquals(3, lib.states(renamed).size)
        // The art records follow their files, so the sources still read as before.
        val cover = ArtSources(paths.covers(Console.SFC))
        assertNull(cover.entry("Mario (USA)"))
        assertEquals(ArtSource.MANUAL, cover.matching("Super Mario World", File(paths.covers(Console.SFC), "Super Mario World.png"))!!.source)
        assertEquals(ArtSource.SYNCED, ArtSources(paths.labels(Console.SFC)).matching("Super Mario World", File(paths.labels(Console.SFC), "Super Mario World.png"))!!.source)
        assertEquals(listOf(mario.key), covers.forgotten)
        assertEquals(listOf(mario.key to null, renamed.key to File(paths.labels(Console.SFC), "Super Mario World.png")), labels.changes)
        // The metadata on disk moved too.
        assertTrue(Library(paths, { true }).romMeta(renamed.key).favorite)
    }

    @Test
    fun `a pick made in game for the old name lands before the key changes`() {
        val lib = seed()
        val mario = game(lib, "Mario (USA).sfc")
        val pick = SHADERS.first { it.id != "none" && it.id != Console.SFC.defaultShader }.id
        ShaderPicks.record(paths.shaderPicks, mario.key, pick)

        val renamed = (RomRenamer(paths, lib, covers, labels).rename(mario, "Mario") as RenameResult.Done).rom

        assertEquals(pick, lib.romMeta(renamed.key).shader)
    }

    @Test
    fun `the name is checked before anything moves`() {
        val lib = seed()
        put(paths.roms(Console.SFC), "notes.txt")
        val renamer = RomRenamer(paths, lib, covers, labels)
        val mario = game(lib, "Mario (USA).sfc")

        assertEquals(RenameCheck.UNCHANGED, renamer.check(mario, "Mario (USA) "))
        assertEquals(RenameCheck.EMPTY, renamer.check(mario, "   "))
        RomRenamer.FORBIDDEN.forEach { assertEquals(RenameCheck.BAD_CHARS, renamer.check(mario, "Mario${it}World")) }
        assertEquals(RenameCheck.BAD_CHARS, renamer.check(mario, "Mario\u0001"))
        assertEquals(RenameCheck.BAD_CHARS, renamer.check(mario, "Mario\u007f"))
        assertEquals(RenameCheck.LEADING_DOT, renamer.check(mario, ".hidden"))
        assertEquals(RenameCheck.LEADING_DOT, renamer.check(mario, ".."))
        assertEquals(RenameCheck.TOO_LONG, renamer.check(mario, "a".repeat(250)))
        assertEquals(RenameCheck.OK, renamer.check(mario, "a".repeat(240)))
        // Another game, in any case, or any other file of the folder.
        assertEquals(RenameCheck.CLASH, renamer.check(mario, "zelda"))
        assertEquals(RenameCheck.OK, renamer.check(mario, "notes"))
        assertEquals(RenameCheck.OK, renamer.check(mario, "Mario World"))

        assertEquals(RenameResult.Refused(RenameCheck.CLASH), renamer.rename(mario, "Zelda"))
        assertTrue(mario.file.exists())
    }

    @Test
    fun `a game listed whose file is gone still holds its name`() {
        val lib = seed()
        val zelda = game(lib, "Zelda.sfc")
        zelda.file.delete()

        assertEquals(RenameCheck.CLASH, RomRenamer(paths, lib, covers, labels).check(game(lib, "Mario (USA).sfc"), "ZELDA"))
    }

    @Test
    fun `data left under the new name by a removed game refuses it`() {
        val lib = seed()
        val renamer = RomRenamer(paths, lib, covers, labels)
        val mario = game(lib, "Mario (USA).sfc")
        put(paths.saves(Console.SFC), "Old.srm")
        put(paths.states(Console.SFC), "Gone.state3")
        File(paths.playtime, "SFC_Played.txt").writeText("5")
        put(paths.covers(Console.SFC), "Art.webp")
        put(paths.labels(Console.SFC), "Label.none")

        listOf("old", "Gone", "played", "Art", "Label").forEach { assertEquals(it, RenameCheck.LEFTOVERS, renamer.check(mario, it)) }
        // Not theirs: another stem, or a file that is not a battery save.
        put(paths.saves(Console.SFC), "Other.sfc")
        put(paths.states(Console.SFC), "Other.statefile")
        assertEquals(RenameCheck.OK, renamer.check(mario, "Other"))
    }

    @Test
    fun `a change of case only goes through a temporary name`() {
        val lib = seed()
        val calls = mutableListOf<Pair<String, String>>()
        val renamer = RomRenamer(paths, lib, covers, labels) { from, to -> calls += from.name to to.name; from.renameTo(to) }
        val mario = game(lib, "Mario (USA).sfc")

        assertEquals(RenameCheck.OK, renamer.check(mario, "MARIO (USA)"))
        val renamed = (renamer.rename(mario, "MARIO (USA)") as RenameResult.Done).rom

        assertEquals("SFC/MARIO (USA).sfc", renamed.key)
        assertEquals(RomRenamer.TMP_NAME, calls[0].second)
        assertEquals("MARIO (USA).sfc", calls[1].second)
        assertTrue(renamed.file.exists())
        assertTrue(lib.romMeta(renamed.key).favorite)
    }

    @Test
    fun `a failed move undoes every move before it and changes nothing else`() {
        for (failAt in 0 until 13) {
            root.listFiles()!!.forEach { it.deleteRecursively() }
            val lib = seed()
            var n = 0
            val renamer = RomRenamer(paths, lib, covers, labels) { from, to -> if (n++ == failAt) false else from.renameTo(to) }
            val mario = game(lib, "Mario (USA).sfc")
            val before = listOf(paths.roms(Console.SFC), paths.states(Console.SFC), paths.saves(Console.SFC), paths.playtime,
                paths.covers(Console.SFC), paths.labels(Console.SFC)).associateWith { names(it) }

            assertEquals("fail at $failAt", RenameResult.Failed, renamer.rename(mario, "Super Mario World"))

            before.forEach { (dir, list) -> assertEquals("fail at $failAt: $dir", list, names(dir)) }
            assertEquals(listOf(mario.key, "SFC/Zelda.sfc"), lib.roms.value.map { it.key })
            assertTrue(lib.romMeta(mario.key).favorite)
            assertTrue(labels.changes.isEmpty())
            labels.changes.clear()
        }
    }

    @Test
    fun `a case change whose second step fails puts the file back`() {
        val lib = seed()
        var n = 0
        // First move (to the temporary name) works, the second one fails.
        val renamer = RomRenamer(paths, lib, covers, labels) { from, to -> if (n++ == 1) false else from.renameTo(to) }
        val mario = game(lib, "Mario (USA).sfc")

        assertEquals(RenameResult.Failed, renamer.rename(mario, "mario (usa)"))

        assertTrue(File(paths.roms(Console.SFC), "Mario (USA).sfc").exists())
        assertFalse(File(paths.roms(Console.SFC), RomRenamer.TMP_NAME).exists())
    }

    @Test
    fun `a temporary name already taken refuses the case change`() {
        val lib = seed()
        put(paths.roms(Console.SFC), RomRenamer.TMP_NAME)
        val mario = game(lib, "Mario (USA).sfc")

        assertEquals(RenameResult.Failed, RomRenamer(paths, lib, covers, labels).rename(mario, "MARIO (USA)"))
        assertTrue(mario.file.exists())
    }

    @Test
    fun `metadata that cannot be saved undoes the rename`() {
        val lib = seed()
        val mario = game(lib, "Mario (USA).sfc")
        // The metadata is written through a .tmp file: a folder in its place makes the write throw.
        File(paths.meta.path + ".tmp").mkdirs()

        assertEquals(RenameResult.Failed, RomRenamer(paths, lib, covers, labels).rename(mario, "Super Mario World"))

        assertTrue(mario.file.exists())
        assertTrue(File(paths.states(Console.SFC), "Mario (USA).state.auto").exists())
        assertEquals(listOf(mario.key, "SFC/Zelda.sfc"), lib.roms.value.map { it.key })
        assertTrue(lib.romMeta(mario.key).favorite)
    }

    @Test
    fun `a mirror cover the new name would lose is kept as a synced file`() {
        put(paths.roms(Console.MD), "Sonic (USA).md")
        val lib = library()
        val sonic = game(lib, "Sonic (USA).md", Console.MD)
        covers.matches["Sonic (USA)"] = "https://example.invalid/Sonic.jpg?x=1"
        covers.cached = ByteArray(9)
        ArtSources(paths.covers(Console.MD)).recordUrl("Sonic (USA)", "https://example.invalid/Sonic.jpg?x=1")

        val renamed = (RomRenamer(paths, lib, covers, labels).rename(sonic, "My Sonic") as RenameResult.Done).rom

        val kept = File(paths.covers(Console.MD), "My Sonic.jpg")
        assertEquals(9L, kept.length())
        val entry = ArtSources(paths.covers(Console.MD)).matching("My Sonic", kept)!!
        assertEquals(ArtSource.SYNCED, entry.source)
        assertEquals(ArtProvider.MIRROR.id, entry.provider)
        assertEquals(kept, covers.localCover(renamed))
        assertTrue(labels.changes.none { it.first == renamed.key })
    }

    @Test
    fun `no cover is copied when the new name matches the same one, or nothing was cached`() {
        put(paths.roms(Console.MD), "Sonic (USA).md")
        put(paths.roms(Console.MD), "Streets.md")
        val lib = library()
        covers.matches["Sonic (USA)"] = "https://example.invalid/sonic.webp"
        covers.matches["Sonic"] = "https://example.invalid/sonic.webp"
        covers.cached = ByteArray(9)
        val renamer = RomRenamer(paths, lib, covers, labels)

        renamer.rename(game(lib, "Sonic (USA).md", Console.MD), "Sonic") as RenameResult.Done
        assertEquals(emptyList<String>(), names(paths.covers(Console.MD)))

        // No extension the covers use: kept as PNG; nothing in the cache: nothing kept.
        covers.matches["Streets"] = "https://example.invalid/streets"
        covers.cached = null
        renamer.rename(game(lib, "Streets.md", Console.MD), "Streets of Rage") as RenameResult.Done
        assertEquals(emptyList<String>(), names(paths.covers(Console.MD)))
    }

    @Test
    fun `a cover kept without an extension the covers use is a png`() {
        put(paths.roms(Console.MD), "Streets.md")
        val lib = library()
        covers.matches["Streets"] = "https://example.invalid/streets"
        covers.cached = ByteArray(4)

        RomRenamer(paths, lib, covers, labels).rename(game(lib, "Streets.md", Console.MD), "Streets of Rage") as RenameResult.Done

        assertEquals(listOf("Streets of Rage.png"), names(paths.covers(Console.MD)))
    }

    @Test
    fun `a game without an extension keeps none`() {
        // No console takes a bare name, so the scan never lists one: built by hand.
        val file = put(paths.roms(Console.SFC), "README")
        val lib = library()
        val rom = Rom("SFC/README", Console.SFC, file, "README")
        val renamed = (RomRenamer(paths, lib, covers, labels).rename(rom, "NOTES") as RenameResult.Done).rom

        assertEquals("NOTES", renamed.file.name)
        assertEquals(listOf("SFC/NOTES"), lib.roms.value.map { it.key })
    }

    @Test
    fun `a game sharing its base name with another one is not renamed alone`() {
        val lib = seed()
        put(paths.roms(Console.SFC), "Mario (USA).smc")
        runBlocking { lib.scan() }
        val renamer = RomRenamer(paths, lib, covers, labels)
        val mario = game(lib, "Mario (USA).sfc")

        assertEquals("Mario (USA).smc", renamer.sharer(mario)!!.file.name)
        assertEquals(RenameCheck.SHARED, renamer.check(mario, "Super Mario World"))
        assertEquals(RenameResult.Refused(RenameCheck.SHARED), renamer.rename(mario, "Super Mario World"))
        assertTrue(File(paths.states(Console.SFC), "Mario (USA).state.auto").exists())
        // A game whose base name is its own shares nothing.
        assertNull(renamer.sharer(game(lib, "Zelda.sfc")))
    }

    @Test
    fun `a rename waits for the artwork sync`() {
        val lib = seed()
        val renamer = RomRenamer(paths, lib, covers, labels)
        val mario = game(lib, "Mario (USA).sfc")

        covers.syncing = true
        assertEquals(RenameCheck.BUSY, renamer.check(mario, "Super Mario World"))
        covers.syncing = false
        labels.syncing = true
        assertEquals(RenameCheck.BUSY, renamer.check(mario, "Super Mario World"))
        labels.syncing = false
        assertEquals(RenameCheck.OK, renamer.check(mario, "Super Mario World"))
    }

    @Test
    fun `meta moves its entry and drops a stale one under the new key`() {
        val meta = Meta(roms = mapOf("A" to RomMeta(favorite = true), "B" to RomMeta(color = "x"), "C" to RomMeta()))
        assertEquals(mapOf("B" to RomMeta(favorite = true), "C" to RomMeta()), meta.renamedRom("A", "B").roms)
        assertEquals(mapOf("A" to RomMeta(favorite = true), "C" to RomMeta()), meta.renamedRom("Z", "B").roms)
    }

    @Test
    fun `art records move with the art and leave the others alone`() {
        val dir = paths.covers(Console.SFC)
        val sources = ArtSources(dir)
        sources.rename("A", "B") // no file yet: nothing to do
        assertFalse(sources.exists())
        val art = put(dir, "A.png")
        sources.record("A", art, ArtSource.MANUAL)
        sources.recordUrl("B", "https://example.invalid/b.png")
        sources.recordUrl("C", "https://example.invalid/c.png")

        sources.rename("Z", "Y") // neither exists: unchanged
        assertEquals(setOf("A", "B", "C"), sources.entries().keys)
        sources.rename("A", "B")
        assertEquals(setOf("B", "C"), sources.entries().keys)
        assertEquals("B.png", sources.entry("B")!!.file)
        // A stale record under the new name goes even when the old one has none.
        sources.rename("Q", "C")
        assertEquals(setOf("B"), sources.entries().keys)
        // A URL record has no file to rename.
        sources.recordUrl("U", "https://example.invalid/u.png")
        sources.rename("U", "V")
        assertNull(sources.entry("V")!!.file)
    }
}
