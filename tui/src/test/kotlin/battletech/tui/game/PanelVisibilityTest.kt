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
import battletech.tui.view.HelpView
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
    fun `movement phase shows only LOG, UNIT STATUS and HELP`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleKeys(appState)

        assertEquals(setOf(LogView.KEY, UnitStatusView.KEY, HelpView.KEY), visible)
    }

    @Test
    fun `weapon attack phase includes DECLARED TARGETS`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleKeys(appState)

        assertTrue(visible.contains(LogView.KEY))
        assertTrue(visible.contains(UnitStatusView.KEY))
        assertTrue(visible.contains(DeclaredTargetsView.KEY))
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

        val visible = PanelVisibility.visibleKeys(appState)

        assertFalse(visible.contains(DeclaredTargetsView.KEY))
    }

    @Test
    fun `movement phase does not include attack panels`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleKeys(appState)

        assertFalse(visible.contains(DeclaredTargetsView.KEY))
        assertFalse(visible.contains('3')) // TARGETS
        assertFalse(visible.contains('4')) // TARGET STATUS
    }

    @Test
    fun `HELP is visible even though it starts collapsed by default`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        )

        val visible = PanelVisibility.visibleKeys(appState)

        assertTrue(visible.contains(HelpView.KEY))
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

        val visible = PanelVisibility.visibleKeys(appState)

        assertTrue(visible.contains(AttackResultsView.KEY))
    }

    @Test
    fun `results panel shows during movement`() {
        val appState = AppState(
            gameState = emptyState,
            phase = MovementPhase.SelectingUnit,
            cursor = cursor,
        ).copy(lastAttackResults = listOf(aResult()))

        val visible = PanelVisibility.visibleKeys(appState)

        assertTrue(visible.contains(AttackResultsView.KEY))
    }

    @Test
    fun `results panel hidden during weapon attack phase`() {
        val appState = AppState(
            gameState = emptyState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            cursor = cursor,
        ).copy(lastAttackResults = listOf(aResult()))

        val visible = PanelVisibility.visibleKeys(appState)

        assertFalse(visible.contains(AttackResultsView.KEY))
    }
}
