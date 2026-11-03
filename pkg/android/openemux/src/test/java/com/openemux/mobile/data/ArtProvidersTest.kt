package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtProvidersTest {
    @Test
    fun `the default keeps the app's behaviour - mirror covers, ScreenScraper labels, libretro last`() {
        val s = ProviderSettings()
        assertEquals(listOf(ArtProvider.MIRROR, ArtProvider.SCREENSCRAPER, ArtProvider.LIBRETRO), s.serving(ArtKind.COVER))
        assertEquals(listOf(ArtProvider.SCREENSCRAPER), s.serving(ArtKind.LABEL))
        assertTrue(s.serves(ArtProvider.SCREENSCRAPER, ArtKind.LABEL))
        assertFalse(s.serves(ArtProvider.MIRROR, ArtKind.LABEL))
    }

    @Test
    fun `switching a provider off takes it out of every kind`() {
        val s = ProviderSettings().setEnabled(ArtProvider.SCREENSCRAPER, false)
        assertEquals(listOf(ArtProvider.MIRROR, ArtProvider.LIBRETRO), s.serving(ArtKind.COVER))
        assertEquals(emptyList<ArtProvider>(), s.serving(ArtKind.LABEL))
    }

    @Test
    fun `a kind can be turned off, and a kind the provider has not got stays off`() {
        val s = ProviderSettings().setKind(ArtProvider.SCREENSCRAPER, ArtKind.LABEL, false).setKind(ArtProvider.MIRROR, ArtKind.LABEL, true)
        assertFalse(s.serves(ArtProvider.SCREENSCRAPER, ArtKind.LABEL))
        assertTrue(s.serves(ArtProvider.SCREENSCRAPER, ArtKind.COVER))
        assertFalse(s.serves(ArtProvider.MIRROR, ArtKind.LABEL))
        assertTrue(s.setKind(ArtProvider.SCREENSCRAPER, ArtKind.LABEL, true).serves(ArtProvider.SCREENSCRAPER, ArtKind.LABEL))
    }

    @Test
    fun `moving changes the order, and nothing moves past either end`() {
        val s = ProviderSettings()
        assertEquals(listOf(ArtProvider.MIRROR, ArtProvider.LIBRETRO, ArtProvider.SCREENSCRAPER), s.move(ArtProvider.LIBRETRO, -1).order.map { it.provider })
        assertSame(s, s.move(ArtProvider.MIRROR, -1))
        assertSame(s, s.move(ArtProvider.LIBRETRO, 1))
    }

    @Test
    fun `settings survive a round trip`() {
        val s = ProviderSettings().move(ArtProvider.LIBRETRO, -2).setEnabled(ArtProvider.MIRROR, false).setKind(ArtProvider.SCREENSCRAPER, ArtKind.COVER, false)
        assertEquals(s, ProviderSettings.parse(s.toJson()))
    }

    @Test
    fun `nothing stored, or something unreadable, gives the defaults`() {
        assertEquals(ProviderSettings(), ProviderSettings.parse(null))
        assertEquals(ProviderSettings(), ProviderSettings.parse(""))
        assertEquals(ProviderSettings(), ProviderSettings.parse("{not json"))
    }

    @Test
    fun `unknown providers are dropped, missing ones added at the end, odd kinds ignored`() {
        val raw = """[{"id":"thegamesdb","enabled":true},{"id":"libretro","enabled":false,"kinds":["cover","label","box"]},"junk",{"id":"libretro"}]"""
        val s = ProviderSettings.parse(raw)
        assertEquals(listOf(ArtProvider.LIBRETRO, ArtProvider.MIRROR, ArtProvider.SCREENSCRAPER), s.order.map { it.provider })
        assertFalse(s.order[0].enabled)
        assertEquals(setOf(ArtKind.COVER), s.order[0].kinds)
        // No "kinds": everything the provider has.
        assertEquals(ArtProvider.SCREENSCRAPER.kinds, ProviderSettings.parse("""[{"id":"screenscraper"}]""").order[0].kinds)
    }

    @Test
    fun `providers are found by id`() {
        assertEquals(ArtProvider.MIRROR, ArtProvider.of("openemux"))
        assertEquals(null, ArtProvider.of("nope"))
        assertEquals(null, ArtProvider.of(null))
    }
}
