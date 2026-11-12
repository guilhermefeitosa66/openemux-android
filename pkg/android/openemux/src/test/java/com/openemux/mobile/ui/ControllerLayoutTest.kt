package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerLayoutTest {
    private val prefs = listOf(ControllerLayoutPref.AUTO, ControllerLayoutPref.ALWAYS, ControllerLayoutPref.NEVER)

    @Test fun theLayoutFollowsTheSettingAndTheConnection() {
        for (connected in listOf(false, true)) {
            assertEquals(connected, padLayoutOn(ControllerLayoutPref.AUTO, connected))
            assertEquals(true, padLayoutOn(ControllerLayoutPref.ALWAYS, connected))
            assertEquals(false, padLayoutOn(ControllerLayoutPref.NEVER, connected))
        }
    }

    @Test fun touchScreenComesBackToTheControllerLookOnAPadPress() {
        // A detected pad alone shows nothing (the false-pad case); a press brings the ring and the hints back, a
        // touch takes them away again, with or without a pad the system reports (issue 873).
        for (connected in listOf(false, true)) {
            assertEquals(false, gamepadLook(ControllerLayoutPref.NEVER, connected, lastPad = false))
            assertEquals(true, gamepadLook(ControllerLayoutPref.NEVER, connected, lastPad = true))
        }
    }

    @Test fun theLookIsOnInTheGamepadLayoutOrAfterAPress() {
        for (pref in prefs) for (connected in listOf(false, true)) for (lastPad in listOf(false, true)) {
            val expected = lastPad || pref == ControllerLayoutPref.ALWAYS || (pref == ControllerLayoutPref.AUTO && connected)
            assertEquals("$pref $connected $lastPad", expected, gamepadLook(pref, connected, lastPad))
        }
    }

    @Test fun aTouchInTheGamepadLayoutKeepsTheLook() {
        // A touch clears the last input, the look stays: a controller is connected.
        assertEquals(true, gamepadLook(ControllerLayoutPref.AUTO, connected = true, lastPad = false))
        // Touch screen: the touch hides it.
        assertEquals(false, gamepadLook(ControllerLayoutPref.NEVER, connected = true, lastPad = false))
    }

    @Test fun storedValuesParse() {
        assertEquals(ControllerLayoutPref.AUTO, ControllerLayoutPref.parse("auto"))
        assertEquals(ControllerLayoutPref.ALWAYS, ControllerLayoutPref.parse("always"))
        assertEquals(ControllerLayoutPref.NEVER, ControllerLayoutPref.parse("never"))
        assertEquals(ControllerLayoutPref.AUTO, ControllerLayoutPref.parse("sometimes"))
        assertEquals(ControllerLayoutPref.AUTO, ControllerLayoutPref.parse(null))
    }

    @Test fun inputModeReadsTheLayoutAndWritesTheLastInput() {
        val before = InputMode.padConnected to InputMode.lastInputPad
        try {
            InputMode.padConnected = false
            InputMode.gamepad = true
            assertEquals(true, InputMode.lastInputPad)
            assertEquals(true, InputMode.gamepad)
            assertEquals(false, InputMode.padLayout)
            InputMode.gamepad = false
            assertEquals(false, InputMode.gamepad)
            // Automatic, the default: a connected controller turns the layout and the look on.
            InputMode.padConnected = true
            assertEquals(true, InputMode.padLayout)
            assertEquals(true, InputMode.gamepad)
        } finally {
            InputMode.padConnected = before.first
            InputMode.lastInputPad = before.second
        }
    }

    @Test fun sizesFollowTheLayout() {
        val before = InputMode.padConnected
        try {
            InputMode.padConnected = false
            assertEquals(ChipHeight, padSize(ChipHeight, ChipHeightPad))
            InputMode.padConnected = true
            assertEquals(ChipHeightPad, padSize(ChipHeight, ChipHeightPad))
        } finally {
            InputMode.padConnected = before
        }
    }

    @Test fun touchScreenKeepsTheTouchControlsInGame() {
        val never = ControllerLayoutPref.NEVER
        for (detected in listOf(true, false)) for (forced in listOf(true, false)) {
            assertEquals(false, gamepadSkin(never, detected, forced))
        }
        for (pref in listOf(ControllerLayoutPref.AUTO, ControllerLayoutPref.ALWAYS)) {
            assertEquals(true, gamepadSkin(pref, detected = true, forced = false))
            assertEquals(false, gamepadSkin(pref, detected = true, forced = true))
            assertEquals(false, gamepadSkin(pref, detected = false, forced = false))
        }
    }

    @Test fun touchScreenOffersNoTouchControlsTile() {
        for (detected in listOf(true, false)) assertEquals(false, touchControlsTile(ControllerLayoutPref.NEVER, detected))
        for (pref in listOf(ControllerLayoutPref.AUTO, ControllerLayoutPref.ALWAYS)) {
            assertEquals(true, touchControlsTile(pref, detected = true))
            assertEquals(false, touchControlsTile(pref, detected = false))
        }
    }
}
