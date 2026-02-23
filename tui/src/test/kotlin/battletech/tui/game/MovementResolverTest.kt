package battletech.tui.game

import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MovementResolverTest {

    @Test
    fun `applying movement updates unit position`() {
        val unit = aUnit(id = "atlas", position = HexCoordinates(0, 0))
        val gameState = aGameState(units = listOf(unit))
        val destination = ReachableHex(
            position = HexCoordinates(2, 1),
            facing = HexDirection.SE,
            mpSpent = 2,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.SE),
                MovementStep(HexCoordinates(2, 1), HexDirection.SE),
            ),
        )

        val result = MovementResolver.apply(gameState, unit.id, destination)

        val movedUnit = result.units.find { it.id == unit.id }!!
        assertEquals(HexCoordinates(2, 1), movedUnit.position)
        assertEquals(HexDirection.SE, movedUnit.facing)
    }

    @Test
    fun `other units are unchanged`() {
        val atlas = aUnit(id = "atlas", position = HexCoordinates(0, 0))
        val hunchback = aUnit(id = "hunchback", position = HexCoordinates(5, 5))
        val gameState = aGameState(units = listOf(atlas, hunchback))
        val destination = ReachableHex(
            position = HexCoordinates(1, 1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(1, 1), HexDirection.N)),
        )

        val result = MovementResolver.apply(gameState, atlas.id, destination)

        assertEquals(HexCoordinates(5, 5), result.units.find { it.id == hunchback.id }!!.position)
    }
}
