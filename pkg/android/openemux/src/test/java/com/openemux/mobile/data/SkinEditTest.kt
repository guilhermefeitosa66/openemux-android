package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The skin editor's maths (issue 784): grid, snapping, resizing, selection, history and the editing session. */
class SkinEditTest {
    // 900 x 1800: one grid step is 50, the pull 20.
    private val size = SkinSize(900, 1800)
    private val dpad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1200f, 200f, 200f), group = "dpad")
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(650f, 1250f, 100f, 100f), group = "face", label = "A")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(550f, 1350f, 100f, 100f), group = "face", label = "B")
    private val start = SkinElement("start", SkinElementType.BUTTON, "start", SkinRect(400f, 1600f, 100f, 50f))
    private val layout = SkinLayout(size, listOf(SkinRect(0f, 0f, 900f, 700f)), elements = listOf(dpad, a, b, start))
    private val skin = OpenEmuxSkin(
        Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout, SkinLayoutId.PHONE_LANDSCAPE to layout.copy(size = SkinSize(1800, 1800))),
    )

    private fun others(vararg skip: SkinElement) = layout.elements.filter { it !in skip }.associate { it.id to it.rect }

    @Test fun gridStepIsTheShortSideOverEighteen() {
        assertEquals(50f, SkinGrid.step(size), 0.001f)
        assertEquals(50f, SkinGrid.step(SkinSize(1800, 900)), 0.001f)
        assertEquals(1f, SkinGrid.step(SkinSize(10, 10)), 0.001f)
    }

    @Test fun boundsCoverEveryRect() {
        assertEquals(SkinRect(550f, 1250f, 200f, 200f), SkinSnap.bounds(listOf(a.rect, b.rect)))
    }

    @Test fun snapOffMovesFreelyAndStaysInside() {
        val r = SkinSnap.move(start.rect, others(start), size, 3f, 7f, snap = false)
        assertEquals(3f, r.dx, 0f)
        assertEquals(7f, r.dy, 0f)
        assertTrue(r.guides.isEmpty())
        val out = SkinSnap.move(start.rect, others(start), size, 5000f, 5000f, snap = false)
        assertEquals(400f, out.dx, 0f)
        assertEquals(150f, out.dy, 0f)
    }

    @Test fun snapsToTheScreensCentre() {
        val r = SkinSnap.move(start.rect, others(start), size, 3f, 0f, snap = true)
        assertEquals(0f, r.dx, 0f)
        assertEquals(listOf(SnapGuide(SnapAxis.X, 450f, SnapKind.CENTER)), r.guides)
    }

    @Test fun snapsToTheMirrorOfAnotherControl() {
        val r = SkinSnap.move(b.rect, others(a, b), size, 105f, 0f, snap = true)
        assertEquals(100f, r.dx, 0f)
        assertEquals(listOf(SnapGuide(SnapAxis.X, 700f, SnapKind.MIRROR, "dpad")), r.guides)
    }

    @Test fun snapsToAnotherControlsCentreLine() {
        val r = SkinSnap.move(b.rect, others(a, b), size, 0f, -95f, snap = true)
        assertEquals(-100f, r.dy, 0f)
        assertEquals(listOf(SnapGuide(SnapAxis.Y, 1300f, SnapKind.ALIGN, "dpad")), r.guides)
    }

    @Test fun snapsToAnotherControlsEdge() {
        val r = SkinSnap.move(start.rect, others(start), size, -95f, 0f, snap = true)
        assertEquals(-100f, r.dx, 0f)
        assertEquals(listOf(SnapGuide(SnapAxis.X, 300f, SnapKind.EDGE, "dpad")), r.guides)
    }

    @Test fun withNothingNearTheCentreLandsOnHalfAGridStep() {
        val r = SkinSnap.move(start.rect, others(start), size, 80f, 0f, snap = true)
        assertEquals(75f, r.dx, 0f)
        assertTrue(r.guides.isEmpty())
    }

    @Test fun theNearestTargetWins() {
        // The other's centre line is 2 away, its right edge 8 and its left edge 12: the centre line wins.
        val r = SkinSnap.move(SkinRect(612f, 100f, 100f, 100f), mapOf("x" to SkinRect(600f, 400f, 120f, 120f)), size, 0f, 0f, snap = true)
        assertEquals(listOf(SnapGuide(SnapAxis.X, 660f, SnapKind.ALIGN, "x")), r.guides.filter { it.axis == SnapAxis.X })
    }

    @Test fun aGuideTheClampMovesOffIsDropped() {
        val r = SkinSnap.move(SkinRect(10f, 500f, 100f, 100f), mapOf("o" to SkinRect(50f, 100f, 100f, 100f)), size, -55f, 0f, snap = true)
        assertEquals(-10f, r.dx, 0f)
        assertTrue(r.guides.none { it.axis == SnapAxis.X })
        val y = SkinSnap.move(SkinRect(500f, 10f, 100f, 100f), mapOf("o" to SkinRect(100f, 50f, 100f, 100f)), size, 0f, -55f, snap = true)
        assertTrue(y.guides.none { it.axis == SnapAxis.Y })
    }

    @Test fun clampKeepsASpanLargerThanTheLayoutAtZero() {
        assertEquals(0f, SkinSnap.clamp(-30f, 2000f, 900), 0f)
        assertEquals(800f, SkinSnap.clamp(900f, 100f, 900), 0f)
    }

    @Test fun nudgeMovesOneGridStepAndStaysInside() {
        val moved = SkinSnap.nudge(mapOf("a" to a.rect, "b" to b.rect), size, 1, -1)
        assertEquals(SkinRect(700f, 1200f, 100f, 100f), moved["a"])
        assertEquals(SkinRect(600f, 1300f, 100f, 100f), moved["b"])
        val edge = SkinSnap.nudge(mapOf("a" to SkinRect(820f, 0f, 80f, 80f)), size, 1, -1)
        assertEquals(SkinRect(820f, 0f, 80f, 80f), edge["a"])
        assertTrue(SkinSnap.nudge(emptyMap(), size, 1, 1).isEmpty())
    }

    @Test fun resizeStepsAreFivePercentFromFiftyToTwoHundred() {
        assertEquals(100, SkinResize.percent(a.rect, a.rect))
        assertEquals(150, SkinResize.percent(a.rect, a.rect.copy(width = 150f)))
        assertEquals(105, SkinResize.step(103f))
        assertEquals(100, SkinResize.step(102.4f))
        assertEquals(50, SkinResize.step(10f))
        assertEquals(200, SkinResize.step(400f))
    }

    @Test fun oneControlScalesAboutItsCentre() {
        val scaled = SkinResize.scale(mapOf("a" to a.rect), a.rect, "a", 150, size)
        assertEquals(SkinRect(625f, 1225f, 150f, 150f), scaled["a"])
    }

    @Test fun aGroupScalesAboutItsCentreKeepingItsShape() {
        val scaled = SkinResize.scale(mapOf("a" to a.rect, "b" to b.rect), a.rect, "a", 50, size)
        assertEquals(SkinRect(650f, 1300f, 50f, 50f), scaled["a"])
        assertEquals(SkinRect(600f, 1300f, 100f, 100f), SkinSnap.bounds(scaled.values))
    }

    @Test fun theScaleIsRelativeToTheSavedSize() {
        // Already at 200 %: asking for 100 % halves it.
        val big = a.rect.copy(x = 600f, y = 1200f, width = 200f, height = 200f)
        val scaled = SkinResize.scale(mapOf("a" to big), a.rect, "a", 100, size)
        assertEquals(100f, scaled.getValue("a").width, 0.001f)
    }

    @Test fun aScaleNeverOutgrowsTheLayoutAndIsPushedBackInside() {
        val wide = SkinRect(0f, 0f, 600f, 100f)
        val scaled = SkinResize.scale(mapOf("w" to wide), wide, "w", 200, size).getValue("w")
        assertEquals(900f, scaled.width, 0.001f)
        assertEquals(0f, scaled.x, 0.001f)
        assertEquals(0f, scaled.y, 0.001f)
        assertTrue(scaled.inside(size))
    }

    @Test fun membersAreTheGroupInLayoutOrder() {
        assertEquals(listOf("a", "b"), SkinSelect.members(layout, "b"))
        assertEquals(listOf("start"), SkinSelect.members(layout, "start"))
        assertEquals(listOf("nope"), SkinSelect.members(layout, "nope"))
    }

    @Test fun theFirstTapTakesTheGroupTheSecondOneControl() {
        val group = SkinSelection.Group("face", listOf("a", "b"))
        assertEquals(group, SkinSelect.tap(layout, null, "a"))
        assertEquals(SkinSelection.One("a"), SkinSelect.tap(layout, group, "a"))
        assertEquals(SkinSelection.One("b"), SkinSelect.tap(layout, SkinSelection.One("a"), "b"))
        assertEquals(SkinSelection.One("a"), SkinSelect.tap(layout, SkinSelection.One("a"), "a"))
        assertEquals(group, SkinSelect.tap(layout, SkinSelection.One("start"), "a"))
        assertEquals(SkinSelection.One("dpad"), SkinSelect.tap(layout, null, "dpad"))
        assertEquals(SkinSelection.One("start"), SkinSelect.tap(layout, group, "start"))
        assertEquals(listOf("a"), SkinSelection.One("a").ids)
    }

    @Test fun aDragMovesTheSelectionWhenItHoldsTheControl() {
        val group = SkinSelection.Group("face", listOf("a", "b"))
        assertSame(group, SkinSelect.dragged(layout, group, "b"))
        assertEquals(SkinSelection.One("b"), SkinSelect.dragged(layout, SkinSelection.One("b"), "b"))
        assertEquals(group, SkinSelect.dragged(layout, SkinSelection.One("a"), "b"))
        assertEquals(SkinSelection.One("start"), SkinSelect.dragged(layout, null, "start"))
    }

    @Test fun theGamepadStepsThroughGroupsAndThroughAGroupsMembers() {
        val units = SkinSelect.units(layout)
        assertEquals(listOf(SkinSelection.One("dpad"), SkinSelection.Group("face", listOf("a", "b")), SkinSelection.One("start")), units)
        assertEquals(units.first(), SkinSelect.step(layout, null, 1))
        assertEquals(units.last(), SkinSelect.step(layout, null, -1))
        assertEquals(units[1], SkinSelect.step(layout, units[0], 1))
        assertEquals(units[0], SkinSelect.step(layout, units[2], 1))
        assertEquals(units[2], SkinSelect.step(layout, SkinSelection.One("dpad"), -1))
        assertEquals(SkinSelection.One("b"), SkinSelect.step(layout, SkinSelection.One("a"), 1))
        assertEquals(SkinSelection.One("b"), SkinSelect.step(layout, SkinSelection.One("a"), -1))
        assertNull(SkinSelect.step(layout.copy(elements = emptyList()), null, 1))
    }

    @Test fun toggleSwitchesBetweenAGroupAndOneOfItsControls() {
        assertNull(SkinSelect.toggle(layout, null))
        assertEquals(SkinSelection.One("a"), SkinSelect.toggle(layout, SkinSelection.Group("face", listOf("a", "b"))))
        assertEquals(SkinSelection.Group("face", listOf("a", "b")), SkinSelect.toggle(layout, SkinSelection.One("b")))
        assertEquals(SkinSelection.One("start"), SkinSelect.toggle(layout, SkinSelection.One("start")))
    }

    @Test fun historyUndoesRedoesAndForgetsTheRedosOnANewStep() {
        val h = SkinEditHistory(0, cap = 2)
        assertFalse(h.canUndo)
        assertFalse(h.undo())
        assertFalse(h.redo())
        h.push(0)
        assertFalse(h.canUndo)
        h.push(1); h.push(2); h.push(3)
        assertTrue(h.undo()); assertTrue(h.undo())
        assertFalse(h.undo())
        assertEquals(1, h.current)
        assertTrue(h.canRedo)
        assertTrue(h.redo())
        assertEquals(2, h.current)
        h.push(9)
        assertFalse(h.canRedo)
        assertEquals(9, h.current)
    }

    private fun model(draft: SkinRects? = null) = SkinEditorModel(skin, draft).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }

    @Test fun aSessionEditsTheLayoutForTheFormAndRotation() {
        val m = model()
        assertEquals(SkinLayoutId.PHONE_PORTRAIT, m.layoutId)
        assertEquals(layout, m.layout)
        assertFalse(m.dirty)
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        assertNull(m.layoutId)
        assertNull(m.layout)
        assertNull(m.hit(450f, 1300f))
    }

    @Test fun withNoLayoutEveryActionIsANoOp() {
        val m = model()
        m.show(SkinForm.FOLD, SkinOrientation.LANDSCAPE)
        m.tap("a"); m.step(1); m.toggle(); m.beginMove("a"); m.moveBy(10f, 10f); m.nudge(1, 0); m.reset(); m.resetLayout()
        assertNull(m.selection)
        assertFalse(m.dirty)
    }

    @Test fun hitFindsTheTopmostControlWithinTheSlop() {
        val m = model()
        assertEquals("a", m.hit(700f, 1300f))
        assertEquals("b", m.hit(640f, 1360f))
        assertNull(m.hit(645f, 1240f))
        assertEquals("a", m.hit(645f, 1245f, slop = 6f))
    }

    @Test fun tapsStepsAndToggleChangeTheSelection() {
        val m = model()
        m.tap("a")
        assertEquals(SkinSelection.Group("face", listOf("a", "b")), m.selection)
        m.toggle()
        assertEquals(SkinSelection.One("a"), m.selection)
        m.step(1)
        assertEquals(SkinSelection.One("b"), m.selection)
        m.clearSelection()
        assertNull(m.selection)
        m.tap("a")
        m.show(SkinForm.PHONE, SkinOrientation.LANDSCAPE)
        assertNull(m.selection)
        m.tap("a")
        m.show(SkinForm.PHONE, SkinOrientation.LANDSCAPE)
        assertEquals(SkinSelection.Group("face", listOf("a", "b")), m.selection)
    }

    @Test fun aDragIsOneUndoStepWithItsGuides() {
        val m = model()
        m.moveBy(10f, 10f)
        assertFalse(m.dirty)
        m.tap("b"); m.tap("b")
        m.beginMove("b")
        m.moveBy(0f, -95f)
        // B's right edge already touches A's left one: both lines show.
        assertEquals(listOf(SnapGuide(SnapAxis.X, 650f, SnapKind.EDGE, "a"), SnapGuide(SnapAxis.Y, 1300f, SnapKind.ALIGN, "dpad")), m.guides)
        assertEquals(1250f, m.layout!!.elements.first { it.id == "b" }.rect.y, 0f)
        assertEquals(1250f, m.layout!!.elements.first { it.id == "a" }.rect.y, 0f)
        assertFalse(m.canUndo)
        m.endGesture()
        assertTrue(m.guides.isEmpty())
        assertTrue(m.canUndo)
        assertTrue(m.dirty)
        m.undo()
        assertFalse(m.dirty)
        assertTrue(m.canRedo)
        m.redo()
        assertTrue(m.dirty)
    }

    @Test fun aFreeDragDoesNotSnap() {
        val m = model()
        m.tap("start"); m.beginMove("start")
        m.moveBy(3f, 0f, free = true)
        assertTrue(m.guides.isEmpty())
        assertEquals(403f, m.layout!!.elements.first { it.id == "start" }.rect.x, 0f)
        m.snap = false
        m.moveBy(3f, 0f)
        assertTrue(m.guides.isEmpty())
        m.cancelGesture()
        assertFalse(m.dirty)
        m.endGesture()
        assertFalse(m.canUndo)
    }

    @Test fun resizingFollowsTheGestureInStepsAndCommitsOnce() {
        val m = model()
        assertNull(m.resizeBy(2f))
        m.beginResize()
        assertNull(m.resizeBy(2f))
        assertNull(m.percent())
        m.tap("a"); m.toggle()
        m.beginResize()
        assertEquals(125, m.resizeBy(1.23f))
        assertEquals(200, m.resizeBy(9f))
        assertEquals(50, m.resizeBy(0.1f))
        m.endGesture()
        assertEquals(50, m.percent())
        m.undo()
        assertEquals(100, m.percent())
    }

    @Test fun theSizeToolStepsAndStopsAtTheLimits() {
        val m = model()
        m.sizeStep(1)
        assertFalse(m.dirty)
        m.tap("start")
        m.sizeStep(1)
        assertEquals(105, m.percent())
        m.sizeStep(-1); m.sizeStep(-1)
        assertEquals(95, m.percent())
        repeat(30) { m.sizeStep(-1) }
        assertEquals(50, m.percent())
    }

    @Test fun nudgeResetAndResetLayout() {
        val m = model()
        m.nudge(1, 0)
        m.reset()
        assertFalse(m.dirty)
        m.tap("a")
        m.nudge(1, 0)
        assertEquals(700f, m.layout!!.elements.first { it.id == "a" }.rect.x, 0f)
        assertEquals(600f, m.layout!!.elements.first { it.id == "b" }.rect.x, 0f)
        m.reset()
        assertFalse(m.dirty)
        m.tap("start"); m.nudge(0, 1)
        m.tap("a"); m.nudge(0, 1)
        m.resetLayout()
        assertFalse(m.dirty)
        assertTrue(m.canUndo)
    }

    @Test fun editsAreOnlyWhatChangedPerLayout() {
        val m = model()
        assertTrue(m.edits().isEmpty())
        m.tap("start"); m.nudge(1, 0)
        assertEquals(mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("start" to SkinRect(450f, 1600f, 100f, 50f))), m.edits())
    }

    @Test fun aDraftsRectsAreTakenForWhatTheSkinHas() {
        val draft = mapOf(
            SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(0f, 0f, 100f, 100f), "ghost" to SkinRect(1f, 1f, 1f, 1f)),
            SkinLayoutId.TABLET_PORTRAIT to mapOf("a" to SkinRect(5f, 5f, 5f, 5f)),
        )
        val m = model(draft)
        assertTrue(m.dirty)
        assertEquals(SkinRect(0f, 0f, 100f, 100f), m.layout!!.elements.first { it.id == "a" }.rect)
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), m.rects.keys)
        assertFalse("ghost" in m.rects.getValue(SkinLayoutId.PHONE_PORTRAIT))
    }

    @Test fun theSessionWritesItsRectsIntoSkinJson() {
        val m = model()
        m.tap("start"); m.nudge(1, 0)
        val json = JSONObject(m.json(SkinFormat.toJson(skin)))
        val start = json.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(3)
        assertEquals(450, start.getInt("x"))
        assertEquals("start", start.getString("bind"))
        assertEquals(SkinFormat.parse(json.toString()).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.map { it.bind }, layout.elements.map { it.bind })
    }

    @Test fun patchRoundsClampsAndKeepsEveryOtherKey() {
        val text = "\uFEFF" + JSONObject(SkinFormat.toJson(skin)).put("creator", "me").apply {
            getJSONObject("layouts").put("tablet-portrait", JSONObject().put("size", JSONObject().put("width", 10).put("height", 10)))
        }.toString()
        val edits = mapOf(
            SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(850.6f, -3f, 99.6f, 0.2f), "ghost" to SkinRect(0f, 0f, 1f, 1f)),
            SkinLayoutId.TABLET_PORTRAIT to mapOf("a" to SkinRect(0f, 0f, 1f, 1f)),
            SkinLayoutId.FOLD_PORTRAIT to mapOf("a" to SkinRect(0f, 0f, 1f, 1f)),
        )
        val out = JSONObject(SkinEditJson.patch(text, edits))
        assertEquals("me", out.getString("creator"))
        val a = out.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(1)
        assertEquals(800, a.getInt("x"))
        assertEquals(0, a.getInt("y"))
        assertEquals(100, a.getInt("width"))
        assertEquals(1, a.getInt("height"))
        assertEquals("A", a.getString("label"))
        assertFalse(out.getJSONObject("layouts").has("fold-portrait"))
    }
}
