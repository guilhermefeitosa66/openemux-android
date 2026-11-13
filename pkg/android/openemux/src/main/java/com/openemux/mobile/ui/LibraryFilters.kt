package com.openemux.mobile.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Paths
import com.openemux.mobile.data.Rom
import com.retroarch.R
import java.io.File

/** The library's orders (issue 927); [section] heads the grid while the order is on. */
internal enum class SortMode(val id: String, @StringRes val label: Int, @StringRes val section: Int) {
    Az("az", R.string.library_sort_az, R.string.library_section_carts),
    Recent("recent", R.string.library_sort_recent, R.string.library_section_recent),
    Added("added", R.string.library_sort_added, R.string.library_section_added),
    Played("played", R.string.library_sort_most_played, R.string.library_section_played),
    Unplayed("unplayed", R.string.library_sort_unplayed, R.string.library_section_unplayed);

    companion object {
        /** A stored id, or [Az] for none or one this version does not know. */
        fun parse(stored: String?): SortMode = entries.firstOrNull { it.id == stored } ?: Az
    }
}

/** The library's order, kept across screens and launches (issue 927). */
internal object LibrarySortPref {
    private const val KEY = "library_sort"

    var value by mutableStateOf(SortMode.Az)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("openemux", Context.MODE_PRIVATE)

    fun load(context: Context) {
        value = SortMode.parse(prefs(context).getString(KEY, null))
    }

    fun set(context: Context, sort: SortMode) {
        value = sort
        prefs(context).edit().putString(KEY, sort.id).apply()
    }
}

/** The facets a page can be narrowed by, any of them together (issue 927). */
internal enum class LibraryFlag(val id: String, @StringRes val label: Int) {
    Favorite("fav", R.string.library_flag_favorites),
    Saved("saved", R.string.library_flag_saved),
    MissingArt("noart", R.string.library_flag_missing_art),
    Unplayed("unplayed", R.string.library_flag_unplayed);

    companion object {
        /** The flags a saved state names, unknown ids dropped. */
        fun parse(stored: String?): Set<LibraryFlag> =
            stored.orEmpty().split(',').mapNotNull { id -> entries.firstOrNull { it.id == id } }.toSet()

        fun join(flags: Set<LibraryFlag>): String = entries.filter { it in flags }.joinToString(",") { it.id }
    }
}

/**
 * [items] in [sort]'s order. [items] come A to Z already (the library's own order), which every other order falls
 * back to for ties, so the orders are stable: the newest, the most played and the unplayed first, A to Z within.
 */
internal fun <T> sortGames(items: List<T>, sort: SortMode, lastPlayed: (T) -> Long, added: (T) -> Long, playtime: (T) -> Long): List<T> = when (sort) {
    SortMode.Az -> items
    SortMode.Recent -> items.sortedByDescending(lastPlayed)
    SortMode.Added -> items.sortedByDescending(added)
    SortMode.Played -> items.sortedByDescending(playtime)
    SortMode.Unplayed -> items.sortedBy { if (lastPlayed(it) > 0) 1 else 0 }
}

/** Whether [flag] holds for a game, from what the page knows about it. */
internal fun <T> flagHolds(flag: LibraryFlag, item: T, favorite: (T) -> Boolean, saved: (T) -> Boolean, missingArt: (T) -> Boolean, lastPlayed: (T) -> Long): Boolean =
    when (flag) {
        LibraryFlag.Favorite -> favorite(item)
        LibraryFlag.Saved -> saved(item)
        LibraryFlag.MissingArt -> missingArt(item)
        LibraryFlag.Unplayed -> lastPlayed(item) <= 0
    }

/** The games every one of [flags] holds for; all of them with no flag. */
internal fun <T> flaggedGames(items: List<T>, flags: Set<LibraryFlag>, favorite: (T) -> Boolean, saved: (T) -> Boolean, missingArt: (T) -> Boolean, lastPlayed: (T) -> Long): List<T> =
    if (flags.isEmpty()) items else items.filter { item -> flags.all { flagHolds(it, item, favorite, saved, missingArt, lastPlayed) } }

/** How many of [items] each flag holds for: the chips' counts, the page's scope before the flags. */
internal fun <T> flagCounts(items: List<T>, favorite: (T) -> Boolean, saved: (T) -> Boolean, missingArt: (T) -> Boolean, lastPlayed: (T) -> Long): Map<LibraryFlag, Int> =
    LibraryFlag.entries.associateWith { flag -> items.count { flagHolds(flag, it, favorite, saved, missingArt, lastPlayed) } }

/** The keys of the games with a save state, one folder listing per console: reads the disk. */
internal fun savedGameKeys(roms: List<Rom>, paths: Paths): Set<String> {
    val byConsole = roms.groupBy { it.console }
    val saved = HashSet<String>()
    for ((console, games) in byConsole) {
        val names = paths.states(console).list()?.filter { !it.endsWith(".png") }?.toHashSet() ?: continue
        if (names.isEmpty()) continue
        for (rom in games) {
            val prefix = "${rom.baseName}.state"
            if (names.any { it.startsWith(prefix) }) saved += rom.key
        }
    }
    return saved
}

/** Whether a game's artwork is still missing: the label for a cartridge console, the box art for the others. */
internal fun artMissing(console: Console, label: File?, cover: String?): Boolean = if (console.frame != null) label == null else cover == null
