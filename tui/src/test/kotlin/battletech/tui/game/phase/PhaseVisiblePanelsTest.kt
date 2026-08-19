package battletech.tui.game.phase

import battletech.tactical.model.TurnPhase
import battletech.tui.aGameState
import battletech.tui.anAppState
import battletech.tui.game.GamePanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class PhaseVisiblePanelsTest {

    private val gameState = aGameState()

    @Test
    fun `movement phase declares no panels`() {
        val app = anAppState(MovementPhase.SelectingUnit, gameState = gameState)
        assertEquals(emptySet<GamePanelId>(), MovementPhase.SelectingUnit.visiblePanels(app))
    }

    @Test
    fun `weapon-attack attacker selection reserves DECLARED TARGETS`() {
        val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)

        assertEquals(setOf(GamePanelId.DECLARED_TARGETS), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }

    @Test
    fun `physical-attack reuse of the attack phase does not reserve DECLARED TARGETS`() {
        val phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK)

        assertEquals(emptySet<GamePanelId>(), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }

    @Test
    fun `physical attacker selection declares no panels until targeting`() {
        val phase = PhysicalAttackPhase.SelectingAttacker()
        assertEquals(emptySet<GamePanelId>(), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }

    @Test
    fun `no phase ever declares BOARD — it is the PanelSet's main panel, not a side panel`() {
        val phases = listOf(
            MovementPhase.SelectingUnit,
            AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
            AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK),
            PhysicalAttackPhase.SelectingAttacker(),
        )
        for (phase in phases) {
            assertFalse(
                phase.visiblePanels(anAppState(phase, gameState = gameState)).contains(GamePanelId.BOARD),
                "$phase must never declare BOARD",
            )
        }
    }
}
