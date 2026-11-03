package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtSuggestionsTest {
    private val names = listOf(
        "Super Mario Land (World)", "Super Mario Land 2 - 6 Golden Coins (USA, Europe)", "Super Mario Bros. Deluxe (USA, Europe)",
        "Tetris (World)", "Tetris DX (World)", "Mario's Picross (USA, Europe)", "Kirby's Dream Land (USA, Europe)",
    )

    @Test
    fun `every typed word present, the last as a prefix, ranked by similarity`() {
        val s = ArtSuggestions.suggest("super mario la", names)
        assertEquals(listOf("Super Mario Land (World)", "Super Mario Land 2 - 6 Golden Coins (USA, Europe)"), s.map { it.title })
        assertTrue(s.none { it.approximate })
    }

    @Test
    fun `with no full match, names sharing a word are approximate`() {
        val s = ArtSuggestions.suggest("Mario Golf", names)
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.approximate })
        assertTrue(s.all { "Mario" in it.title })
    }

    @Test
    fun `sharing no word, only names similar enough are offered`() {
        val s = ArtSuggestions.suggest("Tetrys", names)
        assertEquals("Tetris (World)", s.first().title)
        assertTrue("Tetris DX (World)" in s.map { it.title })
        assertTrue(s.all { it.approximate })
        assertEquals(emptyList<Suggestion>(), ArtSuggestions.suggest("Zelda", names))
    }

    @Test
    fun `at most ten, and nothing for nothing`() {
        val many = (1..30).map { "Tetris $it (World)" }
        assertEquals(ArtSuggestions.MAX, ArtSuggestions.suggest("Tetris", many).size)
        assertEquals(emptyList<Suggestion>(), ArtSuggestions.suggest("  ", names))
        assertEquals(emptyList<Suggestion>(), ArtSuggestions.suggest("Tetris", emptyList()))
    }

    @Test
    fun `the similarity is difflib's ratio`() {
        assertEquals(1.0, ArtSuggestions.ratio("", ""), 0.0)
        assertEquals(1.0, ArtSuggestions.ratio("abc", "abc"), 0.0)
        assertEquals(0.0, ArtSuggestions.ratio("abc", "xyz"), 0.0)
        // difflib.SequenceMatcher(None, "tetrys", "tetris").ratio() == 0.8333...
        assertEquals(10.0 / 12.0, ArtSuggestions.ratio("tetrys", "tetris"), 1e-9)
        assertEquals(0.75, ArtSuggestions.ratio("abcd", "bcde"), 1e-9)
    }
}
