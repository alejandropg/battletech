package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.Impulse
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.PhaseServices
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AttackSelectingAttackerPhaseTest {

    private val services = PhaseServices(
        actionQueryService = ActionQueryService(
            MoveActionDefinition(),
            listOf(FireWeaponActionDefinition()),
        ),
    )

    private fun aTurnState(
        attackOrder: List<Impulse> = listOf(
            Impulse(PlayerId.PLAYER_1, 1),
            Impulse(PlayerId.PLAYER_2, 1),
        ),
        currentAttackImpulseIndex: Int = 0,
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        attackSequence = ImpulseSequence(attackOrder, currentAttackImpulseIndex),
        attackImpulse = ImpulseDeclarations(
            attackOrder.getOrNull(currentAttackImpulseIndex)?.player ?: PlayerId.PLAYER_1,
        ),
    )

    private fun anAppState(
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState? = null,
        attackTurnPhase: TurnPhase = TurnPhase.WEAPON_ATTACK,
    ) = AppState(
        gameState = gameState,
        cursor = cursor,
        phase = AttackPhase.SelectingAttacker(attackTurnPhase),
        turnState = turnState,
    )

    private fun enterKey(): KeyboardEvent = KeyboardEvent("Enter")
    private fun cKey(): KeyboardEvent = KeyboardEvent("c")

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

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(enterKey(), state, services)

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

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(enterKey(), state, services)

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

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state, services)

            assertNotNull(result)
            assertEquals(1, result!!.app.turnState!!.attackSequence.currentIndex)
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

            val afterLoser = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state, services)!!
            assertEquals(PlayerId.PLAYER_2, afterLoser.app.turnState!!.activeAttackPlayer)
            assertEquals(TurnPhase.WEAPON_ATTACK, afterLoser.app.currentPhase)

            val nextPhase = afterLoser.app.phase
            val afterWinner = nextPhase.handle(cKey(), afterLoser.app, services)!!
            assertEquals(TurnPhase.PHYSICAL_ATTACK, afterWinner.app.currentPhase)
        }

        @Test
        fun `commit when no turn state does nothing`() {
            val state = anAppState(turnState = null)
            // No turn state — commit returns unchanged state
            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), state, services)
            assertNotNull(result)
            assertNull(result!!.flash)
        }
    }
}
