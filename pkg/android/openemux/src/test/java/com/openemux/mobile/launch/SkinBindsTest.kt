package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkinBindsTest {
    @Test fun aConsolesOwnNamesWinOverTheGenericOnes() {
        assertEquals("b", SkinBinds.bind(Console.N64, "a", false))
        assertEquals("r_x_plus", SkinBinds.bind(Console.N64, "cRight", false))
        assertEquals("y", SkinBinds.bind(Console.MD, "a", false))
        assertEquals("select", SkinBinds.bind(Console.MD, "mode", false))
        assertEquals("l2", SkinBinds.bind(Console.GC, "l", false))
        // A name the console's table lacks falls through to the generic ones.
        assertEquals("start", SkinBinds.bind(Console.GC, "start", false))
    }

    @Test fun theRetroPadNamesPassThrough() {
        listOf("up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select")
            .forEach { assertEquals(it, SkinBinds.bind(Console.SFC, it, false)) }
        assertEquals("a", SkinBinds.bind(null, "a", false))
    }

    @Test fun theFormatsOtherNamesAreRenamed() {
        assertEquals("l", SkinBinds.bind(Console.PS, "l1", false))
        assertEquals("r", SkinBinds.bind(Console.PS, "r1", false))
        assertEquals("menu_toggle", SkinBinds.bind(Console.PS, "menu", false))
        assertEquals("save_state", SkinBinds.bind(Console.PS, "quickSave", false))
        assertEquals("load_state", SkinBinds.bind(Console.PS, "quickLoad", false))
        assertEquals("hold_fast_forward", SkinBinds.bind(Console.PS, "fastForward", false))
        assertEquals("toggle_fast_forward", SkinBinds.bind(Console.PS, "toggleFastForward", false))
        assertEquals("turbo_fire_toggle", SkinBinds.bind(Console.PS, "turbo", false))
        assertEquals("screenshot", SkinBinds.bind(Console.PS, "screenshot", false))
        assertEquals("reset", SkinBinds.bind(Console.PS, "restart", false))
        assertEquals("reset", SkinBinds.bind(Console.PS, "reset", false))
    }

    @Test fun hardcoreLeavesTheSpeedKeysOutOnly() {
        assertNull(SkinBinds.bind(Console.GBA, "fastForward", true))
        assertNull(SkinBinds.bind(Console.GBA, "toggleFastForward", true))
        assertEquals("save_state", SkinBinds.bind(Console.GBA, "quickSave", true))
        assertEquals("b", SkinBinds.bind(Console.N64, "a", true))
    }

    @Test fun anUnknownNameIsLeftOut() {
        assertNull(SkinBinds.bind(Console.GBA, "keypad1", false))
        assertNull(SkinBinds.bind(Console.GBA, "c", false))
        assertNull(SkinBinds.bind(Console.GBA, "", false))
    }
}
