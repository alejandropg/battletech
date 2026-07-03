package battletech.tui.game

import battletech.tactical.session.Impulse
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.HexCoordinates
import battletech.tactical.session.TurnState
import battletech.tui.aGameState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AttackSelectingAttackerPhaseTest {

    private fun anAppState(
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState = TurnState.NULL,
        attackTurnPhase: TurnPhase = TurnPhase.WEAPON_ATTACK,
    ) = AppState(gameState, turnState, AttackPhase.SelectingAttacker(attackTurnPhase), cursor)

    private fun enterKey(): KeyboardEvent = KeyboardEvent("Enter")
    private fun cKey(): KeyboardEvent = KeyboardEvent("c")
    private fun tabKey(): KeyboardEvent = KeyboardEvent("Tab")

    @Nested
    inner class TrySelectUnitTest {
        @Test
        fun `enters declaring phase for valid attacker`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1Unit))
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(enterKey(), state)

            assertNotNull(result)
            assertInstanceOf(AttackPhase.Declaring::class.java, result!!.app.phase)
        }

        @Test
        fun `rejects opposing unit`() {
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p2Unit))
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(enterKey(), state)

            assertNotNull(result)
            assertEquals("Not your unit", result!!.flash?.text)
        }
    }

    @Nested
    inner class CommitDeclarationsTest {
        @Test
        fun `commit advances impulse even when no weapons were toggled`() {
            val turnState = aTurnState()
            val state = anAppState(turnState = turnState)

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state)

            assertNotNull(result)
            assertEquals(1, result!!.app.turnState.attack.sequence.currentIndex)
        }

        @Test
        fun `commit hands control from loser to winner, then resolves on second commit`() {
            val turnState = aTurnState(
                attackOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 2),
                    Impulse(PlayerId.PLAYER_2, 3),
                ),
            )
            val state = anAppState(turnState = turnState)

            val afterLoser = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state)!!
            assertEquals(PlayerId.PLAYER_2, afterLoser.app.turnState.attack.activePlayer)
            assertEquals(TurnPhase.WEAPON_ATTACK, afterLoser.app.currentPhase)

            val nextPhase = afterLoser.app.phase
            val afterWinner = nextPhase.handle(cKey(), afterLoser.app)!!
            assertEquals(TurnPhase.PHYSICAL_ATTACK, afterWinner.app.currentPhase)
        }

        @Test
        fun `commit when no turn state does nothing`() {
            val state = anAppState()
            // No turn state — commit returns unchanged state
            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state)
            assertNotNull(result)
            assertNull(result!!.flash)
        }
    }

    @Nested
    inner class CycleUnitTest {
        // Tab is unified with the movement phase: it cycles to the next
        // selectable attacker AND enters Declaring for it (previously it only
        // moved the cursor).
        @Test
        fun `cycles to next selectable attacker and enters Declaring`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2))
            val gameState = aGameState(units = listOf(u1, u2))
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = aTurnState())

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val declaring = assertInstanceOf(AttackPhase.Declaring::class.java, result.app.phase)
            assertEquals(u2.id, declaring.unitId)
        }

        @Test
        fun `Tab with cursor on empty hex enters Declaring for the first attacker`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2))
            val gameState = aGameState(units = listOf(u1))
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = aTurnState())

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val declaring = assertInstanceOf(AttackPhase.Declaring::class.java, result.app.phase)
            assertEquals(u1.id, declaring.unitId)
        }

        @Test
        fun `Tab with no selectable attackers is a no-op`() {
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = aGameState(), turnState = aTurnState())

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(state, result!!.app)
        }
    }
}
