package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveSlotsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun opensOnTheFirstNumberedSlotWithNothingSaved() {
        assertEquals(1, SaveSlots.opening(emptyList()))
    }

    @Test fun opensAfterTheHighestSlotInUse() {
        assertEquals(6, SaveSlots.opening(listOf(5, 2)))
    }

    @Test fun theAutoStateAndSlotZeroDoNotMoveTheOpening() {
        assertEquals(1, SaveSlots.opening(listOf(-1, 0)))
    }

    @Test fun pastTheEndOpensOnTheLowestFreeSlot() {
        assertEquals(1, SaveSlots.opening(listOf(99)))
        assertEquals(3, SaveSlots.opening(listOf(1, 2, 99)))
        assertEquals(1, SaveSlots.opening(listOf(150)))
    }

    @Test fun onlySlotZeroFreeOpensThere() {
        assertEquals(0, SaveSlots.opening((1..99).toList()))
    }

    @Test fun everySlotTakenOpensOnTheFirstNumberedOne() {
        assertEquals(1, SaveSlots.opening((0..99).toList()))
    }

    @Test fun theArrowsMoveOneSlot() {
        assertEquals(5, SaveSlots.step(4, 1))
        assertEquals(3, SaveSlots.step(4, -1))
    }

    @Test fun theArrowsWrapAtBothEnds() {
        assertEquals(0, SaveSlots.step(99, 1))
        assertEquals(99, SaveSlots.step(0, -1))
    }

    @Test fun deletingRemovesTheStateAndItsPicture() {
        val state = folder.newFile("game.state3")
        val picture = folder.newFile("game.state3.png")
        assertTrue(SaveSlots.delete(state))
        assertFalse(state.exists())
        assertFalse(picture.exists())
    }

    @Test fun deletingLeavesTheOtherSlotsAlone() {
        val state = folder.newFile("game.state3")
        val others = listOf("game.state", "game.state2", "game.state2.png", "game.state30", "game.state.auto").map(folder::newFile)
        SaveSlots.delete(state)
        others.forEach { assertTrue(it.name, it.exists()) }
    }

    @Test fun aStateWithNoPictureIsDeleted() {
        val state = folder.newFile("game.state3")
        assertTrue(SaveSlots.delete(state))
        assertFalse(state.exists())
    }

    @Test fun aStateAlreadyGoneCountsAsDeleted() {
        assertTrue(SaveSlots.delete(File(folder.root, "game.state7")))
    }

    @Test fun aStateThatStaysKeepsItsPicture() {
        // A directory with something in it: File.delete() refuses it, as it would a file it cannot remove.
        val state = folder.newFolder("game.state4")
        File(state, "inside").writeText("x")
        val picture = folder.newFile("game.state4.png")
        assertFalse(SaveSlots.delete(state))
        assertTrue(picture.exists())
    }
}
