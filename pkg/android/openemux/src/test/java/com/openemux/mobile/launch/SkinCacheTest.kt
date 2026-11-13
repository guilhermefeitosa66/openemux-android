package com.openemux.mobile.launch

import com.openemux.mobile.data.NdsLayouts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The skins kept between launches (issue 917). */
class SkinCacheTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("skin-cache").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private val layout = { orientation: String, id: String -> NdsLayouts.find(orientation, id) }

    private fun draw(name: String = "skin.cfg", packaged: Boolean = false, nds: Boolean = false): (File) -> GameSkin.Drawn = { dir ->
        dir.mkdirs()
        File(dir, name).writeText("overlays = 1\noverlay0_overlay = \"page.png\"\n")
        File(dir, "page.png").writeBytes(byteArrayOf(1, 2, 3))
        GameSkin.Drawn(File(dir, name), packaged, if (nds) NdsLayouts.default("portrait") to NdsLayouts.default("landscape") else null)
    }

    @Test
    fun `a key names its inputs and two keys with the same text share an id`() {
        val a = SkinCache.Key("console=FC\nsize=1080x2400x0")
        val b = SkinCache.Key("console=FC\nsize=1080x2400x0")
        val c = SkinCache.Key("console=FC\nsize=1080x2400x1")
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, c.id)
        assertEquals(24, a.id.length)
    }

    @Test
    fun `a miss draws and the next lookup finds the drawn skin in place`() {
        val cache = SkinCache(root)
        val key = SkinCache.Key("console=FC")
        assertNull(cache.lookup(key, layout))
        var draws = 0
        val drawn = cache.render(key) { dir -> draws++; draw(packaged = true, nds = true)(dir) }
        assertEquals(1, draws)
        assertTrue(drawn.file.isFile)
        assertEquals(File(root, key.id), drawn.file.parentFile.parentFile)
        assertTrue(File(drawn.file.parentFile, "page.png").isFile)
        assertFalse(File(root, "${key.id}.tmp").exists())

        val found = cache.lookup(key, layout)!!
        assertEquals(drawn.file, found.file)
        assertTrue(found.packaged)
        assertEquals(NdsLayouts.default("portrait").id, found.ndsLayouts!!.first.id)
        assertEquals(NdsLayouts.default("landscape").id, found.ndsLayouts!!.second.id)
        assertEquals(1, cache.size)
    }

    @Test
    fun `a key with another text is another entry, even on the same id prefix`() {
        val cache = SkinCache(root)
        val key = SkinCache.Key("console=FC\ngamepad=false")
        cache.render(key, draw())
        assertNull(cache.lookup(SkinCache.Key("console=FC\ngamepad=true"), layout))
        // The note's key is checked, not only the id: a stale note is a miss.
        File(File(root, key.id), "drawn.txt").writeText("key=other\nfile=skin/skin.cfg\npackaged=false\n")
        assertNull(cache.lookup(key, layout))
    }

    @Test
    fun `an entry without its file or its note is a miss`() {
        val cache = SkinCache(root)
        val key = SkinCache.Key("console=GB")
        val drawn = cache.render(key, draw())
        drawn.file.delete()
        assertNull(cache.lookup(key, layout))
        cache.render(key, draw())
        File(File(root, key.id), "drawn.txt").delete()
        assertNull(cache.lookup(key, layout))
    }

    @Test
    fun `only the newest entries are kept, and a hit counts as new`() {
        val cache = SkinCache(root, keep = 2)
        val keys = (1..3).map { SkinCache.Key("console=$it") }
        keys.forEachIndexed { i, k -> cache.render(k, draw()); File(root, k.id).setLastModified(1_000_000L + i * 10_000) }
        // Three rendered with keep = 2: the oldest is gone once the third lands.
        cache.render(SkinCache.Key("console=4"), draw())
        assertEquals(2, cache.size)
        assertNull(cache.lookup(keys[0], layout))
        assertNull(cache.lookup(keys[1], layout))
        // A hit on the third makes it the newest; the fourth goes next.
        File(root, keys[2].id).setLastModified(1_000_000L)
        File(root, SkinCache.Key("console=4").id).setLastModified(2_000_000L)
        cache.lookup(keys[2], layout)
        cache.render(SkinCache.Key("console=5"), draw())
        assertTrue(cache.lookup(keys[2], layout) != null)
        assertNull(cache.lookup(SkinCache.Key("console=4"), layout))
    }

    @Test
    fun `a signature follows a skin file or folder and its changes`() {
        assertEquals("", SkinCache.signature(null))
        val zip = File(root, "skin.zip").apply { writeBytes(ByteArray(10)); setLastModified(1_700_000_000_000) }
        val before = SkinCache.signature(zip)
        zip.writeBytes(ByteArray(11)); zip.setLastModified(1_700_000_000_000)
        assertNotEquals(before, SkinCache.signature(zip))
        val folder = File(root, "skin").apply { mkdirs() }
        File(folder, "skin.json").writeText("{}")
        File(folder, "a.png").writeBytes(ByteArray(3))
        val one = SkinCache.signature(folder)
        File(folder, "b.png").writeBytes(ByteArray(3))
        assertNotEquals(one, SkinCache.signature(folder))
        assertTrue("a.png" in SkinCache.signature(folder) && "b.png" in SkinCache.signature(folder))
    }
}
