package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Painting in the skin editor's session (issue 786): colours, textures, undo, drafts and what is written. */
class SkinEditPaintTest {
    private val size = SkinSize(900, 1800)
    private val dpad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1200f, 200f, 200f), group = "dpad")
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(650f, 1250f, 100f, 100f), group = "face", label = "A", colors = mapOf("letter" to 0xFFFFFFFF.toInt()))
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(550f, 1350f, 100f, 100f), group = "face", label = "B")
    private val turbo = SkinElement("turbo", SkinElementType.ACTION, "turbo", SkinRect(10f, 650f, 40f, 40f))
    private val layout = SkinLayout(
        size, listOf(SkinRect(0f, 0f, 900f, 600f)), frame = SkinFrame(SkinRect(0f, 600f, 900f, 100f)), elements = listOf(dpad, a, b, turbo),
    )
    private val skin = OpenEmuxSkin(
        Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout, SkinLayoutId.PHONE_LANDSCAPE to layout.copy(frame = null, size = SkinSize(1800, 1800))),
    )
    private val buttons = SkinTarget.Controls(SkinElementType.BUTTON)
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    /** The skin has images for A, the D-pad's ball, the background and the turbo key. */
    private val files = setOf("skin.json", "phone-portrait/a.png", "phone-portrait/dpad-ball.png", "phone-portrait/background.png", "phone-portrait/turbo.png")

    private fun model(
        looks: Map<SkinLayoutId, SkinLook>? = null, images: SkinImageEdits = emptyMap(), orientation: SkinOrientation = SkinOrientation.PORTRAIT,
    ) = SkinEditorModel(skin, looks = looks, images = images, existing = files).also { it.show(SkinForm.PHONE, orientation) }

    private fun SkinEditorModel.element(id: String) = layout!!.elements.first { it.id == id }

    @Test fun aColourIsLiveUntilItIsDoneThenOneUndoStep() {
        val m = model()
        assertFalse(m.dirty)
        m.paint(buttons, listOf("b"), SkinPart.BUTTON, red)
        assertEquals(red, m.element("b").colors["button"])
        assertTrue(m.dirty)
        assertFalse(m.canUndo)
        // Dragging on the picker: the next colour replaces the live one.
        m.paint(buttons, listOf("b"), SkinPart.BUTTON, blue)
        m.endGesture()
        assertEquals(blue, m.element("b").colors["button"])
        assertTrue(m.canUndo)
        m.undo()
        assertNull(m.element("b").colors["button"])
        assertFalse(m.dirty)
        m.redo()
        assertEquals(blue, m.element("b").colors["button"])
    }

    @Test fun aColourOnAPartWithAnImageDropsTheImage() {
        val m = model()
        assertTrue(m.textured(listOf("a"), SkinPart.BUTTON))
        assertTrue(m.hasImage("phone-portrait/a.png"))
        m.paint(buttons, listOf("a"), SkinPart.BUTTON, red)
        m.endGesture()
        assertEquals(SkinImageEdit.Removed, m.images["phone-portrait/a.png"])
        assertFalse(m.hasImage("phone-portrait/a.png"))
        assertFalse(m.textured(listOf("a"), SkinPart.BUTTON))
        // The letter has no file of its own: painting it leaves the images alone.
        val n = model()
        n.paint(buttons, listOf("a"), SkinPart.LETTER, red)
        n.endGesture()
        assertTrue(n.images.isEmpty())
        // Undo brings the image back.
        m.undo()
        assertTrue(m.hasImage("phone-portrait/a.png"))
    }

    @Test fun iconsDropTheQuickActionsImagesAndTheFramesColoursChange() {
        val m = model()
        m.paint(SkinTarget.Frame, emptyList(), SkinPart.ICONS, red)
        m.endGesture()
        assertEquals(SkinImageEdit.Removed, m.images["phone-portrait/turbo.png"])
        assertEquals(red, m.layout!!.frame!!.colors.icons)
        m.tone(SkinIconTone.DARK)
        assertEquals(SkinIconTone.DARK, m.layout!!.frame!!.colors.activeIcon)
        m.undo()
        assertNull(m.layout!!.frame!!.colors.activeIcon)
        m.paint(SkinTarget.Background, emptyList(), SkinPart.BACKGROUND, blue)
        m.endGesture()
        assertEquals(blue, m.layout!!.background.color)
        assertEquals(SkinImageEdit.Removed, m.images["phone-portrait/background.png"])
    }

    @Test fun aTextureReplacesTheImageAndRemovingItBringsTheColourBack() {
        val m = model()
        m.texture(mapOf("phone-portrait/b.png" to "t-1.png", "phone-portrait/nothing.png" to "t-2.png", "elsewhere/b.png" to "t-3.png"))
        // Only images the skin can have are kept.
        assertEquals(mapOf("phone-portrait/b.png" to SkinImageEdit.Staged("t-1.png")), m.images)
        assertTrue(m.textured(listOf("b"), SkinPart.BUTTON))
        m.removeTexture(buttons, listOf("b"), SkinPart.BUTTON)
        // b had no image before the texture: nothing is left to remove.
        assertTrue(m.images.isEmpty())
        m.removeTexture(buttons, listOf("a"), SkinPart.BUTTON)
        assertEquals(SkinImageEdit.Removed, m.images["phone-portrait/a.png"])
        // The letter takes no texture: nothing to remove.
        val before = m.images
        m.removeTexture(buttons, listOf("a"), SkinPart.LETTER)
        assertEquals(before, m.images)
        // A colour on a textured part drops the staged texture.
        m.texture(mapOf("phone-portrait/b.png" to "t-4.png"))
        m.paint(buttons, listOf("b"), SkinPart.BUTTON, red)
        m.endGesture()
        assertNull(m.images["phone-portrait/b.png"])
        m.removeTexture(SkinTarget.Frame, emptyList(), SkinPart.FRAME)
        assertNull(m.images["phone-portrait/frame.png"])
    }

    @Test fun paintNeedsALayout() {
        val m = SkinEditorModel(skin, existing = files)
        m.paint(buttons, listOf("a"), SkinPart.BUTTON, red)
        m.tone(SkinIconTone.DARK)
        m.removeTexture(buttons, listOf("a"), SkinPart.BUTTON)
        assertFalse(m.textured(listOf("a"), SkinPart.BUTTON))
        assertFalse(m.dirty)
        // A form the skin has no layout for.
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.paint(buttons, listOf("a"), SkinPart.BUTTON, red)
        assertFalse(m.dirty)
    }

    @Test fun aDraftsColoursAndImagesComeBack() {
        val draft = mapOf(
            SkinLayoutId.PHONE_PORTRAIT to SkinLook(red, SkinFrameColors(icons = blue), mapOf("a" to mapOf("button" to blue), "gone" to mapOf("button" to red))),
            // The landscape layout has no frame: a draft's frame colours for it are dropped.
            SkinLayoutId.PHONE_LANDSCAPE to SkinLook(frame = SkinFrameColors(frame = red)),
            SkinLayoutId.TABLET_PORTRAIT to SkinLook(red),
        )
        val m = model(draft, mapOf("phone-portrait/a.png" to SkinImageEdit.Removed, "../a.png" to SkinImageEdit.Removed, "phone-portrait/frame.png" to SkinImageEdit.Staged("t.png")))
        assertTrue(m.dirty)
        assertEquals(red, m.layout!!.background.color)
        assertEquals(blue, m.layout!!.frame!!.colors.icons)
        assertEquals(blue, m.element("a").colors["button"])
        assertEquals(setOf("phone-portrait/a.png", "phone-portrait/frame.png"), m.images.keys)
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), m.looks.keys)
        m.show(SkinForm.PHONE, SkinOrientation.LANDSCAPE)
        assertNull(m.layout!!.frame)
        // A draft that leaves a layout out keeps the skin's colours there.
        val partial = model(mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook()))
        assertEquals(skin.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).look(), partial.looks.getValue(SkinLayoutId.PHONE_LANDSCAPE))
    }

    @Test fun onlyChangedColoursAreWrittenAndMovesKeepWorking() {
        val m = model()
        m.paint(buttons, listOf("b"), SkinPart.LETTER, red)
        m.endGesture()
        m.tap("b")
        m.nudge(1, 0)
        val look = m.lookEdits().getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(setOf("b"), look.colors.keys)
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT), m.lookEdits().keys)
        // Undoing the nudge keeps the colour: one history for both.
        m.undo()
        assertEquals(red, m.element("b").colors["letter"])
        assertTrue(m.edits().isEmpty())
        val json = JSONObject(m.json(SkinFormat.toJson(skin)))
        val e = json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(2)
        assertEquals("#FF0000", e.getJSONObject("colors").getString("letter"))
    }

    @Test fun thePatchWritesEveryColourKeyAndClearsUnsetOnes() {
        val painted = skin.copy(
            layouts = skin.layouts + (SkinLayoutId.PHONE_PORTRAIT to layout.copy(
                background = SkinBackground(color = red, image = "bg"),
                frame = layout.frame!!.copy(colors = SkinFrameColors(red, red, red, SkinIconTone.LIGHT)),
                elements = layout.elements.map { if (it.id == "b") it.copy(colors = mapOf("button" to red)) else it },
            )),
        )
        val json = SkinFormat.toJson(painted)
        val look = SkinLook(
            background = 0x80123456.toInt(),
            frame = SkinFrameColors(frame = blue, activeIcon = SkinIconTone.DARK),
            colors = mapOf("a" to mapOf("button" to blue, "letter" to red, "pad" to red), "b" to emptyMap(), "nothing" to mapOf("button" to red)),
        )
        val out = JSONObject(SkinEditJson.patch(json, emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to look, SkinLayoutId.TABLET_PORTRAIT to look)))
        val l = out.getJSONObject("layouts").getJSONObject("phone-portrait")
        assertEquals("#123456" + "80", l.getJSONObject("background").getString("color"))
        assertEquals("bg", l.getJSONObject("background").getString("image"))
        val colors = l.getJSONObject("frame").getJSONObject("colors")
        assertEquals("#0000FF", colors.getString("frame"))
        assertFalse(colors.has("icons"))
        assertFalse(colors.has("active"))
        assertEquals("dark", colors.getString("activeIcon"))
        val elements = l.getJSONArray("elements")
        val ea = elements.getJSONObject(1).getJSONObject("colors")
        assertEquals("#0000FF", ea.getString("button"))
        assertEquals("#FF0000", ea.getString("letter"))
        // A part the type does not take is not written.
        assertFalse(ea.has("pad"))
        assertFalse(elements.getJSONObject(2).has("colors"))
        // It still reads as a skin.
        val read = SkinFormat.parse(out.toString()).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(blue, read.elements[1].colors["button"])
        // Everything cleared: the empty objects go too.
        val cleared = JSONObject(SkinEditJson.patch(json, emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook(frame = SkinFrameColors()))))
        val c = cleared.getJSONObject("layouts").getJSONObject("phone-portrait")
        assertEquals("bg", c.getJSONObject("background").getString("image"))
        assertFalse(c.getJSONObject("frame").has("colors"))
        val noImage = JSONObject(SkinEditJson.patch(SkinFormat.toJson(skin), emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook(), SkinLayoutId.PHONE_LANDSCAPE to SkinLook(frame = SkinFrameColors(red)))))
        val n = noImage.getJSONObject("layouts")
        assertFalse(n.getJSONObject("phone-portrait").has("background"))
        assertFalse(n.getJSONObject("phone-landscape").has("frame"))
    }

    @Test fun aLayoutWithNoElementsTakesItsBackgroundColour() {
        val json = JSONObject(SkinFormat.toJson(skin)).apply {
            getJSONObject("layouts").getJSONObject("phone-portrait").remove("elements")
        }.toString()
        val out = JSONObject(SkinEditJson.patch(json, emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook(background = red))))
        assertEquals("#FF0000", out.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONObject("background").getString("color"))
        // An element of a type this app does not know keeps no colours.
        val odd = JSONObject(SkinFormat.toJson(skin)).apply {
            getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(1).put("type", "lever")
        }.toString()
        val patched = JSONObject(SkinEditJson.patch(odd, emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook(colors = mapOf("a" to mapOf("button" to red))))))
        assertFalse(patched.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(1).has("colors"))
    }

    @Test fun colourKeysAreWrittenWithAlphaOnlyWhenNotOpaque() {
        assertEquals("#FF0000", SkinEditJson.color(red))
        assertEquals("#FF000080", SkinEditJson.color(0x80FF0000.toInt()))
    }
}
