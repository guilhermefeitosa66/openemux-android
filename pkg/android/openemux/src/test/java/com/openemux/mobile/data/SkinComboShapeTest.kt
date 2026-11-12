package com.openemux.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A combo button's outline (issue 842, from human QA): circle, pill or a triangle at a corner, per layout. */
class SkinComboShapeTest {
    private val triangleBL = ComboOutline(SkinShape.TRIANGLE, SkinCorner.BOTTOM_LEFT)

    // --- The outline itself ---

    @Test fun theToolOffersCirclePillRoundedSquareAndTheFourTriangles() {
        assertEquals(7, ComboOutline.ALL.size)
        assertEquals(ComboOutline.CIRCLE, ComboOutline.ALL[0])
        assertEquals(ComboOutline.PILL, ComboOutline.ALL[1])
        // The rounded square (issue 891) is the format's rect.
        assertEquals(ComboOutline.SQUARE, ComboOutline.ALL[2])
        assertEquals(SkinShape.RECT, ComboOutline.SQUARE.shape)
        assertEquals(SkinCorner.entries.toList(), ComboOutline.ALL.drop(3).map { it.corner })
        assertTrue(ComboOutline.ALL.drop(3).all { it.shape == SkinShape.TRIANGLE })
    }

    @Test fun cornersPointAwayFromTheCentre() {
        assertEquals(-1f to 1f, SkinCorner.BOTTOM_LEFT.sx to SkinCorner.BOTTOM_LEFT.sy)
        assertEquals(1f to 1f, SkinCorner.BOTTOM_RIGHT.sx to SkinCorner.BOTTOM_RIGHT.sy)
        assertEquals(-1f to -1f, SkinCorner.TOP_LEFT.sx to SkinCorner.TOP_LEFT.sy)
        assertEquals(1f to -1f, SkinCorner.TOP_RIGHT.sx to SkinCorner.TOP_RIGHT.sy)
        assertEquals(SkinCorner.TOP_RIGHT, SkinCorner.byId("top-right"))
        assertNull(SkinCorner.byId("left"))
        assertNull(SkinCorner.byId(null))
    }

