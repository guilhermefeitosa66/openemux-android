package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinStoreException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * A controller skin in Share nearby: one of the player's own goes as an entry with no ROM and one zip of kind
 * `skin`, and lands on the other device as a new skin of its own for the same console.
 */
class ShareSkinTest {
    private val lib = FakeLibrary()
    private val paths = lib.paths
    private val skins get() = lib.skins
    private val sfc get() = skins.root(Console.SFC)
    private val work = File(paths.internal, "share-skin")

    private val results = mutableListOf<ReceiveResult>()
    private val aborts = mutableListOf<Pair<String, Boolean>>()

    private fun build(id: String = "night", name: String = "Night", console: Console = Console.SFC) =
        SkinShare.build(lib, SkinToSend(console, id, name), work)

    private fun item(built: BuiltManifest) = built.json.getJSONArray("games").getJSONObject(0).getJSONArray("items").getJSONObject(0)

    /** An offer of one skin entry whose zip is [bytes]. */
    private fun offer(bytes: ByteArray = skinZipBytes(), console: String = "SFC", title: String = "Night", name: String = "night.zip") = manifest {
        game("g1", console, title) { item(Item.SKIN, name, bytes) }
    }

    private fun plan(manifest: Manifest, unchecked: Set<String> = emptySet()) =
        planReceive(manifest, paths, CoreIdentities(paths), unchecked = unchecked, probe = FakeSpace())

    private fun ReceivePlan.of(id: String) = decisions.single { it.itemId == id }

    private fun transfer(manifest: Manifest) = IncomingTransfer(
        "offer1", plan(manifest), lib, CoreIdentities(paths),
        onDone = { results += it }, onAbort = { id, inUpload -> aborts += id to inUpload },
    )

    private fun IncomingTransfer.put(id: String, body: ByteArray) = handlePut(id, ByteArrayInputStream(body), body.size.toLong())

    private fun temps(): List<File> = listOf(paths.base, paths.internal).flatMap { root ->
        root.walk().filter { it.name.startsWith(".") }.toList()
    }

    private fun names(console: Console = Console.SFC) = skins.list(console).map { it.name }

    // -- sending ------------------------------------------------------------------------

    @Test
    fun `a skin is offered as an entry with no ROM and one zip of kind skin`() {
        lib.ownSkin("night", images = mapOf("phone-portrait/a.png" to PNG, "phone-landscape/background.png" to JPEG))
        val built = build()
        val zip = built.sources.getValue("g1i1")
        assertEquals(File(work, "skin.zip"), zip)
        val expected = JSONObject().put("id", "g1i1").put("kind", "skin").put("name", "night.zip").put("size", zip.length())
            .put("mtime", zip.lastModified() / 1000).put("sha256", sha256(zip.readBytes()))
        assertEquals(expected.toString(), item(built).toString())
        val game = built.json.getJSONArray("games").getJSONObject(0)
        assertEquals(setOf("id", "console", "title", "items"), game.keys().asSequence().toSet())
        assertEquals(listOf("g1", "SFC", "Night"), listOf(game.getString("id"), game.getString("console"), game.getString("title")))
        assertEquals(1, built.json.getJSONArray("games").length())
        assertEquals(zip.length(), built.json.getLong("total_size"))
        assertEquals(1, built.json.getInt("file_count"))
        assertTrue(built.excluded.isEmpty())
        // What goes is the skin itself, in the format the import reads.
        val sent = SkinFormat.readZip(zip)
        assertEquals(testSkin(), sent.skin)
        assertEquals(setOf("skin.json", "phone-portrait/a.png", "phone-landscape/background.png"), sent.source.files)
        assertTrue(SkinShare.isSkinFor(zip, Console.SFC))
        // The wire's parser takes it as it is: a kind it knows, in a manifest of skins only.
        val manifest = ManifestParser.parse(built.json)
        assertTrue(manifest.items.single().knownKind)
        assertTrue(manifest.games.single().isSkin)
        assertTrue(manifest.skinOnly)
    }

