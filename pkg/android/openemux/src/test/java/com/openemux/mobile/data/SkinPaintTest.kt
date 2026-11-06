package com.openemux.mobile.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** The skin editor's appearance rules (issue 786): parts, defaults, colour maths, palettes, what is painted, textures, contrast. */
class SkinPaintTest {
    private val dpad = SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(100f, 1200f, 200f, 200f), group = "dpad")
    private val a = SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(650f, 1250f, 100f, 100f), group = "face", label = "A")
    private val b = SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(550f, 1350f, 100f, 100f), group = "face", label = "B")
    private val start = SkinElement("start", SkinElementType.BUTTON, "start", SkinRect(400f, 1600f, 100f, 50f))
    private val stick = SkinElement("stick", SkinElementType.STICK, "analogStick", SkinRect(100f, 1500f, 200f, 200f))
    private val turbo = SkinElement("turbo", SkinElementType.ACTION, "turbo", SkinRect(10f, 650f, 40f, 40f))
    private val frame = SkinFrame(SkinRect(0f, 600f, 900f, 100f))
    private val layout = SkinLayout(
        SkinSize(900, 1800), listOf(SkinRect(0f, 0f, 900f, 600f)), frame = frame, elements = listOf(dpad, a, b, start, stick, turbo),
    )
    private val bare = layout.copy(frame = null)

    @Test fun partsKnowTheirImageFiles() {
        assertEquals(0, SkinPart.BUTTON.imageIndex)
        assertEquals(0, SkinPart.PAD.imageIndex)
        assertEquals(0, SkinPart.BASE.imageIndex)
        assertEquals(1, SkinPart.BALL.imageIndex)
        assertEquals(1, SkinPart.KNOB.imageIndex)
        listOf(SkinPart.RELIEF, SkinPart.LETTER, SkinPart.ICONS, SkinPart.ACTIVE, SkinPart.BACKGROUND, SkinPart.FRAME).forEach { assertNull(it.imageIndex) }
        assertTrue(SkinPart.BACKGROUND.texturable)
        assertTrue(SkinPart.FRAME.texturable)
        assertTrue(SkinPart.KNOB.texturable)
        assertFalse(SkinPart.LETTER.texturable)
        assertFalse(SkinPart.RELIEF.texturable)
        assertFalse(SkinPart.ICONS.texturable)
        assertFalse(SkinPart.ACTIVE.texturable)
    }

    @Test fun partsFollowTheFormatsColourKeys() {
        SkinPart.entries.forEach { assertSame(it, SkinPart.byId(it.id)) }
        assertNull(SkinPart.byId("nope"))
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.RELIEF, SkinPart.LETTER), SkinPart.of(SkinElementType.BUTTON))
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.RELIEF, SkinPart.LETTER), SkinPart.of(SkinElementType.KEYPAD))
        // The D-pad's four: its cross, its ball, its relief and its arrows.
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL, SkinPart.RELIEF, SkinPart.LETTER), SkinPart.of(SkinElementType.DPAD))
        assertEquals(listOf(SkinPart.BASE, SkinPart.KNOB), SkinPart.of(SkinElementType.STICK))
        assertTrue(SkinPart.of(SkinElementType.ACTION).isEmpty())
        assertEquals(listOf(SkinPart.FRAME, SkinPart.ICONS, SkinPart.ACTIVE), SkinPart.FRAME_PARTS)
    }

    @Test fun aSelectionsTargetIsItsControlsOrTheFrameForAQuickAction() {
        assertEquals(SkinTarget.Controls(SkinElementType.BUTTON), SkinTarget.of(layout, listOf("a", "b")))
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL, SkinPart.RELIEF, SkinPart.LETTER), SkinTarget.of(layout, listOf("dpad"))!!.parts)
        assertEquals(SkinTarget.Frame, SkinTarget.of(layout, listOf("turbo")))
        assertNull(SkinTarget.of(bare, listOf("turbo")))
        assertNull(SkinTarget.of(layout, listOf("nothing")))
        assertNull(SkinTarget.of(layout, emptyList()))
        assertEquals(listOf(SkinPart.BACKGROUND), SkinTarget.Background.parts)
        assertEquals(SkinPart.FRAME_PARTS, SkinTarget.Frame.parts)
    }

    @Test fun defaultsAreTheRenderersDarkFamily() {
        assertEquals(0xFF16161A.toInt(), SkinPaintDefaults.of(SkinPart.BACKGROUND))
        assertEquals(0xFF0E0E11.toInt(), SkinPaintDefaults.of(SkinPart.FRAME))
        assertEquals(0xFFFFFFFF.toInt(), SkinPaintDefaults.of(SkinPart.ICONS))
        assertEquals(0xFF7C3AED.toInt(), SkinPaintDefaults.of(SkinPart.ACTIVE))
        assertEquals(0xFF2E2E35.toInt(), SkinPaintDefaults.of(SkinPart.BUTTON))
        assertEquals(0xFFFFFFFF.toInt(), SkinPaintDefaults.of(SkinPart.LETTER))
        assertEquals(0xFF2E2E35.toInt(), SkinPaintDefaults.of(SkinPart.PAD))
        assertEquals(0xFF6B6B76.toInt(), SkinPaintDefaults.of(SkinPart.BALL))
        assertEquals(0xFF2E2E35.toInt(), SkinPaintDefaults.of(SkinPart.BASE))
        assertEquals(0xFF6B6B76.toInt(), SkinPaintDefaults.of(SkinPart.KNOB))
        assertEquals(0xFF000000.toInt(), SkinPaintDefaults.icon(SkinIconTone.DARK))
        assertEquals(0xFFFFFFFF.toInt(), SkinPaintDefaults.icon(SkinIconTone.LIGHT))
        assertEquals(0xFFFFFFFF.toInt(), SkinPaintDefaults.icon(null))
    }

    @Test fun aReliefFollowsItsFaceAndADpadsArrowsItsBall() {
        val purple = 0xFF7C4DFF.toInt()
        // 42 % of the way to black, channel by channel; the default is the default button's.
        assertEquals(0xFF482D94.toInt(), SkinPaintDefaults.relief(purple))
        assertEquals(SkinColorMath.darken(purple, SkinPaintDefaults.RELIEF_SHADE), SkinPaintDefaults.relief(purple))
        assertEquals(0xFF1B1B1F.toInt(), SkinPaintDefaults.of(SkinPart.RELIEF))
        // A face is a button's or a key's "button", the D-pad's "pad".
        assertEquals(SkinPaintDefaults.BUTTON, SkinPaintDefaults.face(a))
        assertEquals(purple, SkinPaintDefaults.face(a.copy(colors = mapOf("button" to purple))))
        assertEquals(purple, SkinPaintDefaults.face(a.copy(type = SkinElementType.KEYPAD, colors = mapOf("button" to purple))))
        assertEquals(SkinPaintDefaults.PAD, SkinPaintDefaults.face(dpad))
        assertEquals(purple, SkinPaintDefaults.face(dpad.copy(colors = mapOf("pad" to purple, "ball" to 7))))
        // The relief set wins; unset, it is its face's colour, darker.
        assertEquals(7, SkinPaintDefaults.relief(a.copy(colors = mapOf("button" to purple, "relief" to 7))))
        assertEquals(SkinPaintDefaults.relief(purple), SkinPaintDefaults.relief(a.copy(colors = mapOf("button" to purple))))
        assertEquals(SkinPaintDefaults.relief(purple), SkinPaintDefaults.relief(dpad.copy(colors = mapOf("pad" to purple))))
        assertEquals(SkinPaintDefaults.of(SkinPart.RELIEF), SkinPaintDefaults.relief(a))
        // A button's letter is white; a D-pad's arrows take its ball's colour, set or not.
        assertEquals(SkinPaintDefaults.LETTER, SkinPaintDefaults.letter(a))
        assertEquals(7, SkinPaintDefaults.letter(a.copy(colors = mapOf("letter" to 7))))
        assertEquals(SkinPaintDefaults.BALL, SkinPaintDefaults.letter(dpad))
        assertEquals(purple, SkinPaintDefaults.letter(dpad.copy(colors = mapOf("ball" to purple))))
        assertEquals(7, SkinPaintDefaults.letter(dpad.copy(colors = mapOf("ball" to purple, "letter" to 7))))
    }

    @Test fun hexReadsSixDigitsWithOrWithoutTheHash() {
        assertEquals(0xFFF472B6.toInt(), SkinColorMath.parseHex("#F472B6"))
        assertEquals(0xFFF472B6.toInt(), SkinColorMath.parseHex("  f472b6 "))
        assertNull(SkinColorMath.parseHex("#F472B"))
        assertNull(SkinColorMath.parseHex("#F472B6AA"))
        assertNull(SkinColorMath.parseHex("pink"))
        assertNull(SkinColorMath.parseHex(""))
        assertEquals("#F472B6", SkinColorMath.hex(0x80F472B6.toInt()))
        assertEquals("#000000", SkinColorMath.hex(0xFF000000.toInt()))
    }

    @Test fun hsvRoundTripsEverySextant() {
        val colors = listOf("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#F472B6", "#22C55E", "#808080", "#000000", "#FFFFFF")
        for (hex in colors) {
            val c = SkinColorMath.parseHex(hex)!!
            val hsv = SkinColorMath.toHsv(c)
            assertEquals(hex, c, SkinColorMath.fromHsv(hsv[0], hsv[1], hsv[2]))
        }
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), SkinColorMath.toHsv(0xFFFF0000.toInt()), 0.001f)
        assertArrayEquals(floatArrayOf(120f, 1f, 1f), SkinColorMath.toHsv(0xFF00FF00.toInt()), 0.001f)
        assertArrayEquals(floatArrayOf(240f, 1f, 1f), SkinColorMath.toHsv(0xFF0000FF.toInt()), 0.001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), SkinColorMath.toHsv(0xFF000000.toInt()), 0.001f)
        // The hue wraps; saturation and value are clamped.
        assertEquals(0xFFFF0000.toInt(), SkinColorMath.fromHsv(360f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), SkinColorMath.fromHsv(-360f, 2f, 5f))
        assertEquals(0xFF000000.toInt(), SkinColorMath.fromHsv(90f, -1f, -1f))
        assertEquals(0xFFFF00FF.toInt(), SkinColorMath.fromHsv(300f, 1f, 1f))
    }

    @Test fun contrastIsWcagsRatio() {
        assertEquals(0.0, SkinColorMath.luminance(0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, SkinColorMath.luminance(0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(0.2126, SkinColorMath.luminance(0xFFFF0000.toInt()), 1e-4)
        assertEquals(21.0, SkinColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 1e-6)
        assertEquals(21.0, SkinColorMath.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 1e-6)
        assertEquals(1.0, SkinColorMath.contrast(0xFF3B82F6.toInt(), 0xFF3B82F6.toInt()), 1e-9)
        // A very dark grey (below the curve's linear part) on black.
        assertTrue(SkinColorMath.contrast(0xFF050505.toInt(), 0xFF000000.toInt()) < SkinColorMath.MIN_CONTRAST)
    }

    @Test fun coloursMixChannelByChannelKeepingTheFirstOnesAlpha() {
        val red = 0x80FF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals(red, SkinColorMath.mix(red, blue, 0f))
        assertEquals(0x800000FF.toInt(), SkinColorMath.mix(red, blue, 1f))
        assertEquals(0x80800080.toInt(), SkinColorMath.mix(red, blue, 0.5f))
        // The share is kept between none and all of the way.
        assertEquals(red, SkinColorMath.mix(red, blue, -1f))
        assertEquals(0x800000FF.toInt(), SkinColorMath.mix(red, blue, 3f))
        // Lighter is toward white, darker toward black.
        assertEquals(0xFF808080.toInt(), SkinColorMath.lighten(0xFF000000.toInt(), 0.5f))
        assertEquals(0xFF808080.toInt(), SkinColorMath.darken(0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(0x40FFFFFF, SkinColorMath.lighten(0x40123456, 1f))
        assertEquals(0x40000000, SkinColorMath.darken(0x40123456, 1f))
        assertEquals(0x40123456, SkinColorMath.darken(0x40123456, 0f))
        assertEquals(0x40123456, SkinColorMath.lighten(0x40123456, 0f))
    }

    @Test fun palettesAreValidAndBasicIsTheFallback() {
        assertEquals(listOf("basic", "dracula", "pastel", "nord", "gruvbox", "neon", "earth", "green-screen"), SkinPalettes.ALL.map { it.id })
        SkinPalettes.ALL.forEach { p ->
            assertTrue(p.id, p.colors.size in 4..7)
            p.colors.forEach { assertEquals(0xFF, it ushr 24) }
        }
        assertSame(SkinPalettes.BASIC, SkinPalettes.byId(null))
        assertSame(SkinPalettes.BASIC, SkinPalettes.byId("gone"))
        assertEquals("nord", SkinPalettes.byId("nord").id)
    }

    @Test fun recentColoursAreNewestFirstWithoutRepeats() {
        assertEquals(listOf(1, 2, 3), SkinPalettes.recent(listOf(2, 3), 1))
        assertEquals(listOf(3, 1, 2), SkinPalettes.recent(listOf(1, 2, 3), 3))
        assertEquals((10 downTo 3).toList(), SkinPalettes.recent((9 downTo 1).toList(), 10))
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals("#FF0000,#0000FF", SkinPalettes.recentText(listOf(red, blue)))
        assertEquals(listOf(red, blue), SkinPalettes.recentFrom("#FF0000,junk,#0000FF,#FF0000"))
        assertEquals(emptyList<Int>(), SkinPalettes.recentFrom(null))
        assertEquals(SkinPalettes.RECENT_MAX, SkinPalettes.recentFrom((1..12).joinToString(",") { "#0000%02X".format(it) }).size)
    }

    @Test fun whatIsSelectedIsWhatIsPainted() {
        // One control selected, one painted; a group selected, the group; in layout order, whatever the selection's.
        assertEquals(listOf("a"), SkinPainter.painted(layout, listOf("a"), SkinPart.BUTTON))
        assertEquals(listOf("a", "b"), SkinPainter.painted(layout, listOf("b", "a"), SkinPart.LETTER))
        // Only the controls that have the part: a stick has no relief, a quick action no colour of its own.
        assertEquals(listOf("dpad", "a"), SkinPainter.painted(layout, listOf("a", "dpad", "stick", "turbo"), SkinPart.RELIEF))
        assertTrue(SkinPainter.painted(layout, listOf("turbo"), SkinPart.ICONS).isEmpty())
        assertTrue(SkinPainter.painted(layout, emptyList(), SkinPart.BUTTON).isEmpty())
    }

    @Test fun thePartsOfferedAreTheOnesThatShow() {
        val buttons = SkinTarget.Controls(SkinElementType.BUTTON)
        // No relief to show (depth 0), so none to paint; "start" has no label either.
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.LETTER), SkinPainter.parts(layout, buttons, listOf("a", "b")))
        assertEquals(listOf(SkinPart.BUTTON), SkinPainter.parts(layout, buttons, listOf("start")))
        val raised = layout.copy(elements = layout.elements.map { it.copy(depth = 8f) })
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.RELIEF, SkinPart.LETTER), SkinPainter.parts(raised, buttons, listOf("a")))
        // One control of the selection having the part is enough.
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.RELIEF, SkinPart.LETTER), SkinPainter.parts(raised, buttons, listOf("start", "a")))
        // The D-pad's arrows are always there to paint; its relief with its depth.
        val pad = SkinTarget.Controls(SkinElementType.DPAD)
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL, SkinPart.LETTER), SkinPainter.parts(layout, pad, listOf("dpad")))
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL, SkinPart.RELIEF, SkinPart.LETTER), SkinPainter.parts(raised, pad, listOf("dpad")))
        // The frame and the body offer what they have.
        assertEquals(SkinPart.FRAME_PARTS, SkinPainter.parts(layout, SkinTarget.Frame, emptyList()))
        assertEquals(listOf(SkinPart.BACKGROUND), SkinPainter.parts(layout, SkinTarget.Background, emptyList()))
    }

    @Test fun aTextureFrameStaysWithinItsLimits() {
        val f = SkinTextureFrame().moved(0.5f, -0.25f, 2f)
        assertEquals(SkinTextureFrame(SkinFill.STRETCH, 2f, 0.5f, -0.25f), f)
        assertEquals(SkinTextureFrame(SkinFill.STRETCH, SkinTextureFit.MAX_ZOOM, 1f, -1f), f.moved(5f, -5f, 10f))
        assertEquals(SkinTextureFit.MIN_ZOOM, f.moved(0f, 0f, 0.01f).zoom, 0f)
    }

    @Test fun stretchCoversTheBoxAndFitShowsTheWholePicture() {
        assertEquals(SkinRect(0f, 0f, 100f, 50f), SkinTextureFit.place(400, 400, 100f, 50f, SkinTextureFrame(SkinFill.STRETCH)))
        // A square picture in a wide box: as tall as the box, centred across.
        assertEquals(SkinRect(25f, 0f, 50f, 50f), SkinTextureFit.place(400, 400, 100f, 50f, SkinTextureFrame(SkinFill.FIT)))
        // A wide picture in a square box: as wide as the box, centred down.
        assertEquals(SkinRect(0f, 25f, 100f, 50f), SkinTextureFit.place(200, 100, 100f, 100f, SkinTextureFrame(SkinFill.FIT)))
        // Zoomed about the centre and moved by shares of the box; a zero-sized picture is read as one pixel.
        assertEquals(SkinRect(-50f + 10f, -25f - 5f, 200f, 100f), SkinTextureFit.place(400, 400, 100f, 50f, SkinTextureFrame(SkinFill.STRETCH, 2f, 0.1f, -0.1f)))
        assertEquals(SkinRect(0f, 0f, 100f, 50f), SkinTextureFit.place(0, 0, 100f, 50f, SkinTextureFrame(SkinFill.STRETCH, 0f, 0f, 0f)))
    }

    @Test fun repeatTilesCoverTheBox() {
        // A tile half the box's shorter side: 25 x 25 in 100 x 50, centred at (37.5, 12.5).
        val tile = SkinTextureFit.place(10, 10, 100f, 50f, SkinTextureFrame(SkinFill.REPEAT))
        assertEquals(SkinRect(37.5f, 12.5f, 25f, 25f), tile)
        val tiles = SkinTextureFit.tiles(tile, 100f, 50f)
        // Every tile touches the box, and together they cover its corners.
        assertTrue(tiles.all { it.x < 100f && it.y < 50f && it.x + it.width > 0f && it.y + it.height > 0f })
        assertTrue(tiles.any { it.x <= 0f && it.y <= 0f })
        assertTrue(tiles.any { it.x + it.width >= 100f && it.y + it.height >= 50f })
        assertTrue(tiles.contains(tile))
        assertEquals(15, tiles.size)
    }

    @Test fun aBigPictureIsDecodedSmallerButNotTooSmall() {
        assertEquals(1, SkinTextureFit.sample(400, 400, 200, 200))
        assertEquals(4, SkinTextureFit.sample(4000, 3000, 300, 300))
        assertEquals(1, SkinTextureFit.sample(100, 100, 300, 300))
    }

    @Test fun aLookIsTheLayoutsColoursAndPaintsItBack() {
        val painted = layout.copy(
            background = SkinBackground(color = 1), frame = frame.copy(colors = SkinFrameColors(frame = 2)),
            elements = layout.elements.map { if (it.id == "a") it.copy(colors = mapOf("button" to 3)) else it },
        )
        val look = painted.look()
        assertEquals(1, look.background)
        assertEquals(SkinFrameColors(frame = 2), look.frame)
        assertEquals(mapOf("button" to 3), look.colors["a"])
        assertEquals(painted, layout.withLook(look))
        // A control the look leaves out keeps its colours; a layout with no frame gets none.
        assertEquals(mapOf("button" to 3), painted.withLook(SkinLook()).elements.first { it.id == "a" }.colors)
        assertNull(bare.withLook(look).frame)
        assertEquals(frame.colors, layout.withLook(SkinLook()).frame!!.colors)
    }

    @Test fun aLookCarriesWhatEachControlsImageHolds() {
        val held = layout.copy(elements = layout.elements.map { if (it.id == "a") it.copy(art = SkinArt.KEY) else it })
        val look = held.look()
        assertEquals(SkinArt.KEY, look.art["a"])
        assertEquals(layout.elements.associate { it.id to SkinArt.FULL } + ("a" to SkinArt.KEY), look.art)
        assertEquals(held, layout.withLook(look))
        // A control the look leaves out keeps what it had; one the layout does not have is nothing.
        assertEquals(held, held.withLook(SkinLook()))
        assertEquals(held, held.withLook(SkinLook(art = mapOf("nothing" to SkinArt.FACE))))
        assertEquals(SkinArt.FACE, held.withLook(SkinLook(art = mapOf("a" to SkinArt.FACE))).elements.first { it.id == "a" }.art)
    }

    @Test fun paintSetsTheRightKey() {
        val look = layout.look()
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Background, emptyList(), SkinPart.BACKGROUND, 7).background)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.FRAME, 7).frame!!.frame)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.ICONS, 7).frame!!.icons)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.ACTIVE, 7).frame!!.active)
        // A frame look that was never read starts from no colours.
        assertEquals(SkinFrameColors(icons = 7), SkinPainter.paint(SkinLook(), layout, SkinTarget.Frame, emptyList(), SkinPart.ICONS, 7).frame)
        assertSame(look, SkinPainter.paint(look, bare, SkinTarget.Frame, emptyList(), SkinPart.FRAME, 7))
        val buttons = SkinPainter.paint(look, layout, SkinTarget.Controls(SkinElementType.BUTTON), listOf("a", "b", "dpad", "stick"), SkinPart.LETTER, 7)
        assertEquals(mapOf("letter" to 7), buttons.colors["a"])
        assertEquals(mapOf("letter" to 7), buttons.colors["b"])
        // The D-pad's letter is its arrows; a stick has none. A control painted again keeps its other parts.
        assertEquals(mapOf("letter" to 7), buttons.colors["dpad"])
        assertEquals(emptyMap<String, Int>(), buttons.colors["stick"])
        val again = SkinPainter.paint(buttons, layout, SkinTarget.Controls(SkinElementType.BUTTON), listOf("a"), SkinPart.BUTTON, 8)
        assertEquals(mapOf("letter" to 7, "button" to 8), again.colors["a"])
        // A control a look never read starts from the layout's colours.
        assertEquals(mapOf("button" to 8), SkinPainter.paint(SkinLook(), layout, SkinTarget.Controls(SkinElementType.BUTTON), listOf("a"), SkinPart.BUTTON, 8).colors["a"])
        // An element part with no controls target changes nothing.
        assertSame(look, SkinPainter.paint(look, layout, SkinTarget.Background, listOf("a"), SkinPart.BUTTON, 8))
    }

    @Test fun theIconToneNeedsAFrame() {
        assertEquals(SkinIconTone.DARK, SkinPainter.tone(layout.look(), layout, SkinIconTone.DARK).frame!!.activeIcon)
        assertEquals(SkinIconTone.LIGHT, SkinPainter.tone(SkinLook(), layout, SkinIconTone.LIGHT).frame!!.activeIcon)
        val look = bare.look()
        assertSame(look, SkinPainter.tone(look, bare, SkinIconTone.DARK))
    }

    @Test fun eachPartKnowsTheFilesThatDrawIt() {
        val id = SkinLayoutId.PHONE_PORTRAIT
        assertEquals(listOf("phone-portrait/background.png"), SkinPainter.files(id, layout, emptyList(), SkinPart.BACKGROUND))
        assertEquals(listOf("phone-portrait/frame.png"), SkinPainter.files(id, layout, emptyList(), SkinPart.FRAME))
        assertTrue(SkinPainter.files(id, bare, emptyList(), SkinPart.FRAME).isEmpty())
        assertEquals(listOf("phone-portrait/turbo.png"), SkinPainter.files(id, layout, emptyList(), SkinPart.ICONS))
        assertTrue(SkinPainter.files(id, layout, emptyList(), SkinPart.ACTIVE).isEmpty())
        // A control's label and relief are in its image as far as the image holds them (SkinElement.art): the whole
        // control holds both, a key without its label only the relief, a face alone neither.
        fun art(art: SkinArt) = layout.copy(elements = layout.elements.map { it.copy(art = art) })
        assertEquals(listOf("phone-portrait/a.png"), SkinPainter.files(id, layout, listOf("a"), SkinPart.LETTER))
        assertEquals(listOf("phone-portrait/dpad.png", "phone-portrait/a.png"), SkinPainter.files(id, layout, listOf("a", "dpad"), SkinPart.RELIEF))
        assertTrue(SkinPainter.files(id, art(SkinArt.KEY), listOf("a", "dpad"), SkinPart.LETTER).isEmpty())
        assertEquals(listOf("phone-portrait/a.png"), SkinPainter.files(id, art(SkinArt.KEY), listOf("a"), SkinPart.RELIEF))
        assertTrue(SkinPainter.files(id, art(SkinArt.FACE), listOf("a", "dpad"), SkinPart.LETTER).isEmpty())
        assertTrue(SkinPainter.files(id, art(SkinArt.FACE), listOf("a", "dpad"), SkinPart.RELIEF).isEmpty())
        // A face is still the face's own file.
        assertEquals(listOf("phone-portrait/a.png"), SkinPainter.files(id, art(SkinArt.FACE), listOf("a"), SkinPart.BUTTON))
        assertEquals(listOf("phone-portrait/a.png", "phone-portrait/b.png"), SkinPainter.files(id, layout, listOf("a", "b", "dpad"), SkinPart.BUTTON))
        assertEquals(listOf("phone-portrait/dpad.png"), SkinPainter.files(id, layout, listOf("dpad"), SkinPart.PAD))
        assertEquals(listOf("phone-portrait/dpad-ball.png"), SkinPainter.files(id, layout, listOf("dpad"), SkinPart.BALL))
        assertEquals(listOf("phone-portrait/stick-base.png"), SkinPainter.files(id, layout, listOf("stick"), SkinPart.BASE))
        assertEquals(listOf("phone-portrait/stick-knob.png"), SkinPainter.files(id, layout, listOf("stick"), SkinPart.KNOB))
    }

    @Test fun thePickerStartsOnTheColourDrawn() {
        val painted = layout.withLook(
            SkinLook(9, SkinFrameColors(1, 2, 3), mapOf("a" to mapOf("button" to 4, "letter" to 5), "dpad" to mapOf("ball" to 6))),
        )
        assertEquals(9, SkinPainter.colorOf(painted, emptyList(), SkinPart.BACKGROUND))
        assertEquals(1, SkinPainter.colorOf(painted, emptyList(), SkinPart.FRAME))
        assertEquals(2, SkinPainter.colorOf(painted, emptyList(), SkinPart.ICONS))
        assertEquals(3, SkinPainter.colorOf(painted, emptyList(), SkinPart.ACTIVE))
        assertEquals(4, SkinPainter.colorOf(painted, listOf("a", "b"), SkinPart.BUTTON))
        assertEquals(5, SkinPainter.colorOf(painted, listOf("a"), SkinPart.LETTER))
        assertEquals(6, SkinPainter.colorOf(painted, listOf("dpad"), SkinPart.BALL))
        // Unset: the renderer's default.
        assertEquals(SkinPaintDefaults.PAD, SkinPainter.colorOf(painted, listOf("dpad"), SkinPart.PAD))
        assertEquals(SkinPaintDefaults.FRAME, SkinPainter.colorOf(bare, emptyList(), SkinPart.FRAME))
        assertEquals(SkinPaintDefaults.BACKGROUND, SkinPainter.colorOf(bare, emptyList(), SkinPart.BACKGROUND))
        assertEquals(SkinPaintDefaults.KNOB, SkinPainter.colorOf(bare, listOf("nothing"), SkinPart.KNOB))
    }

    @Test fun thePickerStartsOnAReliefsAndADpadsArrowsDerivedColour() {
        val purple = 0xFF7C4DFF.toInt()
        val painted = layout.withLook(SkinLook(colors = mapOf("a" to mapOf("button" to purple), "b" to mapOf("relief" to 5), "dpad" to mapOf("ball" to 6))))
        // Unset, a relief is what the game draws under that face, not the default button's.
        assertEquals(SkinPaintDefaults.relief(purple), SkinPainter.colorOf(painted, listOf("a"), SkinPart.RELIEF))
        assertEquals(5, SkinPainter.colorOf(painted, listOf("b"), SkinPart.RELIEF))
        assertEquals(SkinPaintDefaults.relief(SkinPaintDefaults.PAD), SkinPainter.colorOf(painted, listOf("dpad"), SkinPart.RELIEF))
        // The D-pad's arrows: its ball's colour until they get one of their own.
        assertEquals(6, SkinPainter.colorOf(painted, listOf("dpad"), SkinPart.LETTER))
        assertEquals(SkinPaintDefaults.BALL, SkinPainter.colorOf(layout, listOf("dpad"), SkinPart.LETTER))
        assertEquals(9, SkinPainter.colorOf(layout.withLook(SkinLook(colors = mapOf("dpad" to mapOf("ball" to 6, "letter" to 9)))), listOf("dpad"), SkinPart.LETTER))
        assertEquals(SkinPaintDefaults.LETTER, SkinPainter.colorOf(painted, listOf("a"), SkinPart.LETTER))
        // The first control that has the part, in layout order: the stick has no relief, the D-pad comes before A.
        assertEquals(SkinPaintDefaults.relief(purple), SkinPainter.colorOf(painted, listOf("stick", "a"), SkinPart.RELIEF))
        assertEquals(SkinPaintDefaults.relief(SkinPaintDefaults.PAD), SkinPainter.colorOf(painted, listOf("a", "dpad"), SkinPart.RELIEF))
        // Nothing with the part: the renderer's default.
        assertEquals(SkinPaintDefaults.of(SkinPart.RELIEF), SkinPainter.colorOf(painted, listOf("stick", "turbo"), SkinPart.RELIEF))
        assertEquals(SkinPaintDefaults.LETTER, SkinPainter.colorOf(painted, emptyList(), SkinPart.LETTER))
    }

    @Test fun aLetterTooCloseToItsButtonIsFlagged() {
        val controls = SkinTarget.Controls(SkinElementType.BUTTON)
        // The defaults (white on dark grey) read well.
        assertTrue(SkinContrastCheck.issues(layout, controls, listOf("a")).isEmpty())
        val grey = 0xFF808080.toInt()
        val close = layout.withLook(SkinLook(colors = mapOf("a" to mapOf("button" to grey, "letter" to 0xFF8A8A8A.toInt()), "start" to mapOf("button" to grey))))
        assertEquals(setOf(SkinContrast.LETTER), SkinContrastCheck.issues(close, controls, listOf("a", "b")))
        // Start has no label: nothing to read on it, whatever its colours.
        val blank = layout.withLook(SkinLook(colors = mapOf("start" to mapOf("button" to 0xFFFFFFFF.toInt()))))
        assertTrue(SkinContrastCheck.issues(blank, controls, listOf("start")).isEmpty())
        assertTrue(SkinContrastCheck.issues(close, SkinTarget.Background, emptyList()).isEmpty())
    }

    @Test fun aLabelBesideItsControlIsReadOnTheBody() {
        val controls = SkinTarget.Controls(SkinElementType.BUTTON)
        val white = 0xFFFFFFFF.toInt()
        fun laidOut(e: SkinElement, body: Int? = null) =
            layout.copy(background = SkinBackground(color = body), elements = layout.elements.map { if (it.id == e.id) e else it })
        // START printed under its key: white on the dark body reads, whatever the key's colour.
        val word = start.copy(label = "START", labelOffset = SkinPoint(0f, 60f), colors = mapOf("button" to white))
        assertTrue(SkinContrastCheck.issues(laidOut(word), controls, listOf("start")).isEmpty())
        // The same word on the key itself does not.
        assertEquals(setOf(SkinContrast.LETTER), SkinContrastCheck.issues(laidOut(word.copy(labelOffset = null)), controls, listOf("start")))
        // On a body as light as the letter it is lost, though the key is dark.
        assertEquals(setOf(SkinContrast.LETTER), SkinContrastCheck.issues(laidOut(word.copy(colors = emptyMap()), body = white), controls, listOf("start")))
        assertTrue(SkinContrastCheck.issues(laidOut(word.copy(colors = emptyMap(), labelOffset = null), body = white), controls, listOf("start")).isEmpty())
        // A D-pad's arrows are no label of its own: nothing to warn about, whatever their colour.
        val pad = layout.withLook(SkinLook(colors = mapOf("dpad" to mapOf("pad" to white, "letter" to white))))
        assertTrue(SkinContrastCheck.issues(pad, SkinTarget.Controls(SkinElementType.DPAD), listOf("dpad")).isEmpty())
    }

    @Test fun theFramesIconsAndActiveIconAreChecked() {
        assertTrue(SkinContrastCheck.issues(layout, SkinTarget.Frame, emptyList()).isEmpty())
        val dim = layout.withLook(SkinLook(frame = SkinFrameColors(frame = 0xFFFFFFFF.toInt(), active = 0xFFFFFFF0.toInt())))
        assertEquals(setOf(SkinContrast.ICONS, SkinContrast.ACTIVE_ICON), SkinContrastCheck.issues(dim, SkinTarget.Frame, emptyList()))
        val dark = layout.withLook(SkinLook(frame = SkinFrameColors(active = 0xFFFFFFF0.toInt(), activeIcon = SkinIconTone.DARK)))
        assertTrue(SkinContrastCheck.issues(dark, SkinTarget.Frame, emptyList()).isEmpty())
        assertTrue(SkinContrastCheck.issues(bare, SkinTarget.Frame, emptyList()).isEmpty())
    }

    private class Bytes(private val map: Map<String, ByteArray>) : SkinSource {
        override val files: Set<String> = map.keys
        override fun open(path: String): InputStream? = map[path]?.let { ByteArrayInputStream(it) }
    }

    @Test fun anEditedSkinHidesRemovedImagesAndReadsStagedOnes() {
        val stage = Files.createTempDirectory("stage").toFile()
        try {
            File(stage, "t1.png").writeBytes(byteArrayOf(4))
            val base = Bytes(mapOf("p/a.png" to byteArrayOf(1), "p/b.png" to byteArrayOf(2)))
            val edits = mapOf("p/a.png" to SkinImageEdit.Removed, "p/c.png" to SkinImageEdit.Staged("t1.png"), "p/d.png" to SkinImageEdit.Staged("gone.png"))
            val source = EditedSkinSource(base, edits) { File(stage, it) }
            assertEquals(setOf("p/b.png", "p/c.png", "p/d.png"), source.files)
            assertNull(source.open("p/a.png"))
            assertArrayEquals(byteArrayOf(2), source.open("p/b.png")!!.readBytes())
            assertArrayEquals(byteArrayOf(4), source.open("p/c.png")!!.readBytes())
            assertNull(source.open("p/d.png"))
            assertNull(EditedSkinSource(base, edits) { null }.open("p/c.png"))
        } finally {
            stage.deleteRecursively()
        }
    }

    @Test fun anEditOnAnImagesPngNameCoversTheWebpTheSkinKeepsItIn() {
        val stage = Files.createTempDirectory("stage").toFile()
        try {
            File(stage, "t1.png").writeBytes(byteArrayOf(4))
            val base = Bytes(mapOf("skin.json" to byteArrayOf(0), "p/a.webp" to byteArrayOf(1), "p/b.webp" to byteArrayOf(2), "p/c.webp" to byteArrayOf(3)))
            val edits = mapOf("p/a.png" to SkinImageEdit.Removed, "p/b.png" to SkinImageEdit.Staged("t1.png"))
            val source = EditedSkinSource(base, edits) { File(stage, it) }
            // The removed image is gone under either name; the texture, a PNG, takes the WebP's place.
            assertEquals(setOf("skin.json", "p/b.png", "p/c.webp"), source.files)
            assertNull(source.open("p/a.webp"))
            assertNull(source.open("p/a.png"))
            assertArrayEquals(byteArrayOf(4), source.open("p/b.png")!!.readBytes())
            assertNull(source.open("p/b.webp"))
            // What no edit names is the skin's own, WebP or not.
            assertArrayEquals(byteArrayOf(3), source.open("p/c.webp")!!.readBytes())
            assertArrayEquals(byteArrayOf(0), source.open("skin.json")!!.readBytes())
            assertNull(source.open("p/c.png"))
        } finally {
            stage.deleteRecursively()
        }
    }
}
