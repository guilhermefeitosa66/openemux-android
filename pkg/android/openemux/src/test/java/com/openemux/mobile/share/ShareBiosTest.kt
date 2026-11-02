package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.biosFilesIn
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** BIOS files in Share nearby (#678): sent from and placed in RetroArch's flat `system/` folder. */
class ShareBiosTest {
    private val lib = FakeLibrary()
    private val paths = lib.paths
    private val system get() = paths.system

    private fun items(built: BuiltManifest, game: Int = 0) =
        built.json.getJSONArray("games").getJSONObject(game).getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun build(vararg roms: Rom) = ManifestBuilder(lib).build(roms.toList())

    private fun plan(manifest: Manifest, overrides: Map<String, Policy> = emptyMap(), unchecked: Set<String> = emptySet()) =
        planReceive(manifest, paths, CoreIdentities(paths), overrides = overrides, unchecked = unchecked, probe = FakeSpace())

    private fun ReceivePlan.of(id: String) = decisions.single { it.itemId == id }

    private fun psGame(bios: ByteArray = "bios".toByteArray(), name: String = "scph5501.bin", hash: Boolean = true) = manifest {
        game("g1", "PS", "Game") {
            item("rom", "Game.chd", "rom".toByteArray())
            item("bios", name, bios, hash = hash)
        }
    }

    // -- the catalog files in system/ ----------------------------------------------

    @Test
    fun `only the console's catalog files are found, under the catalog spelling`() {
        File(system, "SCPH5501.BIN").put("upper")
        File(system, "scph1001.bin").put("exact")
        File(system, "notes.txt").put("not a BIOS")
        File(system, "gba_bios.bin").put("another console's")
        File(system, "scph5500.bin").mkdirs()
        val found = biosFilesIn(system, Console.PS)
        assertEquals(listOf("scph1001.bin", "scph5501.bin"), found.map { it.first })
        assertEquals("SCPH5501.BIN", found[1].second.name)
        assertTrue(biosFilesIn(File(system, "missing"), Console.PS).isEmpty())
    }

    @Test
    fun `the exact spelling wins over a case variant, whichever is listed first`() {
        val dir = Files.createTempDirectory("bios-case").toFile()
        // A case-sensitive folder can hold both: the one a core opens is the one sent.
        File(dir, "PSXONPSP660.BIN").put("variant")
        File(dir, "PSXONPSP660.bin").put("exact")
        File(dir, "psxonpsp660.bin").put("lower")
        assertEquals("PSXONPSP660.bin", biosFilesIn(dir, Console.PS).single().second.name)
    }

    // -- sending ------------------------------------------------------------------

    @Test
    fun `the first game of a console carries its BIOS, once, and the next games do not`() {
        File(system, "scph5501.bin").put(ByteArray(512) { 1 }, mtime = 1_700_000_500)
        File(system, "SCPH1001.BIN").put(ByteArray(512) { 2 })
        File(system, "notes.txt").put("x")
        File(system, "gba_bios.bin").put("gba")
        val first = lib.rom(Console.PS, "First.chd")
        val second = lib.rom(Console.PS, "Second.chd")
        val snes = lib.rom(Console.SFC, "Snes.sfc")
        val built = build(first, second, snes)
        val carried = items(built, 0).filter { it.getString("kind") == Item.BIOS }
        assertEquals(listOf("scph1001.bin", "scph5501.bin"), carried.map { it.getString("name") })
        assertEquals(1_700_000_500L, carried[1].getLong("mtime"))
        assertEquals(sha256(ByteArray(512) { 1 }), carried[1].getString("sha256"))
        assertEquals(File(system, "SCPH1001.BIN"), built.sources[carried[0].getString("id")])
        assertTrue(items(built, 1).none { it.getString("kind") == Item.BIOS })
        assertTrue(items(built, 2).none { it.getString("kind") == Item.BIOS })
        assertEquals(5, built.json.getInt("file_count"))
        assertEquals(5, ManifestParser.parse(JSONObject(built.json.toString())).items.size)
    }

    @Test
    fun `BIOS that would go past the item limit wait for the next game of the console`() {
        File(system, "scph5501.bin").put("bios")
        val roms = (1..ShareProtocol.MAX_ITEMS).map { lib.rom(Console.PS, "G$it.chd") }
        val built = build(*roms.toTypedArray())
        // Every game takes one item; the BIOS ride with the first, which still has room.
        assertEquals(Item.BIOS, items(built, 0)[1].getString("kind"))
        assertEquals(1, built.excluded.size)

        val full = FakeLibrary()
        File(full.paths.system, "scph5501.bin").put("bios")
        val many = (1 until ShareProtocol.MAX_ITEMS).map { full.rom(Console.SFC, "S$it.sfc") }
        val ps1 = full.rom(Console.PS, "A.chd")
        val ps2 = full.rom(Console.PS, "B.chd")
        val tight = ManifestBuilder(full).build(many + ps1 + ps2)
        // A.chd takes the last item: no room for its BIOS, and B.chd is left out with no room either.
        val games = tight.json.getJSONArray("games")
        val a = games.getJSONObject(games.length() - 1)
        assertEquals("A", a.getString("title"))
        assertEquals(1, a.getJSONArray("items").length())
        assertEquals(listOf(SkipReason.TOO_MANY_ITEMS), tight.excluded.map { it.reason })
    }

    // -- receiving ----------------------------------------------------------------

    @Test
    fun `a catalog BIOS goes to system under the catalog spelling, last in the commit`() {
        val plan = plan(psGame(name = "SCPH5501.BIN"))
        val d = plan.of("g1i2")
        assertTrue(d.send)
        assertEquals(File(system, "scph5501.bin"), d.target)
        assertEquals(listOf(Item.ROM, Item.BIOS), plan.toSend().map { it.kind })
    }

    @Test
    fun `a name the catalog does not list for that console is invalid_item`() {
        assertEquals(SkipReason.INVALID_ITEM, plan(psGame(name = "gba_bios.bin")).of("g1i2").reason)
        assertEquals(SkipReason.INVALID_ITEM, plan(psGame(name = "evil.so")).of("g1i2").reason)
    }

    @Test
    fun `the same BIOS already there is exists_skip and raises no conflict`() {
        File(system, "scph5501.bin").put("bios")
        val manifest = psGame()
        assertEquals(SkipReason.EXISTS_SKIP, plan(manifest).of("g1i2").reason)
        val model = OfferModel.build(IncomingOffer("o", OfferSender("a".repeat(32), "Desk", "desktop", "b".repeat(64)), manifest, "1"), { plan(manifest, it) }, emptyMap(), 0)
        assertTrue(model.conflicts().isEmpty())
    }

    @Test
    fun `a different BIOS is a conflict kept by default, replaced only on Replace`() {
        val local = File(system, "scph5501.bin").put("mine", mtime = 1_600_000_000)
        val manifest = psGame()
        assertEquals(SkipReason.EXISTS_SKIP, plan(manifest).of("g1i2").reason)
        val replaced = plan(manifest, overrides = mapOf("g1i2" to Policy.REPLACE)).of("g1i2")
        assertTrue(replaced.send)
        assertEquals(local, replaced.replaces)
        val model = OfferModel.build(IncomingOffer("o", OfferSender("a".repeat(32), "Desk", "desktop", "b".repeat(64)), manifest, "1"), { plan(manifest, it) }, emptyMap(), 0)
        val conflict = model.conflicts().single()
        assertEquals(Item.BIOS, conflict.kind)
        assertEquals("scph5501.bin", conflict.name)
        assertEquals(Policy.SKIP, conflict.policy)
        assertEquals(OfferModel.ART_POLICIES, conflict.allowed)
        // Newest is no choice for a BIOS, but a policy map that says so still never keeps two copies.
        assertEquals(SkipReason.EXISTS_SKIP, plan(manifest, overrides = mapOf("g1i2" to Policy.KEEP_BOTH)).of("g1i2").reason)
    }

    @Test
    fun `a BIOS without a hash is never taken for the same file`() {
        File(system, "scph5501.bin").put("bios")
        val manifest = psGame(hash = false)
        val replaced = plan(manifest, overrides = mapOf("g1i2" to Policy.REPLACE)).of("g1i2")
        assertTrue(replaced.send)
        assertEquals(File(system, "scph5501.bin"), replaced.replaces)
    }

    @Test
    fun `a folder or a dangling link where the BIOS goes is invalid_item`() {
        File(system, "scph5501.bin").mkdirs()
        assertEquals(SkipReason.INVALID_ITEM, plan(psGame()).of("g1i2").reason)
        val other = FakeLibrary()
        other.paths.system.mkdirs()
        Files.createSymbolicLink(File(other.paths.system, "scph5501.bin").toPath(), File(other.paths.base, "nowhere").toPath())
        val d = planReceive(psGame(), other.paths, CoreIdentities(other.paths), probe = FakeSpace()).decisions.single { it.itemId == "g1i2" }
        assertEquals(SkipReason.INVALID_ITEM, d.reason)
    }

    @Test
    fun `the BIOS comes whatever became of the ROM, unless the user left it out`() {
        File(paths.roms(Console.PS), "Game.chd").put("rom")
        val existing = plan(psGame())
        assertEquals(RomStatus.EXISTING, existing.games.single().romStatus)
        assertTrue(existing.of("g1i2").send)
        File(paths.roms(Console.PS), "Game.chd").put("other rom")
        // A different ROM under a name kept: skipped, and the BIOS still comes.
        val skipped = plan(psGame(), overrides = mapOf("g1i1" to Policy.SKIP))
        assertTrue(skipped.of("g1i2").send)
        assertEquals(SkipReason.USER_UNCHECKED, plan(psGame(), unchecked = setOf("g1i2")).of("g1i2").reason)
        assertEquals(SkipReason.USER_UNCHECKED, plan(psGame(), unchecked = setOf("g1")).of("g1i2").reason)
    }

    @Test
    fun `the same BIOS twice in one offer is placed once`() {
        val manifest = manifest {
            game("g1", "PS", "A") { item("rom", "A.chd"); item("bios", "scph5501.bin", "one".toByteArray()) }
            game("g2", "PS", "B") { item("rom", "B.chd"); item("bios", "SCPH5501.bin", "two".toByteArray()) }
        }
        val plan = plan(manifest)
        assertTrue(plan.of("g1i2").send)
        assertEquals(SkipReason.EXISTS_SKIP, plan.of("g2i2").reason)
    }

    @Test
    fun `the offer sheet lists a game's BIOS by name, and skipped rows carry the name`() {
        val manifest = psGame(name = "gba_bios.bin")
        val model = OfferModel.build(IncomingOffer("o", OfferSender("a".repeat(32), "Desk", "desktop", "b".repeat(64)), manifest, "1"), { plan(manifest, it) }, emptyMap(), 0)
        assertEquals(listOf("gba_bios.bin"), model.games.single().bios)
        val skipped = model.skipped.single()
        assertEquals(Item.BIOS, skipped.kind)
        assertEquals("gba_bios.bin", skipped.name)
    }

    // -- reports ------------------------------------------------------------------

    @Test
    fun `the receive report lists the BIOS written, which never count a game as delivered`() {
        File(paths.roms(Console.PS), "Game.chd").put("rom")
        val plan = plan(psGame())
        val report = receiveReport(plan, ReceiveResult(TransferStatus.DONE, skipped = listOf("g1i1" to SkipReason.EXISTS_SKIP)))
        assertEquals(listOf("scph5501.bin"), report.bios)
        assertEquals(0, report.delivered)
        assertEquals(listOf(MissingGame("Game", "PS", SkipReason.EXISTS_SKIP)), report.missing)
        val notWritten = receiveReport(plan, ReceiveResult(TransferStatus.DONE, skipped = listOf("g1i1" to SkipReason.EXISTS_SKIP, "g1i2" to ErrorCode.INTERNAL)))
        assertTrue(notWritten.bios.isEmpty())
    }

    @Test
    fun `the send report lists the BIOS that went, which never count a game as delivered`() {
        File(system, "scph5501.bin").put("bios")
        val rom = lib.rom(Console.PS, "Game.chd")
        val built = build(rom)
        val sent = sendReport(built, emptyList(), SendResult(SendResult.DONE, listOf("g1i2"), listOf("g1i1" to SkipReason.EXISTS_SKIP)))
        assertEquals(listOf("scph5501.bin"), sent.bios)
        assertEquals(0, sent.delivered)
        val skipped = sendReport(built, emptyList(), SendResult(SendResult.DONE, listOf("g1i1"), listOf("g1i2" to SkipReason.INVALID_ITEM)))
        assertTrue(skipped.bios.isEmpty())
        assertEquals(1, skipped.delivered)
    }

    // -- "What to Send" -----------------------------------------------------------

    private fun selection(): Pair<BuiltManifest, SendSelection> {
        File(system, "scph5501.bin").put(ByteArray(100))
        val roms = listOf(lib.rom(Console.PS, "A.chd", ByteArray(10)), lib.rom(Console.PS, "B.chd", ByteArray(20)), lib.rom(Console.SFC, "C.sfc", ByteArray(30)))
        val built = build(*roms.toTypedArray())
        return built to SendSelection.from(built, roms)
    }

    private fun BuiltManifest.biosGame(): String? {
        val games = json.getJSONArray("games")
        return (0 until games.length()).map { games.getJSONObject(it) }.firstOrNull { game ->
            val items = game.getJSONArray("items")
            (0 until items.length()).any { items.getJSONObject(it).getString("kind") == Item.BIOS }
        }?.getString("title")
    }

    @Test
    fun `the carrier lists a BIOS part, counted once in the totals`() {
        val (built, s) = selection()
        val a = s.games[0]
        assertEquals(listOf(SendPart.GAME, SendPart.BIOS), a.parts.map { it.part })
        assertEquals(listOf(SendPart.GAME), s.games[1].parts.map { it.part })
        assertEquals(10L + 100 + 20 + 30, s.totalBytes)
        assertEquals(4, s.fileCount)
        assertEquals(3, s.gameCount)
        assertEquals(Tri.CHECKED, s.state(a.id))
        assertEquals("A", s.filter(built).biosGame())
        assertTrue(s.unchecked().isEmpty())
    }

    @Test
    fun `the BIOS check is the console's, and a game's own checkbox leaves it alone`() {
        val (built, s) = selection()
        val a = s.games[0].id
        val off = s.toggle(a, SendPart.BIOS)
        assertFalse(off.isChecked(a, SendPart.BIOS))
        assertTrue((SendSelection.biosKey("PS") to SendPart.BIOS) in off.unchecked)
        assertEquals(Tri.CHECKED, off.state(a))
        assertEquals(60L, off.totalBytes)
        assertNull(off.filter(built).biosGame())
        assertEquals(setOf(s.games[0].parts[1].itemIds.single()), off.unchecked())
        // The game's checkbox toggles its own parts only.
        val gameOff = off.toggleGame(a)
        assertEquals(Tri.UNCHECKED, gameOff.state(a))
        assertFalse(gameOff.toggleGame(a).isChecked(a, SendPart.BIOS))
        assertTrue(off.toggle(a, SendPart.BIOS).isChecked(a, SendPart.BIOS))
    }

    @Test
    fun `leaving the carrier out hands its BIOS to the next game of the console`() {
        val (built, s) = selection()
        val a = s.games[0].id
        val b = s.games[1].id
        val next = s.toggleGame(a)
        assertEquals(20L + 100 + 30, next.totalBytes)
        assertEquals(100L + 20, next.gameBytes(b))
        assertEquals(2, next.gameCount)
        val filtered = next.filter(built)
        assertEquals("B", filtered.biosGame())
        assertEquals(3, filtered.json.getInt("file_count"))
        assertEquals(150L, filtered.json.getLong("total_size"))
        assertTrue(s.games[0].parts[1].itemIds.single() in filtered.sources)
        assertTrue(ManifestParser.parse(JSONObject(filtered.json.toString())).items.any { it.kind == Item.BIOS })
        // No PS game keeps its ROM: the BIOS stay home.
        val none = next.toggleGame(b)
        assertNull(none.filter(built).biosGame())
        assertEquals(30L, none.totalBytes)
        assertTrue(s.games[0].parts[1].itemIds.single() in none.unchecked())
    }

    @Test
    fun `unchecking a BIOS part of a game this selection does not know is harmless`() {
        val (_, s) = selection()
        assertFalse(s.toggle("nope", SendPart.BIOS).isChecked("nope", SendPart.BIOS))
    }
}
