package battletech.tui.game

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.RangeBand
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.UnitId
import battletech.tui.aGameState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.PhysicalAttackPhase
import battletech.tui.view.AttackResultsView
import battletech.tui.view.DeclaredTargetsView
import battletech.tui.view.LogView
import battletech.tui.view.UnitStatusView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelVisibilityTest {

    private val emptyState = aGameState()
    private val cursor = HexCoordinates(0, 0)

    @Test
    fun `movement phase shows only LOG and UNIT STATUS`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleIndices(appState)

        assertEquals(setOf(LogView.INDEX, UnitStatusView.INDEX), visible)
    }

    @Test
    fun `weapon attack phase includes DECLARED TARGETS`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleIndices(appState)

        assertTrue(visible.contains(LogView.INDEX))
        assertTrue(visible.contains(UnitStatusView.INDEX))
        assertTrue(visible.contains(DeclaredTargetsView.INDEX))
    }

    @Test
    fun `physical attack phase does not reserve DECLARED TARGETS`() {
        // The dedicated physical-attack flow does not populate the declared-targets
        // panel, so reserving its column would render as a blank gap between the
        // tactical map and the attack-results panel. The freed width goes to the map.
        val appState = AppState(
            gameState = emptyState,
            phase = PhysicalAttackPhase.SelectingAttacker(),
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleIndices(appState)

        assertFalse(visible.contains(DeclaredTargetsView.INDEX))
    }

    @Test
    fun `movement phase does not include attack panels`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleIndices(appState)

        assertFalse(visible.contains(DeclaredTargetsView.INDEX))
        assertFalse(visible.contains(3)) // TARGETS
        assertFalse(visible.contains(4)) // TARGET STATUS
    }

    private fun aResult() = AttackResult.Miss(
        attackerId = UnitId("a"),
        targetId = UnitId("b"),
        weaponName = "Med Laser",
        targetNumber = 7,
        toHitRoll = DiceRoll(2, 3),
        gunnery = 4,
        rangeBand = RangeBand.SHORT,
    )

    @Test
    fun `results panel shows during physical attack right after weapon resolution`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK),
            cursor = cursor,
        ).copy(lastAttackResults = listOf(aResult()))

        val visible = PanelVisibility.visibleIndices(appState)

        assertTrue(visible.contains(AttackResultsView.INDEX))
    }

    @Test
    fun `results panel shows during movement`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        ).copy(lastAttackResults = listOf(aResult()))

        val visible = PanelVisibility.visibleIndices(appState)

        assertTrue(visible.contains(AttackResultsView.INDEX))
    }

    @Test
    fun `results panel hidden during weapon attack phase`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            cursor = cursor,
        ).copy(lastAttackResults = listOf(aResult()))

        val visible = PanelVisibility.visibleIndices(appState)

        assertFalse(visible.contains(AttackResultsView.INDEX))
    }
}
