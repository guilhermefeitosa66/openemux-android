package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinKeypadPage
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The anchor key (issue 841): where the renderers put it, after fast forward, and what it presses. */
class AnchorSlotTest {

    private fun assertBox(expected: SkinBox, actual: SkinBox?) {
        requireNotNull(actual)
        listOf(expected.x to actual.x, expected.y to actual.y, expected.w to actual.w, expected.h to actual.h)
            .forEach { (e, a) -> assertEquals("$expected vs $actual", e, a, 0.01f) }
    }

    private fun overlap(a: SkinBox, b: SkinBox) =
        a.x < b.right - 0.01f && b.x < a.right - 0.01f && a.y < b.bottom - 0.01f && b.y < a.bottom - 0.01f

    // Save, load, turbo, fast forward, edge to edge; a D-pad and a button elsewhere.
    private val strip = listOf(
        null,
        SkinBox(6f, 264f, 87f, 30f), SkinBox(93f, 264f, 87f, 30f), SkinBox(180f, 264f, 87f, 30f), SkinBox(267f, 264f, 87f, 30f),
        SkinBox(280f, 500f, 60f, 60f),
    )

    @Test fun afterTakesTheCellRightOfTheKey() {
        val layout = insertSlot(strip, 3, after = true)
        val cell = 348f / 5f
        assertBox(SkinBox(6f + cell * 3, 264f, cell, 30f), layout.slowMotion)
        assertBox(SkinBox(6f + cell * 2, 264f, cell, 30f), layout.moved[3])
        assertBox(SkinBox(6f + cell * 4, 264f, cell, 30f), layout.moved[4])
        assertFalse(5 in layout.moved)
    }

    @Test fun beforeIsWhatSlowMotionAlwaysDid() {
        val before = insertSlot(strip, 4, after = false)
        val slow = slowMotionLayout(strip, 4)
        assertEquals(before.slowMotion, slow.slowMotion)
        assertEquals(before.moved, slow.moved)
    }

    @Test fun aLoneKeyIsSplitWithTheNewKeyOnItsSide() {
        val wide = listOf(SkinBox(0f, 0f, 100f, 40f))
        insertSlot(wide, 0, after = true).let {
            assertBox(SkinBox(50f, 0f, 50f, 40f), it.slowMotion)
            assertBox(SkinBox(0f, 0f, 50f, 40f), it.moved[0])
        }
        insertSlot(wide, 0, after = false).let {
            assertBox(SkinBox(0f, 0f, 50f, 40f), it.slowMotion)
            assertBox(SkinBox(50f, 0f, 50f, 40f), it.moved[0])
        }
        val tall = listOf(SkinBox(0f, 0f, 40f, 100f))
        insertSlot(tall, 0, after = true).let {
            assertBox(SkinBox(0f, 50f, 40f, 50f), it.slowMotion)
            assertBox(SkinBox(0f, 0f, 40f, 50f), it.moved[0])
        }
        insertSlot(tall, 0, after = false).let {
            assertBox(SkinBox(0f, 0f, 40f, 50f), it.slowMotion)
            assertBox(SkinBox(0f, 50f, 40f, 50f), it.moved[0])
        }
    }

    @Test fun theSpeedRowHasSlowMotionBeforeAndAnchorAfterFastForwardInTheSameSpan() {
        val row = speedRow(strip, 4, slowMotion = true)
        val cell = 348f / 6f
        assertBox(SkinBox(6f + cell * 3, 264f, cell, 30f), row.slowMotion)
        assertBox(SkinBox(6f + cell * 4, 264f, cell, 30f), row.moved[4])
        assertBox(SkinBox(6f + cell * 5, 264f, cell, 30f), row.anchor)
        assertBox(SkinBox(6f, 264f, cell, 30f), row.moved[1])
        assertEquals(setOf(1, 2, 3, 4), row.moved.keys)
        val boxes = row.moved.values + row.slowMotion!! + row.anchor
        boxes.forEach { a -> boxes.filter { it !== a }.forEach { b -> assertFalse("$a over $b", overlap(a, b)) } }
    }

    @Test fun aLoneFastForwardMakesThreeCells() {
        val row = speedRow(listOf(SkinBox(0f, 0f, 90f, 30f)), 0, slowMotion = true)
        assertBox(SkinBox(0f, 0f, 30f, 30f), row.slowMotion)
        assertBox(SkinBox(30f, 0f, 30f, 30f), row.moved[0])
        assertBox(SkinBox(60f, 0f, 30f, 30f), row.anchor)
    }

