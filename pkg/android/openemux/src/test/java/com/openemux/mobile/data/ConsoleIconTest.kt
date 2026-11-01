package com.openemux.mobile.data

import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConsoleIconTest {
    @Test
    fun `game boy and game boy color have icons of their own`() {
        assertNotNull(Console.GB.icon)
        assertNotNull(Console.GBC.icon)
        assertNotEquals(Console.GB.icon, Console.GBC.icon)
    }

    @Test
    fun `game boy color uses the pikachu edition icon`() {
        assertEquals(R.drawable.console_gbc, Console.GBC.icon)
        assertEquals(R.drawable.console_gb, Console.GB.icon)
    }
}
