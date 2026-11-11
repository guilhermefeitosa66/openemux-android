package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's combo buttons (issue 842): added to every layout, recorded again, deleted, undone, saved and resumed. */
class SkinEditComboTest {
    private val size = SkinSize(900, 1800)
    private val y = SkinElement("y", SkinElementType.BUTTON, "y", SkinRect(550f, 1250f, 100f, 100f), group = "face", label = "Y")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(650f, 1350f, 100f, 100f), group = "face", label = "B")
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(750f, 1250f, 100f, 100f), group = "face", label = "A")
    private val l = SkinElement("l", SkinElementType.BUTTON, "l", SkinRect(20f, 720f, 200f, 60f), label = "L")
    private val r = SkinElement("r", SkinElementType.BUTTON, "r", SkinRect(680f, 720f, 200f, 60f), label = "R")
    private val dpad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1200f, 200f, 200f))
    private val portrait = SkinLayout(size, listOf(SkinRect(0f, 0f, 900f, 700f)), elements = listOf(dpad, y, b, a, l, r))
    private val landscape = SkinLayout(
        SkinSize(1800, 900), listOf(SkinRect(450f, 0f, 900f, 700f)),
        elements = listOf(y.copy(rect = SkinRect(1500f, 500f, 100f, 100f)), b.copy(rect = SkinRect(1600f, 600f, 100f, 100f))),
    )
    private val skin = OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to portrait, SkinLayoutId.PHONE_LANDSCAPE to landscape))

    private fun model(s: OpenEmuxSkin = skin) = SkinEditorModel(s).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
    private fun SkinEditorModel.combo(id: SkinLayoutId = SkinLayoutId.PHONE_PORTRAIT, combo: String = "combo-1") =
        layoutOf(id)?.elements?.firstOrNull { it.id == combo }

    private fun clearOfEverything(l: SkinLayout, id: String): Boolean {
        val c = l.elements.first { it.id == id }.rect
        return (l.screens + l.elements.filter { it.id != id }.map { it.rect }).none {
            it.x < c.x + c.width && c.x < it.x + it.width && it.y < c.y + c.height && c.y < it.y + it.height
        }
    }

    @Test fun aComboIsAddedToEveryLayoutOverNothingAndSelected() {
        val m = model()
        assertEquals(listOf("y", "b", "a", "l", "r"), m.comboInputs().map { it.bind })
        val c = m.addCombo(setOf("b", "y"))!!
        assertEquals(SkinCombo("combo-1", listOf("y", "b")), c)
        assertEquals(listOf(c), m.combos)
        assertEquals(SkinSelection.One("combo-1"), m.selection)
        assertEquals(c, m.selectedCombo)
        assertTrue(m.dirty)
        for (id in listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)) {
            val e = m.combo(id)!!
            assertEquals("y|b", e.bind)
            assertEquals("Y+B", e.label)
            assertEquals(SkinShape.CIRCLE, e.shape)
            assertEquals(100f, e.rect.width)
            assertTrue(clearOfEverything(m.layoutOf(id)!!, "combo-1"))
            assertTrue(m.changed(id))
        }
    }

    @Test fun buttonsThatMakeNoComboAddNothing() {
        val m = model()
        assertNull(m.addCombo(setOf("y")))
        assertNull(m.addCombo(setOf("y", "b", "a", "l")))
        assertNull(m.addCombo(setOf("y", "up")))
        assertFalse(m.canUndo)
        assertFalse(m.dirty)
        // No layout shown: nothing to add to.
        assertNull(SkinEditorModel(skin).addCombo(setOf("y", "b")))
        assertTrue(SkinEditorModel(skin).comboInputs().isEmpty())
    }

    @Test fun aSecondComboTakesTheNextIdAndItsOwnSpot() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        val c2 = m.addCombo(setOf("l", "r", "a"))!!
        assertEquals("combo-2", c2.id)
        assertEquals("L+R+A", m.combo(combo = "combo-2")!!.label)
        assertTrue(clearOfEverything(m.layoutOf(SkinLayoutId.PHONE_PORTRAIT)!!, "combo-2"))
        // The landscape layout has no L, R or A: the combo is there all the same, near its middle.
        assertNotNull(m.combo(SkinLayoutId.PHONE_LANDSCAPE, "combo-2"))
    }

    @Test fun recordingAgainChangesItsButtonsAndLabelEverywhere() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        assertTrue(m.recordCombo("combo-1", listOf("a", "l")))
        assertEquals(listOf("l", "a"), m.combos.single().buttons)
        assertEquals("l|a", m.combo(SkinLayoutId.PHONE_LANDSCAPE)!!.bind)
        assertEquals("L+A", m.combo()!!.label)
        assertFalse(m.recordCombo("combo-1", listOf("a")))
        assertFalse(m.recordCombo("combo-9", listOf("a", "b")))
        m.undo()
        assertEquals("Y+B", m.combo()!!.label)
        assertFalse(SkinEditorModel(skin).recordCombo("combo-1", listOf("a", "b")))
    }

    @Test fun deletingTakesItOutOfEveryLayoutAndUndoBringsItBack() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.nudge(1, 0)
        val moved = m.combo()!!.rect
        m.deleteCombo("combo-1")
        assertNull(m.selection)
        assertTrue(m.combos.isEmpty())
        assertNull(m.combo())
        assertNull(m.combo(SkinLayoutId.PHONE_LANDSCAPE))
        assertFalse("combo-1" in m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT))
        // Deleting what is not there does nothing.
        m.deleteCombo("combo-1")
        m.undo()
        assertEquals(moved, m.combo()!!.rect)
        m.undo()
        m.undo()
        assertNull(m.combo())
        assertFalse(m.dirty)
        m.redo()
        assertNotNull(m.combo())
        // A combo added after an undo takes a new id: the one undone is kept for redo.
        m.undo()
        assertEquals("combo-2", m.addCombo(setOf("y", "b"))!!.id)
    }

    @Test fun aComboIsMovedResizedAndPaintedLikeAnyButton() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        val at = m.combo()!!.rect
        m.sizeStep(1)
        assertEquals(105, m.percent())
        m.reset()
        assertEquals(at, m.combo()!!.rect)
        m.paint(SkinTarget.of(m.layout!!, listOf("combo-1"))!!, listOf("combo-1"), SkinPart.BUTTON, 0xFF112233.toInt())
        m.endGesture()
        assertEquals(0xFF112233.toInt(), m.combo()!!.colors["button"])
        // A colour is the skin's: the other layout's combo is painted too.
        assertEquals(0xFF112233.toInt(), m.combo(SkinLayoutId.PHONE_LANDSCAPE)!!.colors["button"])
        m.resetLayout()
        assertEquals(at, m.combo()!!.rect)
    }

    @Test fun theSkinJsonGetsTheCombosAndKeepsTheRest() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        val json = m.json(SkinFormat.toJson(skin))
        val read = SkinFormat.parse(json)
        assertEquals(listOf(SkinCombo("combo-1", listOf("y", "b"))), read.combos)
        val e = read.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "combo-1" }
        assertEquals(m.combo()!!.rect, e.rect)
        assertEquals(m.combo()!!.copy(rect = e.rect), e)
        assertEquals(portrait.elements, read.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.filter { !it.isCombo })
        // Deleted and saved: the keys go.
        m.deleteCombo("combo-1")
        val gone = JSONObject(m.json(json))
        assertFalse(gone.has("combos"))
        assertFalse(gone.getJSONObject("layouts").getJSONObject("phone-portrait").has("combos"))
    }

    @Test fun theJsonPutsAMovedComboInWholeUnitsInsideItsLayout() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.moveCombo(33.3f, 0.6f)
        val c = JSONObject(m.json(SkinFormat.toJson(skin))).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos").getJSONObject(0)
        assertTrue(c.get("x") is Int)
        assertTrue(c.get("y") is Int)
    }

    private fun SkinEditorModel.moveCombo(dx: Float, dy: Float) {
        snap = false
        assertTrue(beginMove("combo-1"))
        moveBy(dx, dy)
        endGesture()
    }

    @Test fun aSavedSkinsCombosOpenAndCanBeChanged() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        val saved = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val again = model(saved)
        assertFalse(again.dirty)
        assertEquals(saved.combos, again.combos)
        assertEquals("Y+B", again.combo()!!.label)
        again.recordCombo("combo-1", listOf("l", "r"))
        assertTrue(again.dirty)
        assertTrue(again.changed(SkinLayoutId.PHONE_LANDSCAPE))
        val json = JSONObject(again.json(SkinFormat.toJson(saved)))
        assertEquals("l", json.getJSONArray("combos").getJSONObject(0).getJSONArray("buttons").getString(0))
        again.deleteCombo("combo-1")
        assertTrue(again.dirty)
        assertNull(again.combo())
        again.resetLayout()
        // Reset puts the saved places back, not a combo the skin no longer has.
        assertNull(again.combo())
    }

    @Test fun aDraftBringsItsCombosBack() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.nudge(0, 1)
        val draft = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val resumed = SkinEditorModel.fromDraft(skin, draft).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertTrue(resumed.dirty)
        assertEquals(m.combos, resumed.combos)
        assertEquals(m.combo()!!.rect, resumed.combo()!!.rect)
        assertEquals(m.combo(SkinLayoutId.PHONE_LANDSCAPE)!!.rect, resumed.combo(SkinLayoutId.PHONE_LANDSCAPE)!!.rect)
        resumed.undo()
        assertNotNull(resumed.combo())
        // A draft that deleted a combo the skin has.
        val withCombo = SkinFormat.parse(m.json(SkinFormat.toJson(skin)))
        val dropped = model(withCombo).also { it.deleteCombo("combo-1") }
        val draft2 = SkinFormat.parse(dropped.json(SkinFormat.toJson(withCombo)))
        val resumed2 = SkinEditorModel.fromDraft(withCombo, draft2).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertTrue(resumed2.combos.isEmpty())
        assertNull(resumed2.combo())
        assertTrue(resumed2.dirty)
    }

    @Test fun aFormSwitchedOnGetsTheCombos() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        assertTrue(m.setForm(SkinForm.TABLET, true))
        val tablet = m.layoutOf(SkinLayoutId.TABLET_PORTRAIT)!!
        assertNotNull(tablet.elements.firstOrNull { it.id == "combo-1" && it.bind == "y|b" })
        // Off and on again after a second combo: the layout comes back as made, and the new combo starts on it.
        assertTrue(m.setForm(SkinForm.TABLET, false))
        m.addCombo(setOf("l", "r"))
        assertTrue(m.setForm(SkinForm.TABLET, true))
        val back = m.layoutOf(SkinLayoutId.TABLET_LANDSCAPE)!!
        assertEquals(setOf("combo-1", "combo-2"), back.elements.filter { it.isCombo }.map { it.id }.toSet())
        assertTrue(clearOfEverything(back, "combo-2"))
        // Written whole, combos included, as a layout of its own.
        val json = JSONObject(m.json(SkinFormat.toJson(skin)))
        assertEquals(2, json.getJSONObject("layouts").getJSONObject("tablet-portrait").getJSONArray("combos").length())
        assertEquals(2, SkinFormat.parse(json.toString()).layouts.getValue(SkinLayoutId.TABLET_PORTRAIT).elements.count { it.isCombo })
    }

    @Test fun undoingTheAddLetsGoOfTheCombo() {
        val m = model()
        m.addCombo(setOf("y", "b"))
        m.undo()
        assertNull(m.selection)
        assertNull(m.selectedCombo)
        m.redo()
        m.tap("y")
        val group = m.selection
        m.undo()
        m.redo()
        // A selection the layout still has stays.
        assertEquals(group, m.selection)
        m.selectBody()
        m.undo()
        assertEquals(SkinSelection.Body, m.selection)
    }

    @Test fun aComboANewerAppWroteIsKeptOnSave() {
        val text = JSONObject(SkinFormat.toJson(skin)).apply {
            put("combos", org.json.JSONArray("""[{"id":"combo-1","buttons":["y","b","a","l"]}]"""))
            getJSONObject("layouts").getJSONObject("phone-portrait")
                .put("combos", org.json.JSONArray("""[{"id":"combo-1","x":0,"y":800,"width":100,"height":100}]"""))
        }.toString()
        val read = SkinFormat.parse(text)
        assertTrue(read.combos.isEmpty())
        val m = model(read)
        m.addCombo(setOf("y", "b"))
        val json = JSONObject(m.json(text))
        assertEquals(listOf("combo-2", "combo-1"), (0 until json.getJSONArray("combos").length()).map { json.getJSONArray("combos").getJSONObject(it).getString("id") })
        val placed = json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos")
        assertEquals(setOf("combo-1", "combo-2"), (0 until placed.length()).map { placed.getJSONObject(it).getString("id") }.toSet())
        // Its own combo deleted, the newer one stays.
        m.deleteCombo("combo-2")
        val after = JSONObject(m.json(text))
        assertEquals(1, after.getJSONArray("combos").length())
        assertEquals(1, after.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("combos").length())
        assertFalse(after.getJSONObject("layouts").getJSONObject("phone-landscape").has("combos"))
    }

    @Test fun aSkinsOwnLayoutSwitchedBackOnGetsACombosMadeMeanwhile() {
        val m = model()
        // Phone landscape off is not allowed while editing phone: use a skin with a tablet layout of its own.
        val withTablet = skin.copy(layouts = skin.layouts + (SkinLayoutId.TABLET_PORTRAIT to portrait) + (SkinLayoutId.TABLET_LANDSCAPE to landscape))
        val t = model(withTablet)
        assertTrue(t.setForm(SkinForm.TABLET, false))
        t.addCombo(setOf("y", "b"))
        assertTrue(t.setForm(SkinForm.TABLET, true))
        assertNotNull(t.combo(SkinLayoutId.TABLET_PORTRAIT))
        assertTrue(clearOfEverything(t.layoutOf(SkinLayoutId.TABLET_PORTRAIT)!!, "combo-1"))
        assertFalse(m.dirty)
    }
}
