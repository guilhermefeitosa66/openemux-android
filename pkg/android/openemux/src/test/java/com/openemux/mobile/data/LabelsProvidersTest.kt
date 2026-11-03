package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The label sync follows the provider settings (issue 753). */
class LabelsProvidersTest {
    private val root = Files.createTempDirectory("labels").toFile()
    private val paths = Paths(File(root, "ext"), File(root, "int"))
    private val http = FakeHttp()
    private val rom = File(paths.roms(Console.GB), "Tetris (World).gb").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(16) { it.toByte() }) }
        .let { Rom("GB/${it.name}", Console.GB, it, "Tetris") }
    private fun labels(settings: ProviderSettings) = Labels(paths, ScreenScraper(http, { ScreenScraperAccount() }, "d", "p", pace = 0)) { settings }

    @Test
    fun `with ScreenScraper or its labels off, the sync asks nothing`() = runBlocking {
        labels(ProviderSettings().setEnabled(ArtProvider.SCREENSCRAPER, false)).sync(listOf(rom))
        labels(ProviderSettings().setKind(ArtProvider.SCREENSCRAPER, ArtKind.LABEL, false)).sync(listOf(rom))
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun `with them on, it looks the label up by hash and finds it`() = runBlocking {
        http.on("jeuInfos.php", FakeHttp.game(Triple("support-texture", "wor", "https://ss/l?1"))).on("https://ss/l?1", FakeHttp.png(3))
        val l = labels(ProviderSettings())
        l.sync(listOf(rom))
        assertTrue(http.asked.first().contains("crc="))
        val file = File(paths.labels(Console.GB), "Tetris (World).png")
        assertTrue(file.isFile)
        assertEquals(file, l.found.value[rom.key])
        assertEquals(ArtSource.SYNCED, ArtSources(paths.labels(Console.GB)).matching(rom.baseName, file)?.source)
    }

    @Test
    fun `an answer that is no image is not kept, a missing game is remembered`() = runBlocking {
        http.on("jeuInfos.php", FakeHttp.game(Triple("support-texture", "wor", "https://ss/l?1"))).on("https://ss/l?1", FakeHttp.ok("<html>" + "x".repeat(80)))
        labels(ProviderSettings()).sync(listOf(rom))
        assertFalse(File(paths.labels(Console.GB), "Tetris (World).png").exists())
        http.on("jeuInfos.php", HttpReply(404, null))
        labels(ProviderSettings()).sync(listOf(rom))
        assertTrue(File(paths.labels(Console.GB), "Tetris (World).none").exists())
    }
}
