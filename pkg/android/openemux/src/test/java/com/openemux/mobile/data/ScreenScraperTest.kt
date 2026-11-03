package com.openemux.mobile.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperTest {
    private var account = ScreenScraperAccount()
    private val http = FakeHttp()
    private fun ss(devId: String = "dev", devPassword: String = "pw") = ScreenScraper(http, { account }, devId, devPassword, pace = 0)

    @Test
    fun `the user's own developer account wins only when both fields are set`() {
        assertEquals("dev" to "pw", ss().developer())
        account = ScreenScraperAccount(devId = "mine")
        assertEquals("dev" to "pw", ss().developer())
        account = ScreenScraperAccount(devId = " mine ", devPassword = "secret")
        assertEquals("mine" to "secret", ss().developer())
        assertTrue(ss("", "").usable)
        account = ScreenScraperAccount()
        assertNull(ss("", "").developer())
        assertFalse(ss("", "").usable)
        assertFalse(ss("", "").embedded)
        assertTrue(ss().embedded)
    }

    @Test
    fun `by hash sends the CRC, MD5, file name and size, and the user account when there is one`() = runBlocking {
        account = ScreenScraperAccount(user = "me", password = "p")
        http.on("jeuInfos.php", FakeHttp.game())
        val reply = ss().game(9, "Tetris", RomIdentity("ABCD1234", "MD5", "Tetris (World).gb", 32768))
        assertTrue(reply is SsReply.Ok)
        val url = http.asked.single()
        listOf("devid=dev", "devpassword=pw", "softname=OpenEmux", "output=json", "ssid=me", "sspassword=p", "systemeid=9",
            "romnom=Tetris+%28World%29.gb", "crc=ABCD1234", "md5=MD5", "romtaille=32768").forEach { assertTrue(it, url.contains(it)) }
    }

    @Test
    fun `by name sends the typed name alone`() = runBlocking {
        http.on("jeuInfos.php", FakeHttp.game())
        ss().game(9, "Tetris DX", RomIdentity("1", null, "x.gb", 1).takeIf { false })
        val url = http.asked.single()
        assertTrue(url.contains("romnom=Tetris+DX"))
        assertFalse(url.contains("crc="))
        assertFalse(url.contains("ssid="))
        // No MD5 (a huge zipped ROM): the rest still goes.
        ss().game(9, "", RomIdentity("1", null, "x.gb", 1))
        assertFalse(http.asked.last().contains("md5="))
    }

    @Test
    fun `answers are read the way the desktop reads them`() = runBlocking {
        val cases = mapOf(404 to SsReply.NotFound, 429 to SsReply.Quota, 430 to SsReply.Quota, 401 to SsReply.Refused, 403 to SsReply.Refused, 426 to SsReply.Refused)
        for ((code, expected) in cases) {
            http.on("jeuInfos.php", HttpReply(code, null))
            assertEquals(expected, ss().game(1, "x", null))
        }
        http.on("jeuInfos.php", HttpReply(500, null))
        assertEquals(SsReply.Unreachable(network = false), ss().game(1, "x", null))
        http.on("jeuInfos.php", HttpReply(-1, null))
        assertEquals(SsReply.Unreachable(network = true), ss().game(1, "x", null))
        http.on("jeuInfos.php", FakeHttp.ok("not json"))
        assertEquals(SsReply.Unreachable(network = false), ss().game(1, "x", null))
        assertEquals(SsReply.NoCredentials, ss("", "").game(1, "x", null))
    }

    @Test
    fun `media are asked at a width, through the same pacing`() = runBlocking {
        http.on("media", FakeHttp.png())
        assertEquals(200, ss().media("https://x/media?id=1").code)
        assertTrue(http.asked.single().endsWith("&maxwidth=640"))
    }

    @Test
    fun `requests are paced a second apart`() = runBlocking {
        var now = 0L
        val paced = ScreenScraper(http, { account }, "d", "p", clock = { now }, pace = 1000)
        val start = System.nanoTime()
        paced.media("u?a")
        now = 10
        paced.media("u?b")
        // The second waited about 990 ms of real time.
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 900)
    }

    @Test
    fun `media urls come by type, then by region, distinct and capped`() {
        val game = JSONObject(FakeHttp.game(
            Triple("box-3D", "us", "3d-us"), Triple("box-2D", "jp", "2d-jp"), Triple("box-2D", "", "2d-none"),
            Triple("box-2D", "us", "2d-us"), Triple("box-2D", "xx", "2d-xx"), Triple("box-2D", "eu", "2d-us"),
            Triple("support-texture", "wor", "label"), Triple("box-texture", "wor", ""),
        ).text!!).getJSONObject("response").getJSONObject("jeu")
        val regions = ScreenScraper.regionPriority("Game (USA).gb")
        assertEquals(listOf("us", "wor", "eu", "jp", "ss"), regions)
        assertEquals(listOf("2d-us", "2d-jp", "2d-none", "2d-xx", "3d-us"), ScreenScraper.mediaUrls(game, ArtKind.COVER, regions, 10))
        assertEquals(listOf("2d-us", "2d-jp"), ScreenScraper.mediaUrls(game, ArtKind.COVER, regions, 2))
        assertEquals(listOf("label"), ScreenScraper.mediaUrls(game, ArtKind.LABEL, regions, 4))
        assertEquals(emptyList<String>(), ScreenScraper.mediaUrls(JSONObject(), ArtKind.LABEL, regions, 4))
        assertEquals(listOf("eu", "jp", "br", "wor", "us", "ss"), ScreenScraper.regionPriority("Game (Europe, Japan, Brazil).gb"))
    }

    @Test
    fun `check reads the user's quota`() = runBlocking {
        account = ScreenScraperAccount(user = "me", password = "p")
        http.on("ssuserInfos.php", FakeHttp.ok("""{"response":{"ssuser":{"id":"Me","requeststoday":"1842","maxrequestsperday":"20000"}}}"""))
        assertEquals(SsAccountReply.User("Me", 1842, 20000), ss().check())
        http.on("ssuserInfos.php", FakeHttp.ok("""{"response":{"ssuser":{}}}"""))
        assertEquals(SsAccountReply.User("me", null, null), ss().check())
        http.on("ssuserInfos.php", FakeHttp.ok("nope"))
        assertEquals(SsAccountReply.Unreachable(network = false), ss().check())
        http.on("ssuserInfos.php", HttpReply(403, null))
        assertEquals(SsAccountReply.Refused, ss().check())
        http.on("ssuserInfos.php", HttpReply(430, null))
        assertEquals(SsAccountReply.Quota, ss().check())
        http.on("ssuserInfos.php", HttpReply(-1, null))
        assertEquals(SsAccountReply.Unreachable(network = true), ss().check())
    }

    @Test
    fun `without a user, check asks whether the developer account is accepted`() = runBlocking {
        http.on("ssinfraInfos.php", FakeHttp.ok("{}"))
        assertEquals(SsAccountReply.Developer, ss().check())
        http.on("ssinfraInfos.php", HttpReply(401, null))
        assertEquals(SsAccountReply.Refused, ss().check())
        assertEquals(SsAccountReply.NoCredentials, ss("", "").check())
    }

    @Test
    fun `an account knows which parts are set`() {
        assertTrue(ScreenScraperAccount("u", "p").hasUser)
        assertFalse(ScreenScraperAccount(" ", "p").hasUser)
        assertFalse(ScreenScraperAccount("u", "").hasUser)
        assertTrue(ScreenScraperAccount(devId = "d", devPassword = "p").hasDeveloper)
        assertFalse(ScreenScraperAccount(devId = "d").hasDeveloper)
        assertEquals("abc", HttpReply(200, "abc".toByteArray()).text)
        assertNull(HttpReply(404, null).text)
    }
}