    @Test fun aTallLoneFastForwardKeepsTheAnchorTouchOffSlowMotion() {
        // Slow motion takes the top half; fast forward and the anchor share the bottom half side by side.
        val row = speedRow(listOf(SkinBox(0f, 0f, 40f, 60f)), 0, slowMotion = true)
        val slow = row.slowMotion!!
        assertTrue(row.anchor.y >= slow.bottom - 0.01f)
        val grown = { b: SkinBox -> SkinBox(b.x - 10f, b.y - 10f, b.w + 20f, b.h + 20f) }
        val (slowTouch, anchorTouch) = speedTouches(slow, row.anchor, grown(slow), grown(row.anchor))
        assertEquals(slow.bottom, anchorTouch.y, 0.01f)
        assertFalse(overlap(slowTouch, anchorTouch))
    }

    @Test fun withoutSlowMotionOnlyTheAnchorIsAdded() {
        val row = speedRow(strip, 4, slowMotion = false)
        assertNull(row.slowMotion)
        assertBox(SkinBox(6f + 348f / 5f * 4, 264f, 348f / 5f, 30f), row.anchor)
    }

    @Test fun theAnchorAndFastForwardTouchesStopAtTheirSharedEdge() {
        val row = speedRow(strip, 4, slowMotion = true)
        val ff = row.moved.getValue(4)
        val grown = { b: SkinBox -> SkinBox(b.x - 10f, b.y - 10f, b.w + 20f, b.h + 20f) }
        val (ffTouch, anchorTouch) = speedTouches(ff, row.anchor, grown(ff), grown(row.anchor))
        assertEquals(row.anchor.x, ffTouch.right, 0.01f)
        assertEquals(ff.right, anchorTouch.x, 0.01f)
        assertFalse(overlap(ffTouch, anchorTouch))
    }

    @Test fun theStripCarriesTheAnchorOnlyWhenAsked() {
        assertFalse(ANCHOR_ACTION in stripActions(layoutKey = true))
        val withAnchor = stripActions(layoutKey = true, anchor = true)
        assertEquals(listOf("toggle_fast_forward", ANCHOR_ACTION, "screens"), withAnchor.takeLast(3))
        assertEquals(stripActions(layoutKey = false) + ANCHOR_ACTION, stripActions(layoutKey = false, anchor = true))
        assertTrue(stripActionStroked(ANCHOR_ACTION))
        assertFalse(stripActionOff(ANCHOR_ACTION, hardcore = true))
    }

    @Test fun theAnchorKeyIsWrittenAsANulToggleLitByItsState() {
        assertEquals("anchor", OverlayWriter.LIT_BY[ANCHOR_ACTION])
        assertEquals("nul", OverlayWriter.bindOf(ANCHOR_ACTION))
        assertEquals("toggle_fast_forward", OverlayWriter.bindOf("toggle_fast_forward"))
    }

    @Test fun theAnchorActionIsNeverDimmed() {
        listOf(false, true).forEach { hardcore ->
            val action = OpenEmuxBinds.action(Console.SFC, OpenEmuxBinds.ANCHOR, hardcore)!!
            assertEquals(ANCHOR_ACTION, action.bind)
            assertEquals(ANCHOR_ACTION, action.icon)
            assertEquals("anchor", action.lit)
            assertFalse(action.dim)
        }
    }

    private fun element(id: String, bind: String, x: Float, w: Float = 60f, y: Float = 1000f, h: Float = 40f, type: SkinElementType = SkinElementType.ACTION) =
        SkinElement(id, type, bind, SkinRect(x, y, w, h))

    private val base = SkinLayout(SkinSize(1000, 2000), listOf(SkinRect(0f, 100f, 1000f, 700f)))

