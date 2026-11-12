package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A button drawn in an outline of the player's (issue 891): circle, pill, rounded square or a triangle at a corner, or
 * the skin's own ("Original"), on every layout that has the button; kept in the skin's `outline` key.
 */
class SkinButtonShapeTest {
    private val triangleTR = ComboOutline(SkinShape.TRIANGLE, SkinCorner.TOP_RIGHT)
    private val size = SkinSize(900, 1800)

    // A GBA-like layout: L a long pill, A and B round, a D-pad, a stick and a quick action.
    private val l = SkinElement("l", SkinElementType.BUTTON, "l", SkinRect(100f, 900f, 200f, 80f), shape = SkinShape.PILL, label = "L", rotation = 10f)
    private val r = SkinElement("r", SkinElementType.BUTTON, "r", SkinRect(600f, 900f, 200f, 80f), shape = SkinShape.PILL, label = "R")
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(700f, 1300f, 100f, 100f), group = "face", label = "A")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(600f, 1400f, 100f, 100f), group = "face", label = "B")
    private val pad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1300f, 240f, 240f))
    private val stick = SkinElement("stick", SkinElementType.STICK, "analogStick", SkinRect(300f, 1550f, 200f, 200f))
    private val portrait = SkinLayout(size, listOf(SkinRect(0f, 0f, 900f, 700f)), elements = listOf(l, r, a, b, pad, stick))
    private val landscape = SkinLayout(
        SkinSize(1800, 900), listOf(SkinRect(450f, 0f, 900f, 700f)),
        elements = listOf(l.copy(rect = SkinRect(50f, 50f, 200f, 80f)), a.copy(rect = SkinRect(1600f, 600f, 100f, 100f))),
    )
    private val skin = OpenEmuxSkin(Console.GBA, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to portrait, SkinLayoutId.PHONE_LANDSCAPE to landscape))

    private fun model(s: OpenEmuxSkin = skin) = SkinEditorModel(s).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
    private fun SkinEditorModel.el(id: String, layout: SkinLayoutId = SkinLayoutId.PHONE_PORTRAIT) = layoutOf(layout)!!.elements.first { it.id == id }

    /** Selects [id] on its own (a grouped control takes two taps). */
    private fun SkinEditorModel.one(id: String) {
        tap(id)
        if (selection != SkinSelection.One(id)) tap(id)
    }

    // --- The rules ---

    @Test fun buttonsAndKeysTakeAnOutlineTheRestDoNot() {
        assertTrue(ComboOutline.shapeable(a))
        assertTrue(ComboOutline.shapeable(a.copy(type = SkinElementType.KEYPAD)))
        assertFalse(ComboOutline.shapeable(pad))
        assertFalse(ComboOutline.shapeable(stick))
        assertFalse(ComboOutline.shapeable(a.copy(type = SkinElementType.ACTION)))
    }

    @Test fun anOutlineFollowsTheTypesDefault() {
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(null, null, SkinElementType.BUTTON))
        assertEquals(ComboOutline.SQUARE, ComboOutline.of(null, null, SkinElementType.KEYPAD))
        assertEquals(ComboOutline.SQUARE, ComboOutline.of(SkinShape.RECT, null, SkinElementType.BUTTON))
        assertEquals(ComboOutline.PILL, ComboOutline.of(l))
        assertEquals(triangleTR, ComboOutline.of(SkinShape.TRIANGLE, SkinCorner.TOP_RIGHT, SkinElementType.BUTTON))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(SkinShape.TRIANGLE, null, SkinElementType.BUTTON))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(SkinShape.CIRCLE, null, SkinElementType.KEYPAD))
    }

    @Test fun drawingInAnOutlineKeepsTheSkinsOwn() {
        val circle = ComboOutline.drawn(l, ComboOutline.CIRCLE)
        assertEquals(SkinShape.CIRCLE, circle.shape)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), circle.own)
        assertEquals(10f, circle.rotation)
        assertEquals(ComboOutline.CIRCLE, ComboOutline.chosen(circle))
        // A triangle is never turned; another outline after it keeps the skin's own, turn included.
        val triangle = ComboOutline.drawn(circle, triangleTR)
        assertEquals(0f, triangle.rotation)
        assertEquals(SkinCorner.TOP_RIGHT, triangle.corner)
        assertEquals(circle.own, triangle.own)
        // Original: the skin's own outline and turn.
        val back = ComboOutline.drawn(triangle, null)
        assertEquals(l, back)
        assertNull(ComboOutline.chosen(back))
        assertEquals(l, ComboOutline.drawn(l, null))
    }

    @Test fun aComboOnlyTakesTheOutline() {
        val combo = SkinElement("combo-1", SkinElementType.BUTTON, "a|b", SkinRect(0f, 0f, 10f, 10f), shape = SkinShape.CIRCLE)
        assertEquals(ComboOutline.CIRCLE, ComboOutline.chosen(combo))
        assertEquals(combo, ComboOutline.drawn(combo, null))
        val pill = ComboOutline.drawn(combo, ComboOutline.PILL)
        assertEquals(SkinShape.PILL, pill.shape)
        assertNull(pill.own)
    }

    @Test fun aPillsSizeIsItsHeight() {
        // L's 200 x 80 pill is a 100-wide circle: as tall as the pill over PILL_TALL.
        assertEquals(100f, ComboOutline.side(l.rect, ComboOutline.PILL), 0.01f)
        assertEquals(SkinRect(150f, 890f, 100f, 100f), ComboOutline.reshape(l.rect, ComboOutline.PILL, ComboOutline.CIRCLE))
        assertEquals(SkinRect(150f, 890f, 100f, 100f), ComboOutline.reshape(l.rect, ComboOutline.PILL, ComboOutline.SQUARE))
    }

    @Test fun restoringBringsBackTheSkinsProportions() {
        val circle = SkinRect(150f, 890f, 100f, 100f)
        val own = SkinRect(0f, 0f, 200f, 80f)
        assertEquals(SkinRect(100f, 900f, 200f, 80f), ComboOutline.restore(circle, ComboOutline.CIRCLE, own, ComboOutline.PILL))
        // At the size the control has now: a circle grown to 150 brings back a pill 1.5 times the skin's.
        val grown = SkinRect(125f, 865f, 150f, 150f)
        assertEquals(SkinRect(50f, 880f, 300f, 120f), ComboOutline.restore(grown, ComboOutline.CIRCLE, own, ComboOutline.PILL))
        // Kept inside the layout.
        val edge = ComboOutline.restore(SkinRect(0f, 0f, 100f, 100f), ComboOutline.CIRCLE, own, ComboOutline.PILL, SkinSize(150, 150))
        assertEquals(SkinRect(0f, 20f, 150f, 60f), edge)
    }

    // --- The skin file ---

    private fun portraitJson(element: String) = """{"format":1,"console":"GBA","name":"Mine","layouts":{"phone-portrait":{
        "size":{"width":900,"height":1800},"screens":[{"x":0,"y":0,"width":900,"height":700}],"elements":[$element]}}}"""

    private fun readL(outline: String) = SkinFormat.parse(
        portraitJson("""{"id":"l","type":"button","bind":"l","x":100,"y":900,"width":100,"height":100,"shape":"pill","rotation":10,"outline":$outline}"""),
    ).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.single()

    @Test fun theOutlineKeyIsRead() {
        val e = readL("""{"shape":"circle","width":200,"height":80}""")
        assertEquals(SkinShape.CIRCLE, e.shape)
        assertEquals(10f, e.rotation)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), e.own)
        val t = readL("""{"shape":"triangle","corner":"bottom-left"}""")
        assertEquals(SkinShape.TRIANGLE, t.shape)
        assertEquals(SkinCorner.BOTTOM_LEFT, t.corner)
        assertEquals(0f, t.rotation)
        // No size: the control's own.
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 100f, 100f), t.own)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 100f, 100f), readL("""{"shape":"rect","width":-1,"height":"tall"}""").own)
    }

    @Test fun anOutlineThisAppCannotReadLeavesTheSkinsOwn() {
        for (o in listOf("\"circle\"", "{\"shape\":\"star\"}", "{\"shape\":\"triangle\"}", "{\"shape\":\"triangle\",\"corner\":\"middle\"}", "{}")) {
            val e = readL(o)
            assertEquals(o, SkinShape.PILL, e.shape)
            assertNull(o, e.own)
        }
        // A D-pad takes no outline.
        val d = SkinFormat.parse(portraitJson("""{"id":"dpad","type":"dpad","x":100,"y":900,"width":200,"height":200,"outline":{"shape":"circle"}}"""))
            .layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.single()
        assertNull(d.own)
        assertNull(d.shape)
    }

    @Test fun theOutlineIsWrittenBesideTheSkinsOwn() {
        val shaped = portrait.copy(elements = portrait.elements.map { if (it.id == "l") ComboOutline.drawn(it, triangleTR) else it })
        val json = JSONObject(SkinFormat.toJson(skin.copy(layouts = skin.layouts + (SkinLayoutId.PHONE_PORTRAIT to shaped))))
        val el = json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(0)
        assertEquals("pill", el.getString("shape"))
        assertEquals(10.0, el.getDouble("rotation"), 0.0)
        val o = el.getJSONObject("outline")
        assertEquals("triangle", o.getString("shape"))
        assertEquals("top-right", o.getString("corner"))
        assertEquals(200.0, o.getDouble("width"), 0.0)
        assertEquals(80.0, o.getDouble("height"), 0.0)
        // And read back as it was.
        assertEquals(shaped.elements.first(), SkinFormat.parse(json.toString()).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first())
        // A control in the skin's own outline has no key.
        assertFalse(json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(2).has("outline"))
    }

    // --- The editor ---

    @Test fun theToolTakesAButtonOrItsGroupButNotTheDpadOrAStick() {
        val m = model()
        assertTrue(m.shapeTargets.isEmpty())
        m.one("l")
        assertEquals(listOf("l"), m.shapeTargets.map { it.id })
        assertTrue(m.shapeOriginal)
        assertNull(m.selectedOutline)
        assertFalse(m.shapeMixed)
        m.tap("a")
        assertEquals(listOf("a", "b"), m.shapeTargets.map { it.id })
        m.tap("dpad")
        assertTrue(m.shapeTargets.isEmpty())
        m.shape(ComboOutline.CIRCLE)
        assertFalse(m.canUndo)
        m.tap("stick")
        assertTrue(m.shapeTargets.isEmpty())
        m.selectFrame()
        assertTrue(m.shapeTargets.isEmpty())
    }

    @Test fun lBecomesACircleOnEveryLayout() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        val now = m.el("l")
        assertEquals(SkinShape.CIRCLE, now.shape)
        assertEquals(SkinRect(150f, 890f, 100f, 100f), now.rect)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), now.own)
        assertEquals(ComboOutline.CIRCLE, m.selectedOutline)
        assertEquals(100, m.percent())
        assertTrue(m.dirty)
        // The other rotation takes it too, about L's centre there (an outline is the control's, as a colour is).
        val turned = m.el("l", SkinLayoutId.PHONE_LANDSCAPE)
        assertEquals(SkinShape.CIRCLE, turned.shape)
        assertEquals(SkinRect(100f, 40f, 100f, 100f), turned.rect)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), turned.own)
        assertTrue(m.changed(SkinLayoutId.PHONE_LANDSCAPE))
        // The same outline again is nothing.
        m.shape(ComboOutline.CIRCLE)
        // One undo step brings both back.
        m.undo()
        assertEquals(l, m.el("l"))
        assertEquals(landscape.elements[0], m.el("l", SkinLayoutId.PHONE_LANDSCAPE))
        assertFalse(m.canUndo)
        m.redo()
        assertEquals(SkinShape.CIRCLE, m.el("l").shape)
    }

    @Test fun originalBringsBackTheSkinsOutlineAndSize() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.SQUARE)
        m.shape(null)
        assertEquals(l, m.el("l"))
        assertEquals(landscape.elements[0], m.el("l", SkinLayoutId.PHONE_LANDSCAPE))
        assertNull(m.selectedOutline)
        assertFalse(m.dirty)
    }

    @Test fun aGroupTakesTheOutlineTogether() {
        val m = model()
        m.tap("a")
        m.shape(triangleTR)
        assertEquals(listOf(triangleTR, triangleTR), listOf(m.el("a"), m.el("b")).map(ComboOutline::of))
        assertEquals(triangleTR, m.selectedOutline)
        // One of them changed alone: the group's outlines differ, none is marked.
        m.one("a")
        m.shape(ComboOutline.PILL)
        m.clearSelection()
        m.tap("b")
        assertEquals(SkinSelection.Group("face", listOf("a", "b")), m.selection)
        assertTrue(m.shapeMixed)
        assertNull(m.selectedOutline)
        m.shape(null)
        assertEquals(listOf(a, b), listOf(m.el("a"), m.el("b")))
    }

    @Test fun resetKeepsTheOutlineAndResetLayoutDropsIt() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        m.nudge(3, 0)
        m.reset()
        assertEquals(SkinRect(150f, 890f, 100f, 100f), m.el("l").rect)
        assertEquals(SkinShape.CIRCLE, m.el("l").shape)
        m.resetLayout()
        assertEquals(l, m.el("l"))
        // The other rotation goes back to the skin's pill with it, where L is there.
        assertEquals(landscape.elements[0], m.el("l", SkinLayoutId.PHONE_LANDSCAPE))
    }

    @Test fun theOutlineSurvivesASwitchBetweenLayouts() {
        val m = model()
        m.one("l")
        m.shape(triangleTR)
        m.one("a")
        m.shape(ComboOutline.SQUARE)
        // The device turned: the editor shows landscape, where L and A already have their outlines.
        m.show(SkinForm.PHONE, SkinOrientation.LANDSCAPE)
        assertEquals(triangleTR, ComboOutline.of(m.el("l", SkinLayoutId.PHONE_LANDSCAPE)))
        assertEquals(ComboOutline.SQUARE, ComboOutline.of(m.el("a", SkinLayoutId.PHONE_LANDSCAPE)))
        m.one("l")
        assertEquals(triangleTR, m.selectedOutline)
        // Changed there, portrait follows.
        m.shape(ComboOutline.PILL)
        assertEquals(SkinShape.PILL, m.el("l").shape)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), m.el("l").own)
        // Saved and read back, after a restart: every layout has the same outlines.
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        for (layout in saved.layouts.values) {
            assertEquals(ComboOutline.PILL, ComboOutline.chosen(layout.elements.first { it.id == "l" }))
            assertEquals(ComboOutline.SQUARE, ComboOutline.chosen(layout.elements.first { it.id == "a" }))
        }
    }

    @Test fun aFormSwitchedOnTakesTheOutlines() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        assertTrue(m.setForm(SkinForm.TABLET, true))
        for (id in SkinForms.layoutsOf(SkinForm.TABLET)) assertEquals(ComboOutline.CIRCLE, ComboOutline.chosen(m.el("l", id)))
        // A form the skin had, switched off and on again after the outline changed, takes the new one.
        val tablet = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val next = model(tablet)
        assertTrue(next.setForm(SkinForm.TABLET, false))
        next.one("l")
        next.shape(ComboOutline.SQUARE)
        assertTrue(next.setForm(SkinForm.TABLET, true))
        for (id in SkinForms.layoutsOf(SkinForm.TABLET)) assertEquals(ComboOutline.SQUARE, ComboOutline.chosen(next.el("l", id)))
        // Nothing shown: a form switched on keeps what it had.
        assertTrue(SkinEditorModel(tablet).run { setForm(SkinForm.TABLET, false) && setForm(SkinForm.TABLET, true) })
    }

    // --- A skin saved while each layout kept its own outline ---

    private fun withOutline(e: SkinElement, o: ComboOutline) = ComboOutline.drawn(e, o).let { it.at(ComboOutline.reshape(e.rect, ComboOutline.of(e), o)) }

    @Test fun readingASkinWhoseLayoutsDisagreeGivesEveryLayoutOneOutline() {
        // Portrait made L a triangle, landscape left it the skin's pill (the report on issue 891).
        val split = skin.copy(
            layouts = mapOf(
                SkinLayoutId.PHONE_PORTRAIT to portrait.copy(elements = listOf(withOutline(l, triangleTR), r, a, b, pad, stick)),
                SkinLayoutId.PHONE_LANDSCAPE to landscape,
            ),
        )
        val read = SkinFormat.parse(SkinFormat.toJson(split))
        val turned = read.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).elements.first { it.id == "l" }
        assertEquals(triangleTR, ComboOutline.chosen(turned))
        assertEquals(SkinRect(100f, 40f, 100f, 100f), turned.rect)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), turned.own)
        // Portrait as it was; A, in the skin's own on both, as it was.
        assertEquals(triangleTR, ComboOutline.chosen(read.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "l" }))
        assertNull(read.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).elements.first { it.id == "a" }.own)
    }

    @Test fun theOutlinePickedOnMostLayoutsWinsTheFirstOnATie() {
        fun at(x: Float) = l.copy(rect = SkinRect(x, 100f, 200f, 80f), rotation = 0f)
        fun layout(e: SkinElement) = SkinLayout(size, listOf(SkinRect(0f, 0f, 900f, 700f)), elements = listOf(e))
        val ids = SkinLayoutId.entries
        // Square on two layouts, circle on one: the square, though the circle is on the first.
        val most = mapOf(
            ids[0] to layout(withOutline(at(0f), ComboOutline.CIRCLE)),
            ids[1] to layout(withOutline(at(0f), ComboOutline.SQUARE)),
            ids[2] to layout(withOutline(at(0f), ComboOutline.SQUARE)),
            ids[3] to layout(at(0f)),
        )
        ComboOutline.unify(most).values.forEach { assertEquals(ComboOutline.SQUARE, ComboOutline.chosen(it.elements[0])) }
        // One each: the first layout's (in layout order, whatever the map's).
        val tie = linkedMapOf(ids[1] to layout(withOutline(at(0f), ComboOutline.PILL)), ids[0] to layout(withOutline(at(0f), triangleTR)))
        ComboOutline.unify(tie).values.forEach { assertEquals(triangleTR, ComboOutline.chosen(it.elements[0])) }
        // Already one outline everywhere, or none picked: the layouts as they are.
        val same = mapOf(ids[0] to layout(withOutline(at(0f), ComboOutline.PILL)), ids[1] to layout(withOutline(at(5f), ComboOutline.PILL)))
        assertEquals(same, ComboOutline.unify(same))
        assertEquals(skin.layouts, ComboOutline.unify(skin.layouts))
        // The D-pad is never given one, whatever its id.
        val withPad = mapOf(ids[0] to layout(withOutline(at(0f), ComboOutline.SQUARE)), ids[1] to layout(pad.copy(id = "l")))
        assertEquals(withPad.getValue(ids[1]), ComboOutline.unify(withPad).getValue(ids[1]))
    }

    @Test fun savingWritesTheOutlineAndReadingItBackKeepsOriginal() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val e = saved.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "l" }
        assertEquals(SkinShape.CIRCLE, e.shape)
        assertEquals(SkinRect(150f, 890f, 100f, 100f), e.rect)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), e.own)

        // A session on the saved skin: Original brings back the skin's pill at its own size, even after a restart.
        val next = model(saved)
        next.one("l")
        assertEquals(ComboOutline.CIRCLE, next.selectedOutline)
        next.shape(ComboOutline.PILL)
        next.shape(null)
        assertEquals(SkinShape.PILL, next.el("l").shape)
        assertEquals(SkinRect(100f, 900f, 200f, 80f), next.el("l").rect)
        assertEquals(10f, next.el("l").rotation)
        val back = SkinFormat.parse(next.json(SkinFormat.toJson(saved)))
        assertEquals(l, back.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "l" })

        // Shaped again from the saved circle, the skin's own size stays the one the file keeps.
        val again = model(saved)
        again.one("l")
        again.shape(triangleTR)
        val o = JSONObject(again.json(SkinFormat.toJson(saved))).getJSONObject("layouts").getJSONObject("phone-portrait")
            .getJSONArray("elements").getJSONObject(0).getJSONObject("outline")
        assertEquals("triangle", o.getString("shape"))
        assertEquals(200.0, o.getDouble("width"), 0.0)
    }

    @Test fun aColourEditKeepsTheOutlinesLeftAsTheyWere() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        // Only a colour changes on the saved skin: L stays a circle once saved again.
        val next = model(saved)
        next.paint(SkinTarget.Controls(SkinElementType.BUTTON), listOf("a"), SkinPart.BUTTON, 0xFFFF0000.toInt())
        next.endGesture()
        val back = SkinFormat.parse(next.json(SkinFormat.toJson(saved))).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "l" }
        assertEquals(SkinShape.CIRCLE, back.shape)
        assertEquals(SkinOwnOutline(SkinShape.PILL, 10f, 200f, 80f), back.own)
    }

    @Test fun anOutlineChangeScalesTheReliefAndLabelByHeight() {
        val deep = l.copy(depth = 8f, labelSize = 40f)
        val deepSkin = skin.copy(layouts = skin.layouts + (SkinLayoutId.PHONE_PORTRAIT to portrait.copy(elements = listOf(deep, r, a, b, pad, stick))))
        val m = model(deepSkin)
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        // 80 tall made a 100 x 100 circle: 1.25 times as tall, not half as wide.
        assertEquals(10f, m.el("l").depth, 1e-4f)
        assertEquals(50f, m.el("l").labelSize!!, 1e-4f)
        // The file says the same.
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(deepSkin))).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "l" }
        assertEquals(10f, saved.depth, 1e-4f)
        assertEquals(50f, saved.labelSize!!, 1e-4f)
    }

    @Test fun aDraftKeepsItsOutlinesAndItsOriginals() {
        val m = model()
        m.one("l")
        m.shape(ComboOutline.CIRCLE)
        val draft = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val resumed = SkinEditorModel.fromDraft(skin, draft).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertEquals(SkinShape.CIRCLE, resumed.el("l").shape)

        // A saved circle the draft brought back to the pill: the draft's Original wins.
        val savedCircle = draft
        val original = SkinEditorModel.fromDraft(savedCircle, SkinFormat.parse(SkinFormat.toJson(skin)))
            .also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertEquals(SkinShape.PILL, original.el("l").shape)
        assertNull(original.el("l").own)
    }
}
