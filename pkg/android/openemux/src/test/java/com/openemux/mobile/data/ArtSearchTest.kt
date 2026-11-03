package com.openemux.mobile.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArtSearchTest {
    private val rom = Rom("GB/Tetris (World).gb", Console.GB, File("/nowhere/Tetris (World).gb"), "Tetris")
    private val http = FakeHttp()
    private val ssHttp = FakeHttp()
    private var dev = "dev"
    private val ss get() = ScreenScraper(ssHttp, { ScreenScraperAccount() }, dev, "pw", pace = 0)
    private var settings = ProviderSettings()
    private val titles = object : TitleSource {
        val names = listOf("Tetris (World)", "Tetris (Japan)", "Tetris 2 (USA)", "Tennis (World)")
        override fun titles(console: Console) = names
        override fun titlesFor(console: Console, typed: String) = names.filter { Covers.normalize(it) == Covers.normalize(typed) }
    }
    private var decodable: (ByteArray) -> Boolean = { true }
    private fun search() = ArtSearch(http, ss, titles, { settings }, { decodable(it) })

    private fun found(events: List<SearchEvent>) = events.filterIsInstance<SearchEvent.Found>().map { it.candidate }
    private fun issue(events: List<SearchEvent>) = events.filterIsInstance<SearchEvent.Issue>().singleOrNull()?.issue

    @Test
    fun `providers are asked in the settings' order, and an off one not at all`() = runBlocking {
        http.on("thumbnails.libretro.com", FakeHttp.png(1)).on("openemux-artwork", FakeHttp.png(2))
        ssHttp.on("jeuInfos.php", FakeHttp.game(Triple("box-2D", "wor", "https://ss/m?1")))
        ssHttp.on("https://ss/m?1", FakeHttp.png(3))
        settings = ProviderSettings().move(ArtProvider.LIBRETRO, -2)
        val events = search().search(rom, ArtKind.COVER, "Tetris", null).toList()
        assertEquals(listOf(ArtProvider.LIBRETRO, ArtProvider.MIRROR, ArtProvider.SCREENSCRAPER), found(events).map { it.provider })
        assertNull(issue(events))

        settings = ProviderSettings().setEnabled(ArtProvider.MIRROR, false).setEnabled(ArtProvider.SCREENSCRAPER, false)
        http.asked.clear()
        val only = found(search().search(rom, ArtKind.COVER, "Tetris", null).toList())
        assertEquals(listOf(ArtProvider.LIBRETRO), only.map { it.provider })
        assertTrue(http.asked.none { "openemux-artwork" in it })
    }

    @Test
    fun `the same bytes never show twice, and each provider stops at four`() = runBlocking {
        settings = ProviderSettings().setEnabled(ArtProvider.SCREENSCRAPER, false)
        var n = 0
        val varied = object : HttpGet {
            override suspend fun get(url: String) = if ("openemux-artwork" in url) FakeHttp.png(1) else FakeHttp.png(10 + n++)
        }
        val many = object : TitleSource {
            override fun titles(console: Console) = emptyList<String>()
            override fun titlesFor(console: Console, typed: String) = (1..7).map { "Tetris $it" }
        }
        val events = ArtSearch(varied, ss, many, { settings }).search(rom, ArtKind.COVER, "Tetris", null).toList()
        val list = found(events)
        // The mirror's answers are all the same bytes: one result. libretro's differ: four.
        assertEquals(1, list.count { it.provider == ArtProvider.MIRROR })
        assertEquals(ArtSearch.PER_PROVIDER, list.count { it.provider == ArtProvider.LIBRETRO })
        assertEquals(list.size, list.map { it.sha1 }.toSet().size)
    }

    @Test
    fun `an error page or bytes the platform cannot draw are dropped`() = runBlocking {
        settings = ProviderSettings().setEnabled(ArtProvider.SCREENSCRAPER, false).setEnabled(ArtProvider.LIBRETRO, false)
        http.on("openemux-artwork", FakeHttp.ok("<html>" + "x".repeat(100) + "</html>"))
        assertEquals(emptyList<ArtCandidate>(), found(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        http.on("openemux-artwork", FakeHttp.png())
        decodable = { false }
        assertEquals(emptyList<ArtCandidate>(), found(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        // An empty name asks nothing of the name providers.
        http.asked.clear()
        search().search(rom, ArtKind.COVER, "  ", null).toList()
        assertEquals(emptyList<String>(), http.asked)
    }

    @Test
    fun `labels come from ScreenScraper alone, by hash when there is one`() = runBlocking {
        ssHttp.on("jeuInfos.php", FakeHttp.game(Triple("support-texture", "wor", "https://ss/l?1"), Triple("box-2D", "wor", "https://ss/b?1")))
        ssHttp.on("https://ss/l?1", FakeHttp.png(5))
        val events = search().search(rom, ArtKind.LABEL, "Tetris", RomIdentity("CAFEBABE", "M", "Tetris (World).gb", 4)).toList()
        val list = found(events)
        assertEquals(1, list.size)
        assertEquals(ArtProvider.SCREENSCRAPER, list[0].provider)
        assertEquals("png", list[0].format)
        assertEquals(ArtCandidate.ORIGIN_SEARCH, list[0].origin)
        assertTrue(ssHttp.asked.first().contains("crc=CAFEBABE"))
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun `ScreenScraper's refusals become the manager's messages`() = runBlocking {
        settings = ProviderSettings().setEnabled(ArtProvider.MIRROR, false).setEnabled(ArtProvider.LIBRETRO, false)
        val cases = mapOf(430 to SearchIssue.QUOTA, 403 to SearchIssue.REFUSED, 500 to SearchIssue.UNREACHABLE)
        for ((code, expected) in cases) {
            ssHttp.on("jeuInfos.php", HttpReply(code, null))
            assertEquals(expected, issue(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        }
        ssHttp.on("jeuInfos.php", HttpReply(404, null))
        assertNull(issue(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        // A game but no media worth having: nothing, and nothing wrong.
        ssHttp.on("jeuInfos.php", FakeHttp.game(Triple("box-2D", "wor", "https://ss/b?2")))
        ssHttp.on("https://ss/b?2", HttpReply(-1, null))
        assertEquals(emptyList<ArtCandidate>(), found(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        // Neither a name nor a hash: ScreenScraper is not asked.
        ssHttp.asked.clear()
        search().search(rom, ArtKind.COVER, "", null).toList()
        assertTrue(ssHttp.asked.isEmpty())
    }

    @Test
    fun `without credentials a label search says so, a cover search goes on without ScreenScraper`() = runBlocking {
        dev = ""
        assertEquals(SearchIssue.NO_CREDENTIALS, issue(search().search(rom, ArtKind.LABEL, "Tetris", null).toList()))
        http.on("openemux-artwork", FakeHttp.png())
        val events = search().search(rom, ArtKind.COVER, "Tetris", null).toList()
        assertNull(issue(events))
        assertTrue(ssHttp.asked.isEmpty())
    }

    @Test
    fun `when every request fails on the network, the search is offline`() = runBlocking {
        http.fallback = HttpReply(-1, null)
        ssHttp.on("jeuInfos.php", HttpReply(-1, null))
        assertEquals(SearchIssue.OFFLINE, issue(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
        // One answer that got through: not offline, ScreenScraper's failure stays unreported.
        http.on("openemux-artwork", HttpReply(404, null))
        assertNull(issue(search().search(rom, ArtKind.COVER, "Tetris", null).toList()))
    }

    @Test
    fun `results go out one at a time, as they arrive`() = runBlocking {
        settings = ProviderSettings().setEnabled(ArtProvider.SCREENSCRAPER, false)
        http.on("openemux-artwork", FakeHttp.png(1)).on("thumbnails.libretro.com", FakeHttp.png(2))
        val first = search().search(rom, ArtKind.COVER, "Tetris", null).take(1).toList()
        assertEquals(ArtProvider.MIRROR, (first.single() as SearchEvent.Found).candidate.provider)
    }

    @Test
    fun `suggestions list their names first, then each preview, unavailable offline`() = runBlocking {
        http.on("Tetris%20(World)", FakeHttp.png(7))
        val events = search().suggest(rom, "Tetris").toList()
        val listed = (events.first() as SearchEvent.Suggested).suggestions
        assertEquals(listOf("Tetris (Japan)", "Tetris (World)", "Tetris 2 (USA)"), listed.map { it.title })
        val previews = events.filterIsInstance<SearchEvent.Preview>()
        assertEquals(listed.size, previews.size)
        val world = previews.first { it.title == "Tetris (World)" }.candidate!!
        assertEquals(ArtProvider.MIRROR, world.provider)
        assertEquals(ArtCandidate.ORIGIN_SUGGESTION, world.origin)
        assertNull(previews.first { it.title == "Tetris (Japan)" }.candidate)
        assertNull(issue(events))

        http.fallback = HttpReply(-1, null)
        http.on("Tetris%20(World)", HttpReply(-1, null))
        val offline = search().suggest(rom, "Tetris").toList()
        assertEquals(SearchIssue.OFFLINE, issue(offline))
        assertEquals(3, (offline.first() as SearchEvent.Suggested).suggestions.size)
        assertEquals(SearchEvent.Suggested::class, search().suggest(rom, "zzz").first()::class)
    }

    @Test
    fun `suggestion previews come from the provider the settings allow, and none when they allow none`() = runBlocking {
        http.on("thumbnails.libretro.com", FakeHttp.png(4))
        settings = ProviderSettings().setEnabled(ArtProvider.MIRROR, false)
        val previews = search().suggest(rom, "Tetris").toList().filterIsInstance<SearchEvent.Preview>()
        // The same bytes for every title: each suggestion still gets its preview.
        assertEquals(3, previews.size)
        assertTrue(previews.all { it.candidate?.provider == ArtProvider.LIBRETRO })
        assertTrue(http.asked.none { "openemux-artwork" in it })

        settings = settings.setEnabled(ArtProvider.LIBRETRO, false)
        http.asked.clear()
        val none = search().suggest(rom, "Tetris").toList().filterIsInstance<SearchEvent.Preview>()
        assertEquals(3, none.size)
        assertTrue(none.all { it.candidate == null })
        assertTrue(http.asked.isEmpty())
    }
}
