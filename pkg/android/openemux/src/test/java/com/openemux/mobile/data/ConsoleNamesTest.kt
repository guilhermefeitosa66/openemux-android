package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConsoleNamesTest {
    @Test
    fun `consoles with regional names show a single one`() {
        assertEquals("Nintendo (NES)", Console.FC.displayName)
        assertEquals("Mega Drive", Console.MD.displayName)
        assertEquals("Mega CD", Console.MCD.displayName)
        assertEquals("PC Engine", Console.PCE.displayName)
        assertEquals("PC Engine CD", Console.PCECD.displayName)
        assertEquals("Odyssey²", Console.O2.displayName)
    }

    @Test
    fun `no console name joins several names with a slash`() {
        Console.entries.forEach { assertFalse(it.id, it.displayName.contains("/")) }
    }

    @Test
    fun `only the label changed, not the ids or the artwork folders`() {
        val expected = mapOf(
            Console.FC to ("FC" to "Nintendo_-_Nintendo_Entertainment_System"),
            Console.MD to ("MD" to "Sega_-_Mega_Drive_-_Genesis"),
            Console.MCD to ("MCD" to "Sega_-_Mega-CD_-_Sega_CD"),
            Console.PCE to ("PCE" to "NEC_-_PC_Engine_-_TurboGrafx_16"),
            Console.PCECD to ("PCECD" to "NEC_-_PC_Engine_CD_-_TurboGrafx-CD"),
            Console.O2 to ("O2" to "Magnavox_-_Odyssey2"),
        )
        expected.forEach { (console, ids) ->
            assertEquals(ids.first, console.id)
            assertEquals(ids.second, console.artworkDir)
        }
        assertEquals(setOf("pce", "sgx", "bin"), Console.PCE.extensions)
    }

    @Test
    fun `the full name pairs the short tag with the single name`() {
        assertEquals("MD - Mega Drive", Console.MD.fullName)
        assertEquals("O2 - Odyssey²", Console.O2.fullName)
    }
}
