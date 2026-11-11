package com.openemux.mobile.game

import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TouchControlsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun forcedWhileEveryDetectedPadWasKept() {
        assertTrue(TouchControls.forced(setOf("fake"), setOf("fake", "old")))
        // A real controller plugged in since: the gamepad layout again.
        assertFalse(TouchControls.forced(setOf("fake", "real"), setOf("fake")))
        // No pad: nothing to force.
        assertFalse(TouchControls.forced(emptySet(), setOf("fake")))
        assertFalse(TouchControls.forced(setOf("fake"), emptySet()))
    }

    @Test fun turningItOnKeepsThePadsAndOffForgetsThem() {
        assertEquals(setOf("old", "fake"), TouchControls.set(setOf("old"), setOf("fake"), on = true))
        assertEquals(setOf("old"), TouchControls.set(setOf("old", "fake"), setOf("fake"), on = false))
    }

    @Test fun aPadIsKeptByItsDescriptorElseItsName() {
        val pads = listOf(PadInfo(0, false, "abc", "Pad"), PadInfo(0, false, "", "Sensor"), PadInfo(0, false, "", ""))
        assertEquals(setOf("abc", "Sensor"), TouchControls.keys(pads))
    }

    @Test fun theKeyIsAGamepadPageGoingToItsTouchTwin() {
        assertTrue(TouchControls.tapped("pad-landscape", "landscape", wantGamepad = true))
        assertTrue(TouchControls.tapped("pad-portrait", "portrait", wantGamepad = true))
        assertTrue(TouchControls.tapped("pad-portrait-tabletop@bottom", "portrait-tabletop@bottom", wantGamepad = true))
        assertTrue(TouchControls.tapped("pad-keypad-landscape", "keypad-landscape", wantGamepad = true))
    }

    @Test fun nothingElseIsTheKey() {
        // The app switching itself: it no longer wants the gamepad.
        assertFalse(TouchControls.tapped("pad-landscape", "landscape", wantGamepad = false))
        // The engine's auto-rotate changes the orientation.
        assertFalse(TouchControls.tapped("pad-landscape", "portrait", wantGamepad = true))
        // Unfolding flat changes it too.
        assertFalse(TouchControls.tapped("pad-portrait-tabletop", "portrait", wantGamepad = true))
        // Hide, show, the NDS screens and the keypad stay on gamepad pages; a collapsed page has no key.
        assertFalse(TouchControls.tapped("pad-landscape", "min-pad-landscape", wantGamepad = true))
        assertFalse(TouchControls.tapped("min-pad-landscape", "landscape", wantGamepad = true))
        assertFalse(TouchControls.tapped("pad-portrait@a", "pad-portrait@b", wantGamepad = true))
        // From a touch page, or names that are not the app's.
        assertFalse(TouchControls.tapped("landscape", "landscape", wantGamepad = true))
        assertFalse(TouchControls.tapped(null, "landscape", wantGamepad = true))
        assertFalse(TouchControls.tapped("skin-page", "landscape", wantGamepad = true))
        assertFalse(TouchControls.tapped("pad-landscape", "skin-page", wantGamepad = true))
    }

    @Test fun theStoreKeepsTheChoiceAcrossReads() {
        val store = TouchControlsStore.of(File(tmp.root, "files"))
        assertEquals(emptySet<String>(), store.read())
        assertFalse(store.forced(setOf("fake")))
        store.set(setOf("fake"), on = true)
        assertTrue(TouchControlsStore.of(File(tmp.root, "files")).forced(setOf("fake")))
        store.set(setOf("fake"), on = false)
        assertFalse(store.forced(setOf("fake")))
        assertFalse(File(tmp.root, "files/touch_controls.tmp").exists())
    }

    @Test fun blankLinesAreNoPads() {
        val file = tmp.newFile("touch_controls")
        file.writeText("\n  fake  \n\n")
        assertEquals(setOf("fake"), TouchControlsStore(file).read())
    }

    @Test fun padsAreTheCountedDevicesAndDescribeThemselves() {
        val pad = PadInfo(InputDevice.SOURCE_JOYSTICK, false, "d1", "Sensor")
        val keyboard = PadInfo(InputDevice.SOURCE_KEYBOARD, false, "d2", "Keys")
        assertEquals(listOf(pad), Gamepads.pads(listOf(pad, keyboard)))
        assertTrue(pad.describe().contains("\"Sensor\"") && pad.describe().contains("descriptor=d1"))
    }
}
