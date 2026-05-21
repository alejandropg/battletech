package battletech.tactical.model

import battletech.tactical.attack.applyTorsoFacings
import battletech.tactical.attack.resetTorsoFacings
import battletech.tactical.session.applyHeatDissipation
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class GameStateTransformsTest {

    private fun aUnit(
        id: String = "u1",
        currentHeat: Int = 0,
        heatSinkCapacity: Int = 10,
        facing: HexDirection = HexDirection.N,
        torsoFacing: HexDirection = HexDirection.N,
    ): CombatUnit = CombatUnit(
        id = UnitId(id),
        owner = PlayerId.PLAYER_1,
        name = "Atlas",
        gunnerySkill = 4,
        pilotingSkill = 5,
        weapons = emptyList(),
        position = HexCoordinates(0, 0),
        facing = facing,
        torsoFacing = torsoFacing,
        walkingMP = 0,
        runningMP = 0,
        jumpMP = 0,
        armor = ArmorLayout(9, 47, 14, 32, 10, 32, 10, 34, 34, 41, 41),
        heatSinkCapacity = heatSinkCapacity,
        internalStructure = InternalStructureLayout(3, 31, 21, 21, 17, 17, 21, 21),
        currentHeat = currentHeat,
    )

    private val map = GameMap(
        mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR)),
    )

    @Nested
    inner class ApplyHeatDissipationTest {
        @Test
        fun `reduces heat by sink capacity`() {
            val unit = aUnit(currentHeat = 10, heatSinkCapacity = 4)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatDissipation()

            assertEquals(6, result.units[0].currentHeat)
        }

        @Test
        fun `heat does not go below zero`() {
            val unit = aUnit(currentHeat = 2, heatSinkCapacity = 10)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatDissipation()

            assertEquals(0, result.units[0].currentHeat)
        }

        @Test
        fun `applies to all units`() {
            val u1 = aUnit(id = "u1", currentHeat = 8, heatSinkCapacity = 3)
            val u2 = aUnit(id = "u2", currentHeat = 4, heatSinkCapacity = 4)
            val gameState = GameState(listOf(u1, u2), map)

            val result = gameState.applyHeatDissipation()

            assertEquals(5, result.units[0].currentHeat)
            assertEquals(0, result.units[1].currentHeat)
        }
    }

    @Nested
    inner class ApplyTorsoFacingsTest {
        @Test
        fun `applies torso facing to listed unit`() {
            val unit = aUnit(facing = HexDirection.N, torsoFacing = HexDirection.N)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyTorsoFacings(mapOf(unit.id to HexDirection.NE))

            assertEquals(HexDirection.NE, result.units[0].torsoFacing)
        }

        @Test
        fun `empty map leaves state unchanged`() {
            val unit = aUnit()
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyTorsoFacings(emptyMap())

            assertEquals(gameState, result)
        }
    }

    @Nested
    inner class ResetTorsoFacingsTest {
        @Test
        fun `resets torso to leg facing`() {
            val unit = aUnit(facing = HexDirection.N, torsoFacing = HexDirection.NE)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.resetTorsoFacings()

            assertEquals(HexDirection.N, result.units[0].torsoFacing)
        }
    }
}
