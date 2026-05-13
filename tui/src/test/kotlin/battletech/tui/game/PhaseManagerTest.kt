package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.Impulse
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class PhaseManagerTest {

    private val actionQueryService = ActionQueryService(
        MoveActionDefinition(),
        listOf(FireWeaponActionDefinition()),
    )
    private val manager = PhaseManager(
        movementController = MovementController(actionQueryService),
        attackController = AttackController(),
    )

    private fun aTurnState(
        movementOrder: List<Impulse> = listOf(
            Impulse(PlayerId.PLAYER_1, 1),
            Impulse(PlayerId.PLAYER_2, 1),
        ),
        currentImpulseIndex: Int = 0,
        movedUnitIds: Set<UnitId> = emptySet(),
        unitsMovedInCurrentImpulse: Int = 0,
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(movementOrder, currentImpulseIndex),
        movedUnitIds = movedUnitIds,
        unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse,
    )

    private fun anAppState(
        currentPhase: TurnPhase = TurnPhase.MOVEMENT,
        phase: PhaseState = IdlePhaseState,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        turnState: TurnState? = null,
    ) = AppState(
        gameState = aGameState(),
        currentPhase = currentPhase,
        cursor = cursor,
        phase = phase,
        turnState = turnState,
    )

    @Nested
    inner class FromOutcomeTest {
        @Test
        fun `Continue returns new PhaseState`() {
            val newPhase = IdlePhaseState
            val state = anAppState()

            val result = manager.fromOutcome(PhaseOutcome.Continue(newPhase), state)

            assertEquals(newPhase, result.appState.phase)
            assertNull(result.flash)
        }

        @Test
        fun `Cancelled returns idle during movement`() {
            val turnState = aTurnState()
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                turnState = turnState,
            )

            val result = manager.fromOutcome(PhaseOutcome.Cancelled, state)

            assertInstanceOf(IdlePhaseState::class.java, result.appState.phase)
            assertEquals(movementPrompt(turnState), phasePrompt(result.appState))
        }

        @Test
        fun `Cancelled returns idle during weapon attack`() {
            val turnState = aTurnState().copy(
                attackSequence = ImpulseSequence(listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                )),
            )
            val state = anAppState(
                currentPhase = TurnPhase.WEAPON_ATTACK,
                turnState = turnState,
            )

            val result = manager.fromOutcome(PhaseOutcome.Cancelled, state)

            assertInstanceOf(IdlePhaseState::class.java, result.appState.phase)
            assertEquals(attackPrompt(turnState), phasePrompt(result.appState))
        }

        @Test
        fun `Complete during movement advances impulse`() {
            val unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(unit))
            val newGameState = aGameState(units = listOf(unit.copy(position = HexCoordinates(1, 1))))
            val turnState = aTurnState(
                movementOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                ),
            )
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                turnState = turnState,
            ).copy(gameState = gameState)

            val result = manager.fromOutcome(PhaseOutcome.Complete(newGameState, unit.id), state)

            assertEquals(TurnPhase.MOVEMENT, result.appState.currentPhase)
            assertEquals(PlayerId.PLAYER_2, result.appState.turnState!!.activePlayer)
        }

        @Test
        fun `Complete without turnState advances phase directly`() {
            val newGameState = aGameState(units = listOf(aUnit(position = HexCoordinates(5, 5))))
            val state = anAppState(currentPhase = TurnPhase.WEAPON_ATTACK)

            val result = manager.fromOutcome(PhaseOutcome.Complete(newGameState), state)

            assertEquals(TurnPhase.PHYSICAL_ATTACK, result.appState.currentPhase)
            assertInstanceOf(IdlePhaseState::class.java, result.appState.phase)
        }
    }
}
