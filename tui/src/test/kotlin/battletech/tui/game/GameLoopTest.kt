package battletech.tui.game

import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import battletech.tactical.action.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GameLoopTest {

    @Test
    fun `quit returns quit result`() {
        val gameState = aGameState(units = listOf(aUnit()))
        val gameLoop = GameLoop(gameState, TurnPhase.MOVEMENT)

        val result = gameLoop.handleAction(InputAction.Quit)

        assert(result is GameLoopResult.Quit)
    }

    @Test
    fun `initial phase is movement`() {
        val gameState = aGameState(units = listOf(aUnit()))
        val gameLoop = GameLoop(gameState, TurnPhase.MOVEMENT)

        assertEquals(TurnPhase.MOVEMENT, gameLoop.currentPhase)
    }

    @Test
    fun `advancing from movement goes to weapon attack`() {
        assertEquals(TurnPhase.WEAPON_ATTACK, GameLoop.nextPhase(TurnPhase.MOVEMENT))
    }

    @Test
    fun `advancing from weapon attack goes to physical attack`() {
        assertEquals(TurnPhase.PHYSICAL_ATTACK, GameLoop.nextPhase(TurnPhase.WEAPON_ATTACK))
    }

    @Test
    fun `advancing from end goes to initiative`() {
        assertEquals(TurnPhase.INITIATIVE, GameLoop.nextPhase(TurnPhase.END))
    }
}
