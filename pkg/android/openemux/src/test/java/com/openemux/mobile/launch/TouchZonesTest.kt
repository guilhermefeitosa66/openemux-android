package com.openemux.mobile.launch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchZonesTest {

    private val drawn = SkinBox(100f, 100f, 100f, 100f)

    private fun zone(
        neighbours: List<SkinBox> = emptyList(), peers: List<SkinBox> = emptyList(),
        control: SkinBox = drawn, floor: SkinBox = control,
    ) = TouchZones.startZone(control, floor, neighbours, peers, 2f, 400f, 800f)

    private fun edges(b: SkinBox) = listOf(b.x, b.y, b.right, b.bottom)

    @Test fun aFreeControlReachesItsOwnHalfSizePastTheArtOnEverySide() {
        assertEquals(listOf(50f, 50f, 250f, 250f), edges(zone()))
    }

    @Test fun aSmallControlStillGetsAThumbsWidthFromTheScreen() {
        val small = SkinBox(100f, 100f, 20f, 20f)
        // 12% of the 400-wide screen's short side, past the art.
        assertEquals(listOf(52f, 52f, 168f, 168f), edges(zone(control = small)))
    }

    @Test fun theShortSideOfTheScreenIsWhatCountsInLandscape() {
        val small = SkinBox(100f, 100f, 20f, 20f)
        val z = TouchZones.startZone(small, small, emptyList(), emptyList(), 2f, 800f, 400f)
        assertEquals(52f, z.x, 1e-4f)
    }

    @Test fun aNeighbourFarAwayChangesNothing() {
        assertEquals(listOf(50f, 50f, 250f, 250f), edges(zone(neighbours = listOf(SkinBox(300f, 300f, 40f, 40f)))))
    }

    @Test fun aNeighbourOnOneSidePullsOnlyThatSide() {
        // Right of it.
        assertEquals(listOf(50f, 50f, 218f, 250f), edges(zone(neighbours = listOf(SkinBox(220f, 120f, 40f, 40f)))))
        // Left of it.
        assertEquals(listOf(62f, 50f, 250f, 250f), edges(zone(neighbours = listOf(SkinBox(20f, 120f, 40f, 40f)))))
        // Above it.
        assertEquals(listOf(50f, 62f, 250f, 250f), edges(zone(neighbours = listOf(SkinBox(120f, 20f, 40f, 40f)))))
        // Below it.
        assertEquals(listOf(50f, 50f, 250f, 218f), edges(zone(neighbours = listOf(SkinBox(120f, 220f, 40f, 40f)))))
    }

    @Test fun aDiagonalNeighbourTakesFromTheSideThatLosesTheLeast() {
        assertEquals(listOf(50f, 50f, 238f, 250f), edges(zone(neighbours = listOf(SkinBox(240f, 210f, 40f, 40f)))))
    }

    @Test fun aNeighbourOverTheControlLeavesItWhatItAlreadyHad() {
        assertEquals(edges(drawn), edges(zone(neighbours = listOf(SkinBox(150f, 150f, 10f, 10f)))))
    }

    @Test fun aNeighbourHardAgainstTheControlNeverEatsIntoIt() {
        assertEquals(200f, zone(neighbours = listOf(SkinBox(201f, 120f, 40f, 40f))).right, 1e-4f)
        assertEquals(100f, zone(neighbours = listOf(SkinBox(59f, 120f, 40f, 40f))).x, 1e-4f)
    }

    @Test fun whatIsAlreadyTouchedIsNeverLost() {
        // A package's box sticking out further than the zone would go, on the right.
        val floor = SkinBox(90f, 100f, 200f, 100f)
        assertEquals(listOf(50f, 50f, 290f, 250f), edges(zone(floor = floor)))
    }

    @Test fun twoMovementControlsSplitTheSpaceBetweenThemWhicheverComesFirst() {
        val other = SkinBox(300f, 100f, 100f, 100f)
        val mine = zone(peers = listOf(other))
        val theirs = zone(control = other, peers = listOf(drawn))
        assertEquals(248f, mine.right, 1e-4f)
        assertEquals(252f, theirs.x, 1e-4f)
        assertTrue(mine.right < theirs.x)
    }

    @Test fun aPeerOnEverySideIsSplitAtTheMiddle() {
        assertEquals(52f, zone(peers = listOf(SkinBox(-100f, 100f, 100f, 100f))).x, 1e-4f)
        assertEquals(52f, zone(peers = listOf(SkinBox(100f, -100f, 100f, 100f))).y, 1e-4f)
        assertEquals(248f, zone(peers = listOf(SkinBox(100f, 300f, 100f, 100f))).bottom, 1e-4f)
    }

    @Test fun aZoneNeverTouchesANeighbour() {
        for (x in 0..360 step 20) for (y in 0..360 step 20) {
            val n = SkinBox(x.toFloat(), y.toFloat(), 30f, 30f)
            val free = n.right <= drawn.x || n.x >= drawn.right || n.bottom <= drawn.y || n.y >= drawn.bottom
            if (!free) continue
            val z = zone(neighbours = listOf(n))
            val apart = n.right <= z.x || n.x >= z.right || n.bottom <= z.y || n.y >= z.bottom
            assertTrue("$n against $z", apart)
        }
    }

    @Test fun reachIsInMultiplesOfTheRangeFromItsCentre() {
        val reach = TouchZones.reach(drawn, SkinBox(50f, 75f, 200f, 200f))
        assertArrayEquals(floatArrayOf(2f, 1.5f, 2f, 2.5f), reach, 1e-4f)
    }

    @Test fun aRangeWithNoAreaReachesTheEngineDefault() {
        val one = floatArrayOf(1f, 1f, 1f, 1f)
        assertArrayEquals(one, TouchZones.reach(SkinBox(0f, 0f, 0f, 10f), drawn), 0f)
        assertArrayEquals(one, TouchZones.reach(SkinBox(0f, 0f, 10f, 0f), drawn), 0f)
    }
}
