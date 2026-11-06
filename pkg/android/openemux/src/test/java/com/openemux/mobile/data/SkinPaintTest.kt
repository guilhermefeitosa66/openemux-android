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

/** The skin editor's appearance rules (issue 786): parts, defaults, colour maths, palettes, Apply to, textures, contrast. */
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
        listOf(SkinPart.LETTER, SkinPart.ICONS, SkinPart.ACTIVE, SkinPart.BACKGROUND, SkinPart.FRAME).forEach { assertNull(it.imageIndex) }
        assertTrue(SkinPart.BACKGROUND.texturable)
        assertTrue(SkinPart.FRAME.texturable)
        assertTrue(SkinPart.KNOB.texturable)
        assertFalse(SkinPart.LETTER.texturable)
        assertFalse(SkinPart.ICONS.texturable)
        assertFalse(SkinPart.ACTIVE.texturable)
    }

    @Test fun partsFollowTheFormatsColourKeys() {
        SkinPart.entries.forEach { assertSame(it, SkinPart.byId(it.id)) }
        assertNull(SkinPart.byId("nope"))
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.LETTER), SkinPart.of(SkinElementType.BUTTON))
        assertEquals(listOf(SkinPart.BUTTON, SkinPart.LETTER), SkinPart.of(SkinElementType.KEYPAD))
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL), SkinPart.of(SkinElementType.DPAD))
        assertEquals(listOf(SkinPart.BASE, SkinPart.KNOB), SkinPart.of(SkinElementType.STICK))
        assertTrue(SkinPart.of(SkinElementType.ACTION).isEmpty())
        assertEquals(listOf(SkinPart.FRAME, SkinPart.ICONS, SkinPart.ACTIVE), SkinPart.FRAME_PARTS)
    }

    @Test fun aSelectionsTargetIsItsControlsOrTheFrameForAQuickAction() {
        assertEquals(SkinTarget.Controls(SkinElementType.BUTTON), SkinTarget.of(layout, listOf("a", "b")))
        assertEquals(listOf(SkinPart.PAD, SkinPart.BALL), SkinTarget.of(layout, listOf("dpad"))!!.parts)
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

    @Test fun applyToOffersOnlyScopesThatChangeMore() {
        // One face button: itself, the face buttons, every button.
        assertEquals(listOf(SkinScope.ONE, SkinScope.GROUP, SkinScope.ALL), SkinApply.scopes(layout, listOf("a"), SkinPart.BUTTON))
        assertEquals(listOf("a"), SkinApply.targets(layout, listOf("a"), SkinScope.ONE, SkinPart.BUTTON))
        assertEquals(listOf("a", "b"), SkinApply.targets(layout, listOf("a"), SkinScope.GROUP, SkinPart.BUTTON))
        assertEquals(listOf("a", "b", "start"), SkinApply.targets(layout, listOf("a"), SkinScope.ALL, SkinPart.BUTTON))
        // The whole group selected: the group is the selection, so its chip is not offered twice.
        assertEquals(listOf(SkinScope.ONE, SkinScope.ALL), SkinApply.scopes(layout, listOf("a", "b"), SkinPart.LETTER))
        // A lone control: no group; the only D-pad: nothing more than itself.
        assertEquals(listOf(SkinScope.ONE, SkinScope.ALL), SkinApply.scopes(layout, listOf("start"), SkinPart.BUTTON))
        assertEquals(listOf(SkinScope.ONE), SkinApply.scopes(layout, listOf("dpad"), SkinPart.BALL))
        // A part the selection does not have paints nothing, and is offered nowhere.
        assertTrue(SkinApply.scopes(layout, listOf("turbo"), SkinPart.ICONS).isEmpty())
        assertTrue(SkinApply.targets(layout, emptyList(), SkinScope.GROUP, SkinPart.BUTTON).isEmpty())
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

    @Test fun paintSetsTheRightKey() {
        val look = layout.look()
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Background, emptyList(), SkinPart.BACKGROUND, 7).background)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.FRAME, 7).frame!!.frame)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.ICONS, 7).frame!!.icons)
        assertEquals(7, SkinPainter.paint(look, layout, SkinTarget.Frame, emptyList(), SkinPart.ACTIVE, 7).frame!!.active)
        // A frame look that was never read starts from no colours.
        assertEquals(SkinFrameColors(icons = 7), SkinPainter.paint(SkinLook(), layout, SkinTarget.Frame, emptyList(), SkinPart.ICONS, 7).frame)
        assertSame(look, SkinPainter.paint(look, bare, SkinTarget.Frame, emptyList(), SkinPart.FRAME, 7))
        val buttons = SkinPainter.paint(look, layout, SkinTarget.Controls(SkinElementType.BUTTON), listOf("a", "b", "dpad"), SkinPart.LETTER, 7)
        assertEquals(mapOf("letter" to 7), buttons.colors["a"])
        assertEquals(mapOf("letter" to 7), buttons.colors["b"])
        // The D-pad has no letter; a control painted again keeps its other parts.
        assertEquals(emptyMap<String, Int>(), buttons.colors["dpad"])
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
        assertTrue(SkinPainter.files(id, layout, listOf("a"), SkinPart.LETTER).isEmpty())
        assertTrue(SkinPainter.files(id, layout, emptyList(), SkinPart.ACTIVE).isEmpty())
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
}
