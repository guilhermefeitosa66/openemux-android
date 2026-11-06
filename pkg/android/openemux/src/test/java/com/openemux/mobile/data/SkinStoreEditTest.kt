package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** What the skin editor writes (issue 784): Save, Save as a new skin, and drafts, only ever on the player's own skins. */
class SkinStoreEditTest {
    private lateinit var base: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-edit").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private val layout = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)),
        elements = listOf(SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f))),
    )

    private fun skin(name: String = "Mine", console: Console = Console.SFC) =
        OpenEmuxSkin(console, name, mapOf(SkinLayoutId.PHONE_PORTRAIT to layout))

    private fun own(id: String, console: Console = Console.SFC): File = File(store.root(console), id).apply {
        mkdirs()
        File(this, SkinFormat.SKIN_JSON).writeText(JSONObject(SkinFormat.toJson(skin(id, console))).put("creator", "me").toString(2))
        File(this, "phone-portrait").mkdirs()
        File(this, "phone-portrait/a.png").writeBytes(byteArrayOf(9, 8, 7))
    }

    private fun moved(id: String, x: Int, console: Console = Console.SFC): String =
        SkinEditJson.patch(File(store.root(console), "$id/skin.json").readText(), mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(x.toFloat(), 100f, 20f, 20f))))

    private fun x(dir: File) = SkinFormat.readFolder(dir).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.single().rect.x

    @Test fun saveWritesTheEditedSkinJson() {
        val dir = own("mine")
        store.saveEdits(Console.SFC, "mine", moved("mine", 40))
        assertEquals(40f, x(dir), 0f)
        assertEquals("me", JSONObject(File(dir, "skin.json").readText()).getString("creator"))
        assertArrayEquals(byteArrayOf(9, 8, 7), File(dir, "phone-portrait/a.png").readBytes())
    }

    @Test fun saveRefusesWhatIsNotASkinForTheConsoleAndLeavesTheFile() {
        val dir = own("mine")
        val before = File(dir, "skin.json").readText()
        assertThrows(SkinFormatException::class.java) { store.saveEdits(Console.SFC, "mine", "{}") }
        val other = SkinFormat.toJson(skin(console = Console.GBA))
        assertThrows(SkinStoreException::class.java) { store.saveEdits(Console.SFC, "mine", other) }
        assertThrows(SkinStoreException::class.java) { store.saveEdits(Console.SFC, "../mine", moved("mine", 40)) }
        assertThrows(SkinStoreException::class.java) { store.saveEdits(Console.SFC, "nothing", moved("mine", 40)) }
        assertEquals(before, File(dir, "skin.json").readText())
    }

    /** A skin with a phone-portrait and a tablet-portrait layout, each with its image folder (issue 785). */
    private fun twoForms(id: String): File = File(store.root(Console.SFC), id).apply {
        mkdirs()
        val both = OpenEmuxSkin(Console.SFC, id, mapOf(SkinLayoutId.PHONE_PORTRAIT to layout, SkinLayoutId.TABLET_PORTRAIT to layout))
        File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(both))
        listOf("phone-portrait", "tablet-portrait").forEach { File(this, "$it/a.png").apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1)) }
    }

    private fun phoneOnly(id: String) =
        SkinEditJson.patch(File(store.root(Console.SFC), "$id/skin.json").readText(), emptyMap(), removed = setOf(SkinLayoutId.TABLET_PORTRAIT))

    @Test fun saveDeletesTheFoldersOfTheLayoutsTheSkinNoLongerHas() {
        val dir = twoForms("two")
        store.saveEdits(Console.SFC, "two", phoneOnly("two"))
        assertFalse(File(dir, "tablet-portrait").exists())
        assertTrue(File(dir, "phone-portrait/a.png").isFile)
        assertEquals(listOf(SkinForm.PHONE), SkinFormat.readFolder(dir).skin.forms)
    }

    @Test fun saveAsNewLeavesOutTheFoldersOfTheLayoutsItDropped() {
        val dir = twoForms("two")
        store.saveAsNew(Console.SFC, "two", phoneOnly("two"), "Phone only")
        val copy = File(store.root(Console.SFC), "phone-only")
        assertFalse(File(copy, "tablet-portrait").exists())
        assertTrue(File(copy, "phone-portrait/a.png").isFile)
        assertTrue(File(dir, "tablet-portrait/a.png").isFile)
    }

    @Test fun saveAsNewCopiesTheImagesAndLeavesTheOriginal() {
        val dir = own("mine")
        val ref = store.saveAsNew(Console.SFC, "mine", moved("mine", 50), "  Moved  ")
        assertEquals("own:moved", ref)
        val copy = File(store.root(Console.SFC), "moved")
        assertEquals(50f, x(copy), 0f)
        assertEquals("Moved", SkinFormat.readFolder(copy).skin.name)
        assertArrayEquals(byteArrayOf(9, 8, 7), File(copy, "phone-portrait/a.png").readBytes())
        assertEquals(10f, x(dir), 0f)
        assertThrows(SkinFormatException::class.java) { store.saveAsNew(Console.SFC, "mine", "{}", "Bad") }
        assertThrows(SkinStoreException::class.java) { store.saveAsNew(Console.SFC, "mine", SkinFormat.toJson(skin(console = Console.GBA)), "Bad") }
        assertEquals(setOf("mine", "moved"), store.list(Console.SFC).map { it.id }.toSet())
    }

    @Test fun aDraftComesBackUntilItIsDropped() {
        own("mine")
        assertNull(store.draft(Console.SFC, "mine"))
        val json = moved("mine", 60)
        store.saveDraft(Console.SFC, "mine", json)
        assertEquals(json, store.draft(Console.SFC, "mine"))
        assertTrue(File(store.root(Console.SFC), ".mine.draft.json").isFile)
        // The draft is no skin of its own.
        assertEquals(listOf("mine"), store.list(Console.SFC).map { it.id })
        store.dropDraft(Console.SFC, "mine")
        assertNull(store.draft(Console.SFC, "mine"))
    }

    @Test fun aDraftThatNoLongerReadsIsDropped() {
        own("mine")
        val file = File(store.root(Console.SFC), ".mine.draft.json")
        file.writeText("not json")
        assertNull(store.draft(Console.SFC, "mine"))
        assertFalse(file.exists())
        file.writeText(SkinFormat.toJson(skin(console = Console.GBA)))
        assertNull(store.draft(Console.SFC, "mine"))
        assertFalse(file.exists())
    }

    @Test fun draftsAreOnlyForThePlayersOwnSkins() {
        own("mine")
        assertThrows(SkinStoreException::class.java) { store.saveDraft(Console.SFC, "nothing", moved("mine", 60)) }
        assertThrows(SkinStoreException::class.java) { store.saveDraft(Console.SFC, "../x", moved("mine", 60)) }
        assertNull(store.draft(Console.SFC, "../mine"))
        store.dropDraft(Console.SFC, "../mine")
        val target = File(base, "elsewhere.json").apply { writeText(moved("mine", 60)) }
        Files.createSymbolicLink(File(store.root(Console.SFC), ".mine.draft.json").toPath(), target.toPath())
        assertNull(store.draft(Console.SFC, "mine"))
        store.dropDraft(Console.SFC, "mine")
        assertTrue(target.isFile)
    }

    @Test fun aDraftThatCannotBeWrittenSaysSo() {
        own("mine")
        // A folder where the draft goes: the rename onto it fails.
        File(store.root(Console.SFC), ".mine.draft.json/x").mkdirs()
        assertThrows(java.io.IOException::class.java) { store.saveDraft(Console.SFC, "mine", moved("mine", 60)) }
        assertFalse(File(store.root(Console.SFC), ".mine.draft.json.tmp").exists())
    }

    @Test fun deletingASkinDropsItsDraft() {
        own("mine")
        store.saveDraft(Console.SFC, "mine", moved("mine", 60))
        store.delete(Console.SFC, "mine")
        assertFalse(File(store.root(Console.SFC), ".mine.draft.json").exists())
    }
}
