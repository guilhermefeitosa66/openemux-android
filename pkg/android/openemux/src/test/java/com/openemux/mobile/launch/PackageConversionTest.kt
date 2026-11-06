package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinTemplate
import com.openemux.mobile.data.SkinVariant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * A Delta/Manic package made into a skin in OpenEmux's format (issue 797): the layouts the copy gets, where each
 * element goes and where its images come from, how a control is lifted off the art, and every package the app ships
 * planning into a skin the format's reader accepts.
 */
class PackageConversionTest {
    private val assets = File("src/main/assets")

    private fun frame(x: Number, y: Number, w: Number, h: Number) = JSONObject().put("x", x).put("y", y).put("width", w).put("height", h)

    private fun item(inputs: Any, f: JSONObject, extra: (JSONObject) -> Unit = {}) = JSONObject().put("inputs", inputs).put("frame", f).also(extra)

    private fun buttons(vararg names: String) = JSONArray(names.toList())

    private fun dirs(up: String, down: String, left: String, right: String) =
        JSONObject().put("up", up).put("down", down).put("left", left).put("right", right)

    private fun rep(
        items: List<JSONObject> = emptyList(), w: Number = 100, h: Number = 200, asset: String = "art.png",
        screens: JSONArray? = JSONArray().put(JSONObject().put("outputFrame", frame(0, 0, 100, 60))), extra: (JSONObject) -> Unit = {},
    ) = JSONObject().put("assets", JSONObject().put("large", asset)).put("mappingSize", JSONObject().put("width", w).put("height", h))
        .put("items", JSONArray(items)).also { r -> screens?.let { r.put("screens", it) } }.also(extra)

    private fun info(vararg reps: Pair<String, JSONObject>): JSONObject {
        val all = JSONObject()
        for ((path, rep) in reps) {
            val (family, kind, orientation) = path.split("/")
            val f = all.optJSONObject(family) ?: JSONObject().also { all.put(family, it) }
            val k = f.optJSONObject(kind) ?: JSONObject().also { f.put(kind, it) }
            k.put(orientation, rep)
        }
        return JSONObject().put("name", "Pkg").put("representations", all)
    }

    private fun phone(portrait: JSONObject, landscape: JSONObject = rep()) =
        info("iphone/edgeToEdge/portrait" to portrait, "iphone/edgeToEdge/landscape" to landscape)