    @Test
    fun `the zip's name on the wire is the skin's name in letters and digits`() {
        lib.ownSkin("my-skin", testSkin("Céu & Mar 2"))
        assertEquals("c-u-mar-2.zip", item(build("my-skin", "Céu & Mar 2")).getString("name"))
        assertEquals("Céu & Mar 2", build("my-skin", "Céu & Mar 2").json.getJSONArray("games").getJSONObject(0).getString("title"))
    }

    @Test
    fun `the next build writes over the last zip`() {
        lib.ownSkin("night")
        lib.ownSkin("day", testSkin("Day"), images = emptyMap())
        val first = build().sources.getValue("g1i1").readBytes()
        val second = build("day", "Day")
        assertEquals(listOf("skin.zip"), work.list()!!.toList())
        assertFalse(first.contentEquals(second.sources.getValue("g1i1").readBytes()))
        assertEquals("Day", SkinFormat.readZip(second.sources.getValue("g1i1")).skin.name)
    }

    @Test
    fun `a skin that is not there or cannot be read builds nothing`() {
        assertThrows(SkinStoreException::class.java) { build("missing") }
        File(sfc, "broken/skin.json").put("{")
        assertThrows(Exception::class.java) { build("broken") }
        assertThrows(SkinStoreException::class.java) { build("../night") }
        assertEquals(emptyList<String>(), work.list().orEmpty().toList())
    }

    @Test
    fun `a build cancelled on the way is cancelled`() {
        lib.ownSkin("night")
        var asked = 0
        val error = assertThrows(ShareError::class.java) { SkinShare.build(lib, SkinToSend(Console.SFC, "night", "Night"), work) { ++asked > 1 } }
        assertEquals(ErrorCode.CANCELLED, error.code)
        assertEquals(emptyList<String>(), work.list().orEmpty().toList())
    }

    @Test
    fun `the selection is the skin alone, and what is sent is what was built`() {
        lib.ownSkin("night")
        val built = build()
        val selection = SendSelection.from(built, emptyList())
        val size = built.json.getLong("total_size")
        assertEquals(listOf(SendGame("g1", "SFC", "Night", listOf(SendPartInfo(SendPart.SKIN, listOf("g1i1"), size)))), selection.games)
        assertFalse(selection.isEmpty)
        assertEquals(1, selection.gameCount)
        assertEquals(1, selection.fileCount)
        assertEquals(size, selection.totalBytes)
        val filtered = selection.filter(built)
        assertEquals(built.json.toString(), filtered.json.toString())
        assertEquals(built.sources, filtered.sources)
    }

    @Test
    fun `a skin goes to a phone or a tablet, never to a desktop`() {
        fun peer(platform: String) = Peer("a".repeat(32), "b".repeat(64), "Other", platform, VersionRange.OWN, 53319, listOf("10.0.0.2"), setOf(PeerPath.MDNS))
        assertTrue(SkinShare.accepts(peer("phone")))
        assertTrue(SkinShare.accepts(peer("tablet")))
        assertFalse(SkinShare.accepts(peer("desktop")))
        // A platform nobody declared reads as a desktop everywhere else too.
        assertFalse(SkinShare.accepts(peer("console")))
    }

    // -- the receiver's plan ------------------------------------------------------------

    @Test
    fun `a skin is asked for and staged in its console's skins folder`() {
        val plan = plan(offer())
        val decision = plan.of("g1i1")
        assertTrue(decision.send)
        assertEquals(Item.SKIN, decision.kind)
        assertEquals(sfc, decision.target!!.parentFile)
        assertNull(decision.replaces)
        assertNull(decision.policy)
        assertEquals(listOf(ItemAnswer.send("g1i1")), plan.answerItems())
        val game = plan.games.single()
        assertTrue(game.skin)
        assertEquals(Console.SFC, game.console)
        assertEquals(RomStatus.ABSENT, game.romStatus)
        assertTrue(plan.okToAccept())
        // Planning writes nothing.
        assertFalse(File(paths.base, "skins").exists())
    }