    @Test fun theGameLayoutGetsTheAnchorRightAfterFastForward() {
        val elements = listOf(
            element("save", "quickSave", 100f), element("load", "quickLoad", 160f), element("turbo", "turbo", 220f),
            element("slow", "slowMotion", 280f), element("ff", "fastForward", 340f), element("menu", "menu", 600f),
            SkinElement("pad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1300f, 300f, 300f)),
        )
        val drawn = withAnchor(base.copy(elements = elements))
        assertEquals(elements.size + 1, drawn.elements.size)
        val anchor = drawn.elements.last()
        assertEquals(ANCHOR_ELEMENT, anchor.id)
        assertEquals(OpenEmuxBinds.ANCHOR, anchor.bind)
        assertEquals(SkinElementType.ACTION, anchor.type)
        assertNull(anchor.image)
        // Six cells over the five keys' span (100..400), each key at its proportions (60 x 40 scaled to fit 50 wide).
        val cell = 300f / 6f
        val ff = drawn.elements[4]
        assertEquals(100f + cell * 4, ff.rect.x, 0.01f)
        assertEquals(cell, ff.rect.width, 0.01f)
        assertEquals(40f * cell / 60f, ff.rect.height, 0.01f)
        assertEquals(1000f + (40f - ff.rect.height) / 2f, ff.rect.y, 0.01f)
        assertEquals(100f + cell * 5, anchor.rect.x, 0.01f)
        assertEquals(ff.rect.height, anchor.rect.height, 0.01f)
        // The menu key, apart from the strip, and the D-pad stay where they were.
        assertSame(elements[5], drawn.elements[5])
        assertSame(elements[6], drawn.elements[6])
        // Every element of the drawn layout is reachable by its index, as planPage needs.
        val plan = planPage(Console.SFC, drawn, 1000, 2000, hardcore = true)
        val planned = plan.elements.last() as ElementPlan.Action
        assertEquals(drawn.elements.size - 1, planned.index)
        assertEquals(ANCHOR_ACTION, planned.bind)
        assertEquals("anchor", planned.lit)
    }

    @Test fun aButtonFastForwardGivesAnAnchorButtonWithoutItsLabel() {
        val ff = SkinElement("ff", SkinElementType.BUTTON, "toggleFastForward", SkinRect(400f, 1000f, 100f, 100f), label = "FF")
        val drawn = withAnchor(base.copy(elements = listOf(ff, element("hold", "fastForward", 700f))))
        val anchor = drawn.elements.last()
        assertEquals(SkinElementType.BUTTON, anchor.type)
        assertNull(anchor.label)
        // The toggle wins over the hold key: the anchor splits it.
        assertEquals(450f, anchor.rect.x, 0.01f)
        assertEquals(50f, anchor.rect.width, 0.01f)
        assertEquals(drawn.elements[1], element("hold", "fastForward", 700f))
    }

    @Test fun theKeypadPageGetsTheAnchorToo() {
        val strip = listOf(element("save", "quickSave", 100f), element("ff", "toggleFastForward", 160f))
        val layout = base.copy(elements = strip, keypad = SkinKeypadPage(strip + element("k1", "keypad1", 500f)))
        val drawn = withAnchor(layout)
        assertEquals(ANCHOR_ELEMENT, drawn.elements.last().id)
        val keypad = drawn.keypad!!.elements
        assertEquals(ANCHOR_ELEMENT, keypad.last().id)
        // The same strip on both pages: the anchor key and fast forward do not move when the keypad opens.
        assertEquals(drawn.elements.last().rect, keypad.last().rect)
        assertEquals(drawn.elements[1].rect, keypad[1].rect)
        // A keypad page with no fast forward is left as it is.
        val bare = base.copy(elements = strip, keypad = SkinKeypadPage(listOf(element("k1", "keypad1", 500f))))
        assertSame(bare.keypad, withAnchor(bare).keypad)
    }

    @Test fun aLayoutWithNoFastForwardIsLeftAsItIs() {
        val layout = base.copy(elements = listOf(element("save", "quickSave", 100f)))
        assertSame(layout, withAnchor(layout))
        // A D-pad bound like an action name is not a key.
        val odd = base.copy(elements = listOf(SkinElement("p", SkinElementType.DPAD, "fastForward", SkinRect(0f, 0f, 10f, 10f))))
        assertSame(odd, withAnchor(odd))
    }

    @Test fun aKeyIsScaledDownToItsCellNeverUp() {
        assertEquals(SkinRect(10f, 20f, 30f, 40f), fitted(SkinRect(0f, 0f, 30f, 40f), SkinBox(10f, 20f, 30f, 40f)))
        assertEquals(SkinRect(15f, 25f, 20f, 30f), fitted(SkinRect(0f, 0f, 20f, 30f), SkinBox(10f, 20f, 30f, 40f)))
        val tall = fitted(SkinRect(0f, 0f, 30f, 80f), SkinBox(0f, 0f, 30f, 40f))
        assertEquals(15f, tall.width, 0.01f)
        assertEquals(40f, tall.height, 0.01f)
    }
}
