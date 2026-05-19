package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.EndPhase
import battletech.tui.game.phase.HeatPhase
import battletech.tui.game.phase.InitiativePhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.PhaseServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class PhaseTickTest {

    private val services = PhaseServices(
        actionQueryService = ActionQueryService(
            MoveActionDefinition(),
            listOf(FireWeaponActionDefinition()),
        ),
        roller = DiceRoller.seeded(42),
    )

    private fun anAppState(
        phase: battletech.tui.game.phase.Phase = InitiativePhase,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState = TurnState.NULL,
    ) = AppState(gameState, turnState, phase, cursor)

    @Nested
    inner class InitiativePhaseTickTest {
        @Test
        fun `initiative rolls dice and builds turn state`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
            val p2 = aUnit(id = "p2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
            val gameState = aGameState(units = listOf(p1, p2))
            val state = anAppState(phase = InitiativePhase, gameState = gameState)

            val transition = InitiativePhase.tick(state, services)

            assertEquals(TurnPhase.MOVEMENT, transition.app.currentPhase)
            assertNotNull(transition.app.turnState)
            assertNotNull(transition.flash)
            assertEquals(true, transition.flash!!.text.startsWith("Initiative:"))
        }
    }

    @Nested
    inner class HeatPhaseTickTest {
        @Test
        fun `heat phase applies dissipation to all units`() {
            val unit1 = aUnit(id = "u1", name = "Atlas").copy(currentHeat = 12, heatSinkCapacity = 3)
            val unit2 = aUnit(id = "u2", name = "Hunchback").copy(currentHeat = 5, heatSinkCapacity = 5)
            val gameState = aGameState(units = listOf(unit1, unit2))
            val state = anAppState(phase = HeatPhase, gameState = gameState)

            val transition = HeatPhase.tick(state, services)

            assertEquals(TurnPhase.END, transition.app.currentPhase)
            assertEquals(9, transition.app.gameState.units[0].currentHeat)
            assertEquals(0, transition.app.gameState.units[1].currentHeat)
            assertNotNull(transition.flash)
            assert(transition.flash!!.text.contains("Atlas: 12→9"))
            assert(transition.flash.text.contains("Hunchback: 5→0"))
        }

        @Test
        fun `heat phase with no heat shows no heat message`() {
            val unit = aUnit(id = "u1").copy(currentHeat = 0, heatSinkCapacity = 3)
            val gameState = aGameState(units = listOf(unit))
            val state = anAppState(phase = HeatPhase, gameState = gameState)

            val transition = HeatPhase.tick(state, services)

            assertEquals("Heat: No heat to dissipate", transition.flash!!.text)
        }
    }

    @Nested
    inner class EndPhaseTickTest {
        @Test
        fun `end phase resets torso facings and advances to initiative`() {
            val state = anAppState(phase = EndPhase)

            val transition = EndPhase.tick(state, services)

            assertEquals(TurnPhase.INITIATIVE, transition.app.currentPhase)
            assertInstanceOf(InitiativePhase::class.java, transition.app.phase)
            assertEquals("Turn complete", transition.flash!!.text)
        }
    }

    @Nested
    inner class SelectingAttackerTickTest {
        @Test
        fun `tick seeds attack sequence when order is empty`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
            val p2 = aUnit(id = "p2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
            val gameState = aGameState(units = listOf(p1, p2))
            val initial = InitiativePhase.tick(anAppState(phase = InitiativePhase, gameState = gameState), services)
            val movementTurnState = initial.app.turnState
            val state = anAppState(
                phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
                gameState = gameState,
                turnState = movementTurnState,
            )

            val transition = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).tick(state, services)

            assertNotNull(transition)
            assert(transition!!.app.turnState.attackSequence.order.isNotEmpty())
            assertEquals("Weapon Attack Phase", transition.flash!!.text)
        }

        @Test
        fun `tick returns null when already seeded`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
            val gameState = aGameState(units = listOf(p1))
            val initial = InitiativePhase.tick(anAppState(phase = InitiativePhase, gameState = gameState), services)
            val turnState = initial.app.turnState.copy(
                attackSequence = ImpulseSequence(
                    listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1)),
                ),
                attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
            )
            val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
            val state = anAppState(phase = phase, gameState = gameState, turnState = turnState)

            val transition = phase.tick(state, services)

            assertEquals(null, transition)
        }
    }

    @Nested
    inner class CommitImpulseTest {
        @Test
        fun `commit on selecting attacker advances to next attacker when not done`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val p2 = aUnit(id = "p2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
            val gameState = aGameState(units = listOf(p1, p2))
            val turnState = TurnState(
                initiative = battletech.tactical.action.Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1))),
                attackSequence = ImpulseSequence(listOf(
                    battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1),
                    battletech.tactical.action.Impulse(PlayerId.PLAYER_2, 1),
                )),
                attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
            )
            val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
            val state = anAppState(phase = phase, gameState = gameState, turnState = turnState)

            val transition = battletech.tui.game.phase.commitAttackImpulse(state, TurnPhase.WEAPON_ATTACK, services)

            assertEquals(PlayerId.PLAYER_2, transition.app.turnState.activeAttackPlayer)
            assertEquals(TurnPhase.WEAPON_ATTACK, transition.app.currentPhase)
        }

        @Test
        fun `commit on last weapon attack impulse advances to physical attack`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1))
            val turnState = TurnState(
                initiative = battletech.tactical.action.Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1))),
                attackSequence = ImpulseSequence(listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1))),
                attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
            )
            val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
            val state = anAppState(phase = phase, gameState = gameState, turnState = turnState)

            val transition = battletech.tui.game.phase.commitAttackImpulse(state, TurnPhase.WEAPON_ATTACK, services)

            assertEquals(TurnPhase.PHYSICAL_ATTACK, transition.app.currentPhase)
            assertInstanceOf(AttackPhase.SelectingAttacker::class.java, transition.app.phase)
        }

        @Test
        fun `commit on last physical attack impulse advances to heat`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1))
            val turnState = TurnState(
                initiative = battletech.tactical.action.Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1))),
                attackSequence = ImpulseSequence(listOf(battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1))),
                attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
            )
            val phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK)
            val state = anAppState(phase = phase, gameState = gameState, turnState = turnState)

            val transition = battletech.tui.game.phase.commitAttackImpulse(state, TurnPhase.PHYSICAL_ATTACK, services)

            assertInstanceOf(HeatPhase::class.java, transition.app.phase)
            assertEquals(TurnPhase.HEAT, transition.app.currentPhase)
        }
    }

    @Nested
    inner class MovementAdvanceTest {
        @Test
        fun `advanceAfterMove stays in movement when impulses remain`() {
            val p1u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1u1))
            val turnState = TurnState(
                initiative = battletech.tactical.action.Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(
                    battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1),
                    battletech.tactical.action.Impulse(PlayerId.PLAYER_2, 1),
                )),
            )
            val state = anAppState(
                phase = MovementPhase.SelectingUnit,
                gameState = gameState,
                turnState = turnState,
            )

            val newApp = battletech.tui.game.phase.advanceAfterMove(state, gameState, p1u1.id)

            assertEquals(TurnPhase.MOVEMENT, newApp.currentPhase)
            assertEquals(PlayerId.PLAYER_2, newApp.turnState.activePlayer)
        }

        @Test
        fun `advanceAfterMove transitions to attack when all impulses complete`() {
            val p1u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1u1))
            val turnState = TurnState(
                initiative = battletech.tactical.action.Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(
                    battletech.tactical.action.Impulse(PlayerId.PLAYER_1, 1),
                )),
            )
            val state = anAppState(
                phase = MovementPhase.SelectingUnit,
                gameState = gameState,
                turnState = turnState,
            )

            val newApp = battletech.tui.game.phase.advanceAfterMove(state, gameState, p1u1.id)

            assertInstanceOf(AttackPhase.SelectingAttacker::class.java, newApp.phase)
            assertEquals(TurnPhase.WEAPON_ATTACK, newApp.currentPhase)
        }
    }
}
