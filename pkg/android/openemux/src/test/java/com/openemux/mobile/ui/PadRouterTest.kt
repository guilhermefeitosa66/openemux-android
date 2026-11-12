package com.openemux.mobile.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PadRouterTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    private fun decide(code: Int, action: Int = down, fromPad: Boolean = true, shown: Boolean = true, landed: Int = 0) =
        decidePadKey(code, action, fromPad, shown, landed)

    @Test
    fun aKeyboardKeyIsNotThePads() {
        assertEquals(PadDecision.NotPad, decide(KeyEvent.KEYCODE_A, fromPad = false))
        assertEquals(PadDecision.NotPad, decide(KeyEvent.KEYCODE_BACK, fromPad = false, shown = false))
    }

    @Test
    fun thePhonesOwnKeysAreNeverThePads() {
        // A phone that reports its buttons as a gamepad: the volume rocker must not bring the controller look back.
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertEquals(PadDecision.NotPad, decide(code, fromPad = true))
            assertEquals(PadDecision.NotPad, decide(code, fromPad = false))
        }
    }

    @Test
    fun theDpadAndTheButtonsArePadKeysFromAnyDevice() {
        // adb's injected D-pad comes from the keyboard source: still the pad's.
        assertEquals(PadDecision.Pass, decide(KeyEvent.KEYCODE_DPAD_RIGHT, fromPad = false))
        assertEquals(PadDecision.AsCenter, decide(KeyEvent.KEYCODE_BUTTON_A, fromPad = false))
        assertEquals(PadDecision.Pass, decide(KeyEvent.KEYCODE_BUTTON_3, fromPad = false))
    }

    @Test
    fun theFirstPressAfterATouchMovesFromTheFocusedItem() {
        // It used to only bring the highlight back, and look dead.
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertEquals(PadDecision.Pass, decide(code))
            assertEquals(PadDecision.Pass, decide(code, up))
        }
    }

    @Test
    fun aClicksTheFocusedItem() {
        assertEquals(PadDecision.AsCenter, decide(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(PadDecision.AsCenter, decide(KeyEvent.KEYCODE_BUTTON_A, up))
    }

    @Test
    fun aPressWithNothingShownFocusedLandsFocus() {
        assertEquals(PadDecision.Land, decide(KeyEvent.KEYCODE_DPAD_DOWN, shown = false))
        assertEquals(PadDecision.Land, decide(KeyEvent.KEYCODE_BUTTON_A, shown = false))
        assertEquals(PadDecision.Land, decide(KeyEvent.KEYCODE_DPAD_CENTER, shown = false))
    }

    @Test
    fun theReleaseOfTheLandingPressIsTaken() {
        assertEquals(PadDecision.Swallow, decide(KeyEvent.KEYCODE_BUTTON_A, up, landed = KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(PadDecision.Swallow, decide(KeyEvent.KEYCODE_DPAD_DOWN, up, landed = KeyEvent.KEYCODE_DPAD_DOWN))
        // Another key's release is not.
        assertEquals(PadDecision.AsCenter, decide(KeyEvent.KEYCODE_BUTTON_A, up, landed = KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun aReleaseWithNothingFocusedDoesNotLand() {
        // Its press happened elsewhere (the window under a dialog that just closed).
        assertEquals(PadDecision.Pass, decide(KeyEvent.KEYCODE_DPAD_DOWN, up, shown = false))
        assertEquals(PadDecision.AsCenter, decide(KeyEvent.KEYCODE_BUTTON_A, up, shown = false))
    }

    @Test
    fun bGoesBackOnItsReleaseEvenWithNothingFocused() {
        assertEquals(PadDecision.Back(false), decide(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(PadDecision.Back(true), decide(KeyEvent.KEYCODE_BUTTON_B, up))
        assertEquals(PadDecision.Back(true), decide(KeyEvent.KEYCODE_BUTTON_B, up, shown = false))
    }

    @Test
    fun theScreenButtonsRunOnTheirRelease() {
        val buttons = mapOf(
            KeyEvent.KEYCODE_BUTTON_L1 to PadButton.L1, KeyEvent.KEYCODE_BUTTON_R1 to PadButton.R1,
            KeyEvent.KEYCODE_BUTTON_L2 to PadButton.L2, KeyEvent.KEYCODE_BUTTON_R2 to PadButton.R2,
            KeyEvent.KEYCODE_BUTTON_X to PadButton.X, KeyEvent.KEYCODE_BUTTON_Y to PadButton.Y,
            KeyEvent.KEYCODE_BUTTON_SELECT to PadButton.SELECT, KeyEvent.KEYCODE_BUTTON_START to PadButton.START,
        )
        for ((code, button) in buttons) {
            assertEquals(PadDecision.Bus(button, false), decide(code))
            assertEquals(PadDecision.Bus(button, true), decide(code, up, shown = false))
        }
    }

    @Test
    fun padButtonsCoverTheNamedAndTheGenericOnes() {
        assertTrue(isPadButton(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(isPadButton(KeyEvent.KEYCODE_BUTTON_MODE))
        assertTrue(isPadButton(KeyEvent.KEYCODE_BUTTON_1))
        assertTrue(isPadButton(KeyEvent.KEYCODE_BUTTON_16))
        assertFalse(isPadButton(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(isPadButton(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun eachButtonPicksItsHandler() {
        var ran = ""
        val actions = GamepadActions(
            l1 = { ran += "l1" }, r1 = { ran += "r1" }, x = { ran += "x" }, y = { ran += "y" },
            select = { ran += "select" }, start = { ran += "start" }, l2 = { ran += "l2" }, r2 = { ran += "r2" },
        )
        PadButton.entries.forEach { actions.handler(it)!!.invoke() }
        assertEquals("l1r1l2r2xyselectstart", ran)
        assertNull(GamepadActions().handler(PadButton.X))
    }

    @Test
    fun theStickPointsPastItsDeadZone() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, stickKey(-0.9f, 0f, 0f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, stickKey(0.9f, 0.9f, 0f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, stickKey(0.2f, -0.8f, 0f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, stickKey(0f, 0.8f, 0f, 0f))
        assertEquals(0, stickKey(0.4f, -0.4f, 0f, 0f))
    }

    @Test
    fun theHatAnswersWhileTheStickRests() {
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, stickKey(0.1f, 0f, 1f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, stickKey(0f, 0f, 0f, -1f))
        assertEquals(0, stickKey(0f, 0f, 0f, 0f))
    }

    @Test
    fun aHeldStickPausesThenRepeats() {
        assertEquals(380L, stickDelay(false))
        assertEquals(90L, stickDelay(true))
    }

    private fun spot(shown: Boolean, initial: Boolean = false, last: Long = 0, x: Float = 0f, y: Float = 0f) =
        LandingSpot(shown, initial, last, x, y)

    @Test
    fun anItemFocusedEarlierIsNotPreferred() {
        // The latest one (9) is gone: one focused before it has no say, the initial item and reading order do.
        val spots = listOf(spot(true, initial = true), spot(true, last = 2), spot(true, last = 5), spot(false, last = 9))
        assertEquals(listOf(0, 1, 2, 3), rankLanding(spots))
    }

    @Test
    fun landingTakesTheLatestWhenItShows() {
        val spots = listOf(spot(true, initial = true), spot(true, last = 7, y = 50f))
        assertEquals(listOf(1, 0), rankLanding(spots))
    }

    @Test
    fun aScrolledAwayFocusLandsNearestWhereItWas() {
        val spots = listOf(spot(true, x = 0f, y = 0f), spot(false, last = 4), spot(true, x = 100f, y = 100f), spot(true, initial = true, x = 300f, y = 0f))
        assertEquals(listOf(2, 0, 3, 1), rankLanding(spots, anchor = 90f to 110f))
    }

    @Test
    fun withoutAnAnchorTheInitialItemThenReadingOrder() {
        val spots = listOf(spot(true, x = 0f, y = 40f), spot(true, x = 30f, y = 10f), spot(true, x = 5f, y = 10f), spot(true, initial = true, y = 90f))
        assertEquals(listOf(3, 2, 1, 0), rankLanding(spots))
    }

    @Test
    fun withNothingShownLandingTakesTheInitialThenTheLast() {
        assertEquals(listOf(1, 0), rankLanding(listOf(spot(false, last = 3), spot(false, initial = true))))
        assertEquals(listOf(1), rankLanding(listOf(spot(false, last = 3), spot(false, last = 7))))
        assertEquals(emptyList<Int>(), rankLanding(listOf(spot(false))))
        assertEquals(emptyList<Int>(), rankLanding(emptyList()))
    }

    @Test
    fun aScopeWithNoKnownFocusTrustsItsRoot() {
        val scope = PadScope()
        assertFalse(scope.focusShown())
        scope.rootHasFocus = true
        assertTrue(scope.focusShown())
        assertNull(scope.current())
    }

    @Test
    fun aFocusedItemThatIsNotLaidOutDoesNotShow() {
        val scope = PadScope()
        val a = PadFocusTarget()
        val b = PadFocusTarget(initial = true)
        scope.add(a)
        scope.add(b)
        scope.rootHasFocus = true
        scope.focusChanged(a, true)
        // Disposed or scrolled out: the press lands instead.
        assertFalse(scope.focusShown())
        assertSame(a, scope.current())
        scope.focusChanged(a, false)
        scope.focusChanged(b, true)
        scope.focusChanged(b, false)
        // Neither holds it now: the one that held it last.
        assertSame(b, scope.current())
        assertTrue(a.lastFocus in 1 until b.lastFocus)
        scope.remove(b)
        assertSame(a, scope.current())
    }

    @Test
    fun anItemsTwoTargetsShareTheirFocusTick() {
        val scope = PadScope()
        val a = PadFocusTarget()
        val b1 = PadFocusTarget()
        val b2 = PadFocusTarget()
        listOf(a, b1, b2).forEach(scope::add)
        scope.focusChanged(a, true)
        scope.focusChanged(a, false)
        scope.focusChanged(b1, true)
        scope.focusChanged(b2, true)
        assertEquals(b1.lastFocus, b2.lastFocus)
        assertTrue(a.lastFocus < b1.lastFocus)
        assertEquals(listOf(0L, b1.lastFocus, b1.lastFocus), scope.spots(listOf(a, b1, b2)).map { it.lastFocus })
    }

    @Test
    fun anOlderFocusDoesNotStandInForOneThatIsGone() {
        // The focused game scrolled out of its row and was disposed: the chip focused before it is not "the last".
        val scope = PadScope()
        val chip = PadFocusTarget()
        val game = PadFocusTarget()
        scope.add(chip)
        scope.add(game)
        scope.focusChanged(chip, true)
        scope.focusChanged(chip, false)
        scope.focusChanged(game, true)
        scope.focusChanged(game, false)
        scope.remove(game)
        assertEquals(listOf(0L), scope.spots(listOf(chip)).map { it.lastFocus })
        // Still the one focus goes back to when a window over it closes.
        assertSame(chip, scope.current())
    }

    @Test
    fun landingInAnEmptyScopeFindsNothing() {
        assertFalse(PadScope().land())
    }

    @Test
    fun theDpadScrollsATextByTwoThirdsOfAScreen() {
        assertEquals(600f, textScrollStep(down = true, up = false, height = 900, value = 0, max = 5000))
        assertEquals(-600f, textScrollStep(down = false, up = true, height = 900, value = 100, max = 5000))
    }

    @Test
    fun atAnEndOrOnAnotherKeyTheTextLetsThePressGo() {
        assertNull(textScrollStep(down = true, up = false, height = 900, value = 5000, max = 5000))
        assertNull(textScrollStep(down = false, up = true, height = 900, value = 0, max = 5000))
        assertNull(textScrollStep(down = false, up = false, height = 900, value = 10, max = 5000))
    }
}
