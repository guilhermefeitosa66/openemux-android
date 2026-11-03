package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoversUrlTest {
    @Test
    fun `the mirror URL keeps the list's name, encoded like android's Uri`() {
        assertEquals(
            "https://raw.githubusercontent.com/guilhermefeitosa66/openemux-artwork/main/Nintendo_-_Game_Boy/Tetris%20(World).webp",
            Covers.urlOf(ArtProvider.MIRROR, Console.GB, "Tetris (World)"),
        )
        assertEquals("Tunnels%20%26%20Trolls%20(1983)", Covers.encode("Tunnels & Trolls (1983)"))
        assertEquals("Pok%C3%A9mon_-!.~'*", Covers.encode("Pokémon_-!.~'*"))
    }

    @Test
    fun `the libretro URL uses the system's name and its own file-name rules`() {
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/Tom%20_%20Jerry%20(USA).png",
            Covers.urlOf(ArtProvider.LIBRETRO, Console.GB, "Tom & Jerry (USA)"),
        )
        assertEquals("a_b_c_d_e_f_g_h_i_j_", Covers.libretroName("a&b*c/d:e`f<g>h?i\\j|") + "")
        assertEquals("q_", Covers.libretroName("q\""))
        assertNull(Covers.urlOf(ArtProvider.SCREENSCRAPER, Console.GB, "Tetris"))
    }

    @Test
    fun `a synced URL tells its provider`() {
        assertEquals("libretro", Covers.providerOf(Covers.urlOf(ArtProvider.LIBRETRO, Console.GB, "X")!!))
        assertEquals("openemux", Covers.providerOf(Covers.urlOf(ArtProvider.MIRROR, Console.GB, "X")!!))
    }
}
