package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
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
        movementOrder: List<MovementImpulse> = listOf(
            MovementImpulse(PlayerId.PLAYER_1, 1),
            MovementImpulse(PlayerId.PLAYER_2, 1),
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
        movementOrder = movementOrder,
        currentImpulseIndex = currentImpulseIndex,
        movedUnitIds = movedUnitIds,
        unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse,
    )

    private fun anAppState(
        currentPhase: TurnPhase = TurnPhase.MOVEMENT,
        phase: ActivePhase = manager.idle(),
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
    inner class WrapTest {
        @Test
        fun `wraps Idle into IdlePhase`() {
            val idle = PhaseState.Idle("test prompt")

            val result = manager.wrap(idle)

            assertInstanceOf(IdlePhase::class.java, result)
            assertEquals(idle, result.state)
        }

        @Test
        fun `wraps Browsing into BrowsingPhase`() {
            val browsing = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = emptyList(),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
                prompt = "test",
            )

            val result = manager.wrap(browsing)

            assertInstanceOf(BrowsingPhase::class.java, result)
            assertEquals(browsing, result.state)
        }

        @Test
        fun `wraps SelectingFacing into FacingPhase`() {
            val facing = PhaseState.Movement.SelectingFacing(
                unitId = UnitId("u1"),
                modes = emptyList(),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 1),
                options = emptyList(),
                path = emptyList(),
                prompt = "Select facing",
            )

            val result = manager.wrap(facing)

            assertInstanceOf(FacingPhase::class.java, result)
            assertEquals(facing, result.state)
        }

        @Test
        fun `wraps Attack into AttackPhase`() {
            val attack = PhaseState.Attack(
                unitId = UnitId("u1"),
                attackPhase = TurnPhase.WEAPON_ATTACK,
                torsoFacing = battletech.tactical.model.HexDirection.N,
                arc = emptySet(),
                validTargetIds = emptySet(),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "test",
            )

            val result = manager.wrap(attack)

            assertInstanceOf(AttackPhase::class.java, result)
            assertEquals(attack, result.state)
        }
    }

    @Nested
    inner class FromOutcomeTest {
        @Test
        fun `Continue wraps PhaseState into ActivePhase`() {
            val newPhase = PhaseState.Idle("new prompt")
            val state = anAppState()

            val result = manager.fromOutcome(PhaseOutcome.Continue(newPhase), state)

            assertEquals(newPhase, result.appState.phase.state)
            assertNull(result.flash)
        }

        @Test
        fun `Cancelled returns idle with movement prompt during movement`() {
            val turnState = aTurnState()
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                turnState = turnState,
            )

            val result = manager.fromOutcome(PhaseOutcome.Cancelled, state)

            val idle = result.appState.phase.state as PhaseState.Idle
            assertEquals(movementPrompt(turnState), idle.prompt)
        }

        @Test
        fun `Cancelled returns idle with attack prompt during weapon attack`() {
            val turnState = aTurnState().copy(
                attackOrder = listOf(
                    MovementImpulse(PlayerId.PLAYER_1, 1),
                    MovementImpulse(PlayerId.PLAYER_2, 1),
                ),
            )
            val state = anAppState(
                currentPhase = TurnPhase.WEAPON_ATTACK,
                turnState = turnState,
            )

            val result = manager.fromOutcome(PhaseOutcome.Cancelled, state)

            val idle = result.appState.phase.state as PhaseState.Idle
            assertEquals(attackPrompt(turnState), idle.prompt)
        }

        @Test
        fun `Complete during movement advances impulse`() {
            val unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(unit))
            val newGameState = aGameState(units = listOf(unit.copy(position = HexCoordinates(1, 1))))
            val turnState = aTurnState(
                movementOrder = listOf(
                    MovementImpulse(PlayerId.PLAYER_1, 1),
                    MovementImpulse(PlayerId.PLAYER_2, 1),
                ),
            )
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                turnState = turnState,
            ).copy(gameState = gameState)

            val result = manager.fromOutcome(PhaseOutcome.Complete(newGameState), state)

            assertEquals(TurnPhase.MOVEMENT, result.appState.currentPhase)
            assertEquals(PlayerId.PLAYER_2, result.appState.turnState!!.activePlayer)
        }

        @Test
        fun `Complete without turnState advances phase directly`() {
            val newGameState = aGameState(units = listOf(aUnit(position = HexCoordinates(5, 5))))
            val state = anAppState(currentPhase = TurnPhase.WEAPON_ATTACK)

            val result = manager.fromOutcome(PhaseOutcome.Complete(newGameState), state)

            assertEquals(TurnPhase.PHYSICAL_ATTACK, result.appState.currentPhase)
            assertInstanceOf(PhaseState.Idle::class.java, result.appState.phase.state)
        }
    }
}
