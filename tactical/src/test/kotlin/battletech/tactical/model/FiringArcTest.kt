package battletech.tactical.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FiringArcTest {

    private fun mapWithRadius(center: HexCoordinates, radius: Int): GameMap {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in (center.col - radius)..(center.col + radius)) {
            for (row in (center.row - radius)..(center.row + radius)) {
                val coords = HexCoordinates(col, row)
                if (center.distanceTo(coords) <= radius) {
                    hexes[coords] = Hex(coords, Terrain.CLEAR)
                }
            }
        }
        return GameMap(hexes)
    }

    @Test
    fun `forwardHexSides returns 3 contiguous directions for facing N`() {
        val sides = FiringArc.forwardHexSides(HexDirection.N)
        assertEquals(setOf(HexDirection.NW, HexDirection.N, HexDirection.NE), sides)
    }

    @Test
    fun `forwardHexSides returns 3 contiguous directions for facing SE`() {
        val sides = FiringArc.forwardHexSides(HexDirection.SE)
        assertEquals(setOf(HexDirection.NE, HexDirection.SE, HexDirection.S), sides)
    }

    @Test
    fun `forwardHexSides returns 3 contiguous directions for facing SW`() {
        val sides = FiringArc.forwardHexSides(HexDirection.SW)
        assertEquals(setOf(HexDirection.S, HexDirection.SW, HexDirection.NW), sides)
    }

    @Test
    fun `bearingDirection for hex directly north`() {
        val origin = HexCoordinates(4, 4)
        val target = origin.neighbor(HexDirection.N)
        assertEquals(HexDirection.N, FiringArc.bearingDirection(origin, target))
    }

    @Test
    fun `bearingDirection for hex directly south`() {
        val origin = HexCoordinates(4, 4)
        val target = origin.neighbor(HexDirection.S)
        assertEquals(HexDirection.S, FiringArc.bearingDirection(origin, target))
    }

    @Test
    fun `bearingDirection for each immediate neighbor`() {
        val origin = HexCoordinates(4, 4)
        for (dir in HexDirection.entries) {
            val target = origin.neighbor(dir)
            assertEquals(dir, FiringArc.bearingDirection(origin, target), "Expected $dir for neighbor in that direction")
        }
    }

    @Test
    fun `hex directly ahead is in arc`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        assertTrue(origin.neighbor(HexDirection.N) in arc)
    }

    @Test
    fun `hex directly behind is NOT in arc`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        assertFalse(origin.neighbor(HexDirection.S) in arc)
    }

    @Test
    fun `hex on the edge (60 degree boundary) is in arc`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        // NE and NW are the boundary directions, should be in arc
        assertTrue(origin.neighbor(HexDirection.NE) in arc)
        assertTrue(origin.neighbor(HexDirection.NW) in arc)
    }

    @Test
    fun `hex at the side (not forward) is out of arc`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        assertFalse(origin.neighbor(HexDirection.SE) in arc)
        assertFalse(origin.neighbor(HexDirection.SW) in arc)
    }

    @Test
    fun `origin hex is NOT in arc`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        assertFalse(origin in arc)
    }

    @Test
    fun `facing SE includes hexes to the NE, SE, and S`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.SE, map)
        assertTrue(origin.neighbor(HexDirection.NE) in arc)
        assertTrue(origin.neighbor(HexDirection.SE) in arc)
        assertTrue(origin.neighbor(HexDirection.S) in arc)
        // Opposite side should be out
        assertFalse(origin.neighbor(HexDirection.NW) in arc)
        assertFalse(origin.neighbor(HexDirection.SW) in arc)
        assertFalse(origin.neighbor(HexDirection.N) in arc)
    }

    @Test
    fun `facing S includes hexes to the SE, S, and SW`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.S, map)
        assertTrue(origin.neighbor(HexDirection.SE) in arc)
        assertTrue(origin.neighbor(HexDirection.S) in arc)
        assertTrue(origin.neighbor(HexDirection.SW) in arc)
        assertFalse(origin.neighbor(HexDirection.N) in arc)
    }

    @Test
    fun `arc includes distant hexes in the forward direction`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        // Two hexes north
        val twoNorth = origin.neighbor(HexDirection.N).neighbor(HexDirection.N)
        assertTrue(twoNorth in arc)
    }

    @Test
    fun `arc does not include hexes outside the map`() {
        val origin = HexCoordinates(0, 0)
        val map = mapWithRadius(origin, 2)
        val arc = FiringArc.forwardArc(origin, HexDirection.N, map)
        // All hexes in arc should be on the map
        for (hex in arc) {
            assertTrue(hex in map.hexes, "Hex $hex should be on the map")
        }
    }

    @Test
    fun `torso twist NE shifts arc compared to N`() {
        val origin = HexCoordinates(4, 4)
        val map = mapWithRadius(origin, 5)
        val arcN = FiringArc.forwardArc(origin, HexDirection.N, map)
        val arcNE = FiringArc.forwardArc(origin, HexDirection.NE, map)
        // SE neighbor should be in NE arc but not in N arc
        assertTrue(origin.neighbor(HexDirection.SE) in arcNE)
        assertFalse(origin.neighbor(HexDirection.SE) in arcN)
        // NW neighbor should be in N arc but not in NE arc
        assertTrue(origin.neighbor(HexDirection.NW) in arcN)
        assertFalse(origin.neighbor(HexDirection.NW) in arcNE)
    }
}
