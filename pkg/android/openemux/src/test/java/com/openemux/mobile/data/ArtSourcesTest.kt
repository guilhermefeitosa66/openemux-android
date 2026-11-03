package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant

class ArtSourcesTest {
    private val dir = Files.createTempDirectory("art-sources").toFile()
    private val clock = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()
    private val sources = ArtSources(dir) { clock }

    private fun art(name: String, bytes: Int = 10): File = File(dir, name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `a record round-trips in the desktop's schema`() {
        val file = art("Tetris (World).png")
        sources.record("Tetris (World)", file, ArtSource.MANUAL, provider = "screenscraper", origin = "search")

        val json = JSONObject(sources.file.readText())
        assertEquals(1, json.getInt("version"))
        val raw = json.getJSONObject("entries").getJSONObject("Tetris (World)")
        assertEquals("manual", raw.getString("source"))
        assertEquals("Tetris (World).png", raw.getString("file"))
        assertEquals("2026-11-02T12:00:00Z", raw.getString("updated"))
        val entry = sources.matching("Tetris (World)", file)!!
        assertEquals(ArtSource.MANUAL, entry.source)
        assertEquals(clock, entry.updated)
        assertEquals("screenscraper", entry.provider)
        assertEquals("search", entry.origin)
    }

    @Test
    fun `art replaced by hand no longer matches its record`() {
        val file = art("Game.png")
        sources.record("Game", file, ArtSource.SYNCED)
        file.writeBytes(ByteArray(20))
        assertNull(sources.matching("Game", file))
    }

    @Test
    fun `art touched to another time no longer matches`() {
        val file = art("Game.png")
        sources.record("Game", file, ArtSource.SYNCED)
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(file.lastModified() - 60_000))
        assertNull(sources.matching("Game", file))
    }

    @Test
    fun `a record names its own file`() {
        val png = art("Game.png")
        sources.record("Game", png, ArtSource.SYNCED)
        assertNull(sources.matching("Game", art("Game.jpg")))
    }

    @Test
    fun `art that cannot be read records nothing`() {
        sources.record("Game", File(dir, "absent.png"), ArtSource.SYNCED)
        assertFalse(sources.exists())
    }

    @Test
    fun `a damaged or unknown file reads as no records`() {
        dir.mkdirs()
        sources.file.writeText("{not json")
        assertTrue(sources.entries().isEmpty())
        sources.file.writeText("""{"version": 2, "entries": {"Game": {"source": "synced"}}}""")
        assertTrue(sources.entries().isEmpty())
        sources.file.writeText("""{"version": 1, "entries": {"Game": {"source": "stolen"}, "Other": 3}}""")
        assertTrue(sources.entries().isEmpty())
    }

    @Test
    fun `a damaged file is replaced by the next record`() {
        dir.mkdirs()
        sources.file.writeText("garbage")
        sources.record("Game", art("Game.png"), ArtSource.SYNCED)
        assertEquals(setOf("Game"), sources.entries().keys)
    }

    @Test
    fun `forget drops one entry and leaves the rest`() {
        sources.record("A", art("A.png"), ArtSource.SYNCED)
        sources.record("B", art("B.png"), ArtSource.MANUAL)
        sources.forget("A")
        assertEquals(setOf("B"), sources.entries().keys)
    }

    @Test
    fun `forget without a records file writes none`() {
        sources.forget("A")
        assertFalse(sources.exists())
    }

    @Test
    fun `a mirror cover is recorded by its url`() {
        sources.recordUrl("Game", "https://example.invalid/Game.webp")
        val entry = sources.entry("Game")!!
        assertEquals(ArtSource.SYNCED, entry.source)
        assertEquals("https://example.invalid/Game.webp", entry.url)
        assertEquals(ArtSources.PROVIDER_MIRROR, entry.provider)
        assertNull(entry.file)
    }

    @Test
    fun `an undated record has no date`() {
        val file = art("Game.png")
        sources.record("Game", file, ArtSource.SYNCED, dated = false)
        assertNull(sources.matching("Game", file)!!.updated)
    }

    @Test
    fun `a batch writes once and keeps what it was given`() {
        val a = art("A.png")
        val b = art("B.png")
        sources.batch { batch ->
            batch.record("A", a, ArtSource.SYNCED)
            batch.record("B", b, ArtSource.SYNCED)
            batch.recordUrl("C", "https://example.invalid/C.webp")
            assertFalse("nothing is written before the end", sources.exists())
        }
        assertEquals(setOf("A", "B", "C"), sources.entries().keys)
    }

    @Test
    fun `a batch drops an entry whose file changed meanwhile`() {
        val a = art("A.png")
        sources.batch { batch ->
            batch.record("A", a, ArtSource.SYNCED)
            // The user saves other art for the same game while the sync runs.
            a.writeBytes(ByteArray(99))
            sources.record("A", a, ArtSource.MANUAL)
        }
        assertEquals(ArtSource.MANUAL, sources.matching("A", a)!!.source)
    }

    @Test
    fun `a batch never puts a mirror url over a file's record`() {
        val a = art("A.png")
        sources.batch { batch ->
            batch.recordUrl("A", "https://example.invalid/A.webp")
            sources.record("A", a, ArtSource.MANUAL)
        }
        assertEquals(ArtSource.MANUAL, sources.matching("A", a)!!.source)
    }

    @Test
    fun `a batch also writes when its block throws`() {
        val a = art("A.png")
        runCatching { sources.batch { batch -> batch.record("A", a, ArtSource.SYNCED); error("cancelled") } }
        assertEquals(setOf("A"), sources.entries().keys)
    }

    @Test
    fun `an empty batch writes nothing`() {
        sources.batch { }
        assertFalse(sources.exists())
    }

    @Test
    fun `stamps that do not parse read as unknown`() {
        assertNull(ArtSources.parseStamp(null))
        assertNull(ArtSources.parseStamp(""))
        assertNull(ArtSources.parseStamp("yesterday"))
        assertEquals(clock, ArtSources.parseStamp("2026-11-02T12:00:00Z"))
    }
}
