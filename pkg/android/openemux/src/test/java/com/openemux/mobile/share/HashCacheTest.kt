package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The SHA-256 cache behind Share nearby's offers (issue 921). */
class HashCacheTest {
    private val dir = Files.createTempDirectory("hash-cache").toFile()
    private val store = File(dir, "share/hashes.json")

    @Test
    fun `a remembered file is found by path, size and mtime, and a changed one is not`() {
        val cache = HashCache(store)
        assertNull(cache.lookup("/a", 3, 1000))
        cache.put("/a", 3, 1000, "abc")
        assertEquals("abc", cache.lookup("/a", 3, 1000))
        assertNull(cache.lookup("/a", 4, 1000))
        assertNull(cache.lookup("/a", 3, 1001))
        assertNull(cache.lookup("/b", 3, 1000))
        assertEquals(1, cache.hits)
        assertEquals(4, cache.misses)
    }

    @Test
    fun `saving keeps the files that exist and drops the ones that are gone`() {
        val kept = File(dir, "kept.sfc").put("rom")
        val cache = HashCache(store)
        cache.put(kept.path, kept.length(), kept.lastModified(), "k")
        cache.put(File(dir, "gone.sfc").path, 1, 1, "g")
        cache.save()
        assertTrue(store.isFile)
        assertFalse(File(store.path + ".tmp").exists())
        val again = HashCache(store)
        assertEquals(1, again.size)
        assertEquals("k", again.lookup(kept.path, kept.length(), kept.lastModified()))
    }

    @Test
    fun `nothing is written when nothing changed`() {
        val cache = HashCache(store)
        cache.save()
        assertFalse(store.exists())
        cache.put("/x", 1, 1, "x")
        cache.save()
        // "/x" is no file: dropped on save, so the store holds no entry.
        assertEquals(0, HashCache(store).size)
    }

    @Test
    fun `a malformed store is replaced instead of failing the offer`() {
        store.parentFile.mkdirs()
        store.writeText("{not json")
        val cache = HashCache(store)
        assertEquals(0, cache.size)
        assertNull(cache.lookup("/a", 1, 1))
        cache.save()
        assertEquals("{}", store.readText())
    }

    @Test
    fun `the store lives under the share folder of the internal files`() {
        val lib = FakeLibrary()
        val cache = HashCache.of(lib.paths)
        val f = lib.rom(Console.SFC, "Game.sfc").file
        cache.put(f.path, f.length(), f.lastModified(), "h")
        cache.save()
        assertTrue(File(lib.paths.internal, "share/hashes.json").isFile)
    }
}
