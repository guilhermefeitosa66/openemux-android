package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Rom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.CoroutineContext

class ShareCoordinatorTest {
    private val lib = FakeLibrary()
    private val paths = lib.paths
    private val scheduler = FakeScheduler()
    private val layer = FakeLayer()
    private val notifier = FakeNotifier()
    private var services = 0
    private val share = ShareCoordinator(
        layer, scheduler, notifier, { services++ }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined,
    )

    private fun peer(name: String = "Desk", compatible: Boolean = true) = Peer(
        name.padEnd(32, 'a').take(32).lowercase().map { if (it in "0123456789abcdef") it else 'a' }.joinToString(""),
        "b".repeat(64), name, "desktop", if (compatible) VersionRange.OWN else VersionRange(7, 9), 53319,
        listOf("10.0.0.2"), setOf(PeerPath.MDNS),
    )

    private val sender = OfferSender("c".repeat(32), "Phone", "phone", "d".repeat(64))

    private fun offer(id: String = "offer1", console: String = "SFC") = IncomingOffer(
        id, sender,
        manifest {
            game("g1", console, "Game") {
                item("rom", "Game.sfc", "rom".toByteArray(), 1_700_000_100)
                item("battery_save", "Game.srm", "save".toByteArray(), 1_700_000_100)
            }
        },
        "482913",
    )

    private fun ready(): List<Rom> {
        val rom = lib.rom(Console.SFC, "Game.sfc", "rom".toByteArray())
        layer.built = ManifestBuilder(lib).build(listOf(rom))
        share.startSend(listOf(rom))
        share.openScreen(send = true)
        return listOf(rom)
    }

    // -- the layer's lifetime ----------------------------------------------------

    @Test
    fun `the first screen starts the layer, and it stops 30 s after the last`() {
        layer.warnings = listOf(Discovery.WARNING_LOCK_REFUSED)
        share.openScreen(send = false)
        assertEquals(1, layer.starts)
        assertEquals(ShareAvailability.Available, share.availability.value)
        assertTrue(share.searching.value)
        assertEquals("Pixel", share.deviceName.value)
        assertEquals(ShareAddress("10.0.0.5", 53319, "e".repeat(64)), share.ownAddress.value)
        assertEquals(listOf(Discovery.WARNING_LOCK_REFUSED), share.warnings.value)
        share.closeScreen()
        scheduler.advance(30_000)
        assertEquals(1, layer.stops)
        assertFalse(share.searching.value)
        assertTrue(share.warnings.value.isEmpty())
        assertNull(share.ownAddress.value)
    }

    @Test
    fun `an unavailable install never starts, and the next screen asks again`() {
        layer.availability = ShareAvailability.Unavailable("tls13")
        share.openScreen(send = false)
        assertEquals(ShareAvailability.Unavailable("tls13"), share.availability.value)
        assertEquals(0, layer.starts)
        share.closeScreen()
        layer.availability = ShareAvailability.Available
        share.openScreen(send = false)
        assertEquals(1, layer.starts)
    }

    @Test
    fun `a start that fails is unavailable with its reason`() {
        layer.startError = ShareError(ErrorCode.UNAVAILABLE, "keystore", mapOf("reason" to "keystore"))
        share.openScreen(send = false)
        assertEquals(ShareAvailability.Unavailable("keystore"), share.availability.value)
        assertFalse(share.searching.value)
        share.closeScreen()
        layer.startError = IllegalStateException("port in use")
        share.openScreen(send = false)
        assertEquals(ShareAvailability.Unavailable(ErrorCode.INTERNAL), share.availability.value)
    }

    @Test
    fun `nobody for 15 s is nothing found, until a compatible device shows up`() {
        share.openScreen(send = false)
        scheduler.advance(14_999)
        assertFalse(share.nothingFound.value)
        scheduler.advance(1)
        assertTrue(share.nothingFound.value)
        layer.peers.value = listOf(peer(compatible = false))
        assertTrue(share.nothingFound.value)
        layer.peers.value = listOf(peer())
        assertFalse(share.nothingFound.value)
        assertEquals(1, share.peers.value.size)
    }

    @Test
    fun `peers before the layer runs are ignored`() {
        layer.peers.value = emptyList()
        scheduler.advance(60_000)
        assertFalse(share.nothingFound.value)
    }

    // -- sending -------------------------------------------------------------------

    @Test
    fun `a send screen builds the manifest of the picked games`() {
        val roms = ready()
        assertEquals(roms, share.sendRoms.value)
        val prep = share.sendPrep.value as SendPrep.Ready
        assertEquals(1, prep.selection.games.size)
        assertEquals(listOf(0L to 3L, 3L to 3L), layer.buildProgress)
        val next = prep.selection.toggle(prep.selection.games[0].id, SendPart.GAME)
        share.updateSelection(next)
        assertEquals(next, (share.sendPrep.value as SendPrep.Ready).selection)
    }

