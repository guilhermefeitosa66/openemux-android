package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The layouts a session switches on or off (issue 785) together with the colours and images it paints (issue 786):
 * one undo history, one draft, one Save.
 */
class SkinLayoutsPaintTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val buttons = SkinTarget.Controls(SkinElementType.BUTTON)

    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(650f, 1250f, 100f, 100f), label = "A")
    private val phone = SkinLayout(SkinSize(900, 1800), listOf(SkinRect(50f, 50f, 800f, 550f)), elements = listOf(a))
    private val tablet = SkinLayout(SkinSize(1640, 2360), listOf(SkinRect(70f, 70f, 1500f, 1000f)), elements = listOf(a.copy(rect = SkinRect(1300f, 1800f, 150f, 150f))))
    private val skin = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to phone))
    private val both = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to phone, SkinLayoutId.TABLET_PORTRAIT to tablet))
    private val files = setOf("skin.json", "phone-portrait/a.png", "tablet-portrait/a.png")

    private fun model(s: OpenEmuxSkin = skin) = SkinEditorModel(s, existing = files).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }

    private fun SkinEditorModel.paintA(color: Int) {
        paint(buttons, listOf("a"), SkinPart.BUTTON, color)
        endGesture()
    }

    @Test fun aLayoutSwitchedOnTakesTheSourcesColoursAndIsWrittenWithItsOwn() {
        val m = model()
        m.paintA(red)
        assertTrue(m.setForm(SkinForm.TABLET, true))
        // Derived from the phone layout as it is now: painted red.
        assertEquals(red, m.layoutOf(SkinLayoutId.TABLET_PORTRAIT)!!.elements.single().colors["button"])
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.paintA(blue)
        assertEquals(blue, m.addedLayouts().getValue(SkinLayoutId.TABLET_PORTRAIT).elements.single().colors["button"])
        assertTrue(m.changed(SkinLayoutId.TABLET_PORTRAIT))
        // The new layout is written whole; its colours are not a patch of a layout the file lacks.
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT), m.lookEdits().keys)
        val out = JSONObject(m.json(SkinFormat.toJson(skin))).getJSONObject("layouts")
        assertEquals("#FF0000", out.getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(0).getJSONObject("colors").getString("button"))
        assertEquals("#0000FF", out.getJSONObject("tablet-portrait").getJSONArray("elements").getJSONObject(0).getJSONObject("colors").getString("button"))
    }

    @Test fun aTextureOnALayoutSwitchedOnIsKept() {
        val m = model()
        m.setForm(SkinForm.TABLET, true)
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.texture(mapOf("tablet-portrait/a.png" to "t-1.png", "fold-portrait/a.png" to "t-2.png"))
        assertEquals(mapOf("tablet-portrait/a.png" to SkinImageEdit.Staged("t-1.png")), m.images)
        assertTrue(m.textured(listOf("a"), SkinPart.BUTTON))
    }

    @Test fun switchingAFormOffDropsItsColoursAndImageEditsAndUndoBringsThemBack() {
        val m = model(both)
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.paintA(red)
        // The tablet's own picture of A gave way to the colour.
        assertEquals(mapOf("tablet-portrait/a.png" to SkinImageEdit.Removed), m.images)
        m.show(SkinForm.PHONE, SkinOrientation.PORTRAIT)
        m.paintA(blue)
        assertTrue(m.setForm(SkinForm.TABLET, false))
        assertFalse(SkinLayoutId.TABLET_PORTRAIT in m.looks)
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), m.images)
        assertEquals(setOf(SkinLayoutId.TABLET_PORTRAIT), m.removedLayouts())
        m.undo()
        assertEquals(red, m.looks.getValue(SkinLayoutId.TABLET_PORTRAIT).colors.getValue("a")["button"])
        assertEquals(SkinImageEdit.Removed, m.images["tablet-portrait/a.png"])
        // Back on after the undo is redone: as the skin has it, unpainted.
        m.redo()
        assertTrue(m.setForm(SkinForm.TABLET, true))
        assertEquals(tablet.look(), m.looks.getValue(SkinLayoutId.TABLET_PORTRAIT))
        assertFalse(m.changed(SkinLayoutId.TABLET_PORTRAIT))
    }

    @Test fun aLayoutIsChangedByItsColoursOrItsImagesAlone() {
        val m = model(both)
        assertFalse(m.changed(SkinLayoutId.PHONE_PORTRAIT))
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.tone(SkinIconTone.DARK)
        m.removeTexture(buttons, listOf("a"), SkinPart.BUTTON)
        // An image gone, no colour changed.
        assertTrue(m.changed(SkinLayoutId.TABLET_PORTRAIT))
        assertFalse(m.changed(SkinLayoutId.PHONE_PORTRAIT))
        m.show(SkinForm.PHONE, SkinOrientation.PORTRAIT)
        // A colour changed, on a skin with no image for it.
        val bare = SkinEditorModel(both).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        bare.paintA(red)
        assertTrue(bare.images.isEmpty())
        assertTrue(bare.changed(SkinLayoutId.PHONE_PORTRAIT))
        assertFalse(bare.changed(SkinLayoutId.TABLET_PORTRAIT))
    }

    @Test fun aDraftBringsItsLayoutsColoursAndImages() {
        val painted = phone.copy(background = SkinBackground(blue), elements = listOf(a.copy(colors = mapOf("button" to red))))
        val draft = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to painted, SkinLayoutId.TABLET_PORTRAIT to tablet))
        val images = mapOf(
            "tablet-portrait/a.png" to SkinImageEdit.Staged("t-1.png"),
            "phone-portrait/a.png" to SkinImageEdit.Removed,
            // Neither a layout of the session nor a file of it: dropped.
            "fold-portrait/a.png" to SkinImageEdit.Staged("t-2.png"),
            "phone-portrait/nothing.png" to SkinImageEdit.Removed,
        )
        val m = SkinEditorModel.fromDraft(skin, draft, images, files)
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.TABLET_PORTRAIT), m.rects.keys)
        assertEquals(blue, m.looks.getValue(SkinLayoutId.PHONE_PORTRAIT).background)
        assertEquals(red, m.looks.getValue(SkinLayoutId.PHONE_PORTRAIT).colors.getValue("a")["button"])
        assertEquals(images - "fold-portrait/a.png" - "phone-portrait/nothing.png", m.images)
        assertTrue(m.dirty)
        // A draft that dropped the tablet layout takes its image edits with it.
        val dropped = SkinEditorModel.fromDraft(both, skin, mapOf("tablet-portrait/a.png" to SkinImageEdit.Removed), files)
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT), dropped.rects.keys)
        assertTrue(dropped.images.isEmpty())
        // No draft: the skin as it is, its files known.
        val fresh = SkinEditorModel.fromDraft(skin, null, existing = files)
        assertFalse(fresh.dirty)
        assertTrue(fresh.hasImage("phone-portrait/a.png"))
    }

    // What Save writes.

    private lateinit var base: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-layouts-paint").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private fun own(): File = File(store.root(Console.SFC), "mine").apply {
        mkdirs()
        File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(both))
        File(this, "phone-portrait").mkdirs()
        File(this, "phone-portrait/a.png").writeBytes(byteArrayOf(1))
        File(this, "tablet-portrait").mkdirs()
        File(this, "tablet-portrait/a.png").writeBytes(byteArrayOf(2))
    }

    @Test fun saveWritesTheTexturesAndDropsTheFoldersOfLayoutsSwitchedOff() {
        val dir = own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(7))
        val json = SkinEditJson.patch(SkinFormat.toJson(both), emptyMap(), removed = setOf(SkinLayoutId.TABLET_PORTRAIT))
        store.saveEdits(Console.SFC, "mine", json, mapOf("phone-portrait/a.png" to SkinImageEdit.Staged(t)))
        assertArrayEquals(byteArrayOf(7), File(dir, "phone-portrait/a.png").readBytes())
        assertFalse(File(dir, "tablet-portrait").exists())
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT), SkinFormat.readFolder(dir).skin.layouts.keys)
    }

    @Test fun saveAsNewDoesTheSameOnTheCopyOnly() {
        val dir = own()
        val t = store.stageTexture(Console.SFC, "mine", byteArrayOf(8))
        val json = SkinEditJson.patch(SkinFormat.toJson(both), emptyMap(), removed = setOf(SkinLayoutId.TABLET_PORTRAIT))
        val ref = store.saveAsNew(Console.SFC, "mine", json, "Copy", mapOf("phone-portrait/a.png" to SkinImageEdit.Staged(t)))
        val copy = File(store.root(Console.SFC), SkinStore.parse(ref)!!.second)
        assertArrayEquals(byteArrayOf(8), File(copy, "phone-portrait/a.png").readBytes())
        assertFalse(File(copy, "tablet-portrait").exists())
        // The skin edited is left as it was.
        assertArrayEquals(byteArrayOf(1), File(dir, "phone-portrait/a.png").readBytes())
        assertTrue(File(dir, "tablet-portrait/a.png").exists())
        assertNull(SkinFormat.readFolder(copy).skin.layouts[SkinLayoutId.TABLET_PORTRAIT])
    }
}
