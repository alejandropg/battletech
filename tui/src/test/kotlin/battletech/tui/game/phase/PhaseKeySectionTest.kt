package battletech.tui.game.phase

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.UnitId
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every reachable [Phase] state must declare a [Phase.keyContext] whose keymap layer renders local
 * HELP-panel content: a non-blank title and a non-empty hints list. Mirrors
 * [PhaseVisiblePanelsTest]'s one-instance-per-state coverage.
 */
internal class PhaseKeySectionTest {

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

    private val keys = Keybindings.DEFAULT

    @Test
    fun `every phase's key context declares a non-blank title`() {
        for (phase in phases) {
            assertFalse(keys.hints(phase.keyContext).title.isBlank()) { "${phase::class.simpleName}'s ${phase.keyContext} has a blank title" }
        }
    }

    @Test
    fun `every phase's key context declares at least one key hint`() {
        for (phase in phases) {
            assertTrue(keys.hints(phase.keyContext).hints.isNotEmpty()) { "${phase::class.simpleName}'s ${phase.keyContext} has no key hints" }
        }
    }
}
