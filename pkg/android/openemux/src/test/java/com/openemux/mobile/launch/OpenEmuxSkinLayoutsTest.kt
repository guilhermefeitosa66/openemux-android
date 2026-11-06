package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinKeypadPage
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.ui.Fold
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenEmuxSkinLayoutsTest {
    private val layout = SkinLayout(SkinSize(1000, 2000), listOf(SkinRect(0f, 100f, 1000f, 700f)))
    private fun skin(vararg ids: SkinLayoutId) = OpenEmuxSkin(Console.GBA, "Test", ids.associateWith { layout })
    private fun element(type: SkinElementType, bind: String, x: Float = 100f, y: Float = 1000f, w: Float = 200f, h: Float = 200f) =
        SkinElement("e", type, bind, SkinRect(x, y, w, h))

    private fun plan(console: Console, vararg elements: SkinElement, hardcore: Boolean = false) =
        planPage(console, layout.copy(elements = elements.toList()), 1000, 2000, hardcore).elements

    @Test fun theFormIsTheFoldsWheneverTheWindowSeesOne() {
        val flat = Fold(vertical = true, halfOpened = false, 900, 0, 900, 2000)
        assertEquals(SkinForm.FOLD, skinForm(tablet = true, fold = flat))
        assertEquals(SkinForm.FOLD, skinForm(tablet = false, fold = flat.copy(halfOpened = true)))
        assertEquals(SkinForm.TABLET, skinForm(tablet = true, fold = null))
        assertEquals(SkinForm.PHONE, skinForm(tablet = false, fold = null))
    }

    @Test fun aFormIsUsedOnlyWithBothItsOrientations() {
        val all = skin(*SkinLayoutId.entries.toTypedArray())
        assertEquals(LayoutChoice.Use(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), chooseLayouts(all, SkinForm.PHONE))
        assertEquals(LayoutChoice.Use(SkinLayoutId.TABLET_PORTRAIT, SkinLayoutId.TABLET_LANDSCAPE), chooseLayouts(all, SkinForm.TABLET))
        assertEquals(LayoutChoice.Use(SkinLayoutId.FOLD_PORTRAIT, SkinLayoutId.FOLD_LANDSCAPE), chooseLayouts(all, SkinForm.FOLD))
        assertEquals(LayoutChoice.Missing(SkinLayoutId.PHONE_LANDSCAPE), chooseLayouts(skin(SkinLayoutId.PHONE_PORTRAIT), SkinForm.PHONE))
        assertEquals(LayoutChoice.Missing(SkinLayoutId.PHONE_PORTRAIT), chooseLayouts(skin(SkinLayoutId.PHONE_LANDSCAPE), SkinForm.PHONE))
        // No other form's layouts stand in.
        val phone = skin(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)
        assertEquals(LayoutChoice.Missing(SkinLayoutId.TABLET_PORTRAIT), chooseLayouts(phone, SkinForm.TABLET))
        assertEquals(LayoutChoice.Missing(SkinLayoutId.FOLD_PORTRAIT), chooseLayouts(phone, SkinForm.FOLD))
    }

    @Test fun theLayoutKeepsItsProportionsCentredInThePage() {
        // A taller page than the layout: full width, centred down.
        assertEquals(SkinFit(1.08f, 0f, 90f), SkinFit.of(1000, 2000, 1080, 2340))
        // A wider one: full height, centred across.
        assertEquals(SkinFit(0.5f, 250f, 0f), SkinFit.of(1000, 2000, 1000, 1000))
        assertEquals(SkinFit(1f, 0f, 0f), SkinFit.of(1000, 2000, 1000, 2000))
        assertEquals(SkinBox(300f, 100f, 50f, 25f), SkinFit(0.5f, 250f, 50f).box(SkinRect(100f, 100f, 100f, 50f)))
    }

    @Test fun theViewportIsTheScreensBox() {
        val one = planPage(Console.GBA, layout, 500, 1000, hardcore = false)
        assertEquals(SkinBox(0f, 50f, 500f, 350f), one.viewport)
        assertEquals(1f, one.gap, 0f)
        val nds = layout.copy(screens = listOf(SkinRect(100f, 100f, 800f, 600f), SkinRect(100f, 800f, 800f, 600f)))
        assertEquals(SkinBox(100f, 100f, 800f, 1300f), planPage(Console.NDS, nds, 1000, 2000, hardcore = false).viewport)
    }

    @Test fun buttonsPressTheConsolesOwnButtons() {
        val gba = plan(Console.GBA, element(SkinElementType.BUTTON, "a"), element(SkinElementType.BUTTON, "l1"))
        assertEquals(listOf(ElementPlan.Button(0, SkinBox(100f, 1000f, 200f, 200f), "a"), ElementPlan.Button(1, SkinBox(100f, 1000f, 200f, 200f), "l")), gba)
        assertEquals("b", (plan(Console.N64, element(SkinElementType.BUTTON, "a")).single() as ElementPlan.Button).bind)
        assertEquals("y", (plan(Console.MD, element(SkinElementType.BUTTON, "a")).single() as ElementPlan.Button).bind)
        assertEquals("l2", (plan(Console.GC, element(SkinElementType.BUTTON, "l")).single() as ElementPlan.Button).bind)
        // A name nothing maps on this console is drawn and presses nothing.
        assertNull((plan(Console.GBA, element(SkinElementType.BUTTON, "cUp")).single() as ElementPlan.Button).bind)
    }

    @Test fun buttonsAndKeysPressWhatTheCoreReadsThemAs() {
        // Gearcoleco reads the ColecoVision pad where blueMSX's binds differ (data/Skin.kt's coreBind), as the drawn skin.
        val layout = layout.copy(elements = listOf(
            element(SkinElementType.BUTTON, "a"), element(SkinElementType.KEYPAD, "keypad1", x = 400f),
            element(SkinElementType.KEYPAD, "keypad9", x = 700f), element(SkinElementType.BUTTON, "quickSave", y = 1300f),
        ))
        fun binds(core: String?) = planPage(Console.CV, layout, 1000, 2000, hardcore = false, coreId = core).elements.map {
            when (it) {
                is ElementPlan.Button -> it.bind
                is ElementPlan.Action -> it.bind
                else -> null
            }
        }
        assertEquals(listOf("a", "x", "r_x_minus", "save_state"), binds(null))
        assertEquals(listOf("a", "x", "r_x_minus", "save_state"), binds("bluemsx"))
        assertEquals(listOf("b", "y", "l_y_minus", "save_state"), binds("gearcoleco"))
        assertEquals("b", com.openemux.mobile.data.coreBind("gearcoleco", "a"))
        assertEquals("menu", com.openemux.mobile.data.coreBind("gearcoleco", "menu"))
        assertEquals("a", com.openemux.mobile.data.coreBind(null, "a"))
    }

    @Test fun keypadKeysTypeTheNumberPadAndTheConsolesOwnKeys() {
        fun key(console: Console, name: String) = (plan(console, element(SkinElementType.KEYPAD, name)).single() as ElementPlan.Button).bind
        assertEquals("retrok_num5", key(Console.A5200, "keypad5"))
        assertEquals("retrok_num0", key(Console.GBA, "keypad0"))
        assertEquals("retrok_asterisk", key(Console.A5200, "keypadStar"))
        assertEquals("retrok_hash", key(Console.A5200, "keypadHash"))
        assertEquals("retrok_leftbracket", key(Console.INTV, "keypadStar"))
        // ColecoVision's keypad is on the joypad (blueMSX), not the keyboard.
        assertEquals("x", key(Console.CV, "keypad1"))
        assertEquals("r_x_plus", key(Console.CV, "keypad0"))
        assertEquals("select", key(Console.CV, "keypadStar"))
        // A console with no keypad of its own has no keys beside the 0.
        assertNull(key(Console.GBA, "keypadStar"))
        assertNull(OpenEmuxBinds.keypad(Console.A5200, "keypadDot"))
    }

    @Test fun quickActionsPressTheirHotkeyAndLight() {
        fun action(name: String, hardcore: Boolean = false, type: SkinElementType = SkinElementType.ACTION) =
            plan(Console.GBA, element(type, name), hardcore = hardcore).single() as ElementPlan.Action
        val box = SkinBox(100f, 1000f, 200f, 200f)
        assertEquals(ElementPlan.Action(0, box, "save_state", "save_state", null, false), action("quickSave"))
        assertEquals(ElementPlan.Action(0, box, "turbo_fire_toggle", "turbo_fire_toggle", "turbo", false), action("turbo"))
        assertEquals(ElementPlan.Action(0, box, "toggle_slowmotion", "toggle_slowmotion", "slow_motion", false), action("slowMotion"))
        assertEquals(ElementPlan.Action(0, box, "toggle_fast_forward", "toggle_fast_forward", "fast_forward", false), action("toggleFastForward"))
        assertEquals(ElementPlan.Action(0, box, "hold_fast_forward", "toggle_fast_forward", null, false), action("fastForward"))
        assertEquals(ElementPlan.Action(0, box, "menu_toggle", "menu_toggle", null, false), action("menu"))
        assertEquals(ElementPlan.Action(0, box, "reset", "reset", null, false), action("restart"))
        assertEquals(ElementPlan.Action(0, box, "screenshot", "screenshot", null, false), action("screenshot"))
        // No page per NDS layout to step to.
        assertEquals(ElementPlan.Action(0, box, null, "screens", null, false), action("screens"))
        // An action's name on a button is that action.
        assertEquals(ElementPlan.Action(0, box, "load_state", "load_state", null, false), action("quickLoad", type = SkinElementType.BUTTON))
    }

    @Test fun hardcoreDimsTheSpeedKeysAndLeavesThemOut() {
        fun action(name: String) = plan(Console.GBA, element(SkinElementType.ACTION, name), hardcore = true).single() as ElementPlan.Action
        listOf("slowMotion", "fastForward", "toggleFastForward").forEach { name ->
            val a = action(name)
            assertNull(a.bind)
            assertNull(a.lit)
            assertEquals(true, a.dim)
        }
        assertEquals(ElementPlan.Action(0, SkinBox(100f, 1000f, 200f, 200f), "turbo_fire_toggle", "turbo_fire_toggle", "turbo", false), action("turbo"))
        assertNull(OpenEmuxBinds.action(Console.GBA, "a", hardcore = true))
    }

    @Test fun theDpadsBallTravelsInsideItAndTheWholeBoxStartsATouch() {
        val d = plan(Console.GBA, element(SkinElementType.DPAD, "dpad", 100f, 1000f, 300f, 240f)).single() as ElementPlan.Dpad
        assertEquals(SkinBox(100f, 1000f, 300f, 240f), d.box)
        val tx = 150f * OverlayLines.DPAD_TRAVEL
        val ty = 120f * OverlayLines.DPAD_TRAVEL
        assertEquals(SkinBox(250f - tx, 1120f - ty, 2f * tx, 2f * ty), d.travel)
        val reach = 1f / OverlayLines.DPAD_TRAVEL
        assertArrayEquals(floatArrayOf(reach, reach, reach, reach), d.reach, 0f)
        // Its reach takes a touch on the box's edge.
        assertEquals(150f, tx * d.reach[0], 0.001f)
        assertEquals(listOf("up", "down", "left", "right"), d.binds)
    }

    @Test fun aSecondDpadPressesTheConsolesSecondPadOrNothing() {
        val pad = element(SkinElementType.DPAD, "dpad2", 100f, 1000f, 300f, 300f)
        // The Virtual Boy's right pad: Beetle VB's L2, L3, R2 and R3.
        assertEquals(listOf("l2", "l3", "r2", "r3"), (plan(Console.VB, pad).single() as ElementPlan.Dpad).binds)
        // A console with one D-pad: drawn, pressing nothing; the same place and travel as a first pad's.
        val none = plan(Console.GBA, pad).single() as ElementPlan.Dpad
        assertNull(none.binds)
        assertEquals((plan(Console.GBA, element(SkinElementType.DPAD, "dpad", 100f, 1000f, 300f, 300f)).single() as ElementPlan.Dpad).travel, none.travel)
        assertEquals(listOf("up", "down", "left", "right"), OpenEmuxBinds.dpad(Console.VB, "dpad"))
    }

    @Test fun aSticksKnobIsHalfTheWellAndTravelsWithinIt() {
        val s = plan(Console.PS, element(SkinElementType.STICK, "analogStick", 100f, 1000f, 200f, 240f)).single() as ElementPlan.Stick
        val range = 100f * OverlayWriter.KNOB_TRAVEL
        assertEquals("analog_left", s.bind)
        assertEquals(SkinBox(200f - range, 1120f - range, 2f * range, 2f * range), s.range)
        assertEquals(100f, s.knob, 0f)
        assertEquals("analog_right", (plan(Console.PS, element(SkinElementType.STICK, "rightAnalogStick")).single() as ElementPlan.Stick).bind)
    }

    @Test fun elementsKeepTheirPlaceInTheLayout() {
        val p = plan(Console.GBA, element(SkinElementType.DPAD, "dpad"), element(SkinElementType.BUTTON, "a"), element(SkinElementType.ACTION, "menu"))
        assertEquals(listOf(0, 1, 2), p.map { it.index })
    }

    // Issue 772: the keypad page and the core's own binds.

    private val toggle = SkinElement("keypad-toggle", SkinElementType.BUTTON, SkinFormat.KEYPAD_TOGGLE, SkinRect(400f, 1800f, 200f, 60f))
    private val key = SkinElement("key-1", SkinElementType.KEYPAD, "keypad1", SkinRect(600f, 1000f, 100f, 100f))
    private val page = SkinKeypadPage(listOf(key, toggle))

    @Test fun theKeypadToggleSwitchesToItsPageOrPressesNothing() {
        val withPage = layout.copy(elements = listOf(toggle), keypad = page)
        assertEquals(ElementPlan.Toggle(0, SkinBox(400f, 1800f, 200f, 60f), "keypad-portrait"),
            planPage(Console.CV, withPage, 1000, 2000, false, toggle = "keypad-portrait").elements.single())
        assertEquals(ElementPlan.Toggle(0, SkinBox(400f, 1800f, 200f, 60f), null), plan(Console.CV, toggle).single())
        // On the keypad page: its own controls.
        val keypad = planPage(Console.CV, withPage, 1000, 2000, false, keypadPage = true, toggle = "portrait").elements
        assertEquals(listOf(ElementPlan.Button(0, SkinBox(600f, 1000f, 100f, 100f), "x"), ElementPlan.Toggle(1, SkinBox(400f, 1800f, 200f, 60f), "portrait")), keypad)
    }

    @Test fun keypadPagesNeedBothLayoutsAndAWayBack() {
        val use = LayoutChoice.Use(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)
        fun skinWith(portrait: SkinKeypadPage?, landscape: SkinKeypadPage?) = OpenEmuxSkin(
            Console.CV, "Test",
            mapOf(SkinLayoutId.PHONE_PORTRAIT to layout.copy(keypad = portrait), SkinLayoutId.PHONE_LANDSCAPE to layout.copy(keypad = landscape)),
        )
        assertTrue(keypadPages(skinWith(page, page), use))
        assertFalse(keypadPages(skinWith(page, null), use))
        assertFalse(keypadPages(skinWith(null, page), use))
        assertFalse(keypadPages(skinWith(page, SkinKeypadPage(listOf(key))), use))
    }

    @Test fun theOwnFormatsPagesOpenWhereThePlayerWas() {
        assertEquals(listOf(OwnPage("portrait", true, "landscape", false, null), OwnPage("landscape", false, "portrait", false, null)),
            ownPages(keypad = false, first = null))
        assertEquals(listOf("landscape", "portrait"), ownPages(keypad = false, first = "pad-landscape").map { it.name })
        // A keypad page asked for on a skin with none: its orientation's controls.
        assertEquals("landscape", ownPages(keypad = false, first = "keypad-landscape").first().name)
        val all = ownPages(keypad = true, first = null)
        assertEquals(listOf("portrait", "landscape", "keypad-portrait", "keypad-landscape"), all.map { it.name })
        assertEquals(OwnPage("keypad-landscape", false, "keypad-portrait", true, "landscape"), all[3])
        assertEquals("keypad-portrait", all[0].toggle)
        assertEquals("keypad-landscape", ownPages(keypad = true, first = "keypad-landscape").first().name)
        assertEquals("keypad-portrait", ownPages(keypad = true, first = "keypad-$TABLETOP").first().name)
        assertEquals("portrait", ownPages(keypad = true, first = TABLETOP).first().name)
    }

    @Test fun gearcolecoReadsTheColecoVisionsButtonsSwapped() {
        // blueMSX (the drawn skin's core): the skin's names as they are.
        assertEquals("a", OpenEmuxBinds.button(Console.CV, "a"))
        assertEquals("a", OpenEmuxBinds.button(Console.CV, "a", "bluemsx"))
        assertEquals("b", OpenEmuxBinds.button(Console.CV, "a", "gearcoleco"))
        assertNull(OpenEmuxBinds.button(Console.CV, "nothing", "gearcoleco"))
        assertEquals(listOf("up", "down", "left", "right"), OpenEmuxBinds.dpad(Console.CV, "dpad", "gearcoleco"))
        // The keypad: 1 on X with blueMSX, on Y with Gearcoleco; 9 from the right stick, from the left one; * and # swap.
        assertEquals("x", OpenEmuxBinds.keypad(Console.CV, "keypad1"))
        assertEquals("y", OpenEmuxBinds.keypad(Console.CV, "keypad1", "gearcoleco"))
        assertEquals("l_y_minus", OpenEmuxBinds.keypad(Console.CV, "keypad9", "gearcoleco"))
        assertEquals("start", OpenEmuxBinds.keypad(Console.CV, "keypadStar", "gearcoleco"))
        assertEquals("select", OpenEmuxBinds.keypad(Console.CV, "keypadHash", "gearcoleco"))
        // Other consoles' cores read the names as they are.
        assertEquals("retrok_leftbracket", OpenEmuxBinds.keypad(Console.INTV, "keypadStar", "freeintv"))
        assertEquals("a", (plan(Console.CV, element(SkinElementType.BUTTON, "a")).single() as ElementPlan.Button).bind)
        assertEquals("b", (planPage(Console.CV, layout.copy(elements = listOf(element(SkinElementType.BUTTON, "a"))), 1000, 2000, false, coreId = "gearcoleco")
            .elements.single() as ElementPlan.Button).bind)
    }
}
