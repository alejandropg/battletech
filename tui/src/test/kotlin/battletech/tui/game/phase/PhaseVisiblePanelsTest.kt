package battletech.tui.game.phase

import battletech.tactical.model.TurnPhase
import battletech.tui.aGameState
import battletech.tui.anAppState
import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PhaseVisiblePanelsTest {

    private val gameState = aGameState()

    @Test
    fun `movement phase declares no panels`() {
        val app = anAppState(MovementPhase.SelectingUnit, gameState = gameState)
        assertEquals(emptySet<PanelId>(), MovementPhase.SelectingUnit.visiblePanels(app))
    }

    @Test
    fun `weapon-attack attacker selection reserves DECLARED TARGETS`() {
        val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)

        assertEquals(setOf(PanelId.DECLARED_TARGETS), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }

    @Test
    fun `physical-attack reuse of the attack phase does not reserve DECLARED TARGETS`() {
        val phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK)

        assertEquals(emptySet<PanelId>(), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }

    @Test
    fun `physical attacker selection declares no panels until targeting`() {
        val phase = PhysicalAttackPhase.SelectingAttacker()
        assertEquals(emptySet<PanelId>(), phase.visiblePanels(anAppState(phase, gameState = gameState)))
    }
}
