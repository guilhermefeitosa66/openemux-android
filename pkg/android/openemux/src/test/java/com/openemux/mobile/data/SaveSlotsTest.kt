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

    @Test fun aRomThatNeverChoseASlotIsOnSlotZero() {
        assertEquals(0, SaveSlots.current(folder.root, "game"))
        assertEquals(0, SaveSlots.current(File(folder.root, "missing"), "game"))
    }

    @Test fun theCurrentSlotIsKeptPerRom() {
        assertTrue(SaveSlots.setCurrent(folder.root, "game", 3))
        assertTrue(SaveSlots.setCurrent(folder.root, "other", 7))
        assertEquals(3, SaveSlots.current(folder.root, "game"))
        assertEquals(7, SaveSlots.current(folder.root, "other"))
        assertEquals("3", File(folder.root, "game.slot").readText().trim())
    }

    @Test fun choosingAgainReplacesTheSlotAndLeavesNoTemporaryFile() {
        SaveSlots.setCurrent(folder.root, "game", 3)
        SaveSlots.setCurrent(folder.root, "game", 99)
        assertEquals(99, SaveSlots.current(folder.root, "game"))
        assertEquals(listOf("game.slot"), folder.root.list()!!.toList())
    }

    @Test fun theFolderIsCreatedForTheFirstChoice() {
        val dir = File(folder.root, "states/GBA")
        assertTrue(SaveSlots.setCurrent(dir, "game", 5))
        assertEquals(5, SaveSlots.current(dir, "game"))
    }

    @Test fun theAutoStateAndSlotsPastTheRangeAreNeverCurrent() {
        SaveSlots.setCurrent(folder.root, "game", 4)
        assertFalse(SaveSlots.setCurrent(folder.root, "game", -1))
        assertFalse(SaveSlots.setCurrent(folder.root, "game", SCRATCH_SLOT))
        assertFalse(SaveSlots.setCurrent(folder.root, "game", 100))
        assertEquals(4, SaveSlots.current(folder.root, "game"))
    }

    @Test fun aDamagedOrOutOfRangeFileFallsBackToSlotZero() {
        val file = SaveSlots.currentFile(folder.root, "game")
        for (text in listOf("", "x", "-1", "100", "999")) {
            file.writeText(text)
            assertEquals(text, 0, SaveSlots.current(folder.root, "game"))
        }
        file.writeText(" 12 \n")
        assertEquals(12, SaveSlots.current(folder.root, "game"))
    }

    @Test fun aSlotThatCannotBeWrittenIsReportedAndLeavesNothing() {
        // A folder where the file goes: the rename over it fails.
        File(folder.root, "game.slot").mkdirs()
        File(folder.root, "game.slot/inside").writeText("x")
        assertFalse(SaveSlots.setCurrent(folder.root, "game", 2))
        assertFalse(File(folder.root, ".game.slot.tmp").exists())
        // A file where the folder goes: nothing can be written in it.
        val notDir = folder.newFile("plain")
        assertFalse(SaveSlots.setCurrent(notDir, "game", 2))
    }

    @Test fun theStateListingsDoNotSeeTheSlotFile() {
        assertFalse(SaveSlots.currentFile(folder.root, "game").name.startsWith("game.state"))
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