    @Test
    fun `a skin for a console this app does not run is unsupported_console`() {
        val plan = plan(offer(console = "ZX81"))
        assertEquals(ItemAnswer.skip("g1i1", SkipReason.UNSUPPORTED_CONSOLE), plan.of("g1i1").answer())
        assertNull(plan.games.single().console)
        assertEquals("ZX81", plan.games.single().consoleId)
    }

    @Test
    fun `a skin over the import's limit, under a hidden name, or beside another kind of file is invalid_item`() {
        val huge = ManifestParser.parse(
            JSONObject("""{"games":[{"id":"g1","console":"SFC","title":"Huge","items":[
                {"id":"g1i1","kind":"skin","name":"huge.zip","size":${SkinImport.MAX_BYTES + 1},"mtime":1}]}],
                "total_size":${SkinImport.MAX_BYTES + 1},"file_count":1}"""),
        )
        assertEquals(SkipReason.INVALID_ITEM, plan(huge).of("g1i1").reason)
        assertEquals(SkipReason.INVALID_ITEM, plan(offer(name = ".night.zip")).of("g1i1").reason)
        val mixed = plan(
            manifest {
                game("g1", "SFC", "Night") {
                    item(Item.SKIN, "night.zip", skinZipBytes())
                    item(Item.COVER, "night.png", PNG)
                }
            },
        )
        assertTrue(mixed.of("g1i1").send)
        assertEquals(SkipReason.INVALID_ITEM, mixed.of("g1i2").reason)
    }

    @Test
    fun `a skin among a game's files has nowhere to go, and the game still comes`() {
        val plan = plan(
            manifest {
                game("g1", "SFC", "Game") {
                    item(Item.ROM, "Game.sfc", "rom".toByteArray())
                    item(Item.SKIN, "night.zip", skinZipBytes())
                }
            },
        )
        assertTrue(plan.of("g1i1").send)
        assertEquals(SkipReason.INVALID_ITEM, plan.of("g1i2").reason)
        assertFalse(plan.games.single().skin)
        assertEquals(RomStatus.NEW, plan.games.single().romStatus)
    }

    @Test
    fun `two skins in one offer wait under names of their own`() {
        val plan = plan(
            manifest {
                game("g1", "SFC", "Night") { item(Item.SKIN, "night.zip", skinZipBytes()) }
                game("g2", "SFC", "Night") { item(Item.SKIN, "night.zip", skinZipBytes()) }
                game("g3", "GBA", "Night") { item(Item.SKIN, "night.zip", skinZipBytes(testSkin(console = Console.GBA))) }
            },
        )
        assertTrue(plan.decisions.all { it.send })
        assertEquals(3, plan.decisions.map { it.target }.toSet().size)
        assertEquals(skins.root(Console.GBA), plan.of("g3i1").target!!.parentFile)
    }

    @Test
    fun `a skin the user left out is not asked for, counted or listed`() {
        val plan = plan(offer(), unchecked = setOf("g1"))
        assertEquals(ItemAnswer.skip("g1i1", SkipReason.USER_UNCHECKED), plan.of("g1i1").answer())
        val transfer = IncomingTransfer("offer1", plan, lib, onDone = { results += it })
        transfer.start()
        assertEquals(DeliveryReport(0, 0, emptyList()), receiveReport(plan, results.single()))
        assertEquals(SkipReason.USER_UNCHECKED, plan(offer(), unchecked = setOf("g1i1")).of("g1i1").reason)
    }

    // -- receiving -----------------------------------------------------------------------

