package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.PhaseServices
import battletech.tui.game.phase.commitAttackImpulse
import battletech.tui.game.phase.enterDeclaring
import com.github.ajalt.mordant.input.KeyboardEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AttackPhaseTest {

    private val services = PhaseServices(
        actionQueryService = ActionQueryService(
            MoveActionDefinition(),
            listOf(FireWeaponActionDefinition()),
        ),
    )

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map5x5 = aGameMap(cols = 5, rows = 5)
    private val map7x7 = aGameMap(cols = 7, rows = 7)

    private fun baseTurnState(): TurnState = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 3))),
        attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
    )

    private fun anAppState(
        phase: battletech.tui.game.phase.Phase,
        gameState: GameState,
        turnState: TurnState,
        cursor: HexCoordinates = HexCoordinates(0, 0),
    ) = AppState(gameState = gameState, cursor = cursor, phase = phase, turnState = turnState)

    @Nested
    inner class EnterDeclaringTest {
        @Test
        fun `enter populates Declaring phase`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.NE,
            )
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
            val gameState = GameState(listOf(unit, enemy), map5x5)

            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, baseTurnState())

            assertEquals(unit.id, phase.unitId)
            assertEquals(unit.facing, phase.torsoFacing)
        }

        @Test
        fun `enter with no enemies in arc gives empty targets`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.N,
            )
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 4))
            val gameState = GameState(listOf(unit, enemy), map5x5)

            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, baseTurnState())

            assertTrue(validTargets(unit, phase.torsoFacing, gameState).isEmpty())
        }
    }

    @Nested
    inner class DeclaringHandleTest {

        @Test
        fun `weapon assignments survive tab to other attacker and back`() {
            val unitA = aUnit(
                id = "a", weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 3), facing = HexDirection.N,
            )
            val unitB = aUnit(
                id = "b", weapons = listOf(mediumLaser()),
                position = HexCoordinates(4, 3), facing = HexDirection.N,
            )
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
            val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
            val turnState = baseTurnState()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            // Toggle weapon on A
            val afterToggle = phaseA.handle(KeyboardEvent(" "), state, services)!!
            val toggled = afterToggle.app.phase as AttackPhase.Declaring
            assertTrue(toggled.weaponAssignments[enemy.id]?.contains(0) == true)

            // Tab to B
            val toB = toggled.handle(KeyboardEvent("Tab"), afterToggle.app, services)!!

            // Re-enter A
            val reEntered = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, toB.app.gameState, toB.app.turnState!!)
            assertTrue(reEntered.weaponAssignments.values.any { it.isNotEmpty() })
        }

        @Test
        fun `torso twist clockwise updates torso facing`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(listOf(unit), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val result = phase.handle(KeyboardEvent("ArrowRight"), state, services)

            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, newPhase.torsoFacing)
        }

        @Test
        fun `torso twist counterclockwise updates torso facing`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(listOf(unit), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val result = phase.handle(KeyboardEvent("ArrowLeft"), state, services)

            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NW, newPhase.torsoFacing)
        }

        @Test
        fun `torso twist beyond one hex-side is rejected`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(listOf(unit), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val once = phase.handle(KeyboardEvent("ArrowRight"), state, services)!!
            val twisted = once.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, twisted.torsoFacing)

            val twice = twisted.handle(KeyboardEvent("ArrowRight"), once.app, services)!!
            val stillSame = twice.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, stillSame.torsoFacing)
        }

        @Test
        fun `weapon toggle on and off works`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(listOf(unit, enemy), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val on = phase.handle(KeyboardEvent(" "), state, services)!!
            val onPhase = on.app.phase as AttackPhase.Declaring
            assertTrue(0 in (onPhase.weaponAssignments[enemy.id] ?: emptySet()))

            val off = onPhase.handle(KeyboardEvent(" "), on.app, services)!!
            val offPhase = off.app.phase as AttackPhase.Declaring
            assertFalse(0 in (offPhase.weaponAssignments[enemy.id] ?: emptySet()))
        }

        @Test
        fun `commit after weapon assignment saves declaration`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(listOf(unit, enemy), map5x5)
            val turnState = TurnState(
                initiative = Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
                attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
                attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
            )
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val toggled = phase.handle(KeyboardEvent(" "), state, services)!!
            val togglePhase = toggled.app.phase as AttackPhase.Declaring
            assertTrue(togglePhase.weaponAssignments[enemy.id]?.contains(0) == true)

            // Commit — last impulse so resolve and advance to physical attack
            val committed = togglePhase.handle(KeyboardEvent("c"), toggled.app, services)!!
            assertEquals(TurnPhase.PHYSICAL_ATTACK, committed.app.currentPhase)
        }

        @Test
        fun `commitImpulse returns torso facings for committed units`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.N,
            )
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
            val gameState = GameState(listOf(unit, enemy), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            // Twist torso clockwise (N -> NE)
            val twisted = phase.handle(KeyboardEvent("ArrowRight"), state, services)!!
            val twistedPhase = twisted.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, twistedPhase.torsoFacing)

            // Commit the impulse
            val committed = commitAttackImpulse(twisted.app, TurnPhase.WEAPON_ATTACK, services)

            val updatedUnit = committed.app.gameState.units.first { it.id == unit.id }
            assertThat(updatedUnit.torsoFacing).isEqualTo(HexDirection.NE)
        }

        @Test
        fun `toggle off last weapon on primary clears primaryTargetId`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(listOf(unit, enemy), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val on = phase.handle(KeyboardEvent(" "), state, services)!!
            val onPhase = on.app.phase as AttackPhase.Declaring
            assertEquals(enemy.id, onPhase.primaryTargetId)

            val off = onPhase.handle(KeyboardEvent(" "), on.app, services)!!
            val offPhase = off.app.phase as AttackPhase.Declaring
            assertThat(offPhase.primaryTargetId).isNull()
        }

        @Test
        fun `toggle off last weapon on primary promotes secondary to primary`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser(), mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.N,
            )
            val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
            val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val targets = targetInfos(unit, phase.torsoFacing, gameState)
            val enemy1Idx = targets.indexOfFirst { it.unitId == enemy1.id }
            assertTrue(enemy1Idx >= 0)

            val setup = phase.copy(
                weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
                primaryTargetId = enemy1.id,
                cursorTargetIndex = enemy1Idx,
                cursorWeaponIndex = 0,
            )
            val state = anAppState(setup, gameState, turnState, cursor = enemy1.position)

            val result = setup.handle(KeyboardEvent(" "), state, services)!!
            val resultPhase = result.app.phase as AttackPhase.Declaring
            assertEquals(enemy2.id, resultPhase.primaryTargetId)
        }
    }

    @Nested
    inner class TabAcrossAttackersTest {

        private fun seeded(turnState: TurnState = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
            attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 3))),
            attackImpulse = ImpulseDeclarations(PlayerId.PLAYER_1),
        )): TurnState = turnState

        @Test
        fun `Tab cycles to next attacker`() {
            val unitA = aUnit(
                id = "a", weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 3), facing = HexDirection.N,
            )
            val unitB = aUnit(
                id = "b", weapons = listOf(mediumLaser()),
                position = HexCoordinates(4, 3), facing = HexDirection.N,
            )
            val enemy = aUnit(
                id = "enemy", owner = PlayerId.PLAYER_2,
                position = HexCoordinates(3, 1),
            )
            val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
            val turnState = seeded()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            val result = phaseA.handle(KeyboardEvent("Tab"), state, services)

            assertNotNull(result)
            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(unitB.id, newPhase.unitId)
            assertEquals(unitB.position, result.app.cursor)
        }

        @Test
        fun `Tab preserves previous attacker's weapon assignments when cycling back`() {
            val unitA = aUnit(
                id = "a", weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 3), facing = HexDirection.N,
            )
            val unitB = aUnit(
                id = "b", weapons = listOf(mediumLaser()),
                position = HexCoordinates(4, 3), facing = HexDirection.N,
            )
            val enemy = aUnit(
                id = "enemy", owner = PlayerId.PLAYER_2,
                position = HexCoordinates(3, 1),
            )
            val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
            val turnState = seeded()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, gameState, turnState)
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            // Toggle a weapon on A
            val toggleResult = phaseA.handle(KeyboardEvent(" "), state, services)!!
            val afterToggle = toggleResult.app.phase as AttackPhase.Declaring
            assertTrue(afterToggle.weaponAssignments[enemy.id]?.contains(0) == true)

            // Tab to B
            val toB = afterToggle.handle(KeyboardEvent("Tab"), toggleResult.app, services)!!
            val phaseB = toB.app.phase as AttackPhase.Declaring
            assertEquals(unitB.id, phaseB.unitId)

            // Tab back to A
            val backToA = phaseB.handle(KeyboardEvent("Tab"), toB.app, services)!!
            val phaseAAgain = backToA.app.phase as AttackPhase.Declaring
            assertEquals(unitA.id, phaseAAgain.unitId)

            assertTrue(phaseAAgain.weaponAssignments[enemy.id]?.contains(0) == true)
        }
    }
}