    @Test fun missingOrUnknownKeysReadAsACircle() {
        assertEquals(ComboOutline.CIRCLE, ComboOutline.read(null, null))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.read("circle", null))
        assertEquals(ComboOutline.PILL, ComboOutline.read("pill", "top-left"))
        assertEquals(triangleBL, ComboOutline.read("triangle", "bottom-left"))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.read("triangle", null))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.read("triangle", "middle"))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.read("star", null))
        assertEquals(ComboOutline.SQUARE, ComboOutline.read("rect", null))
    }

    @Test fun anElementsOutline() {
        val e = SkinElement("combo-1", SkinElementType.BUTTON, "y|b", SkinRect(0f, 0f, 10f, 10f))
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(e))
        assertEquals(ComboOutline.PILL, ComboOutline.of(e.copy(shape = SkinShape.PILL)))
        assertEquals(triangleBL, ComboOutline.of(e.copy(shape = SkinShape.TRIANGLE, corner = SkinCorner.BOTTOM_LEFT)))
        // A triangle with no corner is drawn as a circle.
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(e.copy(shape = SkinShape.TRIANGLE)))
    }

    @Test fun reshapingKeepsTheCentreAndTheSide() {
        val circle = SkinRect(100f, 100f, 100f, 100f)
        val pill = ComboOutline.reshape(circle, ComboOutline.CIRCLE, ComboOutline.PILL)
        assertEquals(SkinRect(70f, 110f, 160f, 80f), pill)
        assertEquals(circle, ComboOutline.reshape(pill, ComboOutline.PILL, triangleBL))
        assertEquals(circle, ComboOutline.reshape(circle, ComboOutline.CIRCLE, triangleBL))
        // Kept inside the layout, and never larger than it.
        val size = SkinSize(200, 300)
        assertEquals(SkinRect(0f, 10f, 160f, 80f), ComboOutline.reshape(SkinRect(0f, 0f, 100f, 100f), ComboOutline.CIRCLE, ComboOutline.PILL, size))
        assertEquals(SkinRect(0f, 120f, 120f, 60f), ComboOutline.reshape(SkinRect(25f, 75f, 150f, 150f), ComboOutline.CIRCLE, ComboOutline.PILL, SkinSize(120, 300)))
    }

    @Test fun aTriangleIsTouchedOnItsTriangleAlone() {
        val e = SkinElement("combo-1", SkinElementType.BUTTON, "y|b", SkinRect(0f, 0f, 100f, 100f))
        val corners = mapOf(
            SkinCorner.BOTTOM_LEFT to (10f to 90f), SkinCorner.BOTTOM_RIGHT to (90f to 90f),
            SkinCorner.TOP_LEFT to (10f to 10f), SkinCorner.TOP_RIGHT to (90f to 10f),
        )
        for ((corner, at) in corners) {
            val t = e.copy(shape = SkinShape.TRIANGLE, corner = corner)
            assertTrue(corner.id, ComboOutline.holds(t, at.first, at.second))
            assertTrue(corner.id, ComboOutline.holds(t, 50f, 50f))
            assertFalse(corner.id, ComboOutline.holds(t, 100f - at.first, 100f - at.second))
            // Within the slop of the long edge.
            assertFalse(corner.id, ComboOutline.holds(t, 50f - 6f * corner.sx, 50f - 6f * corner.sy))
            assertTrue(corner.id, ComboOutline.holds(t, 50f - 6f * corner.sx, 50f - 6f * corner.sy, slop = 10f))
        }
        // Every other outline takes its whole rect.
        assertTrue(ComboOutline.holds(e, 99f, 1f))
        assertTrue(ComboOutline.holds(e.copy(shape = SkinShape.PILL), 99f, 1f))
        assertTrue(ComboOutline.holds(e.copy(shape = SkinShape.TRIANGLE), 99f, 1f))
    }

    @Test fun aTrianglesLabelSitsTowardItsSquareCornerAndShrinks() {
        val circle = SkinElement("combo-1", SkinElementType.BUTTON, "l|r|a", SkinRect(0f, 0f, 100f, 100f), label = "L+R+A")
        assertEquals(SkinPoint(50f, 50f), SkinLabels.centre(circle))
        val t = circle.copy(shape = SkinShape.TRIANGLE, corner = SkinCorner.BOTTOM_LEFT)
        val c = SkinLabels.centre(t)
        assertEquals(31f, c.x, 0.01f)
        assertEquals(69f, c.y, 0.01f)
        assertTrue(ComboOutline.holds(t, c.x, c.y))
        assertEquals(69f, SkinLabels.centre(t.copy(corner = SkinCorner.TOP_RIGHT)).x, 0.01f)
        assertEquals(31f, SkinLabels.centre(t.copy(corner = SkinCorner.TOP_RIGHT)).y, 0.01f)
        assertEquals(58f to 58f, SkinLabels.room(t))
        assertEquals(100f to 100f, SkinLabels.room(circle))
        assertTrue(SkinLabels.size(t) < SkinLabels.size(circle))
        // Its own place wins.
        assertEquals(SkinPoint(60f, 50f), SkinLabels.centre(t.copy(labelOffset = SkinPoint(10f, 0f))))
    }

    @Test fun aComboFollowsItsHeightWhenItsRectChanges() {
        val e = SkinElement("combo-1", SkinElementType.BUTTON, "y|b", SkinRect(100f, 100f, 100f, 100f), depth = 10f)
        // A circle made a pill: wider, no deeper than its height allows.
        assertEquals(8f, e.at(SkinRect(70f, 110f, 160f, 80f)).depth, 0.001f)
        val plain = e.copy(id = "a", bind = "a")
        assertEquals(16f, plain.at(SkinRect(70f, 110f, 160f, 80f)).depth, 0.001f)
    }

    // --- skin.json ---

    private fun skin(placement: String) = JSONObject(
        """{"format":1,"console":"SFC","name":"Combo","combos":[{"id":"combo-1","buttons":["y","b"]}],"layouts":{
          "phone-portrait":{"size":{"width":900,"height":1800},"screens":[{"x":0,"y":0,"width":900,"height":700}],
            "elements":[
              {"id":"y","type":"button","bind":"y","x":550,"y":1250,"width":100,"height":100,"label":"Y"},
              {"id":"b","type":"button","bind":"b","x":650,"y":1350,"width":100,"height":100,"label":"B"}
            ]}}}""",
    ).apply { getJSONObject("layouts").getJSONObject("phone-portrait").put("combos", JSONArray("[$placement]")) }.toString()

    private fun OpenEmuxSkin.combo() = layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.isCombo }

    @Test fun aTriangleIsReadAndWrittenBack() {
        val s = SkinFormat.parse(skin("""{"id":"combo-1","x":555,"y":1355,"width":90,"height":90,"shape":"triangle","corner":"bottom-left"}"""))
        assertEquals(SkinShape.TRIANGLE, s.combo().shape)
        assertEquals(SkinCorner.BOTTOM_LEFT, s.combo().corner)
        val c = JSONObject(SkinFormat.toJson(s)).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos").getJSONObject(0)
        assertEquals("triangle", c.getString("shape"))
        assertEquals("bottom-left", c.getString("corner"))
        assertEquals(s, SkinFormat.parse(SkinFormat.toJson(s)))
    }

    @Test fun aTriangleIsNeverTurned() {
        val s = SkinFormat.parse(skin("""{"id":"combo-1","x":555,"y":1355,"width":90,"height":90,"shape":"triangle","corner":"top-left","rotation":30}"""))
        assertEquals(0f, s.combo().rotation)
        val pill = SkinFormat.parse(skin("""{"id":"combo-1","x":555,"y":1355,"width":160,"height":80,"shape":"pill","rotation":30}"""))
        assertEquals(30f, pill.combo().rotation)
    }

    @Test fun unknownOutlinesAreReadAsACircleNotRefused() {
        for (keys in listOf(""""shape":"star"""", """"shape":"triangle"""", """"shape":"triangle","corner":"middle"""", """"corner":"top-left"""")) {
            val s = SkinFormat.parse(skin("""{"id":"combo-1","x":555,"y":1355,"width":90,"height":90,$keys}"""))
            assertEquals(keys, SkinShape.CIRCLE, s.combo().shape)
            assertNull(keys, s.combo().corner)
            val c = JSONObject(SkinFormat.toJson(s)).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos").getJSONObject(0)
            assertFalse(c.has("shape") || c.has("corner"))
        }
    }

    @Test fun aTriangleIsAComboButtonsAlone() {
        val json = JSONObject(skin("""{"id":"combo-1","x":555,"y":1355,"width":90,"height":90}"""))
        json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(0).put("shape", "triangle")
        try {
            SkinFormat.parse(json.toString())
            fail("a triangle on a console button")
        } catch (e: SkinFormatException) {
            assertEquals(SkinError.BAD_SHAPE, e.error)
            assertEquals("layouts.phone-portrait.elements[0].shape", e.path)
        }
    }

    // --- The editor ---

    private val size = SkinSize(900, 1800)
    private val y = SkinElement("y", SkinElementType.BUTTON, "y", SkinRect(550f, 1250f, 100f, 100f), group = "face", label = "Y")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(650f, 1350f, 100f, 100f), group = "face", label = "B")
    private val portrait = SkinLayout(size, listOf(SkinRect(0f, 0f, 900f, 700f)), elements = listOf(y, b))
    private val landscape = SkinLayout(
        SkinSize(1800, 900), listOf(SkinRect(450f, 0f, 900f, 700f)),
        elements = listOf(y.copy(rect = SkinRect(1500f, 500f, 100f, 100f)), b.copy(rect = SkinRect(1600f, 600f, 100f, 100f))),
    )
    private val twoLayouts = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to portrait, SkinLayoutId.PHONE_LANDSCAPE to landscape))

    private fun model(s: OpenEmuxSkin = twoLayouts) = SkinEditorModel(s).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
    private fun SkinEditorModel.combo(id: SkinLayoutId = SkinLayoutId.PHONE_PORTRAIT) = layoutOf(id)!!.elements.first { it.isCombo }

    @Test fun theShapeToolChangesTheComboOnEveryLayout() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        assertEquals(ComboOutline.CIRCLE, m.selectedOutline)
        val before = m.combo().rect
        m.shape(ComboOutline.PILL)
        val pill = m.combo()
        assertEquals(SkinShape.PILL, pill.shape)
        assertEquals(before.width * ComboOutline.PILL_WIDE, pill.rect.width, 0.01f)
        assertEquals(before.height * ComboOutline.PILL_TALL, pill.rect.height, 0.01f)
        assertEquals(before.x + before.width / 2f, pill.rect.x + pill.rect.width / 2f, 0.01f)
        // Measured against the circle it was: still 100 %.
        assertEquals(100, m.percent())
        assertEquals(ComboOutline.PILL, m.selectedOutline)
        m.shape(triangleBL)
        assertEquals(triangleBL, ComboOutline.of(m.combo()))
        m.combo().rect.let { assertEquals(before.x, it.x, 0.01f); assertEquals(before.width, it.width, 0.01f); assertEquals(before.height, it.height, 0.01f) }
        // The other layout takes it too (the bug QA found on issue 891: landscape stayed a circle), at its own size there.
        val other = m.combo(SkinLayoutId.PHONE_LANDSCAPE)
        assertEquals(triangleBL, ComboOutline.of(other))
        assertEquals(other.rect.width, other.rect.height, 0.01f)
        assertTrue(m.changed(SkinLayoutId.PHONE_PORTRAIT))
        assertTrue(m.changed(SkinLayoutId.PHONE_LANDSCAPE))
        // Saved and read back: the triangle on both.
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(twoLayouts)))
        saved.layouts.values.forEach { l -> assertEquals(triangleBL, ComboOutline.of(l.elements.first { it.isCombo })) }
    }

    @Test fun eachShapeIsAnUndoStep() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.shape(ComboOutline.PILL)
        m.shape(triangleBL)
        // The same outline again is no step.
        m.shape(triangleBL)
        m.undo()
        assertEquals(ComboOutline.PILL, ComboOutline.of(m.combo()))
        m.undo()
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(m.combo()))
        m.redo()
        m.redo()
        assertEquals(triangleBL, ComboOutline.of(m.combo()))
    }

    @Test fun resetKeepsTheOutlineAndResetLayoutRestoresIt() {
        val m = model(SkinFormat.parse(model().apply { addCombo(setOf("y", "b")) }.json(SkinFormat.toJson(twoLayouts))))
        m.tap("combo-1")
        val circle = m.combo().rect
        m.shape(ComboOutline.PILL)
        val pill = m.combo().rect
        m.sizeStep(1)
        // Reset: the pill at its saved size, not squeezed into the circle's square.
        m.reset()
        assertEquals(SkinShape.PILL, m.combo().shape)
        assertEquals(pill.width, m.combo().rect.width, 0.01f)
        assertEquals(pill.height, m.combo().rect.height, 0.01f)
        assertEquals(100, m.percent())
        // Reset layout: the skin's circle again, in its own rect.
        m.resetLayout()
        assertEquals(ComboOutline.CIRCLE, ComboOutline.of(m.combo()))
        assertEquals(circle, m.combo().rect)
        assertFalse(m.dirty)
    }

    @Test fun aComboSavedInOneOutlinePerLayoutIsReadInThePickedOne() {
        val made = SkinFormat.parse(model().apply { addCombo(setOf("y", "b")) }.json(SkinFormat.toJson(twoLayouts)))
        // As an earlier build saved it: a triangle in portrait, the default circle in landscape.
        val portraitOnly = made.copy(
            layouts = made.layouts.mapValues { (id, l) ->
                if (id != SkinLayoutId.PHONE_PORTRAIT) l else l.copy(elements = l.elements.map { if (it.isCombo) ComboOutline.drawn(it, triangleBL) else it })
            },
        )
        val read = SkinFormat.parse(SkinFormat.toJson(portraitOnly))
        read.layouts.values.forEach { l -> assertEquals(triangleBL, ComboOutline.of(l.elements.first { it.isCombo })) }
        // Circles everywhere stay circles.
        assertEquals(made, SkinFormat.parse(SkinFormat.toJson(made)))
    }

    @Test fun nothingToShapeWithNothingSelected() {
        val m = model()
        assertNull(m.selectedOutline)
        m.shape(ComboOutline.PILL)
        assertFalse(m.canUndo)
        // No layout shown.
        SkinEditorModel(twoLayouts).shape(ComboOutline.PILL)
        // Any other button takes an outline too since issue 891 (SkinButtonShapeTest).
    }

    @Test fun aTriangleIsSelectedOnItsTriangleOnly() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.shape(ComboOutline(SkinShape.TRIANGLE, SkinCorner.TOP_LEFT))
        val r = m.combo().rect
        assertEquals("combo-1", m.hit(r.x + 5f, r.y + 5f))
        assertNull(m.hit(r.x + r.width - 2f, r.y + r.height - 2f))
    }

    @Test fun theShapeIsSavedResumedAndDeletedWithTheCombo() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.shape(ComboOutline(SkinShape.TRIANGLE, SkinCorner.BOTTOM_RIGHT))
        val text = m.json(SkinFormat.toJson(twoLayouts))
        val placed = JSONObject(text).getJSONObject("layouts")
        val c = placed.getJSONObject("phone-portrait").getJSONArray("combos").getJSONObject(0)
        assertEquals("triangle", c.getString("shape"))
        assertEquals("bottom-right", c.getString("corner"))
        // The other layout's place says the same (issue 891: one outline per combo).
        assertEquals("triangle", placed.getJSONObject("phone-landscape").getJSONArray("combos").getJSONObject(0).getString("shape"))

        // A draft brings it back; the saved skin opens with it.
        val draft = SkinFormat.parse(text)
        val resumed = SkinEditorModel.fromDraft(twoLayouts, draft).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertEquals(SkinCorner.BOTTOM_RIGHT, resumed.combo().corner)
        val saved = model(draft)
        assertEquals(SkinCorner.BOTTOM_RIGHT, saved.combo().corner)
        assertFalse(saved.dirty)

        // Changed again on the saved skin: only the outline is an edit of the look.
        saved.tap("combo-1")
        saved.shape(ComboOutline.PILL)
        assertEquals(mapOf("combo-1" to ComboOutline.PILL), saved.lookEdits().getValue(SkinLayoutId.PHONE_PORTRAIT).shapes)
        assertEquals("pill", JSONObject(saved.json(text)).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos").getJSONObject(0).getString("shape"))

        // Deleted: its outline goes with it.
        saved.deleteCombo("combo-1")
        assertTrue(saved.looks.values.none { "combo-1" in it.shapes })
    }
}