    @Test
    fun `a receive screen or no games builds nothing`() {
        share.openScreen(send = true)
        assertEquals(SendPrep.None, share.sendPrep.value)
        share.updateSelection(SendSelection(emptyList()))
        assertEquals(SendPrep.None, share.sendPrep.value)
        share.startSend(listOf(lib.rom(Console.SFC, "Game.sfc")))
        share.openScreen(send = false)
        assertEquals(SendPrep.None, share.sendPrep.value)
        assertEquals(0, layer.builds)
    }

    @Test
    fun `a manifest that cannot be built says why`() {
        layer.buildError = ShareError(ErrorCode.SOURCE_CHANGED, "gone")
        ready()
        assertEquals(SendPrep.Failed(ShareMessage.SOURCE_CHANGED), share.sendPrep.value)
    }

    @Test
    fun `sending waits for the code, shows the progress and ends sent`() {
        ready()
        val desk = peer()
        share.send(desk)
        assertEquals(TransferState.Waiting("Desk", null), share.transfer.value)
        assertEquals(1, services)
        assertEquals(desk, layer.sentTo)
        layer.onCode("482913")
        assertEquals(TransferState.Waiting("Desk", "482913"), share.transfer.value)
        val p = TransferProgress("g1i1", "Game.sfc", Item.ROM, 1, 3, 1, 3, 0, 1)
        layer.onSendProgress(p)
        assertEquals(TransferState.Running(true, "Desk", 1, p), share.transfer.value)
        // The screen closes: the layer stays up while the transfer runs.
        share.closeScreen()
        scheduler.advance(60_000)
        assertEquals(0, layer.stops)
        layer.onSendDone(SendResult(SendResult.DONE, listOf("g1i1"), listOf("g1i2" to SkipReason.EXISTS_SKIP)))
        assertEquals(TransferState.Finished(ShareResult(ResultKind.SENT, true, "Desk", 1)), share.transfer.value)
        scheduler.advance(30_000)
        assertEquals(1, layer.stops)
        share.dismissResult()
        assertEquals(TransferState.Idle, share.transfer.value)
        share.dismissResult()
        assertEquals(TransferState.Idle, share.transfer.value)
    }

    @Test
    fun `a send counts the games the receiver took and lists the others, the ones left out of the manifest too`() {
        val good = lib.rom(Console.SFC, "Game.sfc", "rom".toByteArray())
        val broken = lib.rom(Console.PS, "Broken.cue", "FILE \"Missing.bin\" BINARY\n".toByteArray())
        layer.built = ManifestBuilder(lib).build(listOf(good, broken))
        share.startSend(listOf(good, broken))
        share.openScreen(send = true)
        val prep = share.sendPrep.value as SendPrep.Ready
        val rom = prep.selection.games.single().parts.first { it.part == SendPart.GAME }.itemIds.single()
        share.send(peer())
        layer.onSendDone(SendResult(SendResult.DONE, skipped = listOf(rom to SkipReason.USER_UNCHECKED)))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(0, result.games)
        assertEquals(2, result.total)
        assertEquals(
            listOf(MissingGame("Game", "SFC", SkipReason.USER_UNCHECKED), MissingGame("Broken", "PS", SkipReason.UNIT_INCOMPLETE)),
            result.missing,
        )
        assertEquals(listOf(result), notifier.finished)
    }

    @Test
    fun `how a send ends maps to its result`() {
        val cases = listOf(
            SendResult(SendResult.DECLINED) to ResultKind.DECLINED,
            SendResult(SendResult.EXPIRED) to ResultKind.EXPIRED,
            SendResult(SendResult.CANCELLED) to ResultKind.CANCELLED,
            SendResult(SendResult.FAILED, error = ErrorCode.UNREACHABLE) to ResultKind.CONNECTION_LOST,
            SendResult(SendResult.FAILED, error = ErrorCode.IDLE_TIMEOUT) to ResultKind.CONNECTION_LOST,
            SendResult(SendResult.FAILED, error = ErrorCode.INTERNAL) to ResultKind.FAILED,
        )
        ready()
        for ((result, kind) in cases) {
            share.send(peer())
            layer.onSendDone(result)
            val finished = (share.transfer.value as TransferState.Finished).result
            assertEquals(kind, finished.kind)
            assertEquals(if (kind == ResultKind.FAILED) ShareMessage.GENERIC else null, finished.error)
            share.dismissResult()
        }
    }

