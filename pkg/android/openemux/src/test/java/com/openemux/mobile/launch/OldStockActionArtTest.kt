package com.openemux.mobile.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** A copy of a bundled skin keeps the old save / load arrows; the renderer swaps that stock art for the floppy (issue 945). */
class OldStockActionArtTest {
    // The GBA package's phone-portrait save.png as the app shipped it before issue 945.
    private val old = File("src/test/resources/old-stock/save.png").readBytes()
    // The silver GC package's fold-landscape save.webp (a WebP package, issue 806) as it shipped before issue 945.
    private val oldWebp = File("src/test/resources/old-stock/save.webp").readBytes()

    @Test fun theOldStockSaveAndLoadArtIsKnown() {
        assertEquals(229, OldStockActionArt.HASHES.size)
        assertTrue(OldStockActionArt.isOld("save_state") { old.inputStream() })
        assertTrue(OldStockActionArt.isOld("load_state") { old.inputStream() })
        assertTrue(OldStockActionArt.isOld("save_state") { oldWebp.inputStream() })
    }

    @Test fun onlySaveAndLoadAreLookedAt() {
        assertFalse(OldStockActionArt.isOld("turbo_fire_toggle") { old.inputStream() })
        assertFalse(OldStockActionArt.isOld(null) { old.inputStream() })
    }

    @Test fun thePlayersOwnArtAndUnreadableFilesStay() {
        assertFalse(OldStockActionArt.isOld("save_state") { (old + 0).inputStream() })
        assertFalse(OldStockActionArt.isOld("save_state") { null })
        assertFalse(OldStockActionArt.isOld("save_state") { throw IOException("gone") })
    }

    @Test fun noBundledSkinTheAppShipsNowIsTakenForTheOldArt() {
        val zips = File("src/main/assets/skins").walk().filter { it.name.endsWith(".zip") }.toList()
        assertTrue(zips.isNotEmpty())
        zips.forEach { zip ->
            ZipFile(zip).use { z ->
                z.entries().asSequence().filter { e -> listOf("/save.png", "/load.png", "/save.webp", "/load.webp").any { e.name.endsWith(it) } }.forEach { e ->
                    assertFalse("${zip.name}:${e.name}", OldStockActionArt.isOld("save_state") { z.getInputStream(e) })
                }
            }
        }
    }
}
