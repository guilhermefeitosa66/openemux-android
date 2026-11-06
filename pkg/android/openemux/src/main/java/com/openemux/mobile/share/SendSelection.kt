package com.openemux.mobile.share

import com.openemux.mobile.data.Rom
import org.json.JSONArray
import org.json.JSONObject

/**
 * What can go along with a game, as "What to Send" lists it; [DETAILS] is the
 * game's metadata, not a file. [BIOS] belongs to the console (#678): only the
 * game that carries the console's BIOS lists it. [SKIN] is a controller skin
 * ([SkinShare]): the one part of an entry that is no game.
 */
enum class SendPart { GAME, SAVE, STATES, COVER, LABEL, BIOS, DETAILS, SKIN }

/** One check of a game: its files of that kind (none for [SendPart.DETAILS]). */
data class SendPartInfo(val part: SendPart, val itemIds: List<String>, val bytes: Long) {
    val count: Int get() = itemIds.size
}

data class SendGame(val id: String, val console: String, val title: String, val parts: List<SendPartInfo>)

/** A game the manifest left out, with the [SkipReason] that kept it. */
data class ExcludedGame(val romKey: String, val console: String, val title: String, val reason: String)

enum class Tri { CHECKED, MIXED, UNCHECKED }

/**
 * The "What to Send" checklist (spec 02): every part of every game checked by
 * default; the user unchecks parts, or whole games. Immutable, like
 * [com.openemux.mobile.ui.RomSelection]: every change is a copy.
 *
 * The BIOS check is the console's ([biosKey]): it holds whichever game carries
 * the files, a game's own checkbox leaves it alone, and when the carrier's ROM
 * is left out its BIOS go with the next game of the console that keeps its ROM.
 */
