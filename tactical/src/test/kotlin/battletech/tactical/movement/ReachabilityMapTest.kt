package battletech.tactical.movement

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ReachabilityMapTest {

    private fun step(col: Int, row: Int, facing: HexDirection) =
        MovementStep(HexCoordinates(col, row), facing)

    private fun hex(col: Int, row: Int, facing: HexDirection, mpSpent: Int) =
        ReachableHex(
            position = HexCoordinates(col, row),
            facing = facing,
            mpSpent = mpSpent,
            path = listOf(step(col, row, facing)),
        )

    @Test
    fun `facingsByPosition groups multiple facings at same hex`() {
        val map = ReachabilityMap(
            mode = MovementMode.WALK,
            maxMP = 4,
            destinations = listOf(
                hex(1, 0, HexDirection.N, 1),
                hex(1, 0, HexDirection.SE, 2),
                hex(2, 0, HexDirection.N, 2),
            ),
        )

        val result = map.facingsByPosition()

        assertEquals(
            mapOf(
                HexCoordinates(1, 0) to setOf(HexDirection.N, HexDirection.SE),
                HexCoordinates(2, 0) to setOf(HexDirection.N),
            ),
            result,
        )
    }

    @Test
    fun `facingsByPosition with single facing per hex`() {
        val map = ReachabilityMap(
            mode = MovementMode.WALK,
            maxMP = 2,
            destinations = listOf(
                hex(1, 0, HexDirection.N, 1),
                hex(0, 1, HexDirection.SE, 1),
            ),
        )

        val result = map.facingsByPosition()

        assertEquals(
            mapOf(
                HexCoordinates(1, 0) to setOf(HexDirection.N),
                HexCoordinates(0, 1) to setOf(HexDirection.SE),
            ),
            result,
        )
    }

    @Test
    fun `facingsByPosition on empty map returns empty map`() {
        val map = ReachabilityMap(
            mode = MovementMode.WALK,
            maxMP = 4,
            destinations = emptyList(),
        )

        assertEquals(emptyMap<HexCoordinates, Set<HexDirection>>(), map.facingsByPosition())
    }

    @Test
    fun `facingsByPosition all six facings at same hex`() {
        val map = ReachabilityMap(
            mode = MovementMode.WALK,
            maxMP = 10,
            destinations = HexDirection.entries.map { dir -> hex(3, 3, dir, 3) },
        )

        val result = map.facingsByPosition()

        assertEquals(
            mapOf(HexCoordinates(3, 3) to HexDirection.entries.toSet()),
            result,
        )
    }
}
