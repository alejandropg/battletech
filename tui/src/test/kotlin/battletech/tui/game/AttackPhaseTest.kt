package battletech.tui.game

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerView
import battletech.tactical.session.Impulse
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitRoster
import battletech.tui.aGameMap
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.commitAttackImpulse
import battletech.tui.game.phase.enterDeclaring
import battletech.tui.input.AttackAction
import battletech.tui.mediumLaser
import battletech.tui.viewFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class AttackPhaseTest {

    private val map5x5 = aGameMap(cols = 5, rows = 5)
    private val map7x7 = aGameMap(cols = 7, rows = 7)

    private fun baseTurnState(): TurnState = aTurnState(attackOrder = listOf(Impulse(PlayerId.PLAYER_1, 3)))

    private fun anAppState(
        phase: battletech.tui.game.phase.Phase,
        gameState: GameState,
        turnState: TurnState,
        cursor: HexCoordinates = HexCoordinates(0, 0),
    ) = AppState(gameState, turnState, phase, cursor)

    private fun viewFor(unit: CombatUnit, gameState: GameState): PlayerView =
        viewFor(unit.owner, gameState)

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
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)

            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))

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
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)

            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))

            assertTrue(viewFor(unit, gameState).validTargets(unit.id, phase.torsoFacing).isEmpty())
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
            val gameState = GameState(UnitRoster(listOf(unitA, unitB, enemy)), map7x7)
            val turnState = baseTurnState()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, viewFor(unitA, gameState))
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            // Toggle weapon on A
            val afterToggle = phaseA.handle(AttackAction.ToggleWeapon, state)!!
            val toggled = afterToggle.app.phase as AttackPhase.Declaring
            assertTrue(toggled.weaponAssignments[enemy.id]?.contains(0) == true)

            // Tab to B
            val toB = toggled.handle(AttackAction.NextAttacker, afterToggle.app)!!
            val phaseB = toB.app.phase as AttackPhase.Declaring

            // Tab back to A
            val backToA = phaseB.handle(AttackAction.NextAttacker, toB.app)!!
            val phaseAAgain = backToA.app.phase as AttackPhase.Declaring

            assertEquals(unitA.id, phaseAAgain.unitId)
            assertTrue(phaseAAgain.weaponAssignments[enemy.id]?.contains(0) == true)
        }

        @Test
        fun `torso twist clockwise updates torso facing`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(UnitRoster(listOf(unit)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val result = phase.handle(AttackAction.TwistTorso(clockwise = true), state)

            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, newPhase.torsoFacing)
        }

        @Test
        fun `torso twist counterclockwise updates torso facing`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(UnitRoster(listOf(unit)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val result = phase.handle(AttackAction.TwistTorso(clockwise = false), state)

            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NW, newPhase.torsoFacing)
        }

        @Test
        fun `d key twists torso clockwise same as arrow right`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(UnitRoster(listOf(unit)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val result = phase.handle(AttackAction.TwistTorso(clockwise = true), state)

            val newPhase = result!!.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, newPhase.torsoFacing)
        }

        @Test
        fun `torso twist beyond one hex-side is rejected`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(UnitRoster(listOf(unit)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val once = phase.handle(AttackAction.TwistTorso(clockwise = true), state)!!
            val twisted = once.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, twisted.torsoFacing)

            val twice = twisted.handle(AttackAction.TwistTorso(clockwise = true), once.app)!!
            val stillSame = twice.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, stillSame.torsoFacing)
        }

        @Test
        fun `weapon toggle on and off works`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val on = phase.handle(AttackAction.ToggleWeapon, state)!!
            val onPhase = on.app.phase as AttackPhase.Declaring
            assertTrue(0 in (onPhase.weaponAssignments[enemy.id] ?: emptySet()))

            val off = onPhase.handle(AttackAction.ToggleWeapon, on.app)!!
            val offPhase = off.app.phase as AttackPhase.Declaring
            assertFalse(0 in (offPhase.weaponAssignments[enemy.id] ?: emptySet()))
        }

        @Test
        fun `commit after weapon assignment saves declaration`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = aTurnState(attackOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)))
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val toggled = phase.handle(AttackAction.ToggleWeapon, state)!!
            val togglePhase = toggled.app.phase as AttackPhase.Declaring
            assertTrue(togglePhase.weaponAssignments[enemy.id]?.contains(0) == true)

            // Commit — last impulse so resolve and advance to physical attack
            val committed = togglePhase.handle(AttackAction.Commit, toggled.app)!!
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
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            // Twist torso clockwise (N -> NE)
            val twisted = phase.handle(AttackAction.TwistTorso(clockwise = true), state)!!
            val twistedPhase = twisted.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, twistedPhase.torsoFacing)

            // Commit the impulse via the helper
            val committed = commitAttackImpulse(twisted.app, twistedPhase.allDrafts())

            val updatedUnit = committed.app.state.units.first { it.id == unit.id }
            assertThat(updatedUnit.torsoFacing).isEqualTo(HexDirection.NE)
        }

        @Test
        fun `toggle off last weapon on primary clears primaryTargetId`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val on = phase.handle(AttackAction.ToggleWeapon, state)!!
            val onPhase = on.app.phase as AttackPhase.Declaring
            assertEquals(enemy.id, onPhase.primaryTargetId)

            val off = onPhase.handle(AttackAction.ToggleWeapon, on.app)!!
            val offPhase = off.app.phase as AttackPhase.Declaring
            assertThat(offPhase.primaryTargetId).isNull()
        }

        @Test
        fun `escape cancels back to SelectingAttacker preserving draft`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = enemy.position)

            val toggled = phase.handle(AttackAction.ToggleWeapon, state)!!
            val toggledPhase = toggled.app.phase as AttackPhase.Declaring
            assertTrue(toggledPhase.weaponAssignments[enemy.id]?.contains(0) == true)

            val cancelled = toggledPhase.handle(AttackAction.Cancel, toggled.app)!!
            val selecting = cancelled.app.phase as AttackPhase.SelectingAttacker
            assertTrue(selecting.drafts[unit.id]?.weaponAssignments?.get(enemy.id)?.contains(0) == true)
        }

        @Test
        fun `escape leaves the twisted unit's draft torso visible in SelectingAttacker's render`() {
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val gameState = GameState(UnitRoster(listOf(unit)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val twisted = phase.handle(AttackAction.TwistTorso(clockwise = true), state)!!
            val twistedPhase = twisted.app.phase as AttackPhase.Declaring
            assertEquals(HexDirection.NE, twistedPhase.torsoFacing)

            val cancelled = twistedPhase.handle(AttackAction.Cancel, twisted.app)!!
            val selecting = cancelled.app.phase as AttackPhase.SelectingAttacker

            assertEquals(HexDirection.NE, selecting.board(cancelled.app).draftTorsoFacings[unit.position])
        }

        @Test
        fun `commit clears drafted torso overrides from the next SelectingAttacker render`() {
            // Two impulses (player 1, then player 2 — aTurnState()'s default order) so the
            // WEAPON_ATTACK phase isn't complete after player 1's commit — unlike baseTurnState()'s
            // single-impulse order used in "commit after weapon assignment saves declaration" above
            // — leaving a fresh SelectingAttacker to inspect.
            val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val turnState = aTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val state = anAppState(phase, gameState, turnState, cursor = unit.position)

            val twisted = phase.handle(AttackAction.TwistTorso(clockwise = true), state)!!
            val twistedPhase = twisted.app.phase as AttackPhase.Declaring

            val committed = commitAttackImpulse(twisted.app, twistedPhase.allDrafts())

            assertEquals(TurnPhase.WEAPON_ATTACK, committed.app.currentPhase)
            val selecting = committed.app.phase as AttackPhase.SelectingAttacker
            assertTrue(selecting.drafts.isEmpty())
            assertEquals(RenderData.EMPTY, selecting.board(committed.app))
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
            val gameState = GameState(UnitRoster(listOf(unit, enemy1, enemy2)), map5x5)
            val turnState = baseTurnState()
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))
            val targets = viewFor(unit, gameState).targetInfos(unit.id, phase.torsoFacing)
            val enemy1Idx = targets.indexOfFirst { it.unitId == enemy1.id }
            assertTrue(enemy1Idx >= 0)

            val setup = phase.copy(
                allocation = phase.allocation.copy(
                    weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
                    primaryTargetId = enemy1.id,
                    cursorTargetIndex = enemy1Idx,
                    cursorWeaponIndex = 0,
                ),
            )
            val state = anAppState(setup, gameState, turnState, cursor = enemy1.position)

            val result = setup.handle(AttackAction.ToggleWeapon, state)!!
            val resultPhase = result.app.phase as AttackPhase.Declaring
            assertEquals(enemy2.id, resultPhase.primaryTargetId)
        }
    }

    @Nested
    inner class TargetStatusUnitTest {

        @Test
        fun `targetStatusUnit returns null for SelectingAttacker`() {
            val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
            val gameState = GameState(UnitRoster(emptyList()), aGameMap())

            assertNull(phase.panels(anAppState(phase, gameState, baseTurnState())).targetStatus)
        }

        @Test
        fun `targetStatusUnit returns null for Declaring with no targets`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.N,
            )
            val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 4))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))

            assertNull(phase.panels(anAppState(phase, gameState, baseTurnState())).targetStatus)
        }

        @Test
        fun `targetStatusUnit returns a ForeignUnit for cursor target`() {
            val unit = aUnit(
                weapons = listOf(mediumLaser()),
                position = HexCoordinates(2, 2),
                facing = HexDirection.NE,
            )
            val enemy = aUnit(id = "enemy", name = "Centurion", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
            val gameState = GameState(UnitRoster(listOf(unit, enemy)), map5x5)
            val phase = enterDeclaring(unit, TurnPhase.WEAPON_ATTACK, viewFor(unit, gameState))

            val result = phase.panels(anAppState(phase, gameState, baseTurnState())).targetStatus

            assertNotNull(result)
            assertEquals("Centurion", result!!.name)
        }
    }

    @Nested
    inner class TabAcrossAttackersTest {

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
            val gameState = GameState(UnitRoster(listOf(unitA, unitB, enemy)), map7x7)
            val turnState = baseTurnState()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, viewFor(unitA, gameState))
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            val result = phaseA.handle(AttackAction.NextAttacker, state)

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
            val gameState = GameState(UnitRoster(listOf(unitA, unitB, enemy)), map7x7)
            val turnState = baseTurnState()
            val phaseA = enterDeclaring(unitA, TurnPhase.WEAPON_ATTACK, viewFor(unitA, gameState))
            val state = anAppState(phaseA, gameState, turnState, cursor = unitA.position)

            // Toggle a weapon on A
            val toggleResult = phaseA.handle(AttackAction.ToggleWeapon, state)!!
            val afterToggle = toggleResult.app.phase as AttackPhase.Declaring
            assertTrue(afterToggle.weaponAssignments[enemy.id]?.contains(0) == true)

            // Tab to B
            val toB = afterToggle.handle(AttackAction.NextAttacker, toggleResult.app)!!
            val phaseB = toB.app.phase as AttackPhase.Declaring
            assertEquals(unitB.id, phaseB.unitId)

            // Tab back to A
            val backToA = phaseB.handle(AttackAction.NextAttacker, toB.app)!!
            val phaseAAgain = backToA.app.phase as AttackPhase.Declaring
            assertEquals(unitA.id, phaseAAgain.unitId)

            assertTrue(phaseAAgain.weaponAssignments[enemy.id]?.contains(0) == true)
        }
    }
}
