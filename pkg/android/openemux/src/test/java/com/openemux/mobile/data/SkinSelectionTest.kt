package com.openemux.mobile.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/** Which skin each console plays with (issue 782): saved per console, kept across a bundled pick, migrated once. */
class SkinSelectionTest {
    private lateinit var root: File
    private lateinit var paths: Paths

    @Before fun setUp() {
        root = Files.createTempDirectory("skin-selection").toFile()
        paths = Paths(File(root, "external"), File(root, "internal"))
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun library() = Library(paths, { true }, storageRetryMs = 0)

    private fun ownSkin(id: String, console: Console = Console.SFC) = File(paths.base, "skins/${console.id}/$id").apply {
        mkdirs()
        File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(OpenEmuxSkin(console, id, emptyMap())))
    }

    private fun pkg(name: String, console: Console = Console.SFC) =
        File(paths.base, "skins/${console.id}/$name").apply { parentFile.mkdirs(); writeText("zip") }

    @Test fun settingsKeepTheCustomSkinThroughJson() {
        val s = ConsoleSettings("snes9x", "none", skin = "dark", custom = "own:mine")
        assertEquals(s, ConsoleSettings.fromJson(s.toJson()))
        val plain = ConsoleSettings("snes9x", "none")
        assertFalse(plain.toJson().has("custom"))
        assertNull(ConsoleSettings.fromJson(plain.toJson()).custom)
        assertNull(ConsoleSettings.fromJson(JSONObject("""{"core":"c","shader":"s","custom":""}""")).custom)
    }

    @Test fun beforeTheMigrationTheFirstSkinByNameIsTheOneInUse() {
        pkg("b.deltaskin")
        ownSkin("a-mine")
        val lib = library()
        assertEquals("own:a-mine", lib.customSkin(Console.SFC))
        assertNull(lib.customSkin(Console.GBA))
        assertFalse(lib.meta.value.skinsMigrated)
    }

    @Test fun theMigrationSavesTheSkinEachConsolePlayedWith() {
        pkg("b.deltaskin")
        ownSkin("x", Console.GBA)
        val lib = library()
        lib.updateConsole(Console.SFC) { it.copy(skin = "dark", color = "red") }
        lib.migrateSkins()
        val reopened = library()
        assertTrue(reopened.meta.value.skinsMigrated)
        assertEquals("pkg:b.deltaskin", reopened.customSkin(Console.SFC))
        assertEquals("own:x", reopened.customSkin(Console.GBA))
        assertNull(reopened.customSkin(Console.FC))
        // The console's other settings and its bundled pick stay.
        assertEquals("dark", reopened.consoleSettings(Console.SFC).skin)
        assertEquals("red", reopened.consoleSettings(Console.SFC).color)
    }

    @Test fun theMigrationRunsOnce() {
        val lib = library()
        lib.migrateSkins()
        pkg("late.deltaskin")
        lib.migrateSkins()
        assertNull(library().customSkin(Console.SFC))
    }

    @Test fun anInstallWithNoSkinsMigratesToTheBundledOnes() {
        val lib = library()
        lib.migrateSkins()
        assertTrue(library().meta.value.skinsMigrated)
        assertNull(library().customSkin(Console.SFC))
    }

    @Test fun pickingABundledSkinKeepsTheCustomOneForLater() {
        ownSkin("mine")
        val lib = library()
        lib.useCustomSkin(Console.SFC, "own:mine")
        lib.useBundledSkin(Console.SFC, "dark")
        assertNull(lib.customSkin(Console.SFC))
        assertEquals(SkinVariant.DARK, lib.skinVariant(Console.SFC))
        assertTrue(File(paths.base, "skins/SFC/mine/skin.json").isFile)
        lib.useCustomSkin(Console.SFC, "own:mine")
        assertEquals("own:mine", library().customSkin(Console.SFC))
        assertEquals(SkinVariant.DARK, library().skinVariant(Console.SFC))
    }

    @Test fun aPickBeforeTheFirstScanIsNotUndoneByTheMigration() {
        pkg("old.deltaskin")
        val lib = library()
        lib.useBundledSkin(Console.SFC, "dark")
        lib.migrateSkins()
        assertNull(library().customSkin(Console.SFC))
    }

    @Test fun deletingTheSkinInUseGoesBackToTheBundledOne() {
        ownSkin("mine")
        ownSkin("other")
        val lib = library()
        lib.useBundledSkin(Console.SFC, "dark")
        lib.useCustomSkin(Console.SFC, "own:mine")
        lib.forgetCustomSkin(Console.SFC, "own:other")
        assertEquals("own:mine", lib.customSkin(Console.SFC))
        lib.forgetCustomSkin(Console.SFC, "own:mine")
        assertNull(library().customSkin(Console.SFC))
        assertEquals(SkinVariant.DARK, library().skinVariant(Console.SFC))
    }

    @Test fun theScanMigratesOnceTheStorageIsReady() {
        pkg("p.deltaskin")
        val lib = library()
        kotlinx.coroutines.runBlocking { lib.scan() }
        assertTrue(library().meta.value.skinsMigrated)
        assertEquals("pkg:p.deltaskin", library().customSkin(Console.SFC))
    }
}