    /** The phone's upright layout of [portrait], art at 1 pixel per unit unless [size] says. */
    private fun upright(portrait: JSONObject, console: Console = Console.GBA, size: Pair<Int, Int>? = 100 to 200) =
        PackageConversion.plan(phone(portrait), console, "Copy") { size }.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)

    @Test fun thePhoneComesFromTheIphoneArtAndTheTabletAndFoldFromTheIpadArt() {
        val plan = PackageConversion.plan(
            info(
                "iphone/edgeToEdge/portrait" to rep(), "iphone/standard/landscape" to rep(),
                "ipad/standard/portrait" to rep(), "ipad/standard/landscape" to rep(), "ipad/edgeToEdge/landscape" to rep(w = 50),
            ),
            Console.SFC, "Copy",
        ) { null }
        assertEquals(SkinLayoutId.entries.toSet(), plan.layouts.keys)
        assertEquals("iphone/portrait", plan.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).art)
        assertEquals("ipad/landscape", plan.layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE).art)
        // A fold draws the tablet's art: one plan for both.
        assertSame(plan.layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE), plan.layouts.getValue(SkinLayoutId.FOLD_LANDSCAPE))
        // Edge-to-edge before the classic art.
        assertEquals(150, plan.layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE).layout.size.width)
        assertEquals(Console.SFC, plan.console)
        assertEquals("Copy", plan.name)
    }

    @Test fun aPackageWithOnlyPhoneArtGivesOnlyPhoneLayouts() {
        val plan = PackageConversion.plan(phone(rep()), Console.GBA, "Copy") { null }
        assertEquals(setOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), plan.layouts.keys)
        assertEquals(listOf(SkinForm.PHONE), PackageConversion.finish(plan, emptyMap()).forms)
    }

    @Test fun aPackageWithNoArtForBothOrientationsOrNoMappingSizeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageConversion.plan(info("iphone/edgeToEdge/portrait" to rep()), Console.GBA, "Copy") { null }
        }
        assertThrows(IllegalArgumentException::class.java) { upright(rep(w = 0)) }
        assertThrows(IllegalArgumentException::class.java) { upright(rep(h = -1)) }
    }

    @Test fun thePixelSpaceIsTheArtsOwnOrAPdfsDrawnAtThreeTimesCapped() {
        val png = upright(rep(listOf(item(buttons("a"), frame(10, 100, 20, 20)))), size = 300 to 600)
        assertEquals(300 to 600, png.layout.size.width to png.layout.size.height)
        assertEquals(SkinRect(30f, 300f, 60f, 60f), png.layout.elements.single().rect)
        assertEquals(listOf(SkinRect(0f, 0f, 300f, 180f)), png.layout.screens)
        val pdf = upright(rep(asset = "art.pdf"), size = null)
        assertEquals("art.pdf", pdf.asset)
        assertEquals(300 to 600, pdf.artW to pdf.artH)
        val huge = upright(rep(w = 2000, h = 1000, screens = null), size = null)
        assertEquals(3000 to 1500, huge.artW to huge.artH)
    }

    @Test fun theArtAssetIsThePdfFirstThenTheLargestImageWhateverTheKeysCase() {
        assertEquals("v.pdf", PackageConversion.assetName(JSONObject().put("Large", "l.png").put("RESIZABLE", "v.pdf")))
        assertEquals("m.png", PackageConversion.assetName(JSONObject().put("small", "s.png").put("medium", "m.png")))
        assertEquals("s.png", PackageConversion.assetName(JSONObject().put("small", "s.png")))
    }

    @Test fun theScreensAreTheUnfilteredOnesTopFirstOrTheGameScreenFrame() {
        val ds = JSONArray()
            .put(JSONObject().put("outputFrame", frame(0, 120, 100, 60)))
            .put(JSONObject().put("outputFrame", frame(0, 0, 100, 200)).put("filters", JSONArray()))
            .put(JSONObject().put("outputFrame", frame(0, 10, 100, 60)))
            .put(JSONObject().put("inputFrame", frame(0, 0, 1, 1)))
        assertEquals(listOf(SkinRect(0f, 10f, 100f, 60f), SkinRect(0f, 120f, 100f, 60f)), upright(rep(screens = ds), Console.NDS).layout.screens)
        val classic = upright(rep(screens = null) { it.put("gameScreenFrame", frame(5, 5, 90, 50)) })
        assertEquals(listOf(SkinRect(5f, 5f, 90f, 50f)), classic.layout.screens)
        // Clamped to the art; one wholly outside it is refused.
        assertEquals(listOf(SkinRect(0f, 150f, 100f, 50f)), upright(rep(screens = JSONArray().put(JSONObject().put("outputFrame", frame(-5, 150, 120, 90))))).layout.screens)
        assertThrows(IllegalArgumentException::class.java) { upright(rep(screens = JSONArray().put(JSONObject().put("outputFrame", frame(0, 300, 10, 10))))) }
    }

    @Test fun controlsOnlyArtGetsTheGameAboveUprightAndUnderneathSideways() {
        val items = listOf(item(buttons("a"), frame(10, 10, 20, 20)))
        val up = upright(rep(items, screens = null))
        // GBA's 3:2 picture as wide as the art, above it.
        assertEquals(67, up.offsetY)
        assertEquals(100 to 267, up.layout.size.width to up.layout.size.height)
        assertEquals(listOf(SkinRect(0f, 0f, 100f, 67f)), up.layout.screens)
        assertEquals(SkinRect(10f, 77f, 20f, 20f), up.layout.elements.single().rect)
        assertEquals(PixelRect(10, 10, 20, 20), (up.elements.single().sources.single() as ImageSource.Art).frame)
        val side = PackageConversion.plan(phone(rep(), rep(items, w = 200, h = 100, screens = null)), Console.GBA, "Copy") { null }
            .layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE)
        assertEquals(0, side.offsetY)
        assertEquals(listOf(SkinRect(0f, 0f, 600f, 300f)), side.layout.screens)
        assertEquals(PackageConversion.TRANSPARENT, side.layout.background.color)
        assertFalse(side.translucent)
        assertTrue(upright(rep(extra = { it.put("translucent", true) })).translucent)
    }

    @Test fun buttonsKeepTheirExtendedEdgesClampedToTheArt() {
        val layout = upright(
            rep(
                listOf(
                    item(buttons("start"), frame(40, 180, 20, 10)) { it.put("extendedEdges", JSONObject().put("bottom", 30).put("left", 5)) },
                    item(buttons("b"), frame(0, 100, 20, 20)),
                ),
            ) { it.put("extendedEdges", JSONObject().put("left", 4).put("top", 4)) },
        )
        val (start, b) = layout.elements
        assertEquals(SkinRect(35f, 180f, 25f, 20f), start.element.rect)
        assertEquals(ImageSource.Art(PixelRect(40, 180, 20, 10), 5, 0), start.sources.single())
        assertEquals("center", start.element.group)
        assertEquals(SkinRect(0f, 96f, 20f, 24f), b.element.rect)
        assertEquals(ImageSource.Art(PixelRect(0, 100, 20, 20), 0, 4), b.sources.single())
        assertEquals("face", b.element.group)
    }

    @Test fun quickActionsAreActionsOnTheirFrameAndNamesTheFormatLacksAreLeftOut() {
        val layout = upright(
            rep(
                listOf(
                    item(buttons("menu"), frame(40, 70, 20, 10)) { it.put("extendedEdges", JSONObject().put("top", 9)) },
                    item(buttons("noSuchKey"), frame(0, 70, 10, 10)),
                    item(buttons("unknown", "y", "x"), frame(0, 90, 10, 10)),
                    item(buttons("screens"), frame(80, 70, 10, 10)),
                    JSONObject().put("inputs", buttons("a")),
                    item(buttons("a"), frame(500, 500, 10, 10)),
                    JSONObject().put("frame", frame(0, 0, 5, 5)),
                    item("a", frame(0, 0, 5, 5)),
                ),
            ),
        )
        val (menu, y) = layout.elements
        assertEquals(2, layout.elements.size)
        assertEquals(SkinElementType.ACTION, menu.element.type)
        assertEquals(SkinRect(40f, 70f, 20f, 10f), menu.element.rect)
        assertEquals("actions", menu.element.group)
        assertEquals("y", y.element.bind)
        // The NDS keeps its screens key, which the format draws and leaves inert.
        val ds = upright(rep(listOf(item(buttons("screens"), frame(80, 70, 10, 10)))), Console.NDS)
        assertEquals("screens", ds.layout.elements.single().bind)
    }

    @Test fun slowMotionTakesACellBeforeFastForwardAndTheStripKeysTheirNarrowerCells() {
        val strip = listOf("quickSave", "quickLoad", "turbo", "toggleFastForward").mapIndexed { i, n -> item(buttons(n), frame(i * 25, 70, 25, 10)) }
        val layout = upright(rep(strip))
        assertEquals(listOf("save", "load", "turbo", "slow-motion", "fast-forward"), layout.elements.map { it.element.id })
        assertEquals(listOf(0f, 20f, 40f, 60f, 80f), layout.elements.map { it.element.rect.x })
        assertTrue(layout.elements.all { it.element.rect.width == 20f && it.element.type == SkinElementType.ACTION })
        assertEquals("slowMotion", layout.elements[3].element.bind)
        assertEquals(ImageSource.SlowMotion(PixelRect(75, 70, 25, 10)), layout.elements[3].sources.single())
        // The key's own frame, centred in its cell.
        assertEquals(ImageSource.Art(PixelRect(25, 70, 25, 10), -2, 0), layout.elements[1].sources.single())
        // A lone fast forward is split in two; a held one counts when there is no toggle.
        val lone = upright(rep(listOf(item(buttons("fastForward"), frame(50, 70, 40, 10)))))
        assertEquals(listOf(SkinRect(50f, 70f, 20f, 10f), SkinRect(70f, 70f, 20f, 10f)), lone.layout.elements.map { it.rect })
    }

    @Test fun aButtonSlowMotionMovesKeepsItsCellAsItsRect() {
        val layout = upright(
            rep(
                listOf(
                    item(buttons("a"), frame(0, 70, 30, 10)) { it.put("extendedEdges", JSONObject().put("top", 5)) },
                    item(buttons("toggleFastForward"), frame(30, 70, 30, 10)),
                ),
            ),
        )
        val a = layout.elements.first()
        assertEquals(SkinRect(0f, 70f, 20f, 10f), a.element.rect)
        assertEquals(ImageSource.Art(PixelRect(0, 70, 30, 10), -5, 0), a.sources.single())
    }

    @Test fun aSlowMotionSlotOutsideTheArtIsLeftOut() {
        // A fast forward reaching past the art: its split half lies outside.
        val layout = upright(rep(listOf(item(buttons("toggleFastForward"), frame(-40, 70, 20, 10)))), size = 100 to 200)
        assertEquals(emptyList<String>(), layout.elements.map { it.element.id })
        val half = upright(rep(listOf(item(buttons("toggleFastForward"), frame(-10, 70, 20, 10)))))
        assertEquals(listOf("fast-forward"), half.elements.map { it.element.id })
    }

    @Test fun aDpadGetsItsCircleCroppedToTheBallAndASticksKnobFillsAsTheAppDrawsIt() {
        val layout = upright(
            rep(
                listOf(
                    item(dirs("up", "down", "left", "right"), frame(0, 100, 40, 40)) { it.put("knob", "circle.png") },
                    item(dirs("up", "down", "left", "right"), frame(50, 100, 40, 40)),
                    item(dirs("analogStickUp", "analogStickDown", "analogStickLeft", "analogStickRight"), frame(0, 150, 40, 40)) {
                        it.put("thumbstick", JSONObject().put("name", "knob.png").put("width", 10).put("height", 10))
                    },
                    item(dirs("cStickUp", "x", "x", "x"), frame(50, 150, 40, 40)) { it.put("thumbstick", JSONObject().put("name", "k2.png")) },
                    item(dirs("rightAnalogStickUp", "x", "x", "x"), frame(0, 0, 10, 10)) { it.put("thumbstick", JSONObject().put("name", "big.png").put("width", 99)) },
                ),
            ),
        )
        val (dpad, bare, stick, c, right) = layout.elements
        assertEquals(SkinElementType.DPAD, dpad.element.type)
        assertEquals("dpad", dpad.element.bind)
        assertEquals(listOf(ImageSource.Art(PixelRect(0, 100, 40, 40), 0, 0), ImageSource.Entry("circle.png", 0.55f, 1f)), dpad.sources)
        assertEquals("dpad-2", bare.element.id)
        assertEquals(1, bare.sources.size)
        assertEquals("stick", stick.element.id)
        assertEquals("analogStick", stick.element.bind)
        assertEquals(ImageSource.Entry("knob.png", 1f, 0.5f), stick.sources[1])
        assertEquals("rightAnalogStick", c.element.bind)
        assertEquals("stick2", c.element.id)
        assertEquals(ImageSource.Entry("k2.png", 1f, 1f), c.sources[1])
        assertEquals(ImageSource.Entry("big.png", 1f, 1f), right.sources[1])
        assertEquals("stick2-2", right.element.id)
        assertEquals("sticks", right.element.group)
    }

    @Test fun aThumbstickOrDirectionsTheFormatCannotTakeAreAStick() {
        val layout = upright(
            rep(
                listOf(
                    item(dirs("up", "down", "left", "right"), frame(0, 100, 40, 40)) { it.put("thumbstick", JSONObject().put("name", "")) },
                    item(dirs("up", "down", "noSuch", "right"), frame(50, 100, 40, 40)),
                    item(dirs("up", "down", "menu", "right"), frame(0, 150, 40, 40)),
                ),
            ),
        )
        assertEquals(listOf("analogStick", "analogStick", "analogStick"), layout.elements.map { it.element.bind })
        assertTrue(layout.elements.all { it.sources.size == 1 })
    }

    @Test fun fourButtonsOnOneItemBecomeFourButtonsOverItsArms() {
        val layout = upright(rep(listOf(item(dirs("cUp", "cDown", "cLeft", "cRight"), frame(10, 100, 30, 30)))), Console.N64)
        assertEquals(listOf("c-up", "c-down", "c-left", "c-right"), layout.elements.map { it.element.id })
        assertEquals(
            listOf(SkinRect(20f, 100f, 10f, 10f), SkinRect(20f, 120f, 10f, 10f), SkinRect(10f, 110f, 10f, 10f), SkinRect(30f, 110f, 10f, 10f)),
            layout.elements.map { it.element.rect },
        )
        assertEquals(ImageSource.Art(PixelRect(20, 100, 10, 10), 0, 0), layout.elements[0].sources.single())
        assertEquals("c-buttons", layout.elements[0].element.group)
        // Too small to have arms: nothing.
        assertEquals(0, upright(rep(listOf(item(dirs("cUp", "cDown", "cLeft", "cRight"), frame(10, 100, 2, 2)))), Console.N64).elements.size)
    }

    @Test fun idsAreTheFormatsUsualNamesMadeUnique() {
        val layout = upright(rep(listOf("a", "a", "a", "cUp", "l1", "reset").mapIndexed { i, n -> item(buttons(n), frame(i * 10, 100, 10, 10)) }))
        assertEquals(listOf("a", "a-2", "a-3", "c-up", "l1", "restart"), layout.elements.map { it.element.id })
        assertEquals("key", PackageConversion.kebab("--"))
        assertEquals("right-analog-stick", PackageConversion.kebab("rightAnalogStick"))
        assertEquals("option1", PackageConversion.kebab("option1"))
    }

    @Test fun pixelRectsClampAndShift() {
        assertEquals(PixelRect(0, 5, 5, 5), PixelRect(-5, 5, 10, 10).clamp(5, 10))
        assertNull(PixelRect(20, 0, 5, 5).clamp(10, 10))
        assertEquals(PixelRect(1, 7, 2, 3), PixelRect(1, 2, 2, 3).shift(5))
        assertEquals(3, PixelRect(1, 2, 2, 3).right)
        assertEquals(5, PixelRect(1, 2, 2, 3).bottom)
    }

    // The pixel side: an art of [w] x [h] in [bg] with [fg] squares.
    private val bg = 0xFF336699.toInt()
    private val fg = 0xFFEEEEEE.toInt()

    private fun art(w: Int, h: Int, vararg squares: PixelRect): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (squares.any { x >= it.x && x < it.right && y >= it.y && y < it.bottom }) fg else bg
    }

    @Test fun distanceAndMedianWeighAlpha() {
        assertEquals(0, PackageConversion.distance(0x00FF0000, 0x0000FF00))
        assertEquals(255, PackageConversion.distance(0xFF000000.toInt(), 0x00000000))
        assertEquals(255, PackageConversion.distance(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(0x80102030.toInt(), PackageConversion.median(intArrayOf(0x80102030.toInt(), 0x80102030.toInt(), 0xFFFFFFFF.toInt())))
    }

    @Test fun aControlOnAPlainSurfaceIsLiftedCleanlyAndPaintedOut() {
        val w = 10
        val px = art(w, 10, PixelRect(3, 3, 3, 3))
        val frame = PixelRect(2, 2, 5, 5)
        val lifted = PackageConversion.lift(px, w, frame)
        assertTrue(lifted.clean)
        assertEquals(bg, lifted.surround)
        assertEquals(5 to 5, lifted.w to lifted.h)
        assertEquals(9, lifted.px.count { it == fg })
        assertEquals(16, lifted.px.count { it == 0 })
        PackageConversion.erase(px, w, frame, lifted)
        assertTrue(px.all { it == bg })
        // Placed in a larger image, offset; and cut off where it sticks out.
        val placed = PackageConversion.place(lifted, 7, 7, 1, 1)
        assertEquals(fg, placed[2 * 7 + 2])
        assertEquals(0, placed[0])
        val cut = PackageConversion.place(lifted, 3, 3, -2, -2)
        assertEquals(listOf(fg, fg, 0, fg, fg, 0, 0, 0, 0), cut.toList())
    }

    @Test fun aControlOnABusySurfaceIsNotClean() {
        val w = 10
        // A checkerboard: half the border is one colour, half the other.
        val px = IntArray(w * 10) { i -> if ((i % w + i / w) % 2 == 0) fg else bg }
        assertFalse(PackageConversion.lift(px, w, PixelRect(1, 1, 8, 8)).clean)
        // A one-pixel strip reads its border once.
        val strip = PackageConversion.lift(art(w, 10), w, PixelRect(0, 0, 10, 1))
        assertTrue(strip.clean)
        val column = PackageConversion.lift(art(w, 10), w, PixelRect(0, 0, 1, 10))
        assertTrue(column.clean)
    }

    @Test fun theGlyphIsTheInkFurthestFromItsSurroundAndItsHeight() {
        val w = 10
        val lifted = PackageConversion.lift(art(w, 10, PixelRect(4, 2, 2, 5)), w, PixelRect(0, 0, 10, 10))
        assertEquals(fg to 5, PackageConversion.glyph(lifted))
        assertNull(PackageConversion.glyph(PackageConversion.lift(art(w, 10), w, PixelRect(0, 0, 10, 10))))
        assertEquals(10f, PackageConversion.slowMotionSize(5, 50, 50), 0f)
        assertEquals(18f, PackageConversion.slowMotionSize(null, 50, 30), 0f)
        assertEquals(9f, PackageConversion.slowMotionSize(40, 10, 50), 0f)
    }

    @Test fun aControlDrawnFromColoursTakesTheColourInItsMiddleAndAReadableInk() {
        val w = 10
        assertEquals(fg, PackageConversion.fill(art(w, 10, PixelRect(2, 2, 6, 6)), w, PixelRect(0, 0, 10, 10)))
        assertEquals(bg, PackageConversion.fill(art(w, 10), w, PixelRect(0, 0, 1, 1)))
        assertEquals(0xFF000000.toInt(), PackageConversion.ink(fg))
        assertEquals(0xFFFFFFFF.toInt(), PackageConversion.ink(bg))
        assertEquals("START", PackageConversion.label("start"))
        assertEquals("C", PackageConversion.label("cUp"))
        assertEquals("L2", PackageConversion.label("l2"))
        assertNull(PackageConversion.label("option1"))
    }

    @Test fun translucentArtIsFadedToThePackageRenderersOpacity() {
        val px = intArrayOf(0xFF102030.toInt(), 0x00102030, 0x80000000.toInt())
        PackageConversion.fade(px)
        assertArrayEquals(intArrayOf(0xB3102030.toInt(), 0x00102030, 0x5A000000), px)
    }

    @Test fun finishDrawsFromColoursOnlyWhatCouldNotBeCut() {
        val layout = upright(
            rep(
                listOf(
                    item(buttons("a"), frame(0, 100, 10, 10)),
                    item(buttons("start"), frame(20, 100, 30, 10)),
                    item(dirs("up", "down", "left", "right"), frame(0, 120, 30, 30)),
                    item(dirs("analogStickUp", "x", "x", "x"), frame(40, 120, 30, 30)),
                    item(buttons("menu"), frame(80, 100, 10, 10)),
                    item(buttons("b"), frame(0, 160, 10, 10)),
                    item(buttons("x"), frame(20, 160, 10, 10)),
                ),
            ),
        )
        val plan = PackagePlan(Console.GBA, "Copy", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout))
        val red = 0xFFCC0000.toInt()
        fun missing(i: Int) = ConvertedElement(setOf(layout.elements[i].element.imageFiles.first()), red)
        val results = listOf(missing(0), missing(1), missing(2), missing(3), missing(4), ConvertedElement(setOf("b.png")), ConvertedElement(setOf("x-other.png"), red))
        val skin = PackageConversion.finish(plan, mapOf(SkinLayoutId.PHONE_PORTRAIT to results))
        val all = skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements
        val (a, start, dpad, stick, menu) = all
        val b = all[5]
        val x = all[6]
        assertEquals(mapOf("button" to red, "letter" to 0xFFFFFFFF.toInt()), a.colors)
        assertEquals(SkinShape.CIRCLE, a.shape)
        assertEquals("A", a.label)
        assertEquals(SkinShape.PILL, start.shape)
        assertEquals(mapOf("pad" to red), dpad.colors)
        assertEquals(mapOf("base" to red), stick.colors)
        assertEquals(layout.elements[4].element, menu)
        assertEquals(layout.elements[5].element, b)
        assertEquals(layout.elements[6].element, x)
        assertEquals("Copy", skin.name)
        // No results: every element as planned.
        assertEquals(layout.layout, PackageConversion.finish(plan, emptyMap()).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT))
        assertEquals(layout.layout, PackageConversion.finish(plan, mapOf(SkinLayoutId.PHONE_PORTRAIT to emptyList())).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT))
    }

    @Test fun theImagesSizesAreTheTemplates() {
        val layout = upright(rep(listOf(item(dirs("up", "down", "left", "right"), frame(0, 100, 60, 60)) { it.put("knob", "c.png") })))
        assertEquals(
            mapOf("background.png" to (100 to 200), "dpad.png" to (60 to 60), "dpad-ball.png" to (44 to 44)),
            PackageConversion.sizes(layout.layout),
        )
        assertEquals(SkinTemplate.images(layout.layout).size, PackageConversion.sizes(layout.layout).size)
    }

    /** Every Delta/Manic package the app ships, in every variant folder. */
    private fun packages(): List<Pair<SkinVariant, File>> = SkinVariant.entries.flatMap { v ->
        File(assets, v.assetDir).listFiles().orEmpty().filter { SkinStore.isPackage(it) }.map { v to it }
    }

    @Test fun everyBundledPackagePlansIntoASkinTheFormatAccepts() {
        val packages = packages()
        assertTrue(packages.size >= 60)
        assertEquals(
            listOf("FC", "GB", "GBA", "GBC", "GC", "MD", "N64", "NDS", "PS", "PSP", "SFC", "SMS"),
            packages.map { it.second.nameWithoutExtension }.distinct().sorted(),
        )
        for ((variant, file) in packages) {
            val where = "${variant.id} ${file.name}"
            val console = Console.entries.first { it.id == file.nameWithoutExtension }
            ZipFile(file).use { zip ->
                val info = SkinStore.packageInfo(zip)
                val plan = PackageConversion.plan(info, console, "Copy") { entry ->
                    zip.getInputStream(zip.getEntry(entry)).use { SkinTemplate.pngSize(it.readNBytes(32)) }
                }
                val skin = PackageConversion.finish(plan, emptyMap())
                // The reader accepts it, and it reads back the same.
                assertEquals(where, skin, SkinFormat.parse(SkinFormat.toJson(skin)))
                assertEquals(where, SkinStore.packageForms(info), skin.forms.toSet())
                assertEquals(where, SkinLayoutId.entries.toSet(), skin.layouts.keys)
                for ((id, layout) in plan.layouts) {
                    val e = layout.layout.elements
                    assertEquals("$where ${id.id}", if (console == Console.NDS) 2 else 1, layout.layout.screens.size)
                    assertEquals("$where ${id.id}", layout.artW to layout.artH, layout.layout.size.width to layout.layout.size.height)
                    // Every item the package draws is a control of the copy, slow motion added.
                    val items = info.getJSONObject("representations").getJSONObject(layout.family).let { f ->
                        listOf("edgeToEdge", "standard").firstNotNullOf { f.optJSONObject(it)?.optJSONObject(layout.orientation) }
                    }.getJSONArray("items")
                    assertEquals("$where ${id.id}", items.length() + 1, e.size)
                    assertNotNull("$where ${id.id}", e.firstOrNull { it.bind == "slowMotion" })
                    assertNotNull("$where ${id.id}", e.firstOrNull { it.type == SkinElementType.DPAD && it.bind == "dpad" })
                    // Each element's images: one source per file, its D-pad circle and its sticks' knobs from the package.
                    for (p in layout.elements) {
                        assertEquals("$where ${p.element.id}", p.element.imageFiles.size, p.sources.size)
                        p.sources.filterIsInstance<ImageSource.Entry>().forEach { assertNotNull("$where ${it.entry}", ImportedSkinRenderer.findEntry(zip, it.entry)) }
                    }
                    if (console in setOf(Console.N64, Console.GC, Console.PS, Console.PSP)) {
                        assertTrue("$where ${id.id}", e.any { it.type == SkinElementType.STICK })
                    }
                }
            }
        }
    }
}

