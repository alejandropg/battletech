package battletech.tactical.model

import battletech.tactical.attack.applyTorsoFacings
import battletech.tactical.attack.resetTorsoFacings
import battletech.tactical.heat.movementHeatSource
import battletech.tactical.session.applyHeatPhase
import battletech.tactical.unit.HeatSource
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
        tonnage = 100,
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
    inner class ApplyHeatPhaseTest {
        @Test
        fun `reduces heat by sink capacity`() {
            val unit = aUnit(currentHeat = 10, heatSinkCapacity = 4)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatPhase()

            assertEquals(6, result.units[0].currentHeat)
        }

        @Test
        fun `heat does not go below zero`() {
            val unit = aUnit(currentHeat = 2, heatSinkCapacity = 10)
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatPhase()

            assertEquals(0, result.units[0].currentHeat)
        }

        @Test
        fun `applies to all units`() {
            val u1 = aUnit(id = "u1", currentHeat = 8, heatSinkCapacity = 3)
            val u2 = aUnit(id = "u2", currentHeat = 4, heatSinkCapacity = 4)
            val gameState = GameState(listOf(u1, u2), map)

            val result = gameState.applyHeatPhase()

            assertEquals(5, result.units[0].currentHeat)
            assertEquals(0, result.units[1].currentHeat)
        }

        @Test
        fun `folds generated heat before dissipating and clears the list`() {
            val unit = aUnit(currentHeat = 5, heatSinkCapacity = 10).copy(
                // walking + medium laser = +1 +3 = +4
                heatGeneratedThisTurn = listOf(
                    movementHeatSource(MovementMode.WALK, 2)!!,
                    HeatSource("Medium Laser", 3),
                ),
            )
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatPhase()

            // 5 + 4 - 10 = -1 -> floored at 0
            assertEquals(0, result.units[0].currentHeat)
            assertEquals(emptyList<HeatSource>(), result.units[0].heatGeneratedThisTurn)
        }

        @Test
        fun `net heat climbs when generation exceeds dissipation`() {
            val unit = aUnit(currentHeat = 10, heatSinkCapacity = 10).copy(
                heatGeneratedThisTurn = listOf(HeatSource("PPC", 10), HeatSource("Running", 2)),
            )
            val gameState = GameState(listOf(unit), map)

            val result = gameState.applyHeatPhase()

            // 10 + 12 - 10 = 12
            assertEquals(12, result.units[0].currentHeat)
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
