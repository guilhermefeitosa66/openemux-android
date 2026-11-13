package com.openemux.mobile.ui

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Paths
import com.openemux.mobile.data.Rom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The library's orders and facets (issue 927). */
class LibraryFiltersTest {
    private data class G(val title: String, val played: Long = 0, val added: Long = 0, val time: Long = 0, val fav: Boolean = false, val saved: Boolean = false, val noArt: Boolean = false)
    private val games = listOf(
        G("Alpha", played = 10, added = 1, time = 5),
        G("Beta", played = 0, added = 3, time = 0, fav = true, noArt = true),
        G("Gamma", played = 30, added = 2, time = 50, saved = true),
        G("Delta", played = 0, added = 3, time = 0, saved = true, fav = true),
    )

    private fun sorted(sort: SortMode) = sortGames(games, sort, { it.played }, { it.added }, { it.time }).map { it.title }

    @Test fun eachOrderIsStableOverTheAToZItStartsFrom() {
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Delta"), sorted(SortMode.Az))
        assertEquals(listOf("Gamma", "Alpha", "Beta", "Delta"), sorted(SortMode.Recent))
        // Beta and Delta share a date: A to Z between them.
        assertEquals(listOf("Beta", "Delta", "Gamma", "Alpha"), sorted(SortMode.Added))
        assertEquals(listOf("Gamma", "Alpha", "Beta", "Delta"), sorted(SortMode.Played))
        assertEquals(listOf("Beta", "Delta", "Alpha", "Gamma"), sorted(SortMode.Unplayed))
    }

    private fun flagged(vararg flags: LibraryFlag) = flaggedGames(games, flags.toSet(), { it.fav }, { it.saved }, { it.noArt }, { it.played }).map { it.title }

    @Test fun facetsCombineAndCount() {
        assertEquals(games.map { it.title }, flagged())
        assertEquals(listOf("Beta", "Delta"), flagged(LibraryFlag.Favorite))
        assertEquals(listOf("Gamma", "Delta"), flagged(LibraryFlag.Saved))
        assertEquals(listOf("Delta"), flagged(LibraryFlag.Favorite, LibraryFlag.Saved))
        assertEquals(listOf("Beta"), flagged(LibraryFlag.MissingArt))
        assertEquals(listOf("Beta", "Delta"), flagged(LibraryFlag.Unplayed))
        assertEquals(emptyList<String>(), flagged(LibraryFlag.MissingArt, LibraryFlag.Saved))
        val counts = flagCounts(games, { it.fav }, { it.saved }, { it.noArt }, { it.played })
        assertEquals(mapOf(LibraryFlag.Favorite to 2, LibraryFlag.Saved to 2, LibraryFlag.MissingArt to 1, LibraryFlag.Unplayed to 2), counts)
    }

    @Test fun storedIdsRoundTripAndUnknownOnesAreDropped() {
        assertEquals(SortMode.Played, SortMode.parse("played"))
        assertEquals(SortMode.Az, SortMode.parse(null))
        assertEquals(SortMode.Az, SortMode.parse("random"))
        val flags = setOf(LibraryFlag.Saved, LibraryFlag.Favorite)
        assertEquals("fav,saved", LibraryFlag.join(flags))
        assertEquals(flags, LibraryFlag.parse("fav,saved"))
        assertEquals(setOf(LibraryFlag.Unplayed), LibraryFlag.parse("unplayed,bogus"))
        assertEquals(emptySet<LibraryFlag>(), LibraryFlag.parse(""))
        assertEquals(emptySet<LibraryFlag>(), LibraryFlag.parse(null))
    }

    @Test fun savedGamesComeFromOneListingPerConsole() {
        val root = Files.createTempDirectory("saved").toFile()
        val paths = Paths(File(root, "ext"), File(root, "int"))
        val a = Rom("FC/A.nes", Console.FC, File(paths.roms(Console.FC), "A.nes"), "A")
        val b = Rom("FC/B.nes", Console.FC, File(paths.roms(Console.FC), "B.nes"), "B")
        val c = Rom("GB/C.gb", Console.GB, File(paths.roms(Console.GB), "C.gb"), "C")
        paths.states(Console.FC).mkdirs()
        File(paths.states(Console.FC), "A.state.auto").writeText("s")
        File(paths.states(Console.FC), "A.state.auto.png").writeText("p")
        File(paths.states(Console.FC), "B.state3.png").writeText("p")
        assertEquals(setOf("FC/A.nes"), savedGameKeys(listOf(a, b, c), paths))
        root.deleteRecursively()
    }

    @Test fun artIsMissingByWhatTheConsoleDraws() {
        assertTrue(artMissing(Console.FC, null, "cover"))
        assertFalse(artMissing(Console.FC, File("label.png"), null))
        assertTrue(artMissing(Console.PS, File("label.png"), null))
        assertFalse(artMissing(Console.PS, null, "cover"))
    }
}
