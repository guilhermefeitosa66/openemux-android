package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's layouts, forms, screen area, Try mode and panel placement (issue 785): pure maths over the skin model.
 */
class SkinLayoutsEditTest {
    private val size = SkinSize(900, 1800)
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(650f, 1250f, 100f, 100f), group = "face", label = "A")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(550f, 1350f, 100f, 100f), group = "face", label = "B")
    private val save = SkinElement("save", SkinElementType.ACTION, "quickSave", SkinRect(20f, 620f, 60f, 60f))
    private val load = SkinElement("load", SkinElementType.ACTION, "quickLoad", SkinRect(20f, 1700f, 60f, 60f))
    private val layout = SkinLayout(
        size, listOf(SkinRect(50f, 50f, 800f, 550f)), frame = SkinFrame(SkinRect(0f, 0f, 900f, 720f)),
        elements = listOf(a, b, save, load),
    )
    private val sideways = SkinLayout(SkinSize(1800, 900), listOf(SkinRect(450f, 50f, 900f, 600f)), elements = listOf(a.copy(rect = SkinRect(1600f, 400f, 100f, 100f))))
    private val skin = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout, SkinLayoutId.PHONE_LANDSCAPE to sideways))

    private fun model(s: OpenEmuxSkin = skin) = SkinEditorModel(s).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }

    // Forms and derived layouts.

    @Test fun eachFormHasAnUprightAndASidewaysLayout() {
        assertEquals(listOf(SkinLayoutId.TABLET_PORTRAIT, SkinLayoutId.TABLET_LANDSCAPE), SkinForms.layoutsOf(SkinForm.TABLET))
        assertEquals(SkinLayoutId.FOLD_LANDSCAPE, SkinForms.id(SkinForm.FOLD, SkinOrientation.LANDSCAPE))
    }

    @Test fun theClosestLayoutKeepsTheOrientationThenTheFormThenAnything() {
        val tp = SkinLayoutId.TABLET_PORTRAIT
        assertEquals(SkinLayoutId.FOLD_PORTRAIT, SkinForms.closest(tp, listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.FOLD_PORTRAIT)))
        assertEquals(SkinLayoutId.PHONE_PORTRAIT, SkinForms.closest(tp, listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.TABLET_LANDSCAPE)))
        assertEquals(SkinLayoutId.TABLET_LANDSCAPE, SkinForms.closest(tp, listOf(SkinLayoutId.TABLET_LANDSCAPE, SkinLayoutId.FOLD_LANDSCAPE)))
        assertEquals(SkinLayoutId.FOLD_LANDSCAPE, SkinForms.closest(tp, listOf(SkinLayoutId.PHONE_LANDSCAPE, SkinLayoutId.FOLD_LANDSCAPE)))
        assertEquals(SkinLayoutId.TABLET_PORTRAIT, SkinForms.closest(SkinLayoutId.PHONE_PORTRAIT, listOf(SkinLayoutId.TABLET_PORTRAIT)))
        assertEquals(SkinLayoutId.TABLET_LANDSCAPE, SkinForms.closest(SkinLayoutId.FOLD_LANDSCAPE, listOf(SkinLayoutId.PHONE_LANDSCAPE, SkinLayoutId.TABLET_LANDSCAPE)))
        assertNull(SkinForms.closest(tp, listOf(tp)))
    }

    @Test fun aDerivedLayoutTakesItsFormsOtherSizeTurnedElseTheNominalOne() {
        assertEquals(SkinSize(1640, 2360), SkinForms.size(SkinLayoutId.TABLET_PORTRAIT, emptyMap()))
        assertEquals(SkinSize(2360, 1640), SkinForms.size(SkinLayoutId.TABLET_LANDSCAPE, emptyMap()))
        assertEquals(SkinSize(2340, 1080), SkinForms.size(SkinLayoutId.PHONE_LANDSCAPE, emptyMap()))
        assertEquals(SkinSize(1812, 2176), SkinForms.size(SkinLayoutId.FOLD_PORTRAIT, emptyMap()))
        assertEquals(SkinSize(900, 1800), SkinForms.size(SkinLayoutId.PHONE_PORTRAIT, mapOf(SkinLayoutId.PHONE_LANDSCAPE to sideways)))
    }

    @Test fun deriveKeepsCentresAtTheirShareScalesByTheSmallerRatioAndDropsTheImages() {
        val source = layout.copy(
            background = SkinBackground(0xFF112233.toInt(), "bg"),
            frame = SkinFrame(SkinRect(0f, 0f, 900f, 720f), image = "bezel"),
            elements = listOf(a.copy(image = "big-a"), SkinElement("edge", SkinElementType.BUTTON, "b", SkinRect(850f, 0f, 50f, 50f))),
            keypad = SkinKeypadPage(listOf(SkinElement("k1", SkinElementType.KEYPAD, "keypad1", SkinRect(0f, 0f, 90f, 90f))), SkinBackground(image = "pad")),
        )
        val d = SkinForms.derive(source, SkinSize(1800, 1800))
        assertEquals(SkinSize(1800, 1800), d.size)
        // x doubles, y stays, sizes keep their ratio 1: a's centre (700, 1300) becomes (1400, 1300).
        assertEquals(SkinRect(1350f, 1250f, 100f, 100f), d.elements[0].rect)
        assertNull(d.elements[0].image)
        assertEquals(SkinRect(1725f, 0f, 50f, 50f), d.elements[1].rect)
        assertEquals(SkinRect(500f, 50f, 800f, 550f), d.screens.single())
        assertEquals(SkinRect(450f, 0f, 900f, 720f), d.frame!!.rect)
        assertNull(d.frame!!.image)
        assertEquals(SkinBackground(0xFF112233.toInt(), null), d.background)
        assertEquals(SkinRect(45f, 0f, 90f, 90f), d.keypad!!.elements.single().rect)
        assertNull(d.keypad!!.background!!.image)
        // Nothing to carry: no frame, no keypad page, a keypad page with no background of its own.
        val bare = SkinForms.derive(sideways, SkinSize(900, 450))
        assertNull(bare.frame)
        assertNull(bare.keypad)
        assertEquals(SkinRect(800f, 200f, 50f, 50f), bare.elements.single().rect)
        assertNull(SkinForms.derive(layout.copy(keypad = SkinKeypadPage(emptyList())), size).keypad!!.background)
        // A rect never leaves the layout, and is at least one unit.
        val tiny = SkinForms.derive(SkinLayout(SkinSize(100, 100), emptyList(), elements = listOf(SkinElement("x", SkinElementType.BUTTON, "a", SkinRect(99f, 99f, 1f, 1f)))), SkinSize(10, 10))
        assertEquals(SkinRect(9f, 9f, 1f, 1f), tiny.elements.single().rect)
    }

    // The screen area.

    @Test fun theScreenAreaIsTheScreensTheFrameAndTheQuickActionsOnIt() {
        assertEquals(listOf("@screen0", "@frame", "save"), SkinArea.ids(layout))
        assertEquals(listOf("@screen0"), SkinArea.ids(sideways))
        assertEquals(1, SkinArea.screenIndex("@screen1"))
        assertNull(SkinArea.screenIndex("@frame"))
        assertNull(SkinArea.screenIndex("a"))
        assertTrue(SkinArea.isArea(SkinArea.FRAME))
        assertFalse(SkinArea.isArea("a"))
    }

    @Test fun aLayoutsRectsHoldItsScreensAndFrameAndGoBackIn() {
        val rects = layout.rects()
        assertEquals(SkinRect(50f, 50f, 800f, 550f), rects["@screen0"])
        assertEquals(SkinRect(0f, 0f, 900f, 720f), rects["@frame"])
        assertEquals(setOf("a", "b", "save", "load", "@screen0"), sideways.rects().keys + setOf("b", "save", "load"))
        val moved = layout.withRects(mapOf("@screen0" to SkinRect(1f, 2f, 3f, 4f), "@frame" to SkinRect(5f, 6f, 7f, 8f), "a" to SkinRect(9f, 9f, 9f, 9f)))
        assertEquals(SkinRect(1f, 2f, 3f, 4f), moved.screens.single())
        assertEquals(SkinRect(5f, 6f, 7f, 8f), moved.frame!!.rect)
        assertEquals(SkinRect(9f, 9f, 9f, 9f), moved.elements[0].rect)
        assertEquals(b, moved.elements[1])
        assertEquals(layout, layout.withRects(emptyMap()))
        assertNull(sideways.withRects(mapOf("@frame" to SkinRect(0f, 0f, 1f, 1f))).frame)
        assertEquals(SkinSelection.Screen(listOf("@screen0")), SkinSelect.toggle(layout, SkinSelection.Screen(listOf("@screen0"))))
    }

    @Test fun theScreenAreaMovesResizesAndResetsTogether() {
        val m = model()
        m.selectScreen()
        assertEquals(SkinSelection.Screen(listOf("@screen0", "@frame", "save")), m.selection)
        assertEquals("@screen0", m.hit(450f, 300f))
        assertEquals("a", m.hit(700f, 1300f))
        m.tap("@screen0")
        assertTrue(m.selection is SkinSelection.Screen)
        m.beginMove("@screen0")
        m.moveBy(0f, 100f, free = true)
        m.endGesture()
        val r = m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(150f, r.getValue("@screen0").y, 0f)
        assertEquals(100f, r.getValue("@frame").y, 0f)
        assertEquals(720f, r.getValue("save").y, 0f)
        assertEquals(load.rect, r.getValue("load"))
        assertEquals(SkinRect(50f, 150f, 800f, 550f), m.layout!!.screens.single())
        assertEquals(100, m.percent())
        m.sizeStep(-1)
        assertEquals(95, m.percent())
        // Written into skin.json and read back: the game draws the picture at the new place.
        val json = SkinFormat.parse(m.json(SkinFormat.toJson(skin))).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(m.layout!!.screens.single().let { SkinRect(Math.round(it.x).toFloat(), Math.round(it.y).toFloat(), Math.round(it.width).toFloat(), Math.round(it.height).toFloat()) }, json.screens.single())
        assertEquals(m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT).getValue("@frame").y.let { Math.round(it).toFloat() }, json.frame!!.rect.y, 0f)
        m.reset()
        assertEquals(layout.screens, m.layout!!.screens)
        assertEquals(layout.frame, m.layout!!.frame)
        m.tap("a")
        assertTrue(m.selection is SkinSelection.Group)
    }

    @Test fun theScreenAreaNeedsALayoutWithAScreen() {
        val m = model()
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.selectScreen()
        assertNull(m.selection)
        val blind = model(OpenEmuxSkin(Console.SFC, "x", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(screens = emptyList(), frame = null))))
        blind.selectScreen()
        assertNull(blind.selection)
    }

    // The Layouts panel's switches.

    @Test fun switchingAFormOnDerivesItsLayoutsAndUndoTakesThemAway() {
        val m = model()
        assertTrue(m.offered(SkinForm.PHONE))
        assertFalse(m.offered(SkinForm.TABLET))
        assertFalse(m.setForm(SkinForm.PHONE, false))
        assertFalse(m.setForm(SkinForm.TABLET, false))
        assertTrue(m.setForm(SkinForm.TABLET, true))
        assertTrue(m.offered(SkinForm.TABLET))
        assertTrue(m.dirty)
        assertTrue(m.changed(SkinLayoutId.TABLET_PORTRAIT))
        assertFalse(m.changed(SkinLayoutId.PHONE_PORTRAIT))
        val tablet = m.layoutOf(SkinLayoutId.TABLET_PORTRAIT)!!
        assertEquals(SkinSize(1640, 2360), tablet.size)
        assertEquals(SkinSize(2360, 1640), m.layoutOf(SkinLayoutId.TABLET_LANDSCAPE)!!.size)
        assertEquals(setOf(SkinLayoutId.TABLET_PORTRAIT, SkinLayoutId.TABLET_LANDSCAPE), m.addedLayouts().keys)
        assertTrue(m.edits().isEmpty())
        assertFalse(m.setForm(SkinForm.TABLET, true))
        m.undo()
        assertFalse(m.offered(SkinForm.TABLET))
        assertNull(m.layoutOf(SkinLayoutId.TABLET_PORTRAIT))
        m.redo()
        assertEquals(tablet, m.layoutOf(SkinLayoutId.TABLET_PORTRAIT))
        // Off and on again: the same layout comes back, not a new one.
        assertTrue(m.setForm(SkinForm.TABLET, false))
        assertTrue(m.setForm(SkinForm.TABLET, true))
        assertEquals(tablet, m.layoutOf(SkinLayoutId.TABLET_PORTRAIT))
        // Editing the derived layout, then its reset, go by the derived rects.
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        m.tap("a")
        m.nudge(1, 0)
        assertTrue(m.layout!!.elements[0].rect.x > tablet.elements[0].rect.x)
        m.resetLayout()
        assertEquals(tablet, m.layout)
    }

    @Test fun switchingOffAFormTheSkinHadRemovesItAndOnBringsItBack() {
        val withTablet = OpenEmuxSkin(Console.SFC, "Mine", skin.layouts + (SkinLayoutId.TABLET_PORTRAIT to layout.copy(size = SkinSize(1600, 2400))))
        val m = model(withTablet)
        assertTrue(m.offered(SkinForm.TABLET))
        assertTrue(m.setForm(SkinForm.TABLET, false))
        assertEquals(setOf(SkinLayoutId.TABLET_PORTRAIT), m.removedLayouts())
        val json = JSONObject(m.json(SkinFormat.toJson(withTablet)))
        assertFalse(json.getJSONObject("layouts").has("tablet-portrait"))
        assertEquals("[\"phone\"]", json.getJSONArray("forms").toString())
        // On again: the skin's own tablet layout, and a sideways one made for it.
        assertTrue(m.setForm(SkinForm.TABLET, true))
        assertEquals(SkinSize(1600, 2400), m.layoutOf(SkinLayoutId.TABLET_PORTRAIT)!!.size)
        assertEquals(SkinSize(2400, 1600), m.layoutOf(SkinLayoutId.TABLET_LANDSCAPE)!!.size)
        assertEquals(setOf(SkinLayoutId.TABLET_LANDSCAPE), m.addedLayouts().keys)
        val back = SkinFormat.parse(m.json(SkinFormat.toJson(withTablet)))
        assertEquals(listOf(SkinForm.PHONE, SkinForm.TABLET), back.forms)
        assertEquals(m.layoutOf(SkinLayoutId.TABLET_LANDSCAPE)!!.elements.size, back.layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE).elements.size)
    }

    @Test fun aFormCannotBeMadeFromNothing() {
        val empty = SkinEditorModel(OpenEmuxSkin(Console.SFC, "x", emptyMap()))
        assertFalse(empty.setForm(SkinForm.TABLET, true))
        assertFalse(empty.offered(SkinForm.TABLET))
    }

    @Test fun aSkinJsonWithNoFormsListGetsNone() {
        val m = model()
        m.setForm(SkinForm.FOLD, true)
        val text = JSONObject(SkinFormat.toJson(skin)).apply { remove("forms") }.toString()
        val json = JSONObject(m.json(text))
        assertFalse(json.has("forms"))
        assertTrue(json.getJSONObject("layouts").has("fold-portrait"))
    }

    @Test fun aDraftBringsBackTheLayoutsItAddedAndDropsTheOnesItRemoved() {
        assertEquals(skin.rects(), SkinEditorModel.fromDraft(skin, null).rects)
        val m = model()
        m.setForm(SkinForm.FOLD, true)
        val draft = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val back = SkinEditorModel.fromDraft(skin, draft)
        assertTrue(back.offered(SkinForm.FOLD))
        assertTrue(back.dirty)
        val withTablet = OpenEmuxSkin(Console.SFC, "Mine", skin.layouts + (SkinLayoutId.TABLET_PORTRAIT to layout))
        val gone = SkinEditorModel.fromDraft(withTablet, skin)
        assertFalse(gone.offered(SkinForm.TABLET))
        assertEquals(setOf(SkinLayoutId.TABLET_PORTRAIT), gone.removedLayouts())
    }

    // Try mode.

    @Test fun tryModeLightsWhatIsPressedAndRemembersTheLastFew() {
        val m = model()
        m.press("a")
        assertNull(m.pressed)
        m.tap("a")
        m.tryMode(true)
        assertTrue(m.trying)
        assertNull(m.selection)
        m.press("nothing")
        assertNull(m.pressed)
        m.press("a")
        assertEquals("a", m.pressed)
        m.release()
        assertNull(m.pressed)
        m.tryStep(1)
        assertEquals("b", m.pressed)
        m.tryStep(-1)
        assertEquals("a", m.pressed)
        assertEquals(listOf("a", "b", "a"), m.recent)
        m.show(SkinForm.PHONE, SkinOrientation.LANDSCAPE)
        assertNull(m.pressed)
        m.tryMode(false)
        assertFalse(m.trying)
        assertTrue(m.recent.isEmpty())
        // From nothing pressed: forward starts at the first control, back at the last.
        m.show(SkinForm.PHONE, SkinOrientation.PORTRAIT)
        m.tryMode(true)
        m.tryStep(-1)
        assertEquals("load", m.pressed)
        m.tryMode(true)
        m.tryStep(1)
        assertEquals("a", m.pressed)
        // The screen area leaves Try mode.
        m.selectScreen()
        assertFalse(m.trying)
        val none = model(OpenEmuxSkin(Console.SFC, "x", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(elements = emptyList()))))
        none.tryMode(true)
        none.tryStep(1)
        assertNull(none.pressed)
    }

    @Test fun tryNamesWhatAControlSends() {
        assertEquals(BindWords.Word(TryWord.DPAD), SkinTry.words("dpad"))
        assertEquals(BindWords.Word(TryWord.DPAD2), SkinTry.words("dpad2"))
        assertEquals(BindWords.Word(TryWord.RESTART), SkinTry.words("reset"))
        assertEquals(BindWords.Word(TryWord.KEYPAD_PAGE), SkinTry.words(SkinFormat.KEYPAD_TOGGLE))
        assertEquals(BindWords.Word(TryWord.DISK), SkinTry.words("disk"))
        assertEquals(BindWords.Word(TryWord.FAST_FORWARD_HOLD), SkinTry.words("fastForward"))
        assertEquals(BindWords.Name("Start"), SkinTry.words("start"))
        assertEquals(BindWords.Name("L"), SkinTry.words("l1"))
        assertEquals(BindWords.Name("C-↑"), SkinTry.words("cUp"))
        assertEquals(BindWords.Name("A"), SkinTry.words("a"))
        assertEquals(BindWords.Name("L2"), SkinTry.words("l2"))
        assertEquals(BindWords.Key("*"), SkinTry.words("keypadStar"))
        assertEquals(BindWords.Key("#"), SkinTry.words("keypadHash"))
        assertEquals(BindWords.Key("7"), SkinTry.words("keypad7"))
        // Every name the format takes has words.
        (SkinFormat.BUTTON_BINDS + SkinFormat.DPAD_BINDS + SkinFormat.STICK_BINDS + SkinFormat.KEYPAD_BINDS).forEach { bind ->
            val w = SkinTry.words(bind)
            assertTrue(bind, w !is BindWords.Name || w.text.isNotBlank())
        }
        assertEquals(listOf("b", "c", "d", "e"), SkinTry.remember(listOf("a", "b", "c", "d"), "e"))
        assertEquals(listOf("a"), SkinTry.remember(emptyList(), "a"))
    }

    // Panels.

    @Test fun panelsOpenAwayFromTheSelection() {
        val screen = SkinRect(0f, 0f, 400f, 300f)
        assertEquals(PanelSide.TOP, SkinPanels.side(false, 400f, screen, null))
        assertEquals(PanelSide.TOP, SkinPanels.side(false, 400f, screen, SkinRect(0f, 500f, 50f, 50f)))
        assertEquals(PanelSide.TOP, SkinPanels.side(false, 400f, SkinRect(0f, 400f, 400f, 300f), SkinRect(0f, 100f, 50f, 50f)))
        assertEquals(PanelSide.BOTTOM, SkinPanels.side(false, 400f, screen, SkinRect(0f, 250f, 50f, 100f)))
        assertEquals(PanelSide.BOTTOM, SkinPanels.side(false, 400f, screen, screen))
        val wide = SkinRect(200f, 0f, 400f, 300f)
        assertEquals(PanelSide.END, SkinPanels.side(true, 800f, wide, null))
        assertEquals(PanelSide.END, SkinPanels.side(true, 800f, wide, SkinRect(20f, 200f, 100f, 100f)))
        assertEquals(PanelSide.START, SkinPanels.side(true, 800f, wide, SkinRect(680f, 200f, 100f, 100f)))
    }
}