    @Test
    fun `a file that changed under the offer builds the manifest again, keeping the choices`() {
        ready()
        val prep = share.sendPrep.value as SendPrep.Ready
        val gameId = prep.selection.games[0].id
        share.updateSelection(prep.selection.toggle(gameId, SendPart.SAVE))
        share.send(peer())
        layer.onSendDone(SendResult(SendResult.FAILED, error = ErrorCode.SOURCE_CHANGED))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(ShareMessage.SOURCE_CHANGED, result.error)
        assertEquals(2, layer.builds)
        assertFalse((share.sendPrep.value as SendPrep.Ready).selection.isChecked(gameId, SendPart.SAVE))
    }

    @Test
    fun `a send left running that finds a changed file leaves the new manifest to the next screen`() {
        ready()
        share.send(peer())
        share.closeScreen(send = true)
        layer.onSendDone(SendResult(SendResult.FAILED, error = ErrorCode.SOURCE_CHANGED))
        assertEquals(1, layer.builds)
        assertEquals(SendPrep.None, share.sendPrep.value)
        share.openScreen(send = true)
        assertEquals(2, layer.builds)
    }

    @Test
    fun `nothing is sent without a manifest, a choice, a compatible peer or while busy`() {
        share.send(peer())
        assertNull(layer.sentTo)
        ready()
        share.send(peer(compatible = false))
        assertNull(layer.sentTo)
        val prep = share.sendPrep.value as SendPrep.Ready
        share.updateSelection(prep.selection.toggleGame(prep.selection.games[0].id))
        share.send(peer())
        assertNull(layer.sentTo)
        share.updateSelection(prep.selection)
        share.send(peer("One"))
        share.send(peer("Two"))
        assertEquals("One", layer.sentTo?.name)
        assertEquals(1, layer.sends)
    }

    @Test
    fun `a send the layer refuses ends failed`() {
        ready()
        layer.sendError = ShareError(ErrorCode.UNAVAILABLE, "no identity")
        share.send(peer())
        assertEquals(ResultKind.FAILED, (share.transfer.value as TransferState.Finished).result.kind)
    }

    @Test
    fun `cancel reaches the transfer, even when asked before it started`() {
        ready()
        share.cancel()
        assertEquals(0, layer.cancels)
        share.send(peer())
        share.cancel()
        assertEquals(1, layer.cancels)
        layer.onSendDone(SendResult(SendResult.CANCELLED))
        share.dismissResult()
        layer.cancelDuringSend = { share.cancel() }
        share.send(peer())
        assertEquals(2, layer.cancels)
    }

    @Test
    fun `a receiving cancel runs on io, and one that fails is ignored`() {
        // Cancelling closes sockets: from the notification or the screen it comes on the main thread.
        val io = QueueDispatcher()
        val share = ShareCoordinator(layer, scheduler, notifier, { services++ }, CoroutineScope(Dispatchers.Unconfined), io)
        layer.incoming.value = offer()
        io.runAll()
        share.accept()
        io.runAll()
        share.cancel()
        assertEquals(0, layer.cancels)
        io.runAll()
        assertEquals(1, layer.cancels)
        layer.onReceiveDone(ReceiveResult(TransferStatus.CANCELLED))
        share.dismissResult()
        layer.cancelError = IllegalStateException("socket")
        layer.incoming.value = offer("offer2")
        io.runAll()
        share.accept()
        io.runAll()
        share.cancel()
        io.runAll()
        assertTrue(share.transfer.value.busy)
    }

    @Test
    fun `progress after the end is ignored`() {
        ready()
        share.send(peer())
        layer.onSendDone(SendResult(SendResult.DONE))
        layer.onCode("123456")
        layer.onSendProgress(TransferProgress("g1i1", "Game.sfc", Item.ROM, 1, 3, 1, 3, 0, 1))
        assertTrue(share.transfer.value is TransferState.Finished)
    }

    // -- a controller skin -----------------------------------------------------------

    private val night = SkinToSend(Console.SFC, "night", "Night")

    private fun phone() = peer("Pixel").copy(platform = "phone")

    private fun skinReady() {
        lib.ownSkin("night")
        share.startSendSkin(night)
        share.openScreen(send = true)
    }

    private fun skinOffer(id: String = "offer1", console: String = "SFC") = IncomingOffer(
        id, sender, manifest { game("g1", console, "Night") { item(Item.SKIN, "night.zip", skinZipBytes()) } }, "482913",
    )

