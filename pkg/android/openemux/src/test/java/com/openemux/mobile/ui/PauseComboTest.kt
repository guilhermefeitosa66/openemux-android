package com.openemux.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The controller combination that pauses (issue 924): what is stored, what the engine gets and what the hint says. */
class PauseComboTest {
    private val combos = listOf(PauseComboPref.HOLD_START, PauseComboPref.START_SELECT, PauseComboPref.L3_R3, PauseComboPref.OFF)

    @Test fun storedValuesParseAndHoldStartIsTheDefault() {
        for (c in combos) assertEquals(c, PauseComboPref.parse(c))
        assertEquals(PauseComboPref.HOLD_START, PauseComboPref.parse(null))
        assertEquals(PauseComboPref.HOLD_START, PauseComboPref.parse("start"))
        assertEquals(PauseComboPref.HOLD_START, PauseComboPref.parse(""))
    }

    @Test fun theEngineValuesAreRetroArchsComboTypes() {
        // input/input_defines.h: NONE 0, L3_R3 2, START_SELECT 4, HOLD_START 7.
        assertEquals("7", PauseComboPref.engineCombo(PauseComboPref.HOLD_START))
        assertEquals("4", PauseComboPref.engineCombo(PauseComboPref.START_SELECT))
        assertEquals("2", PauseComboPref.engineCombo(PauseComboPref.L3_R3))
        assertEquals("0", PauseComboPref.engineCombo(PauseComboPref.OFF))
        // An unknown stored value is the default, never "0": the combination stays on.
        assertEquals("7", PauseComboPref.engineCombo(null))
        assertEquals("7", PauseComboPref.engineCombo("sometimes"))
    }

    @Test fun theHintNamesEveryCombinationAndNoneWhenOff() {
        val hints = listOf(PauseComboPref.HOLD_START, PauseComboPref.START_SELECT, PauseComboPref.L3_R3).map { pauseComboHint(it) }
        hints.forEach { assertNotNull(it) }
        assertEquals(hints.size, hints.toSet().size)
        assertNull(pauseComboHint(PauseComboPref.OFF))
        // Unknown falls back to the default's hint, as the engine falls back to its combination.
        assertEquals(pauseComboHint(PauseComboPref.HOLD_START), pauseComboHint(null))
        assertNotEquals(pauseComboHint(PauseComboPref.HOLD_START), pauseComboHint(PauseComboPref.L3_R3))
    }

    @Test fun theSettingsListOffersTheFourInOrder() {
        assertEquals(combos, PAUSE_COMBOS.map { it.first })
        assertEquals(PAUSE_COMBOS.size, PAUSE_COMBOS.map { it.second }.toSet().size)
        assertEquals(PAUSE_COMBOS.size, PAUSE_COMBOS.map { it.third }.toSet().size)
    }
}
