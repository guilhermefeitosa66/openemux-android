package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewportTest {
    @Test fun eachOverlayGetsItsBoxBottomFromTheTop() {
        val cfg = """
            overlays = 2
            overlay0_name = "portrait"
            overlay0_viewport = "0.00000,0.60000,1.00000,0.40000"
            overlay0_full_screen = true
            overlay1_name = "landscape"
            overlay1_viewport = "0.20000,0.00000,0.60000,1.00000"
        """.trimIndent()
        val bottoms = GameViewport.bottoms(cfg)
        assertEquals(0.4f, bottoms.getValue("portrait"), 1e-5f)
        assertEquals(1f, bottoms.getValue("landscape"), 1e-5f)
    }

    @Test fun anOverlayWithNoBoxOrABadOneIsLeftOut() {
        val cfg = """
            overlay0_name = "no-box"
            overlay1_name = "three"
            overlay1_viewport = "0,0.5,1"
            overlay2_name = "words"
            overlay2_viewport = "a,b,c,d"
            overlay3_name = "good"
            overlay3_viewport = "0, 0.25, 1, 0.75"
            overlay4_viewport = "0,0.5,1,0.5"
        """.trimIndent()
        assertEquals(mapOf("good" to 0.75f), GameViewport.bottoms(cfg))
    }

    @Test fun aBoxOutsideTheWindowIsClamped() {
        val cfg = "overlay0_name = \"low\"\noverlay0_viewport = \"0,-0.2,1,0.5\"\noverlay1_name = \"high\"\noverlay1_viewport = \"0,1.5,1,0.5\""
        assertEquals(mapOf("low" to 1f, "high" to 0f), GameViewport.bottoms(cfg))
    }

    @Test fun noSkinNoBottoms() {
        assertTrue(GameViewport.bottoms("").isEmpty())
    }
}
