package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverShapeTest {
    @Test
    fun `every console has a cover shape of its own`() {
        val missing = Console.entries.filter { it !in COVER_ASPECTS }
        assertTrue("no cover shape for $missing", missing.isEmpty())
    }

    @Test
    fun `every shape is a plausible box`() {
        COVER_ASPECTS.forEach { (console, aspect) -> assertTrue("$console: $aspect", aspect in 0.5f..1.6f) }
    }

    @Test
    fun `known boxes keep their shapes`() {
        // The wide SNES box, not the tall Super Famicom one.
        assertEquals(1.41f, Console.SFC.coverAspect, 0.001f)
        assertEquals(1.37f, Console.N64.coverAspect, 0.001f)
        assertEquals(1.00f, Console.GBA.coverAspect, 0.001f)
        assertEquals(1.00f, Console.GB.coverAspect, 0.001f)
        assertEquals(1.00f, Console.PS.coverAspect, 0.001f)
        assertEquals(0.58f, Console.PSP.coverAspect, 0.001f)
        assertEquals(0.71f, Console.MD.coverAspect, 0.001f)
        assertEquals(0.72f, Console.FC.coverAspect, 0.001f)
    }

    @Test
    fun `a console missing from the table takes the usual tall box`() {
        assertEquals(DEFAULT_COVER_ASPECT, coverAspect(Console.SFC, emptyMap()), 0f)
        assertEquals(0.72f, DEFAULT_COVER_ASPECT, 0f)
    }
}
