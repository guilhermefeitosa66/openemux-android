package com.openemux.mobile.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The cartridge pictures kept between launches (issue 917). */
class CartCacheTest {
    private val root = Files.createTempDirectory("cart-cache").toFile()

    @Test
    fun `a picture is written aside, kept and found again`() {
        val cache = CartCache(root)
        assertNull(cache.lookup("FC/Game.nes|red"))
        val file = cache.keep("FC/Game.nes|red") { it.writeBytes(byteArrayOf(9)) }
        assertTrue(file.isFile && file.name.endsWith(".png"))
        assertEquals(file, cache.lookup("FC/Game.nes|red"))
        assertNull(cache.lookup("FC/Game.nes|blue"))
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `only the newest pictures are kept`() {
        val cache = CartCache(root, keep = 2)
        (1..3).forEach { i -> cache.keep("k$i") { it.writeBytes(byteArrayOf(1)) }.setLastModified(1_000_000L + i * 10_000) }
        cache.keep("k4") { it.writeBytes(byteArrayOf(1)) }
        assertEquals(2, root.listFiles { f -> f.name.endsWith(".png") }!!.size)
        assertNull(cache.lookup("k1"))
        assertNull(cache.lookup("k2"))
    }
}
