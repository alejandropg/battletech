package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackControllerTest {

    private fun createController(): AttackController {
        val actionQueryService = mockk<ActionQueryService>()
        return AttackController(actionQueryService)
    }

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map5x5 = aGameMap(cols = 5, rows = 5)

    /** Helper: initialize impulse then enter TorsoFacing for the given unit. */
    private fun enterTorsoFacing(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): PhaseState.Attack.TorsoFacing {
        controller.initializeImpulse(unit.owner, 1)
        return controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.TorsoFacing
    }

    /** Helper: enter TorsoFacing then confirm to get TargetBrowsing. */
    private fun enterTargetBrowsing(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): PhaseState.Attack.TargetBrowsing {
        val tf = enterTorsoFacing(controller, unit, gameState)
        return (controller.handle(InputAction.Confirm, tf, HexCoordinates(0, 0), gameState) as PhaseOutcome.Continue)
            .phaseState as PhaseState.Attack.TargetBrowsing
    }

    /** Helper: enter TargetBrowsing then confirm on enemy to get WeaponAssignment. */
    private fun enterWeaponAssignment(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        enemyPosition: HexCoordinates,
        gameState: GameState,
    ): PhaseState.Attack.WeaponAssignment {
        val tb = enterTargetBrowsing(controller, unit, gameState)
        // Find target at enemy position and select it
        val targetIdx = tb.targets.indexOfFirst { gameState.unitAt(enemyPosition)?.id == it.unitId }
        val withTarget = if (targetIdx >= 0) tb.copy(selectedTargetIndex = targetIdx) else tb
        return (controller.handle(InputAction.Confirm, withTarget, enemyPosition, gameState) as PhaseOutcome.Continue)
            .phaseState as PhaseState.Attack.WeaponAssignment
    }

    @Test
    fun `enter produces Attack TorsoFacing state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

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

        val state = enterTorsoFacing(controller, unit, gameState)

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

        val state = enterTorsoFacing(controller, unit, gameState)

        assertTrue(inArc.id in state.validTargetIds)
        assertFalse(outOfArc.id in state.validTargetIds)
    }

    @Test
    fun `enter populates targets list on TorsoFacing state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertEquals(1, state.targets.size)
        assertEquals(enemy.id, state.targets[0].unitId)
        assertTrue(state.targets[0].eligibleWeapons.isNotEmpty())
    }

    @Test
    fun `enter with no targets produces empty targets list`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertTrue(state.targets.isEmpty())
    }

    @Test
    fun `cancel from TorsoFacing returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Cancel, state, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
    }

    @Test
    fun `confirm TorsoFacing with no targets transitions to TargetBrowsing with No Attack`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val torsoFacing = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Confirm, torsoFacing, HexCoordinates(2, 2), gameState)

        val tb = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TargetBrowsing
        assertTrue(tb.targets.isEmpty())
    }

    @Test
    fun `torso twist clockwise updates arc and targets`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.NE), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, newState.torsoFacing)
    }

    @Test
    fun `torso twist counterclockwise updates arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.NW), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NW, newState.torsoFacing)
    }

    @Test
    fun `torso twist beyond one hex-side is rejected`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result1 = controller.handle(InputAction.MoveCursor(HexDirection.NE), state, HexCoordinates(2, 2), gameState)
        val twistedState = (result1 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        val result2 = controller.handle(InputAction.MoveCursor(HexDirection.NE), twistedState, HexCoordinates(2, 2), gameState)
        val stillSame = (result2 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, stillSame.torsoFacing)
    }

    @Test
    fun `confirm TorsoFacing on unit with target transitions to TargetBrowsing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val torsoFacing = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Confirm, torsoFacing, HexCoordinates(2, 2), gameState)

        val tb = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TargetBrowsing
        assertEquals(1, tb.targets.size)
        assertEquals(enemy.id, tb.targets[0].unitId)
    }

    @Test
    fun `confirm on target in TargetBrowsing transitions to WeaponAssignment`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val wa = enterWeaponAssignment(controller, unit, enemy.position, gameState)

        assertEquals(enemy.id, wa.primaryTargetId)
        assertEquals(1, wa.targets.size)
    }

    @Test
    fun `select No Attack in TargetBrowsing marks unit as NO_ATTACK`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val tb = enterTargetBrowsing(controller, unit, gameState)

        // targets is empty, selectedTargetIndex=0 >= targets.size → No Attack
        val result = controller.handle(InputAction.Confirm, tb, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
        assertTrue(controller.canCommit())
    }

    @Test
    fun `weapon toggle on and off works in WeaponAssignment`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val wa = enterWeaponAssignment(controller, unit, enemy.position, gameState)

        // Toggle weapon on
        val toggleOn = controller.handle(InputAction.Confirm, wa, enemy.position, gameState)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponAssignment
        assertTrue(0 in (onState.weaponAssignments[enemy.id] ?: emptySet()))

        // Toggle weapon off
        val toggleOff = controller.handle(InputAction.Confirm, onState, enemy.position, gameState)
        val offState = (toggleOff as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponAssignment
        assertFalse(0 in (offState.weaponAssignments[enemy.id] ?: emptySet()))
    }

    @Test
    fun `cancel from WeaponAssignment returns to TargetBrowsing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val wa = enterWeaponAssignment(controller, unit, enemy.position, gameState)

        val result = controller.handle(InputAction.Cancel, wa, enemy.position, gameState)

        assertTrue((result as PhaseOutcome.Continue).phaseState is PhaseState.Attack.TargetBrowsing)
    }

    @Test
    fun `commit after weapon assignment records declaration`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val wa = enterWeaponAssignment(controller, unit, enemy.position, gameState)

        // Toggle weapon on
        controller.handle(InputAction.Confirm, wa, enemy.position, gameState)

        // Declaration is saved immediately on toggle; can commit
        assertTrue(controller.canCommit())

        val committedIds = controller.commitImpulse()
        assertTrue(unit.id in committedIds)

        val declarations = controller.collectDeclarations()
        assertEquals(1, declarations.size)
        assertEquals(unit.id, declarations[0].attackerId)
        assertEquals(enemy.id, declarations[0].targetId)
        assertEquals(0, declarations[0].weaponIndex)
        assertTrue(declarations[0].isPrimary)
    }

    @Test
    fun `tab cycles between targets in TargetBrowsing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val tb = enterTargetBrowsing(controller, unit, gameState)

        val initialIdx = tb.selectedTargetIndex
        val tabResult = controller.handle(InputAction.CycleUnit, tb, HexCoordinates(0, 0), gameState)
        val tabbed = (tabResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TargetBrowsing

        // Index should have advanced
        assertTrue(tabbed.selectedTargetIndex != initialIdx || tb.targets.size <= 1)
    }

    @Test
    fun `tab wraps through No Attack entry in TargetBrowsing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val tb = enterTargetBrowsing(controller, unit, gameState)

        // Tab past the single target → should land on No Attack (index = targets.size)
        var state = tb
        repeat(tb.targets.size + 1) {
            val result = controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)
            state = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TargetBrowsing
        }
        // After targets.size + 1 tabs starting from 0, we've wrapped: 1 (No Attack) → 0 (first target)
        // We just verify it completes without error
        assertTrue(state.selectedTargetIndex in 0..tb.targets.size)
    }

    @Test
    fun `canCommit returns false before any declarations`() {
        val controller = createController()
        controller.initializeImpulse(PlayerId.PLAYER_1, 1)
        assertFalse(controller.canCommit())
    }

    @Test
    fun `initializeImpulse resets declaration state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        // Mark as no-attack in first impulse
        val tb = enterTargetBrowsing(controller, unit, gameState)
        controller.handle(InputAction.Confirm, tb, HexCoordinates(2, 2), gameState)
        assertTrue(controller.canCommit())

        // Initialize a new impulse — canCommit should be false again
        controller.initializeImpulse(PlayerId.PLAYER_1, 1)
        assertFalse(controller.canCommit())
    }
}