    @Test
    fun `a skin picked to send is built in place of games, and games picked next take its place`() {
        skinReady()
        assertEquals(night, share.sendSkin.value)
        assertTrue(share.sendRoms.value.isEmpty())
        assertEquals(listOf(night), layer.skinBuilds)
        assertEquals(0, layer.builds)
        val prep = share.sendPrep.value as SendPrep.Ready
        assertEquals(listOf(SendPart.SKIN), prep.selection.games.single().parts.map { it.part })
        assertEquals("Night", prep.selection.games.single().title)
        // Picking games afterwards is a games screen again.
        ready()
        assertNull(share.sendSkin.value)
        assertEquals(1, layer.builds)
        assertEquals(listOf(SendPart.GAME), (share.sendPrep.value as SendPrep.Ready).selection.games.single().parts.map { it.part })
        // And a skin after games drops the games.
        share.startSendSkin(night)
        assertEquals(SendPrep.None, share.sendPrep.value)
        assertTrue(share.sendRoms.value.isEmpty())
    }

    @Test
    fun `a skin that cannot be packed says so`() {
        share.startSendSkin(night)
        share.openScreen(send = true)
        assertEquals(SendPrep.Failed(ShareMessage.GENERIC), share.sendPrep.value)
    }

    @Test
    fun `a skin is never offered to a desktop`() {
        skinReady()
        share.send(peer())
        assertEquals(TransferState.Idle, share.transfer.value)
        assertEquals(0, layer.sends)
        assertEquals(0, services)
    }

    @Test
    fun `sending a skin says it is a skin all the way to the result`() {
        skinReady()
        share.send(phone())
        assertEquals(TransferState.Waiting("Pixel", null), share.transfer.value)
        assertEquals(1, layer.sends)
        val p = TransferProgress("g1i1", "night.zip", Item.SKIN, 1, 3, 1, 3, 0, 1)
        layer.onSendProgress(p)
        assertEquals(TransferState.Running(true, "Pixel", 1, p, skin = true), share.transfer.value)
        layer.onSendDone(SendResult(SendResult.DONE, listOf("g1i1")))
        assertEquals(TransferState.Finished(ShareResult(ResultKind.SENT, true, "Pixel", 1, skin = true)), share.transfer.value)
        assertEquals(listOf(true), notifier.finished.map { it.skin })
    }

    @Test
    fun `a receiver from before skins leaves the skin not sent, with its answer as the reason`() {
        skinReady()
        share.send(phone())
        layer.onSendDone(SendResult(SendResult.DONE, emptyList(), listOf("g1i1" to SkipReason.INVALID_ITEM)))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(
            ShareResult(ResultKind.SENT, true, "Pixel", 0, total = 1, missing = listOf(MissingGame("Night", "SFC", SkipReason.INVALID_ITEM)), skin = true),
            result,
        )
    }

    @Test
    fun `a skin that ends any other way is still a skin's result`() {
        for ((status, kind) in listOf(SendResult.DECLINED to ResultKind.DECLINED, SendResult.EXPIRED to ResultKind.EXPIRED, SendResult.CANCELLED to ResultKind.CANCELLED)) {
            skinReady()
            share.send(phone())
            layer.onSendDone(SendResult(status))
            assertEquals(ShareResult(kind, true, "Pixel", skin = true), (share.transfer.value as TransferState.Finished).result)
            share.dismissResult()
        }
        skinReady()
        layer.sendError = ShareError(ErrorCode.BUSY, "busy")
        share.send(phone())
        assertEquals(
            ShareResult(ResultKind.FAILED, true, "Pixel", error = ShareMessage.BUSY, skin = true),
            (share.transfer.value as TransferState.Finished).result,
        )
    }

    @Test
    fun `an offered skin is notified and received as a skin, and its result names its console`() {
        layer.incoming.value = skinOffer()
        assertEquals(listOf(true), notifier.skinOffers)
        assertTrue(share.pendingOffer.value!!.skinOnly)
        share.accept()
        assertEquals("offer1", layer.accepted)
        assertEquals(TransferState.Running(false, "Phone", 1, null, skin = true), share.transfer.value)
        val p = TransferProgress("g1i1", "night.zip", Item.SKIN, 3, 3, 3, 3, 1, 1)
        layer.onReceiveProgress(p)
        assertEquals(TransferState.Running(false, "Phone", 1, p, skin = true), share.transfer.value)
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(ShareResult(ResultKind.RECEIVED, false, "Phone", 1, firstConsole = "SFC", skin = true), result)
        assertEquals(listOf(result), notifier.finished)
    }