    @Test
    fun `a received skin becomes a new skin of the player's own, with every layout it had`() {
        // Only one of its two layouts has an image: the other one is drawn from its colours, and still comes.
        val bytes = skinZipBytes(images = mapOf("phone-portrait/a.png" to PNG))
        val transfer = transfer(offer(bytes))
        transfer.start()
        assertEquals(TransferStatus.RECEIVING, transfer.status.value)
        transfer.put("g1i1", bytes)
        assertEquals(TransferStatus.DONE, transfer.status.value)
        val dir = File(sfc, "night")
        assertEquals(listOf(dir), results.single().placed)
        assertEquals(listOf(dir), lib.received.single())
        val stored = skins.list(Console.SFC).single()
        assertEquals("Night", stored.name)
        assertEquals("own:night", stored.ref)
        assertFalse(stored.broken)
        val read = SkinFormat.readFolder(dir)
        assertEquals(testSkin(), read.skin)
        assertEquals(listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), read.skin.layouts.keys.toList())
        assertArrayEquals(PNG, File(dir, "phone-portrait/a.png").readBytes())
        assertEquals(setOf("skin.json", "phone-portrait/a.png"), read.source.files)
        // The app settled which skin each console plays with before the new folder showed up.
        assertEquals(listOf(emptyList<String>()), lib.skinImports)
        assertEquals(listOf("night"), sfc.list()!!.toList())
        assertTrue(temps().isEmpty())
        assertTrue(aborts.isEmpty())
        assertEquals(DeliveryReport(1, 1, emptyList()), receiveReport(transfer.plan, results.single()))
    }

    @Test
    fun `a console goes on playing with the skin it had`() {
        val library = Library(paths, { true }, storageRetryMs = 0, metaDebounceMs = 0)
        // The app's own wiring (LibraryShareAdapter): its store, and the pick saved before a skin comes in.
        val real = object : ShareLibrary by lib {
            override val skins: SkinStore get() = library.skins
            override fun beforeSkinImport() = library.migrateSkins()
        }
        val bytes = skinZipBytes()
        val transfer = IncomingTransfer("offer1", plan(offer(bytes)), real, onDone = { results += it })
        transfer.put("g1i1", bytes)
        assertEquals(TransferStatus.DONE, transfer.status.value)
        assertEquals(listOf("Night"), library.skins.list(Console.SFC).map { it.name })
        // Never in use by arriving: the bundled skin stays, here and after a restart.
        assertNull(library.customSkin(Console.SFC))
        assertNull(Library(paths, { true }, storageRetryMs = 0, metaDebounceMs = 0).customSkin(Console.SFC))
        // And a console that played with one of the player's skins keeps that one.
        library.useCustomSkin(Console.SFC, "own:night")
        val again = IncomingTransfer("offer2", plan(offer(bytes)), real, onDone = { results += it })
        again.put("g1i1", bytes)
        assertEquals(listOf("Night", "Night (2)"), library.skins.list(Console.SFC).map { it.name })
        assertEquals("own:night", library.customSkin(Console.SFC))
    }

    @Test
    fun `a skin by a name that is taken comes as a second skin, and the first is left as it was`() {
        val mine = lib.ownSkin("night", images = mapOf("phone-portrait/a.png" to JPEG))
        val before = File(mine, "skin.json").readText()
        val bytes = skinZipBytes(images = mapOf("phone-portrait/a.png" to PNG))
        val transfer = transfer(offer(bytes))
        transfer.put("g1i1", bytes)
        assertEquals(TransferStatus.DONE, transfer.status.value)
        assertEquals(listOf("Night", "Night (2)"), names())
        assertEquals(listOf("night", "night-2"), sfc.list()!!.sorted())
        assertEquals(listOf(File(sfc, "night-2")), results.single().placed)
        assertEquals(before, File(mine, "skin.json").readText())
        assertArrayEquals(JPEG, File(mine, "phone-portrait/a.png").readBytes())
        assertArrayEquals(PNG, File(sfc, "night-2/phone-portrait/a.png").readBytes())
        // The same skin a third time is a third one.
        val third = IncomingTransfer("offer2", plan(offer(bytes)), lib)
        third.put("g1i1", bytes)
        assertEquals(listOf("Night", "Night (2)", "Night (3)"), names())
    }

    @Test
    fun `a skin made for another console is refused, and nothing of it is kept`() {
        val bytes = skinZipBytes(testSkin(console = Console.GBA))
        val transfer = transfer(offer(bytes))
        val error = assertThrows(ShareError::class.java) { transfer.put("g1i1", bytes) }
        assertEquals(ErrorCode.BAD_REQUEST, error.code)
        assertEquals(TransferStatus.FAILED, transfer.status.value)
        assertEquals(ErrorCode.BAD_REQUEST, results.single().error)
        assertEquals(listOf("offer1" to true), aborts)
        assertTrue(names().isEmpty())
        assertTrue(names(Console.GBA).isEmpty())
        // Not even the folder the zip waited in.
        assertFalse(File(paths.base, "skins").exists())
        assertTrue(lib.skinImports.isEmpty())
    }

    @Test
    fun `a zip that is not a skin is refused`() {
        val notSkins = listOf(
            "not a zip at all".toByteArray(),
            zipBytes(mapOf("photo.png" to PNG)),
            zipBytes(mapOf("skin.json" to "{".toByteArray())),
            // A Delta package is a skin, but not one in OpenEmux's format: it is not what this kind carries.
            zipBytes(mapOf("info.json" to """{"name":"Clear","gameTypeIdentifier":"com.rileytestut.delta.game.snes"}""".toByteArray())),
            zipBytes(mapOf("skin.json" to SkinFormat.toJson(testSkin()).toByteArray(), "../escape.png" to PNG)),
        )
        for ((n, bytes) in notSkins.withIndex()) {
            results.clear()
            val transfer = transfer(offer(bytes))
            val error = assertThrows("zip $n", ShareError::class.java) { transfer.put("g1i1", bytes) }
            assertEquals("zip $n", ErrorCode.BAD_REQUEST, error.code)
            assertEquals("zip $n", TransferStatus.FAILED, results.single().status)
            assertTrue("zip $n", names().isEmpty())
            assertFalse("zip $n", File(paths.base, "skins").exists())
        }
    }

    @Test
    fun `a skin whose bytes differ from the offer is refused like any other file`() {
        val bytes = skinZipBytes()
        val transfer = transfer(offer(bytes))
        val other = skinZipBytes(testSkin("Other")).copyOf(bytes.size)
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ShareError::class.java) { transfer.put("g1i1", other) }.code)
        assertTrue(names().isEmpty())
    }

    @Test
    fun `an import that fails undoes the whole commit, the game and the skin before it included`() {
        val first = skinZipBytes(testSkin("First"))
        val second = skinZipBytes(testSkin("Second"))
        val manifest = manifest {
            game("g1", "SFC", "Game") { item(Item.ROM, "Game.sfc", "rom".toByteArray()) }
            game("g2", "SFC", "First") { item(Item.SKIN, "first.zip", first) }
            game("g3", "SFC", "Second") { item(Item.SKIN, "second.zip", second) }
        }
        lib.failSkinImport = 2
        val transfer = transfer(manifest)
        assertEquals(listOf("rom", "skin", "skin"), transfer.plan.toSend().map { it.kind })
        transfer.put("g2i1", first)
        transfer.put("g3i1", second)
        val error = assertThrows(ShareError::class.java) { transfer.put("g1i1", "rom".toByteArray()) }
        assertEquals(ErrorCode.INTERNAL, error.code)
        assertEquals(TransferStatus.FAILED, transfer.status.value)
        assertEquals(ErrorCode.INTERNAL, results.single().error)
        assertTrue(results.single().placed.isEmpty())
        assertFalse(File(paths.roms(Console.SFC), "Game.sfc").exists())
        assertTrue(names().isEmpty())
        assertFalse(File(paths.base, "skins").exists())
        assertTrue(temps().isEmpty())
        // The first skin was in before the second one failed.
        assertEquals(listOf(emptyList(), listOf("first")), lib.skinImports)
    }

    @Test
    fun `a game and a skin in one offer both land, the skin last`() {
        val bytes = skinZipBytes()
        val manifest = manifest {
            game("g1", "SFC", "Night") { item(Item.SKIN, "night.zip", bytes) }
            game("g2", "SFC", "Game") { item(Item.ROM, "Game.sfc", "rom".toByteArray()) }
        }
        val transfer = transfer(manifest)
        transfer.put("g1i1", bytes)
        assertTrue(names().isEmpty())
        transfer.put("g2i1", "rom".toByteArray())
        assertEquals(TransferStatus.DONE, transfer.status.value)
        assertEquals(listOf(File(paths.roms(Console.SFC), "Game.sfc"), File(sfc, "night")), results.single().placed)
        assertEquals(DeliveryReport(2, 2, emptyList()), receiveReport(transfer.plan, results.single()))
    }

    @Test
    fun `a cancel while the skin waits leaves nothing behind`() {
        val bytes = skinZipBytes()
        val manifest = manifest {
            game("g1", "SFC", "Night") { item(Item.SKIN, "night.zip", bytes) }
            game("g2", "SFC", "Game") { item(Item.ROM, "Game.sfc", "rom".toByteArray()) }
        }
        val transfer = transfer(manifest)
        transfer.put("g1i1", bytes)
        assertEquals(1, sfc.list()!!.size)
        transfer.cancel()
        assertEquals(TransferStatus.CANCELLED, results.single().status)
        assertFalse(File(paths.base, "skins").exists())
        assertTrue(temps().isEmpty())
    }

    // -- what each side says of it ---------------------------------------------------------

    @Test
    fun `the receive report says why a skin did not come`() {
        val plan = plan(offer(console = "ZX81"))
        val transfer = IncomingTransfer("offer1", plan, lib, onDone = { results += it })
        transfer.start()
        assertEquals(
            DeliveryReport(0, 1, listOf(MissingGame("Night", "ZX81", SkipReason.UNSUPPORTED_CONSOLE))),
            receiveReport(plan, results.single()),
        )
    }

    @Test
    fun `the send report counts the skin that went, and names the one a receiver without skins skipped`() {
        lib.ownSkin("night")
        val built = build()
        assertEquals(DeliveryReport(1, 1, emptyList()), sendReport(built, emptyList(), SendResult(SendResult.DONE, listOf("g1i1"))))
        // An OpenEmux from before the kind (and the desktop app) answers it like any kind it does not know.
        val skipped = SendResult(SendResult.DONE, emptyList(), listOf("g1i1" to SkipReason.INVALID_ITEM))
        assertEquals(
            DeliveryReport(0, 1, listOf(MissingGame("Night", "SFC", SkipReason.INVALID_ITEM))),
            sendReport(built, emptyList(), skipped),
        )
        // One that did not answer the item at all.
        assertEquals(
            DeliveryReport(0, 1, listOf(MissingGame("Night", "SFC", null))),
            sendReport(built, emptyList(), SendResult(SendResult.DONE, emptyList(), listOf("g1i1" to null))),
        )
    }

    @Test
    fun `the offer sheet shows a skin as a skin, with nothing to choose`() {
        val bytes = skinZipBytes()
        val offer = IncomingOffer("offer1", OfferSender("c".repeat(32), "Phone", "phone", "d".repeat(64)), offer(bytes), "482913")
        lib.ownSkin("night")
        val model = OfferModel.build(offer, { planReceive(offer.manifest, paths, CoreIdentities(paths), overrides = it, probe = FakeSpace()) }, emptyMap(), 0)
        assertTrue(model.skinOnly)
        val game = model.games.single()
        assertTrue(game.skin)
        assertEquals(listOf("Night", "SFC", bytes.size.toLong()), listOf(game.title, game.console, game.bytes))
        assertFalse(game.hasRom)
        // A skin by that name is here already: it is no conflict, the new one comes beside it.
        assertTrue(model.conflicts().isEmpty())
        assertTrue(model.skipped.isEmpty())
        assertTrue(model.unsupported.isEmpty())
        assertTrue(model.spaceOk)
    }

    @Test
    fun `an offer of games, or of games and a skin, is not a skin offer`() {
        assertFalse(manifest { }.skinOnly)
        assertFalse(manifest { game("g1", "SFC") { item(Item.ROM, "Game.sfc") } }.skinOnly)
        val mixed = manifest {
            game("g1", "SFC") { item(Item.ROM, "Game.sfc") }
            game("g2", "SFC") { item(Item.SKIN, "night.zip") }
        }
        assertFalse(mixed.skinOnly)
        assertEquals(listOf(false, true), mixed.games.map { it.isSkin })
        // A ROM beside it makes the entry a game.
        assertFalse(manifest { game("g1", "SFC") { item(Item.ROM, "Game.sfc"); item(Item.SKIN, "night.zip") } }.games.single().isSkin)
    }
}
