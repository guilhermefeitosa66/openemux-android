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
import java.io.IOException
import java.nio.file.Files

/** What the skin editor's textures and colours write (issue 786): staged textures, removed images, drafts with images. */
class SkinStorePaintTest {
    private lateinit var base: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-paint").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private val layout = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)),
        elements = listOf(
            SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f)),
            SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(40f, 100f, 20f, 20f)),
        ),
    )
    private val skin = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout))
    private val json = SkinFormat.toJson(skin)

    private fun own(id: String = "mine"): File = File(store.root(Console.SFC), id).apply {
        mkdirs()
        File(this, SkinFormat.SKIN_JSON).writeText(json)
        File(this, "phone-portrait").mkdirs()
        File(this, "phone-portrait/a.png").writeBytes(byteArrayOf(1))
    }

    private fun stageDir(id: String = "mine") = File(store.root(Console.SFC), ".$id.draft")

    @Test fun saveCopiesStagedTexturesAndDeletesRemovedImages() {
        val dir = own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(5, 6))
        assertTrue(Regex("t-[0-9a-f]{32}\\.png").matches(t))
        assertArrayEquals(byteArrayOf(5, 6), store.staged(Console.SFC, "mine", t)!!.readBytes())
        val images = mapOf("phone-portrait/b.png" to SkinImageEdit.Staged(t), "phone-portrait/a.png" to SkinImageEdit.Removed)
        store.saveEdits(Console.SFC, "mine", json, images)
        assertArrayEquals(byteArrayOf(5, 6), File(dir, "phone-portrait/b.png").readBytes())
        assertFalse(File(dir, "phone-portrait/a.png").exists())
        // The staged file stays until the draft goes (an undo after the save could still use it).
        assertTrue(store.staged(Console.SFC, "mine", t) != null)
        store.dropDraft(Console.SFC, "mine")
        assertFalse(stageDir().exists())
    }

    @Test fun aTextureIntoALayoutFolderThatIsNotThereYetMakesIt() {
        val dir = File(store.root(Console.SFC), "fresh").apply { mkdirs(); File(this, SkinFormat.SKIN_JSON).writeText(json) }
        val t = store.stageTexture(Console.SFC, "fresh", byteArrayOf(7))
        store.saveEdits(Console.SFC, "fresh", json, mapOf("phone-portrait/a.png" to SkinImageEdit.Staged(t)))
        assertArrayEquals(byteArrayOf(7), File(dir, "phone-portrait/a.png").readBytes())
    }

    @Test fun aSaveWithAMissingTextureFailsAndLeavesTheSkin() {
        val dir = own()
        val before = File(dir, "skin.json").readText()
        val moved = SkinEditJson.patch(json, mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(50f, 100f, 20f, 20f))))
        val missing = mapOf("phone-portrait/b.png" to SkinImageEdit.Staged("t-" + "0".repeat(32) + ".png"))
        assertThrows(IOException::class.java) { store.saveEdits(Console.SFC, "mine", moved, missing) }
        assertEquals(before, File(dir, "skin.json").readText())
        // A path that is not a layout's image is refused before anything is written.
        listOf("../b.png", "phone-portrait/../../b.png", "tablet/b.png", "phone-portrait/B.PNG", "phone-portrait/keypad/a.png").forEach { bad ->
            assertThrows(bad, SkinStoreException::class.java) { store.saveEdits(Console.SFC, "mine", moved, mapOf(bad to SkinImageEdit.Removed)) }
        }
        assertEquals(before, File(dir, "skin.json").readText())
        assertTrue(File(dir, "phone-portrait/a.png").exists())
    }

    @Test fun linksAreNeverWrittenThrough() {
        val dir = own()
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            File(outside, "b.png").writeBytes(byteArrayOf(1))
            File(dir, "tablet-portrait").let { Files.createSymbolicLink(it.toPath(), outside.toPath()) }
            assertThrows(SkinStoreException::class.java) {
                store.saveEdits(Console.SFC, "mine", json, mapOf("tablet-portrait/b.png" to SkinImageEdit.Removed))
            }
            Files.createSymbolicLink(File(dir, "phone-portrait/b.png").toPath(), File(outside, "b.png").toPath())
            assertThrows(SkinStoreException::class.java) {
                store.saveEdits(Console.SFC, "mine", json, mapOf("phone-portrait/b.png" to SkinImageEdit.Removed))
            }
            assertTrue(File(outside, "b.png").exists())
            // A draft folder that is a link is neither staged into nor read.
            Files.createSymbolicLink(stageDir().toPath(), outside.toPath())
            assertThrows(SkinStoreException::class.java) { store.stageTexture(Console.SFC, "mine", byteArrayOf(1)) }
            assertThrows(SkinStoreException::class.java) { store.saveDraft(Console.SFC, "mine", json, mapOf("phone-portrait/a.png" to SkinImageEdit.Removed)) }
            assertThrows(SkinStoreException::class.java) {
                store.saveEdits(Console.SFC, "mine", json, mapOf("phone-portrait/a.png" to SkinImageEdit.Staged("t-" + "1".repeat(32) + ".png")))
            }
            assertTrue(store.draftImages(Console.SFC, "mine").isEmpty())
            assertNull(store.staged(Console.SFC, "mine", "t-" + "1".repeat(32) + ".png"))
            store.dropDraft(Console.SFC, "mine", keepTextures = true)
            store.dropDraft(Console.SFC, "mine")
            assertTrue(File(outside, "b.png").exists())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test fun saveAsNewTakesTheImageEditsAndLeavesTheOriginal() {
        val dir = own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(8))
        val ref = store.saveAsNew(Console.SFC, "mine", json, "Painted", mapOf("phone-portrait/b.png" to SkinImageEdit.Staged(t), "phone-portrait/a.png" to SkinImageEdit.Removed))
        val copy = File(store.root(Console.SFC), ref.removePrefix("own:"))
        assertArrayEquals(byteArrayOf(8), File(copy, "phone-portrait/b.png").readBytes())
        assertFalse(File(copy, "phone-portrait/a.png").exists())
        assertTrue(File(dir, "phone-portrait/a.png").exists())
        assertFalse(File(dir, "phone-portrait/b.png").exists())
    }

    @Test fun aDraftKeepsItsImageEditsAndTheStagedTexturesStayHidden() {
        own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(9))
        val images = mapOf("phone-portrait/b.png" to SkinImageEdit.Staged(t), "phone-portrait/a.png" to SkinImageEdit.Removed)
        store.saveDraft(Console.SFC, "mine", json, images)
        assertEquals(images, store.draftImages(Console.SFC, "mine"))
        // The draft folder is no skin of its own.
        assertEquals(listOf("mine"), store.list(Console.SFC).map { it.id })
        // Undone back to the skin: the list goes, the texture stays for a redo.
        store.dropDraft(Console.SFC, "mine", keepTextures = true)
        assertTrue(store.draftImages(Console.SFC, "mine").isEmpty())
        assertTrue(store.staged(Console.SFC, "mine", t) != null)
        // A draft with no image edits writes no list.
        store.saveDraft(Console.SFC, "mine", json, images)
        store.saveDraft(Console.SFC, "mine", json)
        assertFalse(File(stageDir(), "images.json").exists())
        assertEquals(json, store.draft(Console.SFC, "mine"))
    }

    @Test fun aDraftsImageListSkipsWhatItCannotUse() {
        own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(9))
        val list = JSONObject()
            .put("phone-portrait/a.png", "removed")
            .put("phone-portrait/b.png", "staged:$t")
            .put("phone-portrait/c.png", "staged:t-" + "2".repeat(32) + ".png")
            .put("phone-portrait/d.png", "staged:../../x.png")
            .put("phone-portrait/e.png", "kept")
            .put("../e.png", "removed")
        File(stageDir(), "images.json").writeText(list.toString())
        assertEquals(
            mapOf("phone-portrait/a.png" to SkinImageEdit.Removed, "phone-portrait/b.png" to SkinImageEdit.Staged(t)),
            store.draftImages(Console.SFC, "mine"),
        )
        File(stageDir(), "images.json").writeText("not json")
        assertTrue(store.draftImages(Console.SFC, "mine").isEmpty())
        assertTrue(store.draftImages(Console.SFC, "../mine").isEmpty())
        assertNull(store.staged(Console.SFC, "../mine", t))
        assertNull(store.staged(Console.SFC, "mine", "../$t"))
        // Bad ids are ignored, not thrown at.
        store.dropDraft(Console.SFC, "../mine")
        assertThrows(SkinStoreException::class.java) { store.stageTexture(Console.SFC, "nothing", byteArrayOf(1)) }
    }

    @Test fun deletingTheSkinTakesItsStagedTexturesWithIt() {
        own()
        store.stageTexture(Console.SFC, "mine", byteArrayOf(1))
        store.delete(Console.SFC, "mine")
        assertFalse(stageDir().exists())
    }

    @Test fun imagePathsAreALayoutsFolderAndAPlainName() {
        assertTrue(SkinStore.imagePath("phone-portrait/a.png"))
        assertTrue(SkinStore.imagePath("fold-landscape/dpad-ball.png"))
        assertFalse(SkinStore.imagePath("phone-portrait/a.jpg"))
        assertFalse(SkinStore.imagePath("a.png"))
        assertFalse(SkinStore.imagePath("phone/a.png"))
        assertFalse(SkinStore.imagePath("phone-portrait/keypad/a.png"))
    }
}
