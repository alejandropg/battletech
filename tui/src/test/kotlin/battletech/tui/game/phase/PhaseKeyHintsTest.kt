package battletech.tui.game.phase

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.UnitId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every reachable [Phase] state must declare local HELP-panel content: a non-blank
 * [Phase.keyContext] title and a non-empty [Phase.keyHints] list. Mirrors
 * [PhaseVisiblePanelsTest]'s one-instance-per-state coverage.
 */
internal class PhaseKeyHintsTest {

    private val movementBrowsing = MovementPhase.Browsing(
        unitId = UnitId("u1"),
        modes = emptyList(),
        currentModeIndex = 0,
        hoveredDestination = null,
    )

    private val movementSelectingFacing = MovementPhase.SelectingFacing(
        unitId = UnitId("u1"),
        modes = emptyList(),
        currentModeIndex = 0,
        hex = HexCoordinates(0, 0),
        options = emptyList(),
    )

    private val attackDeclaring = AttackPhase.Declaring(
        attackTurnPhase = TurnPhase.WEAPON_ATTACK,
        unitId = UnitId("u1"),
        allocation = WeaponAllocation(
            torsoFacing = HexDirection.N,
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 0,
        ),
    )

    private val physicalDeclaring = PhysicalAttackPhase.Declaring(
        unitId = UnitId("u1"),
        cursorIndex = 0,
        assignments = emptyMap(),
    )

    private val phases: List<Phase> = listOf(
        MovementPhase.SelectingUnit,
        movementBrowsing,
        movementSelectingFacing,
        AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
        attackDeclaring,
        PhysicalAttackPhase.SelectingAttacker(),
        physicalDeclaring,
    )

    @Test
    fun `every phase declares a non-blank key context`() {
        for (phase in phases) {
            assertFalse(phase.keyContext().isBlank()) { "${phase::class.simpleName} has a blank keyContext" }
        }
    }

    @Test
    fun `every phase declares at least one key hint`() {
        for (phase in phases) {
            assertTrue(phase.keyHints().isNotEmpty()) { "${phase::class.simpleName} has no keyHints" }
        }
    }

}
