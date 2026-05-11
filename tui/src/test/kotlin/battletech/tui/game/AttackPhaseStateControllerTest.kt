package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.AttackAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackPhaseStateControllerTest {

    private fun createController(): AttackController = AttackController()

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map5x5 = aGameMap(cols = 5, rows = 5)

    /** Helper: initialize impulse then enter Declaring for the given unit. */
    private fun enterDeclaring(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): AttackPhaseState {
        controller.initializeImpulse(unit.owner)
        return controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
    }

    @Test
    fun `enter produces Attack Declaring state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterDeclaring(controller, unit, gameState)

        assertEquals(unit.id, state.unitId)
        assertEquals(unit.facing, state.torsoFacing)
    }

    @Test
    fun `enter with no enemies in arc shows empty targets`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 4))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterDeclaring(controller, unit, gameState)

        assertTrue(state.validTargetIds.isEmpty())
    }

    @Test
    fun `enter finds valid targets in arc`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val inArc = aUnit(id = "in-arc", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val outOfArc = aUnit(id = "out-of-arc", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 4))
        val gameState = GameState(listOf(unit, inArc, outOfArc), aGameMap(cols = 6, rows = 6))

        val state = enterDeclaring(controller, unit, gameState)

        assertTrue(inArc.id in state.validTargetIds)
        assertFalse(outOfArc.id in state.validTargetIds)
    }

    @Test
    fun `enter populates targets list with weapons`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterDeclaring(controller, unit, gameState)

        assertEquals(1, state.targets.size)
        assertEquals(enemy.id, state.targets[0].unitId)
        assertTrue(state.targets[0].weapons.isNotEmpty())
    }

    @Test
    fun `enter with no targets produces empty targets list`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        val state = enterDeclaring(controller, unit, gameState)

        assertTrue(state.targets.isEmpty())
    }

    @Test
    fun `cancel saves assignments and returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Toggle a weapon on, then cancel
        val toggled = controller.handle(AttackAction.ToggleWeapon, state, HexCoordinates(2, 2), gameState)
        val toggledState = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val result = controller.handle(AttackAction.Cancel, toggledState, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
        // After cancel, the toggled weapon should still be recorded for commit
        assertEquals(1, controller.commitImpulse().unitIds.size)
    }

    @Test
    fun `weapon assignments preserved when cancelling and re-entering the same unit`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on, then cancel
        val toggled = controller.handle(AttackAction.ToggleWeapon, state, HexCoordinates(2, 2), gameState)
        val toggledState = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        controller.handle(AttackAction.Cancel, toggledState, HexCoordinates(2, 2), gameState)

        // Re-enter the same unit — assignments should be restored
        val reEntered = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
        assertTrue(reEntered.weaponAssignments.values.any { it.isNotEmpty() },
            "Expected weapon assignments to survive cancel+re-enter, but got: ${reEntered.weaponAssignments}")
    }

    @Test
    fun `weapon assignments preserved when switching to another unit and back`() {
        val controller = createController()
        val unitA = aUnit(id = "A", weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val unitB = aUnit(id = "B", weapons = listOf(mediumLaser()), position = HexCoordinates(3, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unitA, unitB, enemy), map5x5)

        controller.initializeImpulse(PlayerId.PLAYER_1)

        // Enter A, toggle weapon, exit via Cancel (Esc)
        val stateA = controller.enter(unitA, TurnPhase.WEAPON_ATTACK, gameState)
        val toggledA = (controller.handle(AttackAction.ToggleWeapon, stateA, enemy.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        controller.handle(AttackAction.Cancel, toggledA, enemy.position, gameState)

        // Enter B, exit via Cancel
        val stateB = controller.enter(unitB, TurnPhase.WEAPON_ATTACK, gameState)
        controller.handle(AttackAction.Cancel, stateB, enemy.position, gameState)

        // Re-enter A — weapon assignments must survive the trip through B
        val reEnteredA = controller.enter(unitA, TurnPhase.WEAPON_ATTACK, gameState)
        assertTrue(reEnteredA.weaponAssignments.values.any { it.isNotEmpty() },
            "Expected A's assignments to survive switching to B and back, but got: ${reEnteredA.weaponAssignments}")
    }

    @Test
    fun `torso twist persists after leaving and re-entering`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Twist torso, then exit without toggling any weapon
        val twisted = (controller.handle(AttackAction.TwistTorso(clockwise = true), state, unit.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twisted.torsoFacing)
        controller.handle(AttackAction.Cancel, twisted, unit.position, gameState)

        // Re-enter — torso facing should still be NE
        val reEntered = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
        assertEquals(HexDirection.NE, reEntered.torsoFacing)
    }

    @Test
    fun `confirm with no weapons assigned records nothing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val result = controller.handle(AttackAction.Confirm, state, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
        // Confirm without toggling weapons leaves the declarations map empty
        assertTrue(controller.commitImpulse().unitIds.isEmpty())
    }

    @Test
    fun `torso twist clockwise updates arc and targets`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val result = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, newState.torsoFacing)
    }

    @Test
    fun `torso twist counterclockwise updates arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val result = controller.handle(AttackAction.TwistTorso(clockwise = false), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NW, newState.torsoFacing)
    }

    @Test
    fun `torso twist beyond one hex-side is rejected`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val result1 = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState)
        val twistedState = (result1 as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        val result2 = controller.handle(AttackAction.TwistTorso(clockwise = true), twistedState, HexCoordinates(2, 2), gameState)
        val stillSame = (result2 as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, stillSame.torsoFacing)
    }

    @Test
    fun `weapon toggle on and off works with ToggleWeapon`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val toggleOn = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertTrue(0 in (onState.weaponAssignments[enemy.id] ?: emptySet()))

        // Toggle weapon off
        val toggleOff = controller.handle(AttackAction.ToggleWeapon, onState, enemy.position, gameState)
        val offState = (toggleOff as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertFalse(0 in (offState.weaponAssignments[enemy.id] ?: emptySet()))
    }

    @Test
    fun `confirm after weapon assignment saves declaration`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val toggleOn = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Confirm
        val result = controller.handle(AttackAction.Confirm, onState, enemy.position, gameState)
        assertTrue(result is PhaseOutcome.Cancelled)

        val commitResult = controller.commitImpulse()
        assertTrue(unit.id in commitResult.unitIds)

        val declarations = controller.collectDeclarations()
        assertEquals(1, declarations.size)
        assertEquals(unit.id, declarations[0].attackerId)
        assertEquals(enemy.id, declarations[0].targetId)
        assertEquals(0, declarations[0].weaponIndex)
        assertTrue(declarations[0].isPrimary)
    }

    @Test
    fun `NextTarget jumps to first weapon of next target`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val initialIdx = state.cursorTargetIndex
        val tabResult = controller.handle(AttackAction.NextTarget, state, HexCoordinates(0, 0), gameState)
        val tabbed = (tabResult as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Index should have advanced
        assertTrue(tabbed.cursorTargetIndex != initialIdx || state.targets.size <= 1)
    }

    @Test
    fun `NextTarget wraps around targets`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // Tab on a single target should wrap back to the same target
        val result = controller.handle(AttackAction.NextTarget, state, HexCoordinates(0, 0), gameState)
        val tabbed = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(0, tabbed.cursorTargetIndex)
    }

    @Test
    fun `NavigateWeapons down moves to next weapon within target`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val result = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState)
        val moved = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Should have moved to weapon index 1 (or wrapped if only 1 available)
        assertTrue(moved.cursorWeaponIndex != state.cursorWeaponIndex || state.targets[0].weapons.size == 1)
    }

    @Test
    fun `NavigateWeapons from last weapon wraps to first weapon`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)
        // Start at target 0, weapon 0 (only 1 weapon, 1 target)
        assertEquals(0, state.cursorTargetIndex)
        assertEquals(0, state.cursorWeaponIndex)

        val result = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState)
        val moved = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Only 1 weapon total, wraps back to same position
        assertEquals(0, moved.cursorTargetIndex)
        assertEquals(0, moved.cursorWeaponIndex)
    }

    @Test
    fun `initializeImpulse resets declaration state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        // Toggle a weapon to record a declaration
        val state = enterDeclaring(controller, unit, gameState)
        controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState)

        // Re-initializing wipes pending declarations for the new impulse
        controller.initializeImpulse(PlayerId.PLAYER_1)
        assertTrue(controller.commitImpulse().unitIds.isEmpty())
    }

    @Test
    fun `torso twist clears assignments for targets that leave the arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), aGameMap(cols = 5, rows = 5))
        val state = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val toggled = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState)
        val withWeapon = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertTrue(withWeapon.weaponAssignments.values.any { it.isNotEmpty() })

        // Twist torso away — if enemy leaves the arc, assignments should be cleared
        val twisted = controller.handle(AttackAction.TwistTorso(clockwise = true), withWeapon, HexCoordinates(2, 2), gameState)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // If enemy is no longer in arc, assignments should be empty
        if (enemy.id !in twistedState.validTargetIds) {
            assertTrue(twistedState.weaponAssignments.values.all { it.isEmpty() })
        }
    }

    @Test
    fun `TwistTorso and NavigateWeapons work independently`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        // TwistTorso clockwise
        val twisted = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        // NavigateWeapons down
        val navigated = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState)
        val navigatedState = (navigated as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(state.torsoFacing, navigatedState.torsoFacing) // torso unchanged
        assertEquals(1, navigatedState.cursorWeaponIndex) // cursor moved
    }

    @Test
    fun `commitImpulse returns torso facings for committed units`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterDeclaring(controller, unit, gameState)
        // Twist torso clockwise (N -> NE)
        val twisted = controller.handle(AttackAction.TwistTorso(clockwise = true), state, unit.position, gameState)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        controller.handle(AttackAction.Confirm, twistedState, unit.position, gameState)

        val result = controller.commitImpulse()

        assertThat(result.unitIds).containsExactly(unit.id)
        assertThat(result.torsoFacings).containsEntry(unit.id, HexDirection.NE)
    }

    @Test
    fun `toggle off last weapon on primary with no secondary clears primaryTargetId`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val onState = (controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy.id, onState.primaryTargetId)

        val offState = (controller.handle(AttackAction.ToggleWeapon, onState, enemy.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertThat(offState.primaryTargetId).isNull()
    }

    @Test
    fun `toggle off last weapon on primary promotes secondary to primary`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val enemy1Idx = state.targets.indexOfFirst { it.unitId == enemy1.id }
        assertTrue(enemy1Idx >= 0, "enemy1 must be a valid target")

        // Directly set up: weapon 0 → enemy1 (primary), weapon 1 → enemy2 (secondary)
        val setup = state.copy(
            weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
            primaryTargetId = enemy1.id,
            cursorTargetIndex = enemy1Idx,
            cursorWeaponIndex = 0,
        )

        // Toggle off weapon 0 from enemy1 → enemy2 should become primary
        val result = (controller.handle(AttackAction.ToggleWeapon, setup, enemy1.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy2.id, result.primaryTargetId)
    }

    @Test
    fun `toggle off last weapon on secondary leaves primary unchanged`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val state = enterDeclaring(controller, unit, gameState)

        val enemy2Idx = state.targets.indexOfFirst { it.unitId == enemy2.id }
        assertTrue(enemy2Idx >= 0, "enemy2 must be a valid target")

        // Directly set up: weapon 0 → enemy1 (primary), weapon 1 → enemy2 (secondary)
        val setup = state.copy(
            weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
            primaryTargetId = enemy1.id,
            cursorTargetIndex = enemy2Idx,
            cursorWeaponIndex = 1,
        )

        // Toggle off weapon 1 from enemy2 → enemy1 remains primary
        val result = (controller.handle(AttackAction.ToggleWeapon, setup, enemy2.position, gameState) as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy1.id, result.primaryTargetId)
    }
}
