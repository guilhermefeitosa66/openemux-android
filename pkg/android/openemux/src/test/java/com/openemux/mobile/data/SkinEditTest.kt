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

    @Test fun patchKeepsAnNdsSkinsScreenLayoutPages() {
        val two = listOf(SkinRect(0f, 0f, 450f, 350f), SkinRect(450f, 0f, 450f, 350f))
        val pages = SkinScreenPage.SCREENS.mapValues { (_, n) -> SkinScreenPage(two.take(n), elements = listOf(a)) }
        val ds = OpenEmuxSkin(Console.NDS, "DS", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(screens = two, screenLayouts = pages)))
        val out = SkinEditJson.patch(SkinFormat.toJson(ds), mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(100f, 1000f, 200f, 200f))))
        val read = SkinFormat.parse(out).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        // The editor moves the layout's own controls; the pages are kept as they were.
        assertEquals(SkinRect(100f, 1000f, 200f, 200f), read.elements.first { it.id == "a" }.rect)
        assertEquals(pages, read.screenLayouts)
    }

    // A label of its own: selected, moved and sized apart from its control.

    /** Start as the consoles print it: the word under its key, 30 tall, 60 under the key's centre (450, 1625). */
    private val word = start.copy(label = "START", labelSize = 30f, labelOffset = SkinPoint(0f, 60f))
    private val wordBox = SkinRect(393f, 1670f, 114f, 30f)
    private val worded = skin.copy(layouts = skin.layouts.mapValues { (_, l) -> l.copy(elements = listOf(dpad, a, b, word)) })
    private val wordedLayout = worded.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
    private val face = SkinSelection.Group("face", listOf("a", "b"))

    private fun wordedModel() = SkinEditorModel(worded).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }

    private fun SkinEditorModel.element(id: String) = layout!!.elements.first { it.id == id }
    private fun SkinEditorModel.now() = rects.getValue(SkinLayoutId.PHONE_PORTRAIT)

    private fun near(expected: SkinRect, actual: SkinRect?) {
        assertEquals("$actual", expected.x, actual!!.x, 0.01f)
        assertEquals("$actual", expected.y, actual.y, 0.01f)
        assertEquals("$actual", expected.width, actual.width, 0.01f)
        assertEquals("$actual", expected.height, actual.height, 0.01f)
    }

    @Test fun aLabelsKeyNamesItsControl() {
        assertEquals("a#label", SkinArea.label("a"))
        assertTrue(SkinArea.isLabel("a#label"))
        assertFalse(SkinArea.isLabel("a"))
        assertEquals("key-1", SkinArea.labelOwner("key-1#label"))
        assertNull(SkinArea.labelOwner("a"))
        assertNull(SkinArea.labelOwner(SkinArea.FRAME))
        // Neither a control's id nor one of the screen area's.
        assertFalse(SkinArea.isArea(SkinArea.label("a")))
        assertNull(SkinArea.screenIndex(SkinArea.label("a")))
        assertFalse(SkinArea.isLabel(SkinArea.screen(0)))
    }

    @Test fun whatIsPaintedAndNeverMovedSaysSo() {
        listOf(SkinSelection.Frame, SkinSelection.Body).forEach {
            assertFalse(it.movable)
            assertTrue(it.ids.isEmpty())
        }
        listOf(SkinSelection.One("a"), SkinSelection.Label("a"), face, SkinSelection.Screen(listOf("@screen0"))).forEach { assertTrue("$it", it.movable) }
        // A label's colour is its control's "letter": it paints that control.
        assertEquals(listOf("a"), SkinSelection.Label("a").ids)
    }

    @Test fun eachTapGoesOneStepInThenStartsOver() {
        val taps = generateSequence(SkinSelect.tap(layout, null, "a")) { SkinSelect.tap(layout, it, "a") }.take(5).toList()
        assertEquals(listOf(face, SkinSelection.One("a"), SkinSelection.Label("a"), face, SkinSelection.One("a")), taps)
        // With a label selected, another control of its group is that one control.
        assertEquals(SkinSelection.One("b"), SkinSelect.tap(layout, SkinSelection.Label("a"), "b"))
        // A lone control: itself, its label, itself.
        assertEquals(SkinSelection.One("start"), SkinSelect.tap(wordedLayout, null, "start"))
        assertEquals(SkinSelection.Label("start"), SkinSelect.tap(wordedLayout, SkinSelection.One("start"), "start"))
        assertEquals(SkinSelection.One("start"), SkinSelect.tap(wordedLayout, SkinSelection.Label("start"), "start"))
    }

    @Test fun aControlWithNoLabelOfItsOwnHasNoLabelStep() {
        // Start has no label here and the D-pad's arrows are not one: a tap on the one selected starts over.
        assertEquals(SkinSelection.One("start"), SkinSelect.tap(layout, SkinSelection.One("start"), "start"))
        assertEquals(SkinSelection.One("dpad"), SkinSelect.tap(layout, SkinSelection.One("dpad"), "dpad"))
        val plain = layout.copy(elements = listOf(dpad, a, b.copy(label = " "), start))
        assertEquals(face, SkinSelect.tap(plain, SkinSelection.One("b"), "b"))
        // The frame, the body and the screen area are no step into a group.
        listOf(SkinSelection.Frame, SkinSelection.Body, SkinSelection.Screen(listOf("@screen0")), SkinSelection.Screen(emptyList())).forEach {
            assertEquals("$it", face, SkinSelect.tap(layout, it, "a"))
        }
        // Another control's label selected says nothing about this one, nor does a label about a control that is gone.
        assertEquals(SkinSelection.One("start"), SkinSelect.tap(layout, SkinSelection.Label("a"), "start"))
        assertEquals(SkinSelection.One("nope"), SkinSelect.tap(layout, SkinSelection.Label("nope"), "nope"))
    }

    @Test fun aLabelStepsTogglesAndDragsAsItsControlDoes() {
        assertEquals(SkinSelection.One("b"), SkinSelect.step(layout, SkinSelection.Label("a"), 1))
        assertEquals(SkinSelection.One("a"), SkinSelect.step(layout, SkinSelection.Label("b"), -1))
        // A lone control's label: on to the next unit, as from the control.
        assertEquals(SkinSelection.One("dpad"), SkinSelect.step(wordedLayout, SkinSelection.Label("start"), 1))
        assertEquals(face, SkinSelect.step(wordedLayout, SkinSelection.Label("start"), -1))
        assertEquals(face, SkinSelect.toggle(layout, SkinSelection.Label("a")))
        assertEquals(SkinSelection.One("start"), SkinSelect.toggle(wordedLayout, SkinSelection.Label("start")))
        // The frame and the body have nothing to toggle.
        assertSame(SkinSelection.Frame, SkinSelect.toggle(layout, SkinSelection.Frame))
        assertSame(SkinSelection.Body, SkinSelect.toggle(layout, SkinSelection.Body))
        // A drag on the control whose label is selected moves the label; on another, what a first tap would take.
        assertEquals(SkinSelection.Label("a"), SkinSelect.dragged(layout, SkinSelection.Label("a"), "a"))
        assertEquals(face, SkinSelect.dragged(layout, SkinSelection.Label("a"), "b"))
        assertEquals(face, SkinSelect.dragged(layout, SkinSelection.Frame, "a"))
    }

    @Test fun aLabelWithAPlaceOfItsOwnHasARectOfItsOwn() {
        assertEquals(wordBox, wordedLayout.rects()["start#label"])
        // One left on its control's face follows the control and needs none.
        assertEquals(setOf("dpad", "a", "b", "start", "@screen0", "start#label"), wordedLayout.rects().keys)
        // Nor does a place with no label to put there: a key with no word, a D-pad.
        val odd = layout.copy(elements = listOf(dpad.copy(label = "+", labelOffset = SkinPoint(0f, 10f)), start.copy(labelOffset = SkinPoint(0f, 60f))))
        assertTrue(odd.rects().keys.none(SkinArea::isLabel))
        // Put back, the layout is the same.
        assertEquals(wordedLayout, wordedLayout.withRects(wordedLayout.rects()))
    }

    @Test fun rectsPutBackScaleAControlAndPlaceItsLabel() {
        val raised = layout.copy(elements = listOf(a.copy(radius = 8f, depth = 12f)))
        // Twice as wide: its relief and its corners twice as large.
        val big = SkinRect(600f, 1200f, 200f, 200f)
        assertEquals(a.copy(rect = big, radius = 16f, depth = 24f), raised.withRects(mapOf("a" to big)).elements.single())
        // Only moved: as it was.
        assertEquals(a.copy(rect = SkinRect(0f, 0f, 100f, 100f), radius = 8f, depth = 12f), raised.withRects(mapOf("a" to SkinRect(0f, 0f, 100f, 100f))).elements.single())
        // A label's rect: its height is the type size, its centre (700, 1410) is said from the control's (700, 1300).
        val place = SkinRect(660f, 1400f, 80f, 20f)
        assertEquals(a.copy(radius = 8f, depth = 12f, labelSize = 20f, labelOffset = SkinPoint(0f, 110f)), raised.withRects(mapOf("a#label" to place)).elements.single())
        // Both: from the control where it now is, and the label's own size rather than the scaled one.
        val sized = layout.copy(elements = listOf(a.copy(labelSize = 30f, labelOffset = SkinPoint(5f, 5f))))
        assertEquals(a.copy(rect = big, labelSize = 20f, labelOffset = SkinPoint(0f, 110f)), sized.withRects(mapOf("a" to big, "a#label" to place)).elements.single())
        // A label's rect with no control to put it on is nothing.
        assertEquals(raised, raised.withRects(mapOf("ghost#label" to place)))
    }

    @Test fun hitAlsoFindsAControlByTheWordPrintedBesideIt() {
        val m = wordedModel()
        // On the word, under the key and to its left: outside the key itself.
        assertEquals("start", m.hit(395f, 1690f))
        assertNull(m.hit(385f, 1690f))
        assertEquals("start", m.hit(385f, 1690f, slop = 10f))
        // Between the key and its word there is nothing.
        assertNull(m.hit(450f, 1660f))
        // A key with no word has nothing beside it to hit.
        assertNull(model().hit(395f, 1690f))
    }

    @Test fun aThirdTapSelectsTheLabelAndTheBoxFollowsTheSelection() {
        val m = wordedModel()
        assertNull(m.box())
        m.tap("a")
        assertEquals(SkinRect(550f, 1250f, 200f, 200f), m.box())
        m.tap("a")
        assertEquals(a.rect, m.box())
        m.tap("a")
        assertEquals(SkinSelection.Label("a"), m.selection)
        // On its control's face, where it is drawn; nothing is edited by selecting it.
        near(SkinLabels.box(a), m.box())
        assertEquals(100, m.percent())
        assertFalse(m.dirty)
        m.tap("a")
        assertEquals(face, m.selection)
        // A lone key with its word: the box holds both, then the word alone.
        m.tap("start")
        assertEquals(SkinRect(393f, 1600f, 114f, 100f), m.box())
        m.tap("start")
        assertEquals(SkinSelection.Label("start"), m.selection)
        assertEquals(wordBox, m.box())
        assertEquals(100, m.percent())
        // With no layout shown there is no box.
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        assertNull(m.box())
    }

    @Test fun aLabelMovedOnItsOwnGetsARectOfItsOwn() {
        val m = model()
        m.tap("a"); m.tap("a"); m.tap("a")
        m.nudge(1, 0)
        val box = SkinLabels.box(a)
        assertEquals(box.copy(x = box.x + 50f), m.now()["a#label"])
        // The control stays; its label is one grid step to the right of where it was, as large as it was.
        assertEquals(a.rect, m.element("a").rect)
        assertEquals(50f, m.element("a").labelOffset!!.x, 0.01f)
        assertEquals(0f, m.element("a").labelOffset!!.y, 0.01f)
        assertEquals(42f, m.element("a").labelSize!!, 0.01f)
        assertEquals(mapOf(SkinLayoutId.PHONE_PORTRAIT to setOf("a#label")), m.edits().mapValues { it.value.keys })
        // Still the label: the next nudge goes on from there.
        assertEquals(SkinSelection.Label("a"), m.selection)
        m.nudge(0, -1)
        assertEquals(-50f, m.element("a").labelOffset!!.y, 0.01f)
        m.undo(); m.undo()
        assertFalse(m.dirty)
        assertNull(m.element("a").labelOffset)
    }

    @Test fun aDraggedLabelStaysInsideTheLayoutAndSnapsLikeAControl() {
        val m = model()
        m.tap("b"); m.tap("b"); m.tap("b")
        assertTrue(m.beginMove("b"))
        assertEquals(SkinSelection.Label("b"), m.selection)
        m.moveBy(5000f, 5000f, free = true)
        m.endGesture()
        val box = m.now().getValue("b#label")
        assertEquals(900f, box.x + box.width, 0.01f)
        assertEquals(1800f, box.y + box.height, 0.01f)
        assertEquals(b.rect, m.element("b").rect)
        // Far from its control, a touch on it is still on that control.
        assertEquals("b", m.hit(890f, 1780f))
        // Dropped 3 from its own control's centre line (600): the control pulls its label onto it, as it would another control.
        m.beginMove("b")
        m.moveBy(600f - (box.x + box.width / 2f) + 3f, -400f)
        assertTrue("${m.guides}", SnapGuide(SnapAxis.X, 600f, SnapKind.ALIGN, "b") in m.guides)
        m.endGesture()
        assertEquals(0f, m.element("b").labelOffset!!.x, 0.01f)
    }

    @Test fun aLabelIsSizedOnItsOwnAndResetGivesItBackItsPlace() {
        val m = wordedModel()
        m.tap("start"); m.tap("start")
        m.sizeStep(1)
        assertEquals(105, m.percent())
        // About its own centre, 60 under the key's: the key is as it was.
        assertEquals(word.rect, m.element("start").rect)
        assertEquals(31.5f, m.element("start").labelSize!!, 0.01f)
        assertEquals(60f, m.element("start").labelOffset!!.y, 0.01f)
        assertTrue(m.beginResize())
        assertEquals(200, m.resizeBy(2f))
        m.endGesture()
        assertEquals(60f, m.element("start").labelSize!!, 0.01f)
        // Reset: the place and the size the skin gave it.
        m.reset()
        assertFalse(m.dirty)
        assertEquals(wordBox, m.now()["start#label"])
    }

    @Test fun resetPutsALabelBackOnItsControlsFace() {
        val m = model()
        m.tap("a"); m.tap("a"); m.tap("a")
        // Nothing moved yet: nothing to put back, no undo step.
        m.reset()
        assertFalse(m.canUndo)
        m.nudge(1, 1)
        assertTrue("a#label" in m.now())
        // The skin gave it no place of its own: it has none again.
        m.reset()
        assertFalse(m.dirty)
        assertFalse("a#label" in m.now())
        assertEquals(SkinSelection.Label("a"), m.selection)
        // Its group's reset takes the label back too, and so does the layout's.
        m.nudge(1, 1)
        m.tap("a")
        assertEquals(face, m.selection)
        m.reset()
        assertFalse(m.dirty)
        m.tap("a"); m.tap("a")
        m.nudge(1, 1)
        m.clearSelection()
        m.resetLayout()
        assertFalse(m.dirty)
    }

    @Test fun aControlMovedTakesItsWordWithIt() {
        val m = wordedModel()
        // A drag on the lone key: the key and its word together.
        assertTrue(m.beginMove("start"))
        assertEquals(SkinSelection.One("start"), m.selection)
        m.moveBy(0f, -100f, free = true)
        m.endGesture()
        assertEquals(SkinRect(400f, 1500f, 100f, 50f), m.now()["start"])
        assertEquals(wordBox.copy(y = 1570f), m.now()["start#label"])
        // The word is where it was from its key.
        assertEquals(word.copy(rect = SkinRect(400f, 1500f, 100f, 50f)), m.element("start"))
        // Against the layout's bottom edge it is the word that stops, and the key with it.
        m.beginMove("start")
        m.moveBy(0f, 5000f, free = true)
        m.endGesture()
        assertEquals(1700f, m.now().getValue("start").y, 0f)
        assertEquals(1770f, m.now().getValue("start#label").y, 0f)
        // Twice the size: the word twice as large, about the middle of the two.
        m.undo(); m.undo()
        m.beginResize()
        assertEquals(200, m.resizeBy(2f))
        m.endGesture()
        assertEquals(60f, m.element("start").labelSize!!, 0.01f)
        assertEquals(120f, m.element("start").labelOffset!!.y, 0.01f)
    }

    @Test fun aGroupMovedTakesItsMembersWordsWithIt() {
        val select = SkinElement("select", SkinElementType.BUTTON, "select", SkinRect(250f, 1600f, 100f, 50f), group = "center")
        val centre = layout.copy(elements = listOf(a, select, word.copy(group = "center")))
        val m = SkinEditorModel(OpenEmuxSkin(Console.SFC, "Mine", mapOf(SkinLayoutId.PHONE_PORTRAIT to centre))).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        m.tap("select")
        assertEquals(SkinSelection.Group("center", listOf("select", "start")), m.selection)
        // The group's box holds the word under Start.
        assertEquals(SkinRect(250f, 1600f, 257f, 100f), m.box())
        m.nudge(0, -1)
        assertEquals(setOf("select", "start", "start#label"), m.edits().getValue(SkinLayoutId.PHONE_PORTRAIT).keys)
        assertEquals(wordBox.copy(y = 1620f), m.now()["start#label"])
        assertEquals(word.copy(group = "center", rect = SkinRect(400f, 1550f, 100f, 50f)), m.element("start"))
        m.reset()
        assertFalse(m.dirty)
    }

    @Test fun controlsDoNotSnapToALabel() {
        val m = wordedModel()
        m.tap("a"); m.tap("a")
        m.beginMove("a")
        // A's top edge 3 from the word's (1670) and out of the pull of every control's lines: the grid, no guide.
        m.moveBy(0f, 423f)
        assertTrue("${m.guides}", m.guides.none { it.axis == SnapAxis.Y })
        assertEquals(1675f, m.element("a").rect.y, 0f)
        m.cancelGesture()
    }

    @Test fun aGestureSaysWhetherItBegan() {
        val m = model()
        // Nothing selected: no size to change.
        assertFalse(m.beginResize())
        assertTrue(m.beginMove("a"))
        assertTrue(m.beginResize())
        m.cancelGesture()
        m.show(SkinForm.TABLET, SkinOrientation.PORTRAIT)
        assertFalse(m.beginMove("a"))
        assertFalse(m.beginResize())
        assertFalse(m.fixed("a"))
    }

    @Test fun aDraftsLabelPlacesAreKeptForTheLabelsTheSkinHas() {
        val place = SkinRect(660f, 1400f, 80f, 20f)
        val m = model(mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("a#label" to place, "start#label" to place, "dpad#label" to place, "ghost#label" to place)))
        // Start has no label and the D-pad's arrows are not one: only A's place is a label's.
        assertEquals(setOf("dpad", "a", "b", "start", "@screen0", "a#label"), m.now().keys)
        assertTrue(m.dirty)
        assertEquals(20f, m.element("a").labelSize)
        assertEquals(SkinPoint(0f, 110f), m.element("a").labelOffset)
        // Written to the draft's skin.json and resumed from it, as the editor does: the label is where it was left.
        val back = SkinEditorModel.fromDraft(skin, SkinFormat.parse(m.json(SkinFormat.toJson(skin)))).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        assertTrue("a#label" in back.now())
        assertEquals(20f, back.element("a").labelSize)
        assertEquals(0f, back.element("a").labelOffset!!.x, 0.01f)
        assertEquals(110f, back.element("a").labelOffset!!.y, 0.01f)
        assertTrue(back.dirty)
    }

    @Test fun theSessionWritesAMovedLabelIntoSkinJson() {
        val m = model()
        m.tap("a"); m.tap("a"); m.tap("a")
        m.nudge(1, 2)
        val read = SkinFormat.parse(m.json(SkinFormat.toJson(skin))).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.first { it.id == "a" }
        // Its control where it was; the label's size and its place from the control's centre, to a tenth of a unit.
        assertEquals(a.copy(labelSize = 42f, labelOffset = SkinPoint(50f, 100f)), read)
    }

    @Test fun aResizedControlIsWrittenWithItsProportions() {
        val shaped = skin.copy(layouts = mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(elements = listOf(a.copy(group = null, radius = 8f, depth = 12f), word))))
        val m = SkinEditorModel(shaped).also { it.show(SkinForm.PHONE, SkinOrientation.PORTRAIT) }
        m.tap("a")
        m.sizeStep(1)
        assertEquals(12.6f, m.element("a").depth, 0.01f)
        assertEquals(8.4f, m.element("a").radius!!, 0.01f)
        m.tap("start")
        m.sizeStep(-1); m.sizeStep(-1)
        val read = SkinFormat.parse(m.json(SkinFormat.toJson(shaped))).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.associateBy { it.id }
        // What the file says is what the editor showed, to the tenth of a unit it is written in.
        assertEquals(105f, read.getValue("a").rect.width, 0f)
        assertEquals(12.6f, read.getValue("a").depth, 0f)
        assertEquals(8.4f, read.getValue("a").radius)
        assertEquals(90f, read.getValue("start").rect.width, 0f)
        assertEquals(27f, read.getValue("start").labelSize)
        assertEquals(m.element("start").labelOffset!!.y, read.getValue("start").labelOffset!!.y, 0.5f)
    }

    @Test fun patchScalesWhatFollowsAControlsWidth() {
        val shaped = skin.copy(layouts = mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(elements = listOf(
            a.copy(radius = 8f, depth = 12f, labelSize = 30f, labelOffset = SkinPoint(4f, -66f)), b.copy(depth = 12.25f, radius = 0.25f), start,
        ))))
        val json = SkinFormat.toJson(shaped)
        fun elements(vararg edits: Pair<String, SkinRect>) =
            JSONObject(SkinEditJson.patch(json, mapOf(SkinLayoutId.PHONE_PORTRAIT to edits.toMap()))).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements")
        val e = elements("a" to SkinRect(600f, 1200f, 105f, 105f), "b" to SkinRect(0f, 0f, 100f, 100f), "start" to SkinRect(400f, 1500f, 200f, 100f))
        // 105 % of its width: each to a tenth of a unit.
        val ea = e.getJSONObject(0)
        assertEquals(8.4, ea.getDouble("radius"), 1e-9)
        assertEquals(12.6, ea.getDouble("depth"), 1e-9)
        assertEquals(31.5, ea.getDouble("labelSize"), 1e-9)
        assertEquals(4.2, ea.getJSONObject("labelOffset").getDouble("x"), 1e-9)
        assertEquals(-69.3, ea.getJSONObject("labelOffset").getDouble("y"), 1e-9)
        // Only moved: nothing is touched, not even rounded.
        assertEquals(12.25, e.getJSONObject(1).getDouble("depth"), 0.0)
        assertEquals(0.25, e.getJSONObject(1).getDouble("radius"), 0.0)
        // A control that sets none of them gets none, however it is resized.
        val es = e.getJSONObject(2)
        assertEquals(200, es.getInt("width"))
        assertFalse(es.has("radius") || es.has("depth") || es.has("labelSize") || es.has("labelOffset"))
        // Twice the width: whole numbers, written as whole numbers.
        val twice = SkinEditJson.patch(json, mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("a" to SkinRect(600f, 1200f, 200f, 200f))))
        listOf("\"radius\": 16", "\"depth\": 24", "\"labelSize\": 60", "\"x\": 8", "\"y\": -132").forEach { assertTrue(it, Regex(Regex.escape(it) + "[,\n]").containsMatchIn(twice)) }
        // It still reads as the skin it was, resized.
        val read = SkinFormat.parse(twice).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0]
        assertEquals(shaped.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0].at(SkinRect(600f, 1200f, 200f, 200f)), read)
    }

    @Test fun patchLeavesAloneWhatItCannotScale() {
        // Hand-written: a label's place with only one of its two numbers, and a control with no width to scale from.
        val text = JSONObject(SkinFormat.toJson(skin)).apply {
            val elements = getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements")
            elements.getJSONObject(1).put("depth", 10).put("labelOffset", JSONObject().put("y", 10))
            elements.getJSONObject(2).put("depth", 10).remove("width")
            elements.getJSONObject(3).put("depth", 10).put("width", 0)
        }.toString()
        val rects = mapOf("a" to SkinRect(600f, 1200f, 200f, 200f), "b" to SkinRect(0f, 0f, 200f, 200f), "start" to SkinRect(0f, 0f, 200f, 100f))
        val e = JSONObject(SkinEditJson.patch(text, mapOf(SkinLayoutId.PHONE_PORTRAIT to rects))).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements")
        assertEquals(20, e.getJSONObject(1).getInt("depth"))
        assertEquals(0, e.getJSONObject(1).getJSONObject("labelOffset").getInt("x"))
        assertEquals(20, e.getJSONObject(1).getJSONObject("labelOffset").getInt("y"))
        // The rect is written; the relief keeps the depth it had.
        assertEquals(200, e.getJSONObject(2).getInt("width"))
        assertEquals(10, e.getJSONObject(2).getInt("depth"))
        assertEquals(200, e.getJSONObject(3).getInt("width"))
        assertEquals(10, e.getJSONObject(3).getInt("depth"))
    }

    @Test fun patchWritesALabelsRectAsItsSizeAndItsPlaceFromTheControl() {
        val json = SkinFormat.toJson(skin)
        fun start(vararg edits: Pair<String, SkinRect>) = JSONObject(SkinEditJson.patch(json, mapOf(SkinLayoutId.PHONE_PORTRAIT to edits.toMap())))
            .getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").getJSONObject(3)
        // The word under the key: its height is the type size, its centre is said from the key's, as the file has it.
        val under = start("start#label" to wordBox)
        assertEquals(30, under.getInt("labelSize"))
        assertEquals(0, under.getJSONObject("labelOffset").getInt("x"))
        assertEquals(60, under.getJSONObject("labelOffset").getInt("y"))
        assertEquals(1600, under.getInt("y"))
        // The key moved in the same edit: from where it now is.
        val both = start("start" to SkinRect(400f, 1500f, 100f, 50f), "start#label" to wordBox)
        assertEquals(160, both.getJSONObject("labelOffset").getInt("y"))
        assertEquals(1500, both.getInt("y"))
        // To a tenth of a unit.
        val fine = start("start#label" to SkinRect(393.04f, 1670.26f, 114f, 30.44f))
        assertEquals(30.4, fine.getDouble("labelSize"), 1e-9)
        assertEquals(0.0, fine.getJSONObject("labelOffset").getDouble("x"), 1e-9)
        assertEquals(60.5, fine.getJSONObject("labelOffset").getDouble("y"), 1e-9)
        // A label of a control the file does not have is skipped, like the control.
        val none = SkinEditJson.patch(json, mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("ghost#label" to wordBox)))
        assertFalse("labelSize" in none || "labelOffset" in none)
        // What is written reads as the layout the editor showed.
        val read = SkinFormat.parse(SkinEditJson.patch(SkinFormat.toJson(worded), mapOf(SkinLayoutId.PHONE_PORTRAIT to mapOf("start#label" to wordBox.copy(x = 293f, y = 1700f)))))
        assertEquals(word.copy(labelOffset = SkinPoint(-100f, 90f)), read.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[3])
    }
}
