package com.openemux.mobile.data

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The combo buttons' rules (issue 842): what a combo may press, its label, its id and where it starts. */
class SkinComboTest {
    private val size = SkinSize(900, 1800)
    private val screen = SkinRect(0f, 0f, 900f, 700f)
    private val dpad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1200f, 200f, 200f))
    private val y = SkinElement("y", SkinElementType.BUTTON, "y", SkinRect(550f, 1250f, 100f, 100f), label = "Y", depth = 10f)
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(650f, 1350f, 100f, 100f), label = "B", depth = 10f)
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(750f, 1250f, 120f, 120f), label = "A", depth = 12f)
    private val x = SkinElement("x", SkinElementType.BUTTON, "x", SkinRect(650f, 1150f, 100f, 100f), label = "X")
    private val l = SkinElement("l", SkinElementType.BUTTON, "l", SkinRect(20f, 720f, 200f, 60f), label = "L")
    private val r = SkinElement("r", SkinElementType.BUTTON, "r", SkinRect(680f, 720f, 200f, 60f), label = "R")
    private val start = SkinElement("start", SkinElementType.BUTTON, "start", SkinRect(400f, 1600f, 100f, 50f))
    private val menu = SkinElement("menu", SkinElementType.BUTTON, "menu", SkinRect(400f, 1700f, 60f, 60f))
    private val save = SkinElement("save", SkinElementType.ACTION, "quickSave", SkinRect(10f, 10f, 60f, 60f))
    private val layout = SkinLayout(size, listOf(screen), elements = listOf(dpad, y, b, a, x, l, r, start, menu, save))

    @Test fun aComboPressesTwoOrThreeDifferentButtons() {
        assertTrue(SkinCombos.valid(listOf("y", "b")))
        assertTrue(SkinCombos.valid(listOf("l", "r", "a")))
        assertFalse(SkinCombos.valid(listOf("y")))
        assertFalse(SkinCombos.valid(listOf("l", "r", "a", "b")))
        assertFalse(SkinCombos.valid(listOf("y", "y")))
        // The D-pad, sticks, quick actions, the menu key and the keypad are never in a combo.
        listOf("up", "dpad", "analogStick", "quickSave", "menu", "keypad1", "l3", "disk").forEach {
            assertFalse(it, SkinCombos.valid(listOf("a", it)))
        }
    }

    @Test fun theBindJoinsTheInputsAndSplitsBack() {
        assertEquals("y|b", SkinCombos.bind(listOf("y", "b")))
        assertEquals(listOf("l", "r", "a"), SkinCombos.buttons("l|r|a"))
        assertTrue(y.copy(bind = "y|b").isCombo)
        assertFalse(y.isCombo)
    }

    @Test fun onlyTheLayoutsButtonsAComboMayPressAreOffered() {
        assertEquals(listOf("y", "b", "a", "x", "l", "r", "start"), SkinCombos.eligible(layout).map { it.bind })
        assertTrue(SkinCombos.offered(layout))
        assertFalse(SkinCombos.offered(layout.copy(elements = listOf(dpad, a, menu, save))))
        // One input twice is one button to offer.
        assertFalse(SkinCombos.offered(layout.copy(elements = listOf(a, a.copy(id = "a2")))))
    }

    @Test fun theButtonsReadLeftToRightThenTopToBottom() {
        assertEquals(listOf("y", "b"), SkinCombos.ordered(setOf("b", "y"), layout))
        assertEquals(listOf("l", "r", "a"), SkinCombos.ordered(listOf("a", "r", "l"), layout))
        assertEquals(listOf("x", "b"), SkinCombos.ordered(listOf("b", "x"), layout))
        // A button the layout lacks goes last.
        assertEquals(listOf("y", "c"), SkinCombos.ordered(listOf("c", "y"), layout))
    }

    @Test fun theLabelIsTheSkinsNamesJoinedWithPlus() {
        assertEquals("Y+B", SkinCombos.label(listOf("y", "b"), listOf(layout)))
        // No label in the skin: the input name, upper-cased.
        assertEquals("Start+A", SkinCombos.label(listOf("start", "a"), listOf(layout)))
        // A combo's own label is never a name.
        val combo = y.copy(id = "combo-1", bind = "x", label = "Q")
        assertEquals("X", SkinCombos.label(listOf("x"), listOf(SkinLayout(size, listOf(screen), elements = listOf(combo.copy(bind = "x|y"))))))
        val symbols = layout.copy(elements = listOf(x.copy(label = "□"), b.copy(label = "✕")))
        assertEquals("□+✕", SkinCombos.label(listOf("x", "b"), listOf(symbols)))
    }

    @Test fun theCombosOfALayoutAreReadOnceInOrder() {
        val c1 = y.copy(id = "combo-1", bind = "y|b")
        val c2 = y.copy(id = "combo-2", bind = "l|r")
        val one = layout.copy(elements = layout.elements + c1 + c2)
        val other = layout.copy(elements = layout.elements + c2)
        assertEquals(listOf(SkinCombo("combo-1", listOf("y", "b")), SkinCombo("combo-2", listOf("l", "r"))), SkinCombos.of(listOf(one, other)))
    }

    @Test fun theNextIdIsTheFirstFreeOne() {
        assertEquals("combo-1", SkinCombos.nextId(listOf(layout)))
        val used = layout.copy(elements = layout.elements + y.copy(id = "combo-1") + y.copy(id = "combo-3"))
        assertEquals("combo-2", SkinCombos.nextId(listOf(layout, used)))
    }

    @Test fun aNewComboTakesTheWidestFaceButtonsSize() {
        assertEquals(a, SkinCombos.template(layout))
        assertEquals(120f, SkinCombos.diameter(layout, a))
        // No face button: the widest button a combo may press; none at all: an eighth of the short side.
        assertEquals(l, SkinCombos.template(layout.copy(elements = listOf(l, start))))
        assertNull(SkinCombos.template(layout.copy(elements = listOf(dpad))))
        assertEquals(900f / 8f, SkinCombos.diameter(layout, null))
    }

    @Test fun aNewComboIsRoundGreyAndAsDeepForItsSizeAsItsTemplate() {
        val e = SkinCombos.element(SkinCombo("combo-1", listOf("y", "b")), "Y+B", SkinRect(0f, 0f, 60f, 60f), a)
        assertEquals("combo-1", e.id)
        assertEquals(SkinElementType.BUTTON, e.type)
        assertEquals("y|b", e.bind)
        assertEquals(SkinShape.CIRCLE, e.shape)
        assertEquals("Y+B", e.label)
        assertEquals(6f, e.depth, 0.001f)
        assertEquals(SkinCombos.BUTTON, e.colors["button"])
        assertEquals(SkinCombos.RELIEF, e.colors["relief"])
        assertEquals(SkinCombos.LETTER, e.colors["letter"])
        assertEquals(0f, SkinCombos.element(SkinCombo("c", listOf("a", "b")), "", SkinRect(0f, 0f, 60f, 60f), null).depth)
        // Never a relief deeper than the face allows.
        val deep = a.copy(depth = 100f)
        assertEquals(60f * (1f - SkinElement.MIN_FACE), SkinCombos.element(SkinCombo("c", listOf("a", "b")), "", SkinRect(0f, 0f, 60f, 60f), deep).depth)
    }

    private fun clear(r: SkinRect, of: List<SkinRect>, margin: Float) = of.none {
        it.x < r.x + r.width + margin && r.x - margin < it.x + it.width && it.y < r.y + r.height + margin && r.y - margin < it.y + it.height
    }

    @Test fun theStartingSpotIsNearTheButtonsAndCoversNothing() {
        val spot = SkinCombos.start(layout, listOf("y", "b"), 100f)
        assertEquals(100f, spot.width)
        assertEquals(100f, spot.height)
        assertTrue(spot.inside(size))
        assertTrue(clear(spot, layout.screens + layout.elements.map { it.rect }, 10f))
        // Near the middle of Y and B (650, 1350): within two sides of it.
        val cx = spot.x + 50f
        val cy = spot.y + 50f
        assertTrue("$spot", kotlin.math.hypot(cx - 650f, cy - 1350f) < 250f)
    }

    @Test fun theStartingSpotKeepsOffTheFrame() {
        val framed = layout.copy(frame = SkinFrame(SkinRect(0f, 0f, 900f, 1000f)))
        val spot = SkinCombos.start(framed, listOf("l", "r"), 100f)
        assertTrue(spot.y >= 1000f + 10f)
    }

    @Test fun withNoneOfItsButtonsTheComboStartsNearTheMiddle() {
        val empty = SkinLayout(size, listOf(screen))
        val spot = SkinCombos.start(empty, listOf("y", "b"), 100f)
        assertTrue(spot.inside(size))
        assertTrue(kotlin.math.hypot(spot.x + 50f - 450f, spot.y + 50f - 900f) < 50f)
    }

    @Test fun withNoClearSpotTheComboStartsOnItsButtonsInsideTheLayout() {
        val full = SkinLayout(SkinSize(300, 300), listOf(SkinRect(0f, 0f, 300f, 300f)), elements = listOf(y.copy(rect = SkinRect(250f, 250f, 50f, 50f))))
        assertEquals(SkinRect(200f, 200f, 100f, 100f), SkinCombos.start(full, listOf("y"), 100f))
        // A side larger than the layout is cut to it.
        assertEquals(SkinRect(0f, 0f, 300f, 300f), SkinCombos.start(full, listOf("y"), 500f))
    }

    @Test fun theHoldTakesTheButtonsAfterASecondUnchanged() {
        val c = ComboCapture()
        assertEquals(0f, c.progress(0L))
        c.press(setOf("y"), 0L)
        // One button is no combo: the ring stays empty.
        assertEquals(0f, c.progress(2000L))
        c.press(setOf("y", "b"), 100L)
        assertEquals(0.5f, c.progress(600L), 0.001f)
        // The same buttons again do not restart it.
        c.press(setOf("y", "b"), 700L)
        assertEquals(0.8f, c.progress(900L), 0.001f)
        // Others do.
        c.press(setOf("y", "a"), 1000L)
        assertEquals(0f, c.progress(1000L), 0.001f)
        assertNull(c.done)
        assertEquals(1f, c.progress(2000L))
        assertEquals(setOf("y", "a"), c.done)
        // Once taken, nothing changes it.
        c.press(setOf("l", "r"), 2100L)
        assertEquals(setOf("y", "a"), c.pressed)
        assertEquals(1f, c.progress(2100L))
    }

    @Test fun fourButtonsHeldNeverMakeACombo() {
        val c = ComboCapture()
        c.press(setOf("y", "b", "a", "x"), 0L)
        assertTrue(c.tooMany)
        assertEquals(0f, c.progress(5000L))
        assertNull(c.done)
        c.press(setOf("y", "b", "a"), 5000L)
        assertFalse(c.tooMany)
        assertEquals(1f, c.progress(6000L))
    }

    @Test fun pickingTogglesUpToThreeButtons() {
        val p = ComboPick()
        assertFalse(p.ready)
        assertEquals(ComboPickResult.NOT_A_BUTTON, p.tap("dpad", eligible = false))
        assertEquals(ComboPickResult.ADDED, p.tap("l", eligible = true))
        assertFalse(p.ready)
        assertEquals(ComboPickResult.ADDED, p.tap("r", eligible = true))
        assertTrue(p.ready)
        assertEquals(ComboPickResult.ADDED, p.tap("a", eligible = true))
        assertEquals(ComboPickResult.TOO_MANY, p.tap("b", eligible = true))
        assertEquals(listOf("l", "r", "a"), p.picked)
        assertEquals(ComboPickResult.REMOVED, p.tap("r", eligible = true))
        assertEquals(listOf("l", "a"), p.picked)
    }

    @Test fun aControllersButtonsAreTheConsolesInputs() {
        val snes = listOf("y", "b", "a", "x", "l", "r", "start", "select")
        assertTrue(PadCombo.reads(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(PadCombo.reads(KeyEvent.KEYCODE_DPAD_UP))
        // The bottom button is the RetroPad's B, the right one A, the left one Y, the top one X.
        assertEquals("b", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_A, snes))
        assertEquals("a", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_B, snes))
        assertEquals("y", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_X, snes))
        assertEquals("x", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_Y, snes))
        assertEquals("l", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_L1, snes))
        assertEquals("start", PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_START, snes))
        // A console naming the RetroPad's buttons its own way: the Mega Drive's C is the RetroPad's A.
        assertEquals("c", PadCombo.input(Console.MD, KeyEvent.KEYCODE_BUTTON_B, listOf("a", "b", "c", "start")))
        // A button the layout has no input for, or one the capture does not read.
        assertNull(PadCombo.input(Console.SFC, KeyEvent.KEYCODE_BUTTON_L2, snes))
        assertNull(PadCombo.input(Console.SFC, KeyEvent.KEYCODE_DPAD_UP, snes))
    }
}
