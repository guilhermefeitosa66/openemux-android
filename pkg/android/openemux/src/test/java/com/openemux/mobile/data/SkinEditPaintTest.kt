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
        // An image that holds the whole control holds its label and its relief: a colour on either takes it away too.
        for (part in listOf(SkinPart.LETTER, SkinPart.RELIEF)) {
            val n = model()
            n.paint(buttons, listOf("a"), part, red)
            n.endGesture()
            assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), n.images)
        }
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

    @Test fun aTextureOnARaisedControlIsItsFaceAlone() {
        val m = model()
        m.texture(mapOf("phone-portrait/b.png" to "t-1.png", "phone-portrait/dpad.png" to "t-2.png", "phone-portrait/turbo.png" to "t-3.png", "phone-portrait/background.png" to "t-4.png"))
        assertEquals(SkinArt.FACE, m.element("b").art)
        assertEquals(SkinArt.FACE, m.element("dpad").art)
        // A quick action is flat, and A got no texture: their images are still all of them.
        assertEquals(SkinArt.FULL, m.element("turbo").art)
        assertEquals(SkinArt.FULL, m.element("a").art)
        // Written with the layout's colours: only what changed, only in the layout it changed in.
        assertEquals(mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("dpad" to SkinArt.FACE, "b" to SkinArt.FACE)), m.lookEdits().mapValues { it.value.art })
        assertTrue(m.lookEdits().getValue(SkinLayoutId.PHONE_PORTRAIT).colors.isEmpty())
        val read = SkinFormat.parse(m.json(SkinFormat.toJson(skin))).layouts
        assertEquals(listOf(SkinArt.FACE, SkinArt.FULL, SkinArt.FACE, SkinArt.FULL), read.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.map { it.art })
        assertTrue(read.getValue(SkinLayoutId.PHONE_LANDSCAPE).elements.all { it.art == SkinArt.FULL })
        // One undo step with the texture.
        m.undo()
        assertEquals(SkinArt.FULL, m.element("b").art)
        assertFalse(m.dirty)
        // The ball is not the D-pad's face: a texture on it alone leaves the pad's own image whole.
        val n = model()
        n.texture(mapOf("phone-portrait/dpad-ball.png" to "t-5.png", "phone-portrait/nothing.png" to "t-6.png"))
        assertEquals(SkinArt.FULL, n.element("dpad").art)
        assertTrue(n.lookEdits().isEmpty())
    }

    @Test fun aTexturedFaceKeepsItsReliefAndItsLabelToPaintAndMove() {
        val m = model()
        m.texture(mapOf("phone-portrait/b.png" to "t-1.png"))
        val staged = mapOf("phone-portrait/b.png" to SkinImageEdit.Staged("t-1.png"))
        // The texture is the face: a colour on the relief or the label goes with it, not in its place.
        for (part in listOf(SkinPart.RELIEF, SkinPart.LETTER)) {
            assertFalse(m.textured(listOf("b"), part))
            m.paint(buttons, listOf("b"), part, red)
            m.endGesture()
            assertEquals(staged, m.images)
        }
        assertTrue(m.textured(listOf("b"), SkinPart.BUTTON))
        // Its label moved on its own leaves the texture where it is.
        m.tap("b"); m.tap("b"); m.tap("b")
        m.nudge(0, 1)
        assertEquals(SkinSelection.Label("b"), m.selection)
        assertEquals(staged, m.images)
        // A colour on the face itself is in its place.
        m.paint(buttons, listOf("b"), SkinPart.BUTTON, blue)
        m.endGesture()
        assertTrue(m.images.isEmpty())
    }

    @Test fun aLabelMovedOnItsOwnTakesTheImageThatHeldIt() {
        val m = model()
        m.tap("a"); m.tap("a"); m.tap("a")
        assertEquals(SkinSelection.Label("a"), m.selection)
        // Selecting it changes nothing yet.
        assertTrue(m.images.isEmpty())
        val gone = mapOf("phone-portrait/a.png" to SkinImageEdit.Removed)
        // Live while it is dragged, back when the drag is abandoned.
        assertTrue(m.beginMove("a"))
        m.moveBy(0f, 80f, free = true)
        assertEquals(gone, m.images)
        assertFalse(m.hasImage("phone-portrait/a.png"))
        m.cancelGesture()
        assertTrue(m.images.isEmpty())
        m.nudge(0, 1)
        assertEquals(gone, m.images)
        // One undo step for the move and the image.
        m.undo()
        assertFalse(m.dirty)
        assertTrue(m.hasImage("phone-portrait/a.png"))
        // Sized on its own, the same.
        m.sizeStep(1)
        assertEquals(gone, m.images)
        assertEquals(105, m.percent())
        // Reset puts the label back on the face, but the image only comes back with undo.
        m.reset()
        assertFalse("a#label" in m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT))
        assertEquals(gone, m.images)
        assertTrue(m.dirty)
        m.undo(); m.undo()
        assertFalse(m.dirty)
    }

    /** A's image is "key": the button without its label, which the app draws wherever it is put. */
    private fun keyedModel(): SkinEditorModel {
        val keyed = skin.copy(layouts = skin.layouts.mapValues { (_, l) -> l.copy(elements = l.elements.map { if (it.id == "a") it.copy(art = SkinArt.KEY) else it }) })
        return SkinEditorModel(keyed, existing = files).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
    }

    @Test fun aLabelItsImageLeavesOutMovesWithoutTouchingTheImage() {
        val m = keyedModel()
        m.tap("a"); m.tap("a"); m.tap("a")
        m.nudge(0, 1)
        m.sizeStep(1)
        assertTrue("a#label" in m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT))
        assertTrue(m.images.isEmpty())
        assertTrue(m.hasImage("phone-portrait/a.png"))
        // B has no image to take away: nothing is marked gone.
        val n = model()
        n.tap("b"); n.tap("b"); n.tap("b")
        n.nudge(0, 1)
        assertTrue("b#label" in n.rects.getValue(SkinLayoutId.PHONE_PORTRAIT))
        assertTrue(n.images.isEmpty())
    }

    @Test fun aKeysImageHoldsItsReliefButNotItsLabel() {
        val m = keyedModel()
        // The label is the app's already: its colour shows without touching the image.
        assertFalse(m.textured(listOf("a"), SkinPart.LETTER))
        m.paint(buttons, listOf("a"), SkinPart.LETTER, red)
        m.endGesture()
        assertTrue(m.images.isEmpty())
        // The relief is in the image: a colour for it takes the image's place.
        assertTrue(m.textured(listOf("a"), SkinPart.RELIEF))
        m.paint(buttons, listOf("a"), SkinPart.RELIEF, blue)
        m.endGesture()
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), m.images)
    }

    @Test fun movingAControlWithItsLabelOnItKeepsItsImage() {
        val m = model()
        // The group, then the one control: the label goes with its face, so the image still shows it right.
        m.tap("a")
        m.nudge(1, 0)
        m.tap("a")
        m.sizeStep(1)
        m.beginMove("a")
        m.moveBy(0f, 60f, free = true)
        m.endGesture()
        assertTrue(m.images.isEmpty())
        assertTrue(m.hasImage("phone-portrait/a.png"))
        assertNull(m.element("a").labelOffset)
    }

    /**
     * A and B with an image that is all of them, label included, and a place of its own for the label all the same:
     * what a skin gets when the images a template drew for it (labels where the editor had moved them) are imported back.
     */
    private fun placedModel(): SkinEditorModel {
        val placed = skin.copy(layouts = skin.layouts.mapValues { (_, l) ->
            l.copy(elements = l.elements.map { if (it.type == SkinElementType.BUTTON) it.copy(labelSize = 30f, labelOffset = SkinPoint(0f, 80f)) else it })
        })
        return SkinEditorModel(placed, existing = files + "phone-portrait/b.png").also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
    }

    @Test fun resettingALabelThatHasNotMovedIsNoEdit() {
        val m = placedModel()
        m.tap("a"); m.tap("a"); m.tap("a")
        assertEquals(SkinSelection.Label("a"), m.selection)
        // It is where the skin has it: nothing to put back, so nothing to draw differently either.
        m.reset()
        assertEquals(emptyMap<String, SkinImageEdit>(), m.images)
        assertFalse(m.dirty)
        assertFalse(m.canUndo)
    }

    @Test fun resettingTheLayoutTakesNoImageAwayWhateverIsSelected() {
        val m = placedModel()
        m.tap("a"); m.tap("a"); m.tap("a")
        // A's label moved on its own: A's image, which held it, goes. B was never touched.
        m.nudge(0, 1)
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), m.images)
        m.resetLayout()
        assertNull(m.images["phone-portrait/b.png"])
        assertTrue(m.hasImage("phone-portrait/b.png"))
        // With nothing selected it is the same reset.
        val n = placedModel()
        n.resetLayout()
        assertFalse(n.dirty)
    }

    @Test fun anImageTheSkinKeepsAsWebpIsEditedUnderItsPngName() {
        // The editor gives the session the skin's files under the names the model gives them (SkinFormat.modelFiles).
        val m = SkinEditorModel(skin, existing = SkinFormat.modelFiles(setOf("skin.json", "phone-portrait/a.webp"))).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertTrue(m.hasImage("phone-portrait/a.png"))
        assertTrue(m.textured(listOf("a"), SkinPart.BUTTON))
        m.paint(buttons, listOf("a"), SkinPart.BUTTON, red)
        m.endGesture()
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), m.images)
        // A texture over it is a PNG under that same name.
        m.undo()
        m.texture(mapOf("phone-portrait/a.png" to "t-1.png"))
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Staged("t-1.png")), m.images)
        m.removeTexture(buttons, listOf("a"), SkinPart.BUTTON)
        assertEquals(mapOf("phone-portrait/a.png" to SkinImageEdit.Removed), m.images)
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

    @Test fun aDraftsFacesComeBack() {
        val m = model(mapOf(SkinLayoutId.PHONE_PORTRAIT to SkinLook(art = mapOf("b" to SkinArt.FACE, "gone" to SkinArt.KEY))))
        assertEquals(SkinArt.FACE, m.element("b").art)
        assertEquals(SkinArt.FULL, m.element("a").art)
        // Only the controls the skin has.
        assertEquals(setOf("dpad", "a", "b", "turbo"), m.looks.getValue(SkinLayoutId.PHONE_PORTRAIT).art.keys)
        assertTrue(m.dirty)
        // Through the draft's skin.json and its image edits, as the editor resumes them.
        val textured = model().also { it.texture(mapOf("phone-portrait/b.png" to "t-1.png")) }
        val draft = SkinFormat.parse(textured.json(SkinFormat.toJson(skin)))
        val back = SkinEditorModel.fromDraft(skin, draft, textured.images, files).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertEquals(SkinArt.FACE, back.element("b").art)
        assertEquals(textured.lookEdits(), back.lookEdits())
        assertEquals(textured.images, back.images)
    }

    @Test fun onlyChangedColoursAreWrittenAndMovesKeepWorking() {
        val m = model()
        m.paint(buttons, listOf("b"), SkinPart.LETTER, red)
        m.endGesture()
        m.tap("b")
        m.nudge(1, 0)
        // The colour is the skin's: written in both of its layouts, and in each only for the control painted.
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), m.lookEdits().keys)
        m.lookEdits().values.forEach { assertEquals(setOf("b"), it.colors.keys) }
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

    @Test fun thePatchWritesWhatAnImageHoldsAndLeavesAWholeOneUnsaid() {
        val keyed = skin.copy(layouts = skin.layouts + (SkinLayoutId.PHONE_PORTRAIT to layout.copy(elements = layout.elements.map { if (it.id == "b") it.copy(art = SkinArt.KEY) else it })))
        val look = SkinLook(art = mapOf("dpad" to SkinArt.KEY, "a" to SkinArt.FACE, "b" to SkinArt.FULL, "nothing" to SkinArt.FACE))
        val out = JSONObject(SkinEditJson.patch(SkinFormat.toJson(keyed), emptyMap(), mapOf(SkinLayoutId.PHONE_PORTRAIT to look, SkinLayoutId.TABLET_PORTRAIT to look)))
        val e = out.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements")
        assertEquals("key", e.getJSONObject(0).getString("art"))
        assertEquals("face", e.getJSONObject(1).getString("art"))
        // The whole image is the default: the key goes.
        assertFalse(e.getJSONObject(2).has("art"))
        // A control the look does not name is left as it was, and so are the colours of those it names no colour for.
        assertFalse(e.getJSONObject(3).has("art"))
        assertEquals("#FFFFFF", e.getJSONObject(1).getJSONObject("colors").getString("letter"))
        // The other layout is not this look's.
        val sideways = out.getJSONObject("layouts").getJSONObject("phone-landscape").getJSONArray("elements")
        assertFalse((0 until sideways.length()).any { sideways.getJSONObject(it).has("art") })
        assertEquals(listOf(SkinArt.KEY, SkinArt.FACE, SkinArt.FULL, SkinArt.FULL), SkinFormat.parse(out.toString()).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.map { it.art })
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
