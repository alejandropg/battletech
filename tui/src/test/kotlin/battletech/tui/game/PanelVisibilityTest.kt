package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.TurnPhase
import battletech.tui.aGameState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.view.DeclaredTargetsView
import battletech.tui.view.LogView
import battletech.tui.view.SidebarView
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

        assertEquals(setOf(LogView.INDEX, SidebarView.INDEX), visible)
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
        assertTrue(visible.contains(SidebarView.INDEX))
        assertTrue(visible.contains(DeclaredTargetsView.INDEX))
    }

    @Test
    fun `physical attack phase includes DECLARED TARGETS`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK),
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleIndices(appState)

        assertTrue(visible.contains(DeclaredTargetsView.INDEX))
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
}
