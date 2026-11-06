package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinShape
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.data.SkinSource
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Create copy and edit" on a console with no bundled package (issue 798): its drawn skin in OpenEmux's format, laid out
 * as the game draws it, every control pressing what the drawn skin's does.
 */
class DrawnSkinCopyTest {
    /** The consoles that play the drawn skin: the app ships no package for them in any variant. */
    private val unpackaged: List<Console> = run {
        val list = { dir: String -> File("src/main/assets", dir).list() }
        Console.entries.filter { c -> SkinVariant.entries.none { ImportedSkinRenderer.bundledName(list(it.assetDir), c) != null } }
    }

    /**
     * The consoles the copy was made for, the ones with a keypad (the converter's hardest case). Issue 772 shipped them
     * packages of their own, but the converter still takes any drawn skin, so they stay its reference.
     */
    private val drawn = listOf(Console.A5200, Console.CV, Console.INTV, Console.O2)

    private fun copy(console: Console) = DrawnSkinCopy.convert(console, "Mine")

    @Test fun theDrawnConsolesAreTheFourWithAKeypad() {
        // Issue 771 shipped packages for the Atari 2600 and 7800, Lynx, NGP, WonderSwan, Vectrex and Virtual Boy, issue
        // 772 for the four with a keypad: no console is left on the drawn skin.
        assertEquals(emptyList<Console>(), unpackaged)
        assertEquals(drawn, Console.entries.filter { it.skin.keypad.isNotEmpty() })
    }

