package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class ArtworkTest {
    private val root = Files.createTempDirectory("artwork").toFile()
    private val paths = Paths(File(root, "ext"), File(root, "int"))
    private val clock = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()

    private val gb = rom(Console.GB, "Tetris (World).gb")
    private val ps = rom(Console.PS, "Wipeout (Europe).cue")

    private fun rom(console: Console, name: String): Rom {
        val file = File(paths.roms(console), name).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(4)) }
        return Rom("${console.id}/$name", console, file, file.nameWithoutExtension)
    }

    private class FakeCovers(private val paths: Paths) : CoverOps {
        override var syncing = false
        var match: String? = "https://example.invalid/cover.webp"
        var result = Fetch.FOUND
        var forgotten = 0
        override fun localCover(rom: Rom): File? = Covers.localCover(paths, rom.console, rom.baseName)
        override fun forgetMatch(rom: Rom) { forgotten++ }
        override fun mirrorUrl(rom: Rom): String? = match
        override suspend fun fetch(url: String): Fetch = result
    }

    private class FakeLabels(private val paths: Paths) : LabelOps {
        override var syncing = false
        var result = Fetch.FOUND
        val changes = mutableListOf<File?>()
        override fun labelFile(rom: Rom) = File(paths.labels(rom.console), "${rom.baseName}.png")
        override fun noneMarker(rom: Rom) = File(paths.labels(rom.console), "${rom.baseName}.none")
        override suspend fun fetch(rom: Rom, target: File): Fetch {
            if (result == Fetch.FOUND) target.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(7)) }
            return result
        }
        override fun changed(rom: Rom, file: File?) { changes += file }
    }

    private val covers = FakeCovers(paths)
    private val labels = FakeLabels(paths)
    private val artwork = Artwork(paths, covers, labels, File(root, "cache")) { clock }

    private fun localCover(rom: Rom, bytes: Int = 5) =
        File(paths.covers(rom.console), "${rom.baseName}.png").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    private fun label(rom: Rom, bytes: Int = 5) =
        File(paths.labels(rom.console), "${rom.baseName}.png").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun `the label exists only where a cartridge frame has a window for it`() {
        assertEquals(listOf(ArtKind.COVER, ArtKind.LABEL), artwork.kinds(Console.GB))
        assertEquals(listOf(ArtKind.COVER), artwork.kinds(Console.PS))
        // The detail screen's Artwork row speaks for the art the library shows.
        assertEquals(ArtKind.LABEL, artwork.primaryKind(Console.GB))
        assertEquals(ArtKind.COVER, artwork.primaryKind(Console.PS))
    }

    @Test
    fun `a mirror cover reads as automatic, dated once the sync recorded it`() {
        var state = artwork.describe(ps, ArtKind.COVER)
        assertEquals(ArtSource.SYNCED, state.source)
        assertNull(state.updated)
        assertFalse(state.removable)
        assertFalse(state.resettable)

        artwork.sources(Console.PS, ArtKind.COVER).recordUrl(ps.baseName, covers.match!!)
        state = artwork.describe(ps, ArtKind.COVER)
        assertEquals(clock, state.updated)
        assertEquals(covers.match, state.url)
    }

    @Test
    fun `a record for another mirror match is not believed`() {
        artwork.sources(Console.PS, ArtKind.COVER).recordUrl(ps.baseName, "https://example.invalid/old.webp")
        assertNull(artwork.describe(ps, ArtKind.COVER).updated)
    }

    @Test
    fun `no file and no mirror match is missing`() {
        covers.match = null
        val state = artwork.describe(ps, ArtKind.COVER)
        assertTrue(state.missing)
        assertFalse(state.resettable)
        assertTrue(artwork.describe(gb, ArtKind.LABEL).missing)
    }

    @Test
    fun `a file nothing vouches for is a local file`() {
        val file = localCover(gb)
        val state = artwork.describe(gb, ArtKind.COVER)
        assertEquals(ArtSource.LOCAL, state.source)
        assertEquals(file, state.file)
        assertTrue(state.removable)
        assertTrue(state.resettable)
    }

    @Test
    fun `manual art reads as manual until it is replaced by hand`() {
        val file = label(gb)
        artwork.sources(Console.GB, ArtKind.LABEL).record(gb.baseName, file, ArtSource.MANUAL, ArtSources.PROVIDER_SCREENSCRAPER, "search")
        val state = artwork.describe(gb, ArtKind.LABEL)
        assertEquals(ArtSource.MANUAL, state.source)
        assertEquals(clock, state.updated)
        assertEquals(ArtSources.PROVIDER_SCREENSCRAPER, state.provider)

        file.writeBytes(ByteArray(50))
        assertEquals(ArtSource.LOCAL, artwork.describe(gb, ArtKind.LABEL).source)
    }

    @Test
    fun `manual art survives a restart`() {
        val file = label(gb)
        artwork.sources(Console.GB, ArtKind.LABEL).record(gb.baseName, file, ArtSource.MANUAL)
        val restarted = Artwork(paths, FakeCovers(paths), FakeLabels(paths), File(root, "cache"))
        assertEquals(ArtSource.MANUAL, restarted.describe(gb, ArtKind.LABEL).source)
    }

    @Test
    fun `sync now records a found cover and bumps the changes`() = runBlocking {
        covers.result = Fetch.FOUND
        assertEquals(ArtOutcome.DONE, artwork.syncNow(ps, ArtKind.COVER))
        assertEquals(1, covers.forgotten)
        assertEquals(clock, artwork.describe(ps, ArtKind.COVER).updated)
        assertEquals(1L, artwork.changes.value)
    }

    @Test
    fun `sync now says what went wrong with a cover`() = runBlocking {
        covers.result = Fetch.MISSING
        assertEquals(ArtOutcome.NOT_FOUND, artwork.syncNow(ps, ArtKind.COVER))
        covers.result = Fetch.UNAVAILABLE
        assertEquals(ArtOutcome.UNAVAILABLE, artwork.syncNow(ps, ArtKind.COVER))
        covers.match = null
        assertEquals(ArtOutcome.NOT_FOUND, artwork.syncNow(ps, ArtKind.COVER))
        assertEquals(0L, artwork.changes.value)
    }

    @Test
    fun `sync now fetches a label past its none marker`() = runBlocking {
        labels.noneMarker(gb).apply { parentFile!!.mkdirs(); createNewFile() }
        assertEquals(ArtOutcome.DONE, artwork.syncNow(gb, ArtKind.LABEL))
        assertFalse(labels.noneMarker(gb).exists())
        val state = artwork.describe(gb, ArtKind.LABEL)
        assertEquals(ArtSource.SYNCED, state.source)
        assertEquals(ArtSources.PROVIDER_SCREENSCRAPER, state.provider)
        assertEquals(listOf(labels.labelFile(gb)), labels.changes)
    }

    @Test
    fun `sync now notes a label that does not exist`() = runBlocking {
        labels.result = Fetch.MISSING
        assertEquals(ArtOutcome.NOT_FOUND, artwork.syncNow(gb, ArtKind.LABEL))
        assertTrue(labels.noneMarker(gb).exists())
        labels.result = Fetch.UNAVAILABLE
        assertEquals(ArtOutcome.UNAVAILABLE, artwork.syncNow(gb, ArtKind.LABEL))
    }

    @Test
    fun `nothing is synced or reset while a library sync runs`() = runBlocking {
        val file = localCover(gb)
        covers.syncing = true
        assertEquals(ArtOutcome.BUSY, artwork.reset(gb, ArtKind.COVER))
        assertEquals(ArtOutcome.BUSY, artwork.syncNow(gb, ArtKind.COVER))
        covers.syncing = false
        labels.syncing = true
        assertEquals(ArtOutcome.BUSY, artwork.reset(gb, ArtKind.LABEL))
        assertTrue(file.exists())
    }

    @Test
    fun `removing a label leaves no none marker and drops its record`() = runBlocking {
        val file = label(gb)
        artwork.sources(Console.GB, ArtKind.LABEL).record(gb.baseName, file, ArtSource.MANUAL)
        assertEquals(ArtOutcome.DONE, artwork.remove(gb, ArtKind.LABEL))
        assertFalse(file.exists())
        assertFalse(labels.noneMarker(gb).exists())
        assertNull(artwork.sources(Console.GB, ArtKind.LABEL).entry(gb.baseName))
        assertEquals(listOf<File?>(null), labels.changes)
        assertTrue(artwork.describe(gb, ArtKind.LABEL).missing)
    }

    @Test
    fun `removing a kept cover falls back to the mirror's`() = runBlocking {
        localCover(gb)
        assertEquals(ArtOutcome.DONE, artwork.remove(gb, ArtKind.COVER))
        assertEquals(ArtSource.SYNCED, artwork.describe(gb, ArtKind.COVER).source)
        assertEquals(1, covers.forgotten)
    }

    @Test
    fun `removing and resetting a cover delete every suffix kept for it`() = runBlocking {
        val png = localCover(gb)
        val jpg = File(paths.covers(gb.console), "${gb.baseName}.jpg").apply { writeBytes(ByteArray(3)) }
        assertEquals(ArtOutcome.DONE, artwork.remove(gb, ArtKind.COVER))
        assertFalse(png.exists())
        assertFalse(jpg.exists())
        localCover(gb)
        jpg.writeBytes(ByteArray(3))
        assertEquals(ArtOutcome.DONE, artwork.reset(gb, ArtKind.COVER))
        assertNull(covers.localCover(gb))
    }

    @Test
    fun `there is nothing to remove from a mirror cover`() = runBlocking {
        assertEquals(ArtOutcome.NOT_FOUND, artwork.remove(ps, ArtKind.COVER))
    }

    @Test
    fun `reset replaces a kept cover with the mirror's`() = runBlocking {
        val file = localCover(gb)
        artwork.sources(Console.GB, ArtKind.COVER).record(gb.baseName, file, ArtSource.MANUAL)
        assertEquals(ArtOutcome.DONE, artwork.reset(gb, ArtKind.COVER))
        assertFalse(file.exists())
        val state = artwork.describe(gb, ArtKind.COVER)
        assertEquals(ArtSource.SYNCED, state.source)
        assertEquals(clock, state.updated)
    }

    @Test
    fun `a reset that finds nothing keeps the current cover`() = runBlocking {
        val file = localCover(gb)
        covers.result = Fetch.MISSING
        assertEquals(ArtOutcome.NOT_FOUND, artwork.reset(gb, ArtKind.COVER))
        covers.result = Fetch.UNAVAILABLE
        assertEquals(ArtOutcome.UNAVAILABLE, artwork.reset(gb, ArtKind.COVER))
        covers.match = null
        assertEquals(ArtOutcome.NOT_FOUND, artwork.reset(gb, ArtKind.COVER))
        assertTrue(file.exists())
        assertEquals(ArtSource.LOCAL, artwork.describe(gb, ArtKind.COVER).source)
    }

    @Test
    fun `reset replaces a label only when one is found`() = runBlocking {
        val file = label(gb, bytes = 3)
        artwork.sources(Console.GB, ArtKind.LABEL).record(gb.baseName, file, ArtSource.MANUAL)
        labels.result = Fetch.MISSING
        assertEquals(ArtOutcome.NOT_FOUND, artwork.reset(gb, ArtKind.LABEL))
        assertEquals(3L, file.length())
        assertEquals(ArtSource.MANUAL, artwork.describe(gb, ArtKind.LABEL).source)
        labels.result = Fetch.UNAVAILABLE
        assertEquals(ArtOutcome.UNAVAILABLE, artwork.reset(gb, ArtKind.LABEL))
        assertEquals(3L, file.length())

        labels.result = Fetch.FOUND
        assertEquals(ArtOutcome.DONE, artwork.reset(gb, ArtKind.LABEL))
        assertEquals(7L, file.length())
        assertEquals(ArtSource.SYNCED, artwork.describe(gb, ArtKind.LABEL).source)
        assertTrue("the scratch file is cleaned up", File(root, "cache").listFiles().isNullOrEmpty())
    }

    @Test
    fun `the label sync vouches for labels from before records, once`() = runBlocking {
        val existing = label(gb)
        val sync = Labels(paths, ScreenScraper(FakeHttp(), { ScreenScraperAccount() }, "", ""))
        sync.sync(listOf(gb, ps))
        val state = artwork.describe(gb, ArtKind.LABEL)
        assertEquals(ArtSource.SYNCED, state.source)
        assertNull("its date is not known", state.updated)

        // From then on a label nobody recorded (received through Share nearby) is a local file.
        val other = rom(Console.GB, "Other (World).gb")
        label(other)
        sync.sync(listOf(gb, other))
        assertEquals(ArtSource.LOCAL, artwork.describe(other, ArtKind.LABEL).source)
        assertNotNull(existing)
    }

    @Test
    fun `the label sync leaves manual art alone`() = runBlocking {
        val file = label(gb)
        artwork.sources(Console.GB, ArtKind.LABEL).record(gb.baseName, file, ArtSource.MANUAL)
        Labels(paths, ScreenScraper(FakeHttp(), { ScreenScraperAccount() }, "", "")).sync(listOf(gb))
        assertEquals(ArtSource.MANUAL, artwork.describe(gb, ArtKind.LABEL).source)
        assertEquals(5L, file.length())
    }

    @Test
    fun `a chosen cover is saved as manual, in its format, and the other suffixes go`() = runBlocking {
        File(paths.covers(ps.console), "${ps.baseName}.png").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(3)) }
        val before = artwork.changes.value
        val outcome = artwork.saveManual(ps, ArtKind.COVER, ByteArray(9) { 1 }, "JPG", "libretro", ArtCandidate.ORIGIN_SEARCH)
        assertEquals(ArtOutcome.DONE, outcome)
        val saved = File(paths.covers(ps.console), "${ps.baseName}.jpg")
        assertTrue(saved.isFile)
        assertFalse(File(paths.covers(ps.console), "${ps.baseName}.png").exists())
        assertFalse(File(paths.covers(ps.console), "${ps.baseName}.jpg.tmp").exists())
        assertEquals(1, covers.forgotten)
        assertEquals(before + 1, artwork.changes.value)
        val state = artwork.describe(ps, ArtKind.COVER)
        assertEquals(ArtSource.MANUAL, state.source)
        assertEquals("libretro", state.provider)
        assertEquals(clock, state.updated)
        assertEquals("search", artwork.sources(ps.console, ArtKind.COVER).entry(ps.baseName)!!.origin)
    }

    @Test
    fun `a chosen label replaces the none marker and tells the library`() = runBlocking {
        labels.noneMarker(gb).apply { parentFile!!.mkdirs(); createNewFile() }
        assertEquals(ArtOutcome.DONE, artwork.saveManual(gb, ArtKind.LABEL, ByteArray(6), "png", null, ArtCandidate.ORIGIN_FILE))
        assertFalse(labels.noneMarker(gb).exists())
        assertEquals(listOf<File?>(labels.labelFile(gb)), labels.changes)
        val state = artwork.describe(gb, ArtKind.LABEL)
        assertEquals(ArtSource.MANUAL, state.source)
        assertNull(state.provider)
        assertEquals("file", artwork.sources(gb.console, ArtKind.LABEL).entry(gb.baseName)!!.origin)
    }

    @Test
    fun `a manual save survives a later sync batch`() = runBlocking {
        val sources = artwork.sources(gb.console, ArtKind.LABEL)
        sources.batch { b ->
            // The sync buffered the old label, then the user chose another one before the batch was written.
            val old = label(gb, 4)
            b.record(gb.baseName, old, ArtSource.SYNCED)
            Thread.sleep(5)
            kotlinx.coroutines.runBlocking { artwork.saveManual(gb, ArtKind.LABEL, ByteArray(11), "png", "screenscraper", ArtCandidate.ORIGIN_SEARCH) }
        }
        assertEquals(ArtSource.MANUAL, artwork.describe(gb, ArtKind.LABEL).source)
    }

    @Test
    fun `a save that cannot be written changes nothing`() = runBlocking {
        // A directory where the file should go: the rename cannot replace it.
        File(paths.covers(ps.console), "${ps.baseName}.png").mkdirs()
        File(paths.covers(ps.console), "${ps.baseName}.png/keep").writeBytes(ByteArray(1))
        val before = artwork.changes.value
        assertEquals(ArtOutcome.UNAVAILABLE, artwork.saveManual(ps, ArtKind.COVER, ByteArray(4), "png", null, ArtCandidate.ORIGIN_FILE))
        assertEquals(before, artwork.changes.value)
        assertNull(artwork.sources(ps.console, ArtKind.COVER).entry(ps.baseName))
    }

    @Test
    fun `a synced cover says which provider served it`() {
        covers.match = Covers.urlOf(ArtProvider.LIBRETRO, Console.PS, "Wipeout (Europe)")
        assertEquals("libretro", artwork.describe(ps, ArtKind.COVER).provider)
        covers.match = Covers.urlOf(ArtProvider.MIRROR, Console.PS, "Wipeout (Europe)")
        assertEquals("openemux", artwork.describe(ps, ArtKind.COVER).provider)
    }
}
