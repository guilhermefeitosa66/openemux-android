package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotStopsTest {
    @Test fun tenStopsWhileTheCurrentSlotIsOneOfThem() {
        assertEquals((0..9).toList(), SlotStops.cells(0))
        assertEquals((0..9).toList(), SlotStops.cells(9))
    }

    @Test fun aSlotPastTheStopsGetsAnEleventhStopAtTheEnd() {
        assertEquals((0..9).toList() + 12, SlotStops.cells(12))
    }

    @Test fun thePointerPicksTheCellUnderIt() {
        assertEquals(0, SlotStops.at(0f, 100f, 10))
        assertEquals(3, SlotStops.at(35f, 100f, 10))
        assertEquals(9, SlotStops.at(99.9f, 100f, 10))
        assertEquals(10, SlotStops.at(105f, 110f, 11))
    }

    @Test fun aPointerPastAnEndTakesThatEnd() {
        assertEquals(0, SlotStops.at(-20f, 100f, 10))
        assertEquals(9, SlotStops.at(100f, 100f, 10))
        assertEquals(9, SlotStops.at(400f, 100f, 10))
    }

    @Test fun aSliderNotLaidOutYetPicksTheFirstCell() {
        assertEquals(0, SlotStops.at(50f, 0f, 10))
        assertEquals(0, SlotStops.at(50f, 100f, 0))
    }

    @Test fun eachStopSitsInTheMiddleOfItsCell() {
        assertEquals(5f, SlotStops.center(0, 100f, 10), 0.001f)
        assertEquals(95f, SlotStops.center(9, 100f, 10), 0.001f)
    }

    @Test fun thePadStepsOneStopAndStopsAtTheEnds() {
        assertEquals(4, SlotStops.step(3, 1))
        assertEquals(2, SlotStops.step(3, -1))
        assertEquals(0, SlotStops.step(0, -1))
        assertEquals(9, SlotStops.step(9, 1))
    }

    @Test fun fromASlotPastTheStopsLeftComesBackAndRightStays() {
        assertEquals(9, SlotStops.step(12, -1))
        assertEquals(12, SlotStops.step(12, 1))
    }
}