    @Test fun everyDrawnSkinReadsBackWithItsSixLayouts() {
        for (console in Console.entries) {
            val skin = copy(console).skin
            val json = SkinFormat.toJson(skin)
            val read = SkinFormat.read(object : SkinSource {
                override val files = setOf(SkinFormat.SKIN_JSON)
                override fun open(path: String) = json.byteInputStream().takeIf { path == SkinFormat.SKIN_JSON }
            }).skin
            assertEquals(console.id, skin, read)
            assertEquals(console.id, SkinLayoutId.entries.toSet(), read.layouts.keys)
            assertEquals(console.id, SkinForm.entries, read.forms)
        }
        // The NDS's picture holds its two screens, one over the other.
        val nds = copy(Console.NDS).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).screens
        assertEquals(2, nds.size)
        assertEquals(nds[0].y + nds[0].height, nds[1].y, 1f)
    }

    @Test fun eachLayoutIsItsReferenceWindow() {
        val layouts = copy(Console.O2).skin.layouts
        assertEquals(SkinSize(1080, 2340), layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).size)
        assertEquals(SkinSize(2340, 1080), layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).size)
        assertEquals(SkinSize(1600, 2560), layouts.getValue(SkinLayoutId.TABLET_PORTRAIT).size)
        assertEquals(SkinSize(2560, 1600), layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE).size)
        assertEquals(SkinSize(1812, 2178), layouts.getValue(SkinLayoutId.FOLD_PORTRAIT).size)
        assertEquals(SkinSize(2178, 1812), layouts.getValue(SkinLayoutId.FOLD_LANDSCAPE).size)
    }

    private fun SkinRect.overlaps(o: SkinRect) = x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height

    @Test fun nothingOverlapsOrLeavesTheLayoutOnTheDrawnConsoles() {
        for (console in drawn) for ((id, layout) in copy(console).skin.layouts) {
            val where = "${console.id} ${id.id}"
            val rects = layout.elements.map { it.id to it.rect } + ("screen" to layout.screens.single())
            for ((name, r) in rects + ("frame" to layout.frame!!.rect)) assertTrue("$where $name", r.inside(layout.size))
            // The quick actions sit on the frame; the rest is clear of each other and of the picture.
            val controls = rects.filter { (name, _) -> layout.elements.none { it.id == name && it.type == SkinElementType.ACTION } }
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                assertFalse("$where ${controls[i].first} / ${controls[j].first}", controls[i].second.overlaps(controls[j].second))
            }
            assertFalse(where, layout.frame!!.rect.overlaps(layout.screens.single()))
        }
    }

    /** What the drawn skin's control with this label presses on [console], by RetroPad bind. */
    private fun drawnBinds(console: Console): List<String> {
        val skin = console.skin
        val face = skin.clusters.filterIsInstance<SkinCluster.Face>().flatMap { f -> f.buttons.mapNotNull { it.bind } }
        val keys = (skin.shouldersLeft + skin.shouldersRight + skin.pills).mapNotNull { it.bind }.filter { it != KEYPAD_BIND }
        return (face + keys + skin.keypad.flatten().mapNotNull { it.bind }).sorted()
    }

    @Test fun everyControlPressesWhatTheDrawnSkinsDoes() {
        for (console in drawn) for ((id, layout) in copy(console).skin.layouts) {
            val where = "${console.id} ${id.id}"
            val plan = planPage(console, layout, layout.size.width, layout.size.height, hardcore = false)
            val pressed = plan.elements.filterIsInstance<ElementPlan.Button>().map { it.bind!! }.sorted()
            assertEquals(where, drawnBinds(console), pressed)
            val actions = plan.elements.filterIsInstance<ElementPlan.Action>().map { it.bind }
            assertEquals(where, listOf("menu_toggle", "save_state", "load_state", "turbo_fire_toggle", "toggle_slowmotion", "toggle_fast_forward"), actions)
            val left = console.skin.clusters.first { it !is SkinCluster.Face }
            if (left is SkinCluster.Stick) assertEquals(where, "analog_left", plan.elements.filterIsInstance<ElementPlan.Stick>().single().bind)
            else assertEquals(where, ElementPlan.Dpad.DIRECTIONS, plan.elements.filterIsInstance<ElementPlan.Dpad>().single().binds)
        }
    }

    @Test fun theKeypadKeepsItsLabelsAndTheKeyThatOpenedItGoes() {
        val layout = copy(Console.INTV).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        val keys = layout.elements.filter { it.type == SkinElementType.KEYPAD }
        assertEquals((1..9).map { "key-$it" } + listOf("key-star", "key-0", "key-hash"), keys.map { it.id })
        assertEquals((1..9).map { "$it" } + listOf("CLR", "0", "ENT"), keys.map { it.label })
        assertEquals(listOf("keypadStar", "keypadHash"), keys.filter { it.label == "CLR" || it.label == "ENT" }.map { it.bind })
        assertTrue(keys.all { it.shape == SkinShape.RECT && it.group == "keypad" })
        assertNull(layout.elements.firstOrNull { it.bind == KEYPAD_BIND })
    }

    private fun keypadRows(layout: SkinLayout) =
        layout.elements.filter { it.type == SkinElementType.KEYPAD }.groupBy { it.rect.y }.values.map { row -> row.size }

    @Test fun uprightOnAPhoneTheKeypadIsABandOfTwoRowsUnderTheClusters() {
        val layout = copy(Console.O2).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(listOf(6, 6), keypadRows(layout))
        val keys = layout.elements.filter { it.type == SkinElementType.KEYPAD }
        val dpad = layout.elements.single { it.type == SkinElementType.DPAD }
        val start = keys.minOf { it.rect.y }
        assertTrue(start > dpad.rect.y + dpad.rect.height)
        // Keys no smaller than a thumb's 40 units (the phone's unit is 1080 / 390 pixels).
        assertTrue(keys.all { it.rect.width >= 40f * 1080f / 390f && it.rect.height >= 40f * 1080f / 390f })
    }

    @Test fun uprightOnATabletOrAFoldTheKeypadIsAGridBetweenTheClusters() {
        for (id in listOf(SkinLayoutId.TABLET_PORTRAIT, SkinLayoutId.FOLD_PORTRAIT)) uprightGrid(id)
    }

    private fun uprightGrid(id: SkinLayoutId) {
        val layout = copy(Console.A5200).skin.layouts.getValue(id)
        assertEquals(listOf(3, 3, 3, 3), keypadRows(layout))
        val keys = layout.elements.filter { it.type == SkinElementType.KEYPAD }
        val stick = layout.elements.single { it.type == SkinElementType.STICK }
        val face = layout.elements.filter { it.group == "face" }
        assertTrue(keys.minOf { it.rect.x } > stick.rect.x + stick.rect.width)
        assertTrue(keys.maxOf { it.rect.x + it.rect.width } < face.minOf { it.rect.x })
    }

    @Test fun sidewaysTheKeypadGoesUnderTheFaceButtonsAndThePillsLeft() {
        for (id in listOf(SkinLayoutId.PHONE_LANDSCAPE, SkinLayoutId.TABLET_LANDSCAPE, SkinLayoutId.FOLD_LANDSCAPE)) {
            val layout = copy(Console.A5200).skin.layouts.getValue(id)
            assertEquals(id.id, listOf(3, 3, 3, 3), keypadRows(layout))
            val keys = layout.elements.filter { it.type == SkinElementType.KEYPAD }
            val face = layout.elements.filter { it.group == "face" }
            assertTrue(id.id, keys.minOf { it.rect.y } > face.maxOf { it.rect.y + it.rect.height })
            assertTrue(id.id, keys.minOf { it.rect.x } > layout.screens.single().let { it.x + it.width })
            val pills = layout.elements.filter { it.group == "center" }
            assertEquals(id.id, listOf("start", "select"), pills.map { it.bind })
            assertTrue(id.id, pills.all { it.rect.x + it.rect.width < layout.screens.single().x })
        }
    }

    @Test fun thePhoneUprightPageIsTheDrawnOnesWithoutAKeypad() {
        // The Game Boy's drawn skin, no keypad: SkinRenderer.Layout.portrait's boxes at 1080 / 390 pixels a unit.
        val u = 1080f / 390f
        val layout = copy(Console.GB).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        val gameH = minOf(390f / Console.GB.gameAspect, 380f)
        val top = gameH + 44f
        val pd = 2340f / u - top
        val m0 = 68f
        val m1 = pd - 108f
        val center = top + (m0 + m1) / 2f
        fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 1f)
        val screen = layout.screens.single()
        near(gameH * u, screen.height)
        near((390f - gameH * Console.GB.gameAspect) / 2f * u, screen.x)
        val frame = layout.frame!!.rect
        near(gameH * u, frame.y)
        near(44f * u, frame.height)
        val dpad = layout.elements.single { it.id == "dpad" }.rect
        near((96f - 75f) * u, dpad.x)
        near((center - 75f) * u, dpad.y)
        near(150f * u, dpad.width)
        val menu = layout.elements.single { it.id == "menu" }.rect
        near(145f * u, menu.x)
        near((top + 14f) * u, menu.y)
        val pills = layout.elements.filter { it.group == "center" }
        assertEquals(listOf("select", "start"), pills.map { it.bind })
        pills.forEach { near((top + pd - 100f) * u, it.rect.y) }
        near((195f - 48f - 30f) * u, pills[0].rect.x)
    }

    @Test fun colorsAndShapesComeFromTheDrawnSkin() {
        val colors = Console.O2.skin.colors
        val layout = copy(Console.O2).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        assertEquals(colors.body, layout.background.color)
        assertEquals(colors.strip, layout.frame!!.colors.frame)
        assertEquals(colors.icon, layout.frame!!.colors.icons)
        val dpad = layout.elements.single { it.type == SkinElementType.DPAD }
        assertEquals(mapOf("pad" to colors.dpad, "ball" to colors.mark), dpad.colors)
        val action = layout.elements.single { it.group == "face" }
        assertEquals("ACTION", action.label)
        assertEquals(SkinShape.CIRCLE, action.shape)
        assertEquals(0xFFD2382C.toInt(), action.colors["button"])
        val stick = copy(Console.A5200).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.single { it.type == SkinElementType.STICK }
        assertEquals(mapOf("base" to Console.A5200.skin.colors.stick, "knob" to Console.A5200.skin.colors.knob), stick.colors)
        assertEquals("analogStick", stick.bind)
    }

    @Test fun theOtherDrawnShapesConvertToo() {
        // Not played as drawn today, but the converter takes any drawn skin: beans, shoulders, a second pad and stick.
        val gc = copy(Console.GC).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements
        assertTrue(gc.any { it.group == "face" && it.shape == SkinShape.PILL })
        assertTrue(gc.any { it.group == "shoulders-left" && it.shape == SkinShape.RECT })
        assertTrue(gc.any { it.id == "stick2" && it.bind == "rightAnalogStick" })
        val vb = copy(Console.VB).skin.layouts.getValue(SkinLayoutId.TABLET_LANDSCAPE).elements
        assertEquals(listOf("dpad", "dpad2"), vb.filter { it.type == SkinElementType.DPAD }.map { it.bind })
        // The N64's drawn A is RetroPad B: the format calls it "a" there.
        assertEquals("a", DrawnSkinCopy.buttonName(Console.N64, "b"))
        assertEquals("b", DrawnSkinCopy.buttonName(Console.GBA, "b"))
        assertNull(DrawnSkinCopy.buttonName(Console.GBA, "r_x_minus"))
        assertEquals("c-up", DrawnSkinCopy.idOf("cUp"))
    }

    @Test fun theArtListsTheImagesColoursCannotDraw() {
        val copy = copy(Console.O2)
        for (id in SkinLayoutId.entries) {
            val layout = copy.skin.layouts.getValue(id)
            val art = copy.art.filter { it.layout == id }
            assertEquals(id.id, setOf("background.png", "menu.png", "dpad.png", "dpad-ball.png"), art.map { it.file }.toSet())
            val bg = art.filterIsInstance<DrawnSkinCopy.Art.Background>().single()
            assertEquals(layout.size, SkinSize(bg.width, bg.height))
            assertEquals("ODYSSEY²", bg.name)
            val dpad = layout.elements.single { it.type == SkinElementType.DPAD }.rect
            val pad = art.filterIsInstance<DrawnSkinCopy.Art.Dpad>().single()
            assertEquals(dpad.width.toInt() to dpad.height.toInt(), pad.width to pad.height)
            val menu = layout.elements.single { it.id == "menu" }.rect
            assertEquals(menu.width.toInt(), art.filterIsInstance<DrawnSkinCopy.Art.Menu>().single().width)
            assertTrue(art.filterIsInstance<DrawnSkinCopy.Art.Ball>().single().width > 0)
        }
        // The ring is the drawn one against the ball the format draws: a little over half of it.
        assertEquals(0.578f, DrawnSkinCopy.RING, 0.001f)
    }
}
