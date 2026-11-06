package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Where a control's label is and how large, and the shape a turned control is drawn as: geometry in a layout's units. */
class SkinLabelsTest {
    // A 100 x 100 button at (200, 400): its centre is (250, 450).
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(200f, 400f, 100f, 100f), label = "A")

    // Start as the consoles print it: a pill with its word under it, 60 below the pill's centre (250, 425).
    private val start = SkinElement(
        "start", SkinElementType.BUTTON, "start", SkinRect(200f, 400f, 100f, 50f), label = "START", labelSize = 30f, labelOffset = SkinPoint(0f, 60f),
    )

    private fun near(expected: SkinRect, actual: SkinRect) {
        assertEquals("$actual", expected.x, actual.x, 0.01f)
        assertEquals("$actual", expected.y, actual.y, 0.01f)
        assertEquals("$actual", expected.width, actual.width, 0.01f)
        assertEquals("$actual", expected.height, actual.height, 0.01f)
    }

    @Test fun aLabelIsTypedOrOneOfTheConsolesGlyphs() {
        listOf("A", "START", "1", "").forEach { assertEquals(it, SkinLabelKind.TEXT, SkinLabels.kind(it)) }
        listOf("△", "○", "✕", "□").forEach { assertEquals(it, SkinLabelKind.SYMBOL, SkinLabels.kind(it)) }
        listOf("▲", "▶", "▼", "◀").forEach { assertEquals(it, SkinLabelKind.ARROW, SkinLabels.kind(it)) }
        assertEquals(SkinLabelKind.MENU, SkinLabels.kind("☰"))
        // Two glyphs are a word, and a letter that looks like a glyph is a letter.
        listOf("△△", "X", "O", "=").forEach { assertEquals(it, SkinLabelKind.TEXT, SkinLabels.kind(it)) }
        // An arrow is drawn once, pointing up, and turned clockwise to where it points.
        assertEquals(mapOf("▲" to 0f, "▶" to 90f, "▼" to 180f, "◀" to 270f), SkinLabels.ARROWS)
    }

    @Test fun onlyAButtonOrAKeyWithALabelHasOneOfItsOwn() {
        assertTrue(SkinLabels.has(a))
        assertTrue(SkinLabels.has(a.copy(type = SkinElementType.KEYPAD, bind = "keypad1", label = "1")))
        assertFalse(SkinLabels.has(a.copy(label = null)))
        assertFalse(SkinLabels.has(a.copy(label = " ")))
        // A D-pad's arrows are painted with it and never move, a stick's letter is part of its knob, a quick action is an icon.
        listOf(SkinElementType.DPAD, SkinElementType.STICK, SkinElementType.ACTION).forEach { assertFalse(it.id, SkinLabels.has(a.copy(type = it))) }
    }

    @Test fun theAppDrawsTheLabelItsImageLeavesOut() {
        // No image: everything is drawn, whatever the skin says an image would hold.
        SkinArt.entries.forEach { assertTrue(it.id, SkinLabels.drawn(a.copy(art = it), hasImage = false)) }
        assertFalse(SkinLabels.drawn(a, hasImage = true))
        assertTrue(SkinLabels.drawn(a.copy(art = SkinArt.KEY), hasImage = true))
        assertTrue(SkinLabels.drawn(a.copy(art = SkinArt.FACE), hasImage = true))
    }

    @Test fun aLabelSitsOnTheMiddleOfTheFaceUnlessItHasAPlaceOfItsOwn() {
        assertEquals(SkinPoint(250f, 450f), SkinLabels.centre(a))
        // The relief is under the face: the label goes up with the face's middle.
        assertEquals(SkinPoint(250f, 440f), SkinLabels.centre(a.copy(depth = 20f)))
        // A place of its own is from the middle of the whole control, relief and all.
        assertEquals(SkinPoint(240f, 455f), SkinLabels.centre(a.copy(depth = 20f, labelOffset = SkinPoint(-10f, 5f))))
        assertEquals(SkinPoint(250f, 485f), SkinLabels.centre(start))
    }

    @Test fun aLabelWhoseCentreIsOutsideItsControlIsBesideItAndSetWider() {
        assertFalse(SkinLabels.beside(a))
        assertEquals(SkinLabels.SPACING, SkinLabels.spacing(a), 0f)
        assertTrue(SkinLabels.beside(start))
        assertEquals(SkinLabels.SPACING_BESIDE, SkinLabels.spacing(start), 0f)
        assertTrue(SkinLabels.SPACING_BESIDE > SkinLabels.SPACING)
        // On the control's very corner it is still on it; half a unit past any edge it is not.
        assertFalse(SkinLabels.beside(a.copy(labelOffset = SkinPoint(50f, -50f))))
        assertTrue(SkinLabels.beside(a.copy(labelOffset = SkinPoint(50.5f, 0f))))
        assertTrue(SkinLabels.beside(a.copy(labelOffset = SkinPoint(0f, -50.5f))))
    }

    @Test fun aLabelsSizeIsItsOwnElseAShareOfItsFace() {
        assertEquals(30f, SkinLabels.size(start), 0f)
        assertEquals(42f, SkinLabels.size(a), 0.001f)
        // Of the face, not of the control: the relief takes from it.
        assertEquals(33.6f, SkinLabels.size(a.copy(depth = 20f)), 0.001f)
        // With no label there is nothing to shrink.
        assertEquals(42f, SkinLabels.size(a.copy(label = null)), 0.001f)
        // A glyph goes by the face's shorter side, each kind with its own share.
        val wide = a.copy(rect = SkinRect(200f, 400f, 100f, 60f))
        assertEquals(31.2f, SkinLabels.size(wide.copy(label = "△")), 0.001f)
        assertEquals(27.6f, SkinLabels.size(wide.copy(label = "▶")), 0.001f)
        assertEquals(31.8f, SkinLabels.size(wide.copy(label = "☰")), 0.001f)
        // A turned control's label is sized by its shape, not by the box around the turned shape.
        assertEquals(73.205f * 0.42f, SkinLabels.size(a.copy(rotation = 30f)), 0.01f)
    }

    @Test fun aLongWordShrinksUntilItFitsItsFace() {
        val select = a.copy(label = "SELECT")
        assertTrue(SkinLabels.size(select) < SkinLabels.size(a))
        // Six letters across the room a label may take of a 100-wide face.
        assertEquals(100f * SkinLabels.FIT, SkinLabels.width("SELECT", SkinLabels.size(select), SkinLabels.SPACING), 0.01f)
        assertEquals(20.202f, SkinLabels.size(select), 0.01f)
        // A size of its own is the skin's word: it is not shrunk.
        assertEquals(42f, SkinLabels.size(select.copy(labelSize = 42f)), 0f)
    }

    @Test fun aWordIsAsWideAsItsLettersAndAGlyphAsItIsTall() {
        assertEquals(12.4f, SkinLabels.width("AB", 10f, 0f), 0.001f)
        assertEquals(15.2f, SkinLabels.width("AB", 10f, SkinLabels.SPACING_BESIDE), 0.001f)
        assertEquals(0f, SkinLabels.width("", 10f, SkinLabels.SPACING), 0f)
        listOf("△", "▼", "☰").forEach { assertEquals(it, 10f, SkinLabels.width(it, 10f, SkinLabels.SPACING_BESIDE), 0f) }
    }

    @Test fun aLabelsBoxIsAroundItsCentre() {
        // One letter at 42: 27.72 wide about (250, 450).
        near(SkinRect(236.14f, 429f, 27.72f, 42f), SkinLabels.box(a))
        // Five letters at 30, set wider beside the key: 114 wide about (250, 485).
        assertEquals(SkinRect(193f, 470f, 114f, 30f), SkinLabels.box(start))
        // A glyph's box is square.
        near(SkinRect(224f, 424f, 52f, 52f), SkinLabels.box(a.copy(label = "○")))
    }

    @Test fun aLabelPutInABoxKeepsItsCentreFromTheControlsAndItsHeightAsItsSize() {
        val placed = SkinLabels.placed(a, SkinRect(300f, 520f, 80f, 24f))
        // The box's centre (340, 532) from the control's (250, 450); nothing else of the control changes.
        assertEquals(a.copy(labelSize = 24f, labelOffset = SkinPoint(90f, 82f)), placed)
        // The box it then has is as tall and about the same point (its width is the word's, not the box's).
        val box = SkinLabels.box(placed)
        assertEquals(24f, box.height, 0f)
        assertEquals(340f, box.x + box.width / 2f, 0.001f)
        assertEquals(532f, box.y + box.height / 2f, 0.001f)
        // Its own box puts a label back where it was.
        assertEquals(start, SkinLabels.placed(start, SkinLabels.box(start)))
    }

    @Test fun aTurnedShapeIsSmallerThanTheBoxAroundIt() {
        assertEquals(100f to 60f, SkinShapes.unturned(100f, 60f, 0f))
        // An 80 x 40 shape turned 20 degrees fills this box: the box gives the shape back, whichever way it was turned.
        val t = Math.toRadians(20.0)
        val w = (80 * cos(t) + 40 * sin(t)).toFloat()
        val h = (80 * sin(t) + 40 * cos(t)).toFloat()
        for (degrees in listOf(20f, -20f)) {
            val (sw, sh) = SkinShapes.unturned(w, h, degrees)
            assertEquals(80f, sw, 0.01f)
            assertEquals(40f, sh, 0.01f)
        }
        // A square turned 30 degrees: the side that fills a 100 box.
        assertEquals(73.205f, SkinShapes.unturned(100f, 100f, 30f).first, 0.01f)
        assertEquals(73.205f, SkinShapes.unturned(100f, 100f, 30f).second, 0.01f)
    }

    @Test fun aTurnTooSteepToTellOrABoxNoShapeFillsGivesTheBoxItself() {
        assertEquals(40f, SkinShapes.MAX_TURN, 0f)
        // The steepest turn the format takes still tells the shape's size.
        assertTrue(SkinShapes.unturned(100f, 100f, SkinShapes.MAX_TURN).first < 100f)
        assertTrue(SkinShapes.unturned(100f, 100f, -SkinShapes.MAX_TURN).first < 100f)
        // Near 45 degrees a square and its box are the same, whatever the square's size: nothing to read.
        listOf(43f, 45f, -45f, 90f, 135f).forEach { assertEquals("$it", 100f to 60f, SkinShapes.unturned(100f, 60f, it)) }
        // A box too thin for any shape turned that far.
        assertEquals(100f to 10f, SkinShapes.unturned(100f, 10f, 30f))
        assertEquals(10f to 100f, SkinShapes.unturned(10f, 100f, 30f))
    }
}