data class SendSelection(
    val games: List<SendGame>,
    val excluded: List<ExcludedGame> = emptyList(),
    val unchecked: Set<Pair<String, SendPart>> = emptySet(),
) {
    private fun key(gameId: String, part: SendPart): Pair<String, SendPart> =
        if (part == SendPart.BIOS) biosKey(games.firstOrNull { it.id == gameId }?.console.orEmpty()) to part else gameId to part

    fun isChecked(gameId: String, part: SendPart) = key(gameId, part) !in unchecked

    fun toggle(gameId: String, part: SendPart): SendSelection {
        val key = key(gameId, part)
        return copy(unchecked = if (key in unchecked) unchecked - key else unchecked + key)
    }

    /** The parts a game's own checkbox stands for: all but the console's BIOS. */
    private fun ownParts(game: SendGame) = game.parts.filter { it.part != SendPart.BIOS }

    /** All of a game's parts on, or (when they all are) all off. */
    fun toggleGame(gameId: String): SendSelection {
        val game = games.firstOrNull { it.id == gameId } ?: return this
        val keys = ownParts(game).map { gameId to it.part }
        return copy(unchecked = if (state(gameId) == Tri.CHECKED) unchecked + keys else unchecked - keys.toSet())
    }

    fun state(gameId: String): Tri {
        val parts = games.firstOrNull { it.id == gameId }?.let(::ownParts).orEmpty()
        val on = parts.count { isChecked(gameId, it.part) }
        return when {
            parts.isEmpty() || on == 0 -> Tri.UNCHECKED
            on == parts.size -> Tri.CHECKED
            else -> Tri.MIXED
        }
    }

    /**
     * What actually goes with [game]: its own checked parts, and the BIOS of its
     * console when it is the game that takes them ([biosCarriers]).
     */
    private fun checkedParts(game: SendGame): List<SendPartInfo> {
        val own = ownParts(game).filter { isChecked(game.id, it.part) }
        val bios = if (biosCarriers[game.console] == game.id) biosOf[game.console]?.let { listOf(it) }.orEmpty() else emptyList()
        return own + bios
    }

    /** Each console's BIOS part, from the game the manifest gave them to. */
    private val biosOf: Map<String, SendPartInfo> by lazy {
        games.mapNotNull { g -> g.parts.firstOrNull { it.part == SendPart.BIOS }?.let { g.console to it } }.toMap()
    }

    /**
     * The game each console's BIOS go with: the first, from the one the manifest
     * gave them to on, that keeps its ROM. None when the BIOS check is off or no
     * such game is left.
     */
    private val biosCarriers: Map<String, String> by lazy {
        biosOf.keys.filter { (biosKey(it) to SendPart.BIOS) !in unchecked }.mapNotNull { console ->
            games.filter { it.console == console }.dropWhile { g -> g.parts.none { it.part == SendPart.BIOS } }
                .firstOrNull { g -> g.parts.any { it.part == SendPart.GAME } && isChecked(g.id, SendPart.GAME) }?.let { console to it.id }
        }.toMap()
    }

    fun gameBytes(gameId: String): Long = games.firstOrNull { it.id == gameId }?.let { g -> checkedParts(g).sumOf { it.bytes } } ?: 0

    val totalBytes: Long get() = games.sumOf { gameBytes(it.id) }
    val fileCount: Int get() = games.sumOf { g -> checkedParts(g).sumOf { it.count } }

    /** Games with at least one file of their own going. */
    val gameCount: Int get() = games.count { g -> checkedParts(g).any { it.part != SendPart.BIOS && it.count > 0 } }

    /** Nothing to send: no file is checked. */
    val isEmpty: Boolean get() = fileCount == 0

    /** The item ids the user left out (the receiver would answer them `user_unchecked`). */
    fun unchecked(): Set<String> {
        val own = games.flatMap { g -> ownParts(g).filter { !isChecked(g.id, it.part) }.flatMap { it.itemIds } }
        val bios = biosOf.filterKeys { it !in biosCarriers }.values.flatMap { it.itemIds }
        return (own + bios).toSet()
    }

    /**
     * [built] without what the user left out: the unchecked items, the
     * metadata when Library Details is off, and the games left with no file;
     * the totals recomputed. A console's BIOS move to the game that takes them.
     * The receiver never hears of the rest.
     */
    fun filter(built: BuiltManifest): BuiltManifest {
        val drop = unchecked()
        val games = JSONArray()
        var total = 0L
        var count = 0
        val source = built.json.getJSONArray("games")
        val all = (0 until source.length()).map { source.getJSONObject(it) }
        fun itemsOf(game: JSONObject) = game.getJSONArray("items").let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
        // A console's BIOS leave the game the manifest gave them to and join the one that takes them.
        val bios = all.flatMap(::itemsOf).filter { it.getString("kind") == Item.BIOS }.associateBy { it.getString("id") }
        for (game in all) {
            val id = game.getString("id")
            val console = game.getString("console")
            val items = JSONArray()
            val carried = if (biosCarriers[console] == id) biosOf.getValue(console).itemIds.mapNotNull(bios::get) else emptyList()
            for (item in itemsOf(game).filter { it.getString("kind") != Item.BIOS } + carried) {
                if (item.getString("id") in drop) continue
                items.put(item)
                total += item.getLong("size")
                count++
            }
            if (items.length() == 0) continue
            val copy = JSONObject(game.toString()).put("items", items)
            if (!isChecked(id, SendPart.DETAILS)) copy.remove("metadata")
            games.put(copy)
        }
        val json = JSONObject().put("games", games).put("total_size", total).put("file_count", count)
        return BuiltManifest(json, built.sources.filterKeys { it !in drop }, built.excluded)
    }

    companion object {
        private val PART_OF = mapOf(
            Item.ROM to SendPart.GAME, Item.BATTERY_SAVE to SendPart.SAVE, Item.STATE to SendPart.STATES,
            Item.COVER to SendPart.COVER, Item.LABEL to SendPart.LABEL, Item.BIOS to SendPart.BIOS,
            Item.SKIN to SendPart.SKIN,
        )

        /** The [unchecked] key of a console's BIOS check. */
        fun biosKey(console: String) = "bios:$console"

        /** Everything checked, from what the manifest builder made of [roms]. */
        fun from(built: BuiltManifest, roms: List<Rom>): SendSelection {
            val source = built.json.getJSONArray("games")
            val games = (0 until source.length()).map { g ->
                val game = source.getJSONObject(g)
                val list = game.getJSONArray("items")
                val byPart = linkedMapOf<SendPart, MutableList<JSONObject>>()
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val part = PART_OF[item.getString("kind")] ?: continue
                    byPart.getOrPut(part) { mutableListOf() } += item
                }
                val parts = SendPart.entries.mapNotNull { part ->
                    if (part == SendPart.DETAILS) {
                        game.optJSONObject("metadata")?.let { SendPartInfo(part, emptyList(), 0) }
                    } else {
                        byPart[part]?.let { items -> SendPartInfo(part, items.map { it.getString("id") }, items.sumOf { it.getLong("size") }) }
                    }
                }
                SendGame(game.getString("id"), game.getString("console"), game.getString("title"), parts)
            }
            val excluded = built.excluded.map { ex ->
                val rom = roms.firstOrNull { it.key == ex.romKey }
                ExcludedGame(ex.romKey, rom?.console?.id ?: ex.romKey.substringBefore('/'), rom?.title ?: ex.romKey.substringAfter('/'), ex.reason)
            }
            return SendSelection(games, excluded)
        }
    }
}
