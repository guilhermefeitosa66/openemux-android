package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** meta.json is written off the caller's thread, once per burst of changes, and on demand (issue 920). */
class LibraryMetaWriterTest {
    private lateinit var root: File
    private lateinit var paths: Paths

    @Before
    fun setUp() {
        root = Files.createTempDirectory("library-meta").toFile()
        paths = Paths(File(root, "external"), File(root, "internal"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun favorites(): Set<String> {
        val roms = JSONObject(paths.meta.readText()).optJSONObject("roms") ?: return emptySet()
        return roms.keys().asSequence().filter { roms.getJSONObject(it).optBoolean("fav") }.toSet()
    }

    @Test
    fun aBurstOfChangesIsWrittenOnceAfterTheQuietTime() {
        val library = Library(paths, { true }, storageRetryMs = 0, metaDebounceMs = 150)
        repeat(10) { i -> library.updateRom("SFC/Game $i.sfc") { it.copy(favorite = true) } }
        // In memory at once; on disk only after the quiet time.
        assertTrue(library.romMeta("SFC/Game 9.sfc").favorite)
        assertFalse(paths.meta.exists())
        Thread.sleep(600)
        assertEquals((0 until 10).map { "SFC/Game $it.sfc" }.toSet(), favorites())
        val written = paths.meta.lastModified()
        Thread.sleep(400)
        assertEquals(written, paths.meta.lastModified())
    }

    @Test
    fun flushWritesWhatIsPendingNow() {
        val library = Library(paths, { true }, storageRetryMs = 0)
        library.updateRom("GB/Tetris.gb") { it.copy(favorite = true) }
        assertFalse(paths.meta.exists())
        library.flushMeta()
        assertEquals(setOf("GB/Tetris.gb"), favorites())
        // Nothing pending: a flush is a no-op, and the writer thread's flush lands too.
        library.flushMeta()
        library.updateRom("GB/Tetris.gb") { it.copy(favorite = false) }
        library.flushMeta(wait = false)
        Thread.sleep(300)
        assertEquals(emptySet<String>(), favorites())
    }

    @Test
    fun aProcessKilledInTheQuietTimeLosesOnlyTheLastChange() {
        val library = Library(paths, { true }, storageRetryMs = 0)
        library.updateRom("GB/A.gb") { it.copy(favorite = true) }
        library.flushMeta()
        library.updateRom("GB/B.gb") { it.copy(favorite = true) }
        // Not flushed, not waited for: the file holds the state before the last change, which only memory has.
        assertEquals(setOf("GB/A.gb"), favorites())
        assertTrue(library.romMeta("GB/B.gb").favorite)
        assertFalse(Library(paths, { true }, storageRetryMs = 0).romMeta("GB/B.gb").favorite)
    }

    @Test
    fun aRenameAndABulkDeleteWriteAtOnce() {
        val dir = paths.roms(Console.GB).apply { mkdirs() }
        File(dir, "A.gb").writeText("a")
        File(dir, "B.gb").writeText("b")
        val library = Library(paths, { true }, storageRetryMs = 0)
        kotlinx.coroutines.runBlocking { library.scan() }
        val a = library.roms.value.first { it.key == "GB/A.gb" }
        val b = library.roms.value.first { it.key == "GB/B.gb" }
        library.updateRom(a.key) { it.copy(favorite = true) }
        library.updateRom(b.key) { it.copy(favorite = true) }
        library.flushMeta()
        // The rename: its metadata is on disk under the new key before it returns.
        val renamed = a.copy(key = "GB/C.gb", file = File(dir, "C.gb"), title = "C")
        a.file.renameTo(renamed.file)
        library.renamed(a, renamed)
        assertEquals(setOf("GB/C.gb", "GB/B.gb"), favorites())
        // The bulk delete too.
        library.deleteAll(listOf(b))
        assertEquals(setOf("GB/C.gb"), favorites())
    }

    @Test
    fun withNoDebounceTheWriteIsSynchronous() {
        val library = Library(paths, { true }, storageRetryMs = 0, metaDebounceMs = 0)
        library.updateRom("GB/A.gb") { it.copy(favorite = true) }
        assertEquals(setOf("GB/A.gb"), favorites())
    }
}
