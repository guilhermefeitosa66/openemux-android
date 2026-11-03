package com.openemux.mobile.data

/** A title from the game-name database offered for box art; [approximate] when no word matched as typed. */
data class Suggestion(val title: String, val approximate: Boolean)

/**
 * Box-art suggestions (issue 753): similar names from the game-name database, which on Android is
 * the mirror's title lists the app already bundles (`assets/covers/covers_<console>.txt`), so
 * nothing is added to the APK or downloaded. Same two modes as the desktop's `artwork_index.py`:
 * every typed word present (the last one as a prefix), else approximate matches ranked by
 * similarity, with a floor when no word is shared at all.
 */
object ArtSuggestions {
    const val MAX = 10

    /** The desktop's floor for a name that shares no word with what was typed. */
    const val FLOOR = 0.5

    fun suggest(typed: String, titles: List<String>, max: Int = MAX): List<Suggestion> {
        val words = Covers.tokens(typed).toList()
        if (words.isEmpty() || titles.isEmpty()) return emptyList()
        val wanted = plain(typed)
        val head = words.dropLast(1)
        val last = words.last()
        val tokenized = titles.map { it to Covers.tokens(it) }
        val exact = tokenized.filter { (_, t) -> head.all { it in t } && t.any { it.startsWith(last) } }
        if (exact.isNotEmpty()) return rank(wanted, exact.map { it.first }).take(max).map { Suggestion(it.first, false) }
        val sharing = tokenized.filter { (_, t) -> words.any { it in t } }.map { it.first }
        val approximate = if (sharing.isNotEmpty()) rank(wanted, sharing)
        else rank(wanted, titles).filter { it.second >= FLOOR }
        return approximate.take(max).map { Suggestion(it.first, true) }
    }

    private fun rank(wanted: String, titles: List<String>): List<Pair<String, Double>> =
        titles.distinct().map { it to ratio(wanted, plain(it)) }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })

    /** Lowercase, without the region and version tags in brackets. */
    private fun plain(name: String) = name.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").lowercase().trim().replace(Regex("\\s+"), " ")

    /** Python's `difflib.SequenceMatcher.ratio()`: twice the matching characters over both lengths. */
    fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return 2.0 * matching(a, 0, a.length, b, 0, b.length) / (a.length + b.length)
    }

    /** Ratcliff/Obershelp: the longest common block, then the same on each side of it. */
    private fun matching(a: String, a0: Int, a1: Int, b: String, b0: Int, b1: Int): Int {
        if (a0 >= a1 || b0 >= b1) return 0
        var best = 0
        var bestA = a0
        var bestB = b0
        var prev = IntArray(b1 - b0 + 1)
        for (i in a0 until a1) {
            val cur = IntArray(b1 - b0 + 1)
            for (j in b0 until b1) {
                if (a[i] == b[j]) {
                    val n = prev[j - b0] + 1
                    cur[j - b0 + 1] = n
                    if (n > best) {
                        best = n
                        bestA = i - n + 1
                        bestB = j - n + 1
                    }
                }
            }
            prev = cur
        }
        if (best == 0) return 0
        return best + matching(a, a0, bestA, b, b0, bestB) + matching(a, bestA + best, a1, b, bestB + best, b1)
    }
}
