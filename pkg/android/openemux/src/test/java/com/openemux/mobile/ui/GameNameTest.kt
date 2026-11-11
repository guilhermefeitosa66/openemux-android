package com.openemux.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a long game name scrolls instead of ending in an ellipsis (issue 869). */
class GameNameTest {

    @Test
    fun scrollsByTouchWithAnimationsOn() {
        assertTrue(marqueeOn(animatorScale = 1f, padLook = false, focused = false))
        assertTrue(marqueeOn(animatorScale = 1f, padLook = false, focused = true))
    }

    @Test
    fun aSlowerOrFasterScaleStillScrolls() {
        assertTrue(marqueeOn(animatorScale = 0.5f, padLook = false, focused = false))
        assertTrue(marqueeOn(animatorScale = 10f, padLook = false, focused = false))
    }

    @Test
    fun removeAnimationsKeepsTheEllipsis() {
        assertFalse(marqueeOn(animatorScale = 0f, padLook = false, focused = false))
        assertFalse(marqueeOn(animatorScale = 0f, padLook = true, focused = true))
    }

    @Test
    fun withTheGamepadLookOnlyTheFocusedNameScrolls() {
        assertTrue(marqueeOn(animatorScale = 1f, padLook = true, focused = true))
        assertFalse(marqueeOn(animatorScale = 1f, padLook = true, focused = false))
    }
}
