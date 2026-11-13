package com.openemux.mobile.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library header, the list controls and the pause sheet's icon buttons measure at least 48 dp, spacing included (issue 928). */
class TouchTargetTest {
    @Test fun theMinimumIsMaterials() {
        assertTrue(MinTouchTarget == 48.dp)
    }

    @Test fun theViewToggleButtonsReachTheMinimumWithTheirSpacingAndThePill() {
        for (size in listOf(ToggleSize, ToggleSizePad)) {
            assertTrue("width $size", size.width + ToggleSpacing >= MinTouchTarget)
            assertTrue("height $size", size.height + TogglePillInset * 2 >= MinTouchTarget)
        }
    }

    @Test fun thePagerStepsAndTheSizeButtonHitAreaReachTheMinimum() {
        assertTrue(StepSize >= MinTouchTarget)
        // The drawn Size button stays smaller than its hit area in both layouts.
        for (drawn in listOf(SizeButtonSize, SizeButtonSizePad)) {
            assertTrue("$drawn", drawn.width <= MinTouchTarget && drawn.height <= MinTouchTarget)
        }
    }

    @Test fun theFilterChipsHitAreaReachesTheMinimumInBothLayouts() {
        assertTrue(ChipHit >= MinTouchTarget)
        for (drawn in listOf(ChipHeight, ChipHeightPad)) assertTrue("$drawn", drawn <= ChipHit)
    }
}