    @Test
    fun `an offered skin this device cannot take is received as nothing, with why`() {
        share.setForeground(true)
        layer.incoming.value = skinOffer(console = "ZX81")
        share.accept()
        assertEquals(TransferState.Running(false, "Phone", 0, null, skin = true), share.transfer.value)
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE, skipped = listOf("g1i1" to SkipReason.UNSUPPORTED_CONSOLE)))
        assertEquals(
            ShareResult(
                ResultKind.RECEIVED, false, "Phone", 0, total = 1,
                missing = listOf(MissingGame("Night", "ZX81", SkipReason.UNSUPPORTED_CONSOLE)), skin = true,
            ),
            (share.transfer.value as TransferState.Finished).result,
        )
    }

    @Test
    fun `an offer of games is notified as games`() {
        layer.incoming.value = offer()
        assertEquals(listOf(false), notifier.skinOffers)
        assertFalse(share.pendingOffer.value!!.skinOnly)
    }

    // -- receiving -------------------------------------------------------------------

    @Test
    fun `an offer shows with its plan, and a notification only when the app is not in front`() {
        layer.incoming.value = offer()
        val model = share.pendingOffer.value!!
        assertEquals("Phone", model.sender)
        assertEquals(scheduler.time, model.receivedAt)
        assertEquals(listOf("Phone" to "482913"), notifier.offers)
        share.setForeground(true)
        assertEquals(1, notifier.cleared)
        layer.incoming.value = offer("offer2")
        assertEquals(1, notifier.offers.size)
        assertEquals("offer2", share.pendingOffer.value!!.offer.offerId)
        // The same offer again is the same sheet.
        val shown = share.pendingOffer.value
        layer.incoming.value = offer("offer2")
        assertTrue(shown === share.pendingOffer.value)
    }

    @Test
    fun `an unanswered offer expires after 60 s with a toast`() {
        val events = mutableListOf<ShareEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch { share.events.collect { events += it } }
        layer.incoming.value = offer()
        scheduler.advance(59_999)
        assertNotNull(share.pendingOffer.value)
        scheduler.advance(1)
        assertNull(share.pendingOffer.value)
        assertEquals(listOf(ShareEvent.OfferExpired("Phone")), events)
        assertTrue(notifier.cleared > 0)
        // The layer still holds it: it is not shown again.
        layer.incoming.value = null
        layer.incoming.value = offer()
        assertNull(share.pendingOffer.value)
    }

    @Test
    fun `the sender cancelling closes the sheet`() {
        layer.incoming.value = offer()
        layer.incoming.value = null
        assertNull(share.pendingOffer.value)
        scheduler.advance(60_000)
    }

    @Test
    fun `an offer that arrives in the grace keeps the layer up for its 60 s`() {
        share.openScreen(send = false)
        share.closeScreen()
        scheduler.advance(15_000)
        layer.incoming.value = offer()
        scheduler.advance(30_000)
        assertEquals(0, layer.stops)
        assertNotNull(share.pendingOffer.value)
        share.decline()
        scheduler.advance(29_999)
        assertEquals(0, layer.stops)
        scheduler.advance(1)
        assertEquals(1, layer.stops)
    }

    @Test
    fun `decline answers the layer and closes the sheet`() {
        share.decline()
        assertTrue(layer.declined.isEmpty())
        layer.incoming.value = offer()
        layer.declineError = ShareError(ErrorCode.EXPIRED, "late")
        share.decline()
        assertEquals(listOf("offer1"), layer.declined)
        assertNull(share.pendingOffer.value)
    }

    @Test
    fun `a choice replans the offer`() {
        File(paths.saves(Console.SFC), "Game.srm").put("mine", mtime = 1_700_000_200)
        layer.incoming.value = offer()
        val save = share.pendingOffer.value!!.conflicts().single()
        share.setOverride(save.itemId, Policy.REPLACE)
        assertEquals(Policy.REPLACE, share.pendingOffer.value!!.conflicts().single().policy)
        share.decline()
        share.setOverride(save.itemId, Policy.SKIP)
        assertNull(share.pendingOffer.value)
    }

    @Test
    fun `accepting receives, and the result names the console to open`() {
        share.setForeground(true)
        File(paths.saves(Console.SFC), "Game.srm").put("mine", mtime = 1_700_000_200)
        layer.incoming.value = offer()
        share.accept()
        assertEquals("offer1", layer.accepted)
        assertNull(share.pendingOffer.value)
        assertEquals(TransferState.Running(false, "Phone", 1, null), share.transfer.value)
        assertEquals(1, services)
        val p = TransferProgress("g1i1", "Game.sfc", Item.ROM, 3, 3, 3, 3, 1, 1)
        layer.onReceiveProgress(p)
        assertEquals(TransferState.Running(false, "Phone", 1, p), share.transfer.value)
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE, skipped = listOf("g1i2" to SkipReason.EXISTS_SKIP)))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(ShareResult(ResultKind.RECEIVED, false, "Phone", 1, 1, null, "SFC"), result)
        // In front: no notification for the end.
        assertTrue(notifier.finished.isEmpty())
    }

    @Test
    fun `how a receive ends maps to its result, and the background hears of it`() {
        val cases = listOf(
            ReceiveResult(TransferStatus.CANCELLED) to ResultKind.CANCELLED,
            ReceiveResult(TransferStatus.FAILED, error = ErrorCode.UNREACHABLE) to ResultKind.CONNECTION_LOST,
            ReceiveResult(TransferStatus.FAILED, error = ErrorCode.NO_SPACE) to ResultKind.FAILED,
        )
        cases.forEachIndexed { i, (result, kind) ->
            layer.incoming.value = offer("offer$i")
            share.accept()
            layer.onReceiveDone(result)
            assertEquals(kind, (share.transfer.value as TransferState.Finished).result.kind)
            share.dismissResult()
        }
        assertEquals(listOf(ResultKind.CANCELLED, ResultKind.CONNECTION_LOST, ResultKind.FAILED), notifier.finished.map { it.kind })
        assertEquals(ShareMessage.NO_SPACE, notifier.finished.last().error)
    }

    @Test
    fun `a receive counts the games that arrived and lists the others`() {
        val offer = IncomingOffer(
            "offer1", sender,
            manifest {
                game("g1", "SFC", "Game") { item("rom", "Game.sfc", "rom".toByteArray()) }
                game("g2", "XYZ", "Alien") { item("rom", "Alien.xyz", "x".toByteArray()) }
            },
            "482913",
        )
        layer.incoming.value = offer
        share.accept()
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE, skipped = listOf("g2i1" to SkipReason.UNSUPPORTED_CONSOLE)))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(ShareResult(ResultKind.RECEIVED, false, "Phone", 1, 0, null, "SFC", 2, listOf(MissingGame("Alien", "XYZ", SkipReason.UNSUPPORTED_CONSOLE))), result)
    }

    @Test
    fun `a receive names the BIOS files it added`() {
        layer.incoming.value = IncomingOffer(
            "offer1", sender,
            manifest { game("g1", "PS", "Game") { item("rom", "Game.chd"); item("bios", "scph5501.bin") } },
            "482913",
        )
        share.accept()
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(listOf("scph5501.bin"), result.bios)
        assertEquals(1, result.games)
    }

    @Test
    fun `a send names the BIOS files that went`() {
        File(paths.system, "scph5501.bin").put("bios")
        val rom = lib.rom(Console.PS, "Game.chd")
        layer.built = ManifestBuilder(lib).build(listOf(rom))
        share.startSend(listOf(rom))
        share.openScreen(send = true)
        share.send(peer())
        layer.onSendDone(SendResult(SendResult.DONE, listOf("g1i1", "g1i2")))
        val result = (share.transfer.value as TransferState.Finished).result
        assertEquals(listOf("scph5501.bin"), result.bios)
        assertEquals(1, result.games)
    }

    @Test
    fun `leaving the progress view keeps the transfer running, and a new transfer starts with it open`() {
        layer.incoming.value = offer()
        share.accept()
        share.hideProgress()
        assertTrue(share.progressHidden.value)
        assertEquals(1, services)
        assertEquals(TransferState.Running(false, "Phone", 1, null), share.transfer.value)
        assertEquals(TransferSurface.CHIP, transferSurface(share.transfer.value, share.progressHidden.value, false))
        share.showProgress()
        assertFalse(share.progressHidden.value)
        share.hideProgress()
        layer.onReceiveDone(ReceiveResult(TransferStatus.DONE))
        assertEquals(TransferSurface.SHEET, transferSurface(share.transfer.value, share.progressHidden.value, false))
        share.dismissResult()
        layer.incoming.value = offer("offer2")
        share.accept()
        assertFalse(share.progressHidden.value)
    }

    @Test
    fun `only a running transfer can be hidden`() {
        share.hideProgress()
        assertFalse(share.progressHidden.value)
        val roms = ready()
        assertEquals(1, roms.size)
        share.send(peer())
        assertTrue(share.transfer.value is TransferState.Waiting)
        share.hideProgress()
        assertFalse(share.progressHidden.value)
    }

    @Test
    fun `send screens are counted, and a receive screen is not one`() {
        share.openScreen(send = false)
        assertEquals(0, share.sendScreens.value)
        share.closeScreen()
        share.openScreen(send = true)
        share.openScreen(send = true)
        assertEquals(2, share.sendScreens.value)
        share.closeScreen(send = true)
        share.closeScreen(send = true)
        share.closeScreen(send = true)
        assertEquals(0, share.sendScreens.value)
    }

    @Test
    fun `where a transfer shows`() {
        val incoming = TransferState.Running(false, "Phone", 1, null)
        val outgoing = TransferState.Running(true, "Phone", 1, null)
        val received = TransferState.Finished(ShareResult(ResultKind.RECEIVED, false, "Phone"))
        val sent = TransferState.Finished(ShareResult(ResultKind.SENT, true, "Phone"))
        assertEquals(TransferSurface.SHEET, transferSurface(incoming, hidden = false, sendScreen = false))
        assertEquals(TransferSurface.CHIP, transferSurface(incoming, hidden = true, sendScreen = false))
        assertEquals(TransferSurface.CHIP, transferSurface(incoming, hidden = true, sendScreen = true))
        assertEquals(TransferSurface.NONE, transferSurface(outgoing, hidden = false, sendScreen = true))
        assertEquals(TransferSurface.NONE, transferSurface(outgoing, hidden = true, sendScreen = true))
        assertEquals(TransferSurface.CHIP, transferSurface(outgoing, hidden = true, sendScreen = false))
        assertEquals(TransferSurface.SHEET, transferSurface(outgoing, hidden = false, sendScreen = false))
        assertEquals(TransferSurface.SHEET, transferSurface(received, hidden = true, sendScreen = true))
        assertEquals(TransferSurface.NONE, transferSurface(sent, hidden = false, sendScreen = true))
        assertEquals(TransferSurface.SHEET, transferSurface(sent, hidden = true, sendScreen = false))
        assertEquals(TransferSurface.NONE, transferSurface(TransferState.Waiting("Phone", null), hidden = false, sendScreen = false))
        assertEquals(TransferSurface.NONE, transferSurface(TransferState.Idle, hidden = true, sendScreen = false))
    }

    @Test
    fun `a refused accept keeps the sheet with the reason`() {
        layer.incoming.value = offer()
        layer.acceptError = ShareError(ErrorCode.TOO_LARGE, "no room")
        share.accept()
        assertEquals(TransferState.Idle, share.transfer.value)
        assertEquals(ShareMessage.TOO_LARGE, share.pendingOffer.value!!.error)
    }

    @Test
    fun `accept does nothing without an offer, without room or while busy`() {
        share.accept()
        assertNull(layer.accepted)
        layer.space = FakeSpace(default = 10)
        layer.incoming.value = offer()
        share.accept()
        assertNull(layer.accepted)
    }

    @Test
    fun `an offer during a transfer is declined on its own`() {
        ready()
        share.send(peer())
        layer.incoming.value = offer()
        assertNull(share.pendingOffer.value)
        assertEquals(listOf("offer1"), layer.declined)
        layer.incoming.value = offer()
        assertEquals(1, layer.declined.size)
    }

    @Test
    fun `an offer that cannot be planned is declined, not shown half-built`() {
        layer.planError = IllegalStateException("broken")
        layer.incoming.value = offer()
        assertNull(share.pendingOffer.value)
        assertEquals(listOf("offer1"), layer.declined)
    }

    // -- the rest ----------------------------------------------------------------------

    @Test
    fun `the device name is trimmed and cut to the protocol's limit`() {
        share.loadName()
        assertEquals("Pixel", share.deviceName.value)
        share.rename("  " + "x".repeat(80) + "  ")
        assertEquals("x".repeat(ShareProtocol.MAX_NAME_CHARS), layer.name)
        assertEquals(layer.name, share.deviceName.value)
        assertEquals("Pixel 8", share.defaultName())
        layer.nameError = ShareError(ErrorCode.UNAVAILABLE, "keystore")
        share.rename("Other")
        assertEquals(layer.name, share.deviceName.value)
        assertEquals(scheduler.time, share.now())
    }

    @Test
    fun `connect by address checks the parse, the reach, the identity and the version`() = runBlocking {
        assertEquals(ConnectOutcome(error = ShareMessage.BAD_REQUEST), share.connect("not an address/"))
        val desk = peer()
        layer.manual = desk
        assertEquals(ConnectOutcome(desk), share.connect("10.0.0.2:53319"))
        assertEquals(ShareAddress("10.0.0.2", 53319), layer.manualAddress)
        val old = peer(compatible = false)
        layer.manual = old
        assertEquals(ConnectOutcome(old, ShareMessage.INCOMPATIBLE_VERSION, 9), share.connect("10.0.0.2"))
        val errors = mapOf(
            ErrorCode.FINGERPRINT_MISMATCH to ShareMessage.FINGERPRINT_MISMATCH,
            ErrorCode.INCOMPATIBLE_VERSION to ShareMessage.INCOMPATIBLE_VERSION,
            ErrorCode.BAD_REQUEST to ShareMessage.BAD_REQUEST,
            ErrorCode.UNREACHABLE to ShareMessage.UNREACHABLE,
            ErrorCode.UNAVAILABLE to ShareMessage.UNREACHABLE,
        )
        for ((code, message) in errors) {
            layer.manualError = ShareError(code, code)
            assertEquals(ConnectOutcome(error = message), share.connect("10.0.0.2"))
        }
    }

    // -- fakes -----------------------------------------------------------------------

    private class FakeNotifier : ShareNotifier {
        val offers = mutableListOf<Pair<String, String>>()
        /** Whether each offer notified was of a controller skin. */
        val skinOffers = mutableListOf<Boolean>()
        val finished = mutableListOf<ShareResult>()
        var cleared = 0
        override fun offer(sender: String, code: String, skin: Boolean) { offers += sender to code; skinOffers += skin }
        override fun clearOffer() { cleared++ }
        override fun finished(result: ShareResult) { finished += result }
    }

    /** Runs what is dispatched to it only when asked: shows what did not run inline. */
    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private inner class FakeLayer : ShareLayer {
        override val peers = MutableStateFlow<List<Peer>>(emptyList())
        override val incoming = MutableStateFlow<IncomingOffer?>(null)
        override val lan = MutableStateFlow(true)
        var availability: ShareAvailability = ShareAvailability.Available
        var startError: Exception? = null
        var warnings = emptyList<String>()
        var starts = 0
        var stops = 0
        var name = "Pixel"
        var nameError: ShareError? = null
        var built: BuiltManifest? = null
        var buildError: ShareError? = null
        var builds = 0
        val skinBuilds = mutableListOf<SkinToSend>()
        val buildProgress = mutableListOf<Pair<Long, Long>>()
        var sentTo: Peer? = null
        var sends = 0
        var sendError: ShareError? = null
        var cancels = 0
        var cancelError: Exception? = null
        var cancelDuringSend: (() -> Unit)? = null
        var onCode: (String) -> Unit = {}
        var onSendProgress: (TransferProgress) -> Unit = {}
        var onSendDone: (SendResult) -> Unit = {}
        var space: SpaceProbe = FakeSpace()
        var planError: Exception? = null
        var accepted: String? = null
        var acceptError: ShareError? = null
        var onReceiveProgress: (TransferProgress) -> Unit = {}
        var onReceiveDone: (ReceiveResult) -> Unit = {}
        val declined = mutableListOf<String>()
        var declineError: ShareError? = null
        var manual: Peer? = null
        var manualAddress: ShareAddress? = null
        var manualError: ShareError? = null

        override fun availability() = availability
        override fun start(): List<String> {
            startError?.let { throw it }
            starts++
            return warnings
        }
        override fun stop() { stops++ }
        override fun deviceName() = name
        override fun defaultName() = "Pixel 8"
        override fun setName(name: String): String {
            nameError?.let { throw it }
            this.name = name
            return name
        }
        override fun ownAddress() = ShareAddress("10.0.0.5", 53319, "e".repeat(64))
        override fun buildManifest(roms: List<Rom>, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): BuiltManifest {
            builds++
            buildError?.let { throw it }
            onProgress(0, 3)
            onProgress(3, 3)
            buildProgress += listOf(0L to 3L, 3L to 3L)
            assertFalse(cancelled())
            return built!!
        }
        override fun buildSkinManifest(skin: SkinToSend, cancelled: () -> Boolean): BuiltManifest {
            skinBuilds += skin
            buildError?.let { throw it }
            assertFalse(cancelled())
            return SkinShare.build(lib, skin, File(paths.internal, "share-skin"), cancelled)
        }
        override fun send(peer: Peer, built: BuiltManifest, onCode: (String) -> Unit, onProgress: (TransferProgress) -> Unit, onDone: (SendResult) -> Unit): () -> Unit {
            sendError?.let { throw it }
            sends++
            sentTo = peer
            this.onCode = onCode
            onSendProgress = onProgress
            onSendDone = onDone
            cancelDuringSend?.invoke()
            return { cancels++ }
        }
        override fun plan(offer: IncomingOffer, overrides: Map<String, Policy>): ReceivePlan {
            planError?.let { throw it }
            return planReceive(offer.manifest, paths, CoreIdentities(paths), overrides = overrides, probe = space)
        }
        override fun accept(offerId: String, plan: ReceivePlan, onProgress: (TransferProgress) -> Unit, onDone: (ReceiveResult) -> Unit): () -> Unit {
            acceptError?.let { throw it }
            accepted = offerId
            onReceiveProgress = onProgress
            onReceiveDone = onDone
            return {
                cancelError?.let { throw it }
                cancels++
            }
        }
        override fun decline(offerId: String) {
            declined += offerId
            declineError?.let { throw it }
        }
        override fun addManualPeer(address: ShareAddress): Peer {
            manualError?.let { throw it }
            manualAddress = address
            return manual!!
        }
    }
}
