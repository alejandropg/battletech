package battletech.tui.game.phase

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.ReachabilityMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.anAppState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PhaseStatusTest {

    private val unit = aUnit(id = "W1", name = "Wolverine WVR-6R")
    private val reachability = ReachabilityMap(MovementMode.WALK, maxMP = 5, destinations = emptyList())

    @Test
    fun `selected action phases expose their unit`() {
        val phases = listOf(
            MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(reachability),
                currentModeIndex = 0,
                hoveredDestination = null,
            ),
            MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(reachability),
                currentModeIndex = 0,
                hex = HexCoordinates(0, 0),
                options = emptyList(),
            ),
            AttackPhase.Declaring(
                attackTurnPhase = TurnPhase.WEAPON_ATTACK,
                unitId = unit.id,
                allocation = WeaponAllocation(torsoFacing = HexDirection.N),
            ),
            PhysicalAttackPhase.Declaring(
                unitId = unit.id,
                cursorIndex = 0,
                assignments = emptyMap(),
            ),
        )

        for (phase in phases) {
            val app = anAppState(phase, gameState = aGameState(units = listOf(unit)))

            assertEquals(unit.id, phase.status(app).actionUnitId)
        }
    }

    @Test
    fun `unit selection phases expose no action unit`() {
        val phases = listOf(
            MovementPhase.SelectingUnit,
            AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            PhysicalAttackPhase.SelectingAttacker(),
        )

        for (phase in phases) {
            val app = anAppState(phase, gameState = aGameState(units = listOf(unit)))

            assertNull(phase.status(app).actionUnitId)
        }
    }
}
